package com.twomemory.app.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** S3-compatible private object storage, configured for Alibaba Cloud OSS in production. */
@Component
@ConditionalOnProperty(name = "moon-letter.storage.mode", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {

    private final S3StorageProperties properties;
    private final S3Presigner presigner;
    private final S3Client client;

    @Autowired
    public S3ObjectStorage(
            @Value("${moon-letter.storage.s3.endpoint:}") String endpoint,
            @Value("${moon-letter.storage.s3.region:}") String region,
            @Value("${moon-letter.storage.s3.bucket:}") String bucket,
            @Value("${moon-letter.storage.s3.access-key:}") String accessKey,
            @Value("${moon-letter.storage.s3.secret-key:}") String secretKey,
            @Value("${moon-letter.storage.s3.path-style-access:false}") boolean pathStyleAccess) {
        this(new S3StorageProperties(endpoint, region, bucket, accessKey, secretKey, pathStyleAccess), null, null);
    }

    S3ObjectStorage(S3StorageProperties properties, S3Presigner presigner, S3Client client) {
        this.properties = properties;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        this.presigner = presigner != null ? presigner : S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess()).build())
                .build();
        this.client = client != null ? client : S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .requestChecksumCalculation(software.amazon.awssdk.core.checksums.RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(software.amazon.awssdk.core.checksums.ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder()
                        .chunkedEncodingEnabled(false)
                        .pathStyleAccessEnabled(properties.pathStyleAccess()).build())
                .build();
    }

    @Override
    public PresignedUpload presignPut(
            String objectKey, String mimeType, long byteSize, String sha256, Instant expiresAt) {
        if (expiresAt.isBefore(Instant.now().plusSeconds(1))) {
            throw new MediaValidationException("upload ticket expiration must be in the future");
        }
        // Compatibility transport hint understood by existing APKs. No external PUT capability
        // is issued: verified originals are written only through the authenticated API.
        return new PresignedUpload("local://" + objectKey, expiresAt);
    }

    @Override public void putVerified(String objectKey, java.io.InputStream input, long expectedSize,
        String expectedHash, String mimeType) throws java.io.IOException {
        var file=java.nio.file.Files.createTempFile("moonletter-oss-", ".part");
        try {
            var digest=MessageDigest.getInstance("SHA-256"); long size=0;
            try(var output=java.nio.file.Files.newOutputStream(file)) {
                byte[] buffer=new byte[8192]; int count;
                while((count=input.read(buffer))!=-1) {
                    size+=count;
                    if(size>expectedSize) throw new MediaValidationException("uploaded object exceeds ticket");
                    digest.update(buffer,0,count); output.write(buffer,0,count);
                }
            }
            if(size!=expectedSize || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash))
                throw new MediaValidationException("uploaded object size/checksum does not match ticket");
            client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(objectKey)
                .contentLength(expectedSize).contentType(mimeType)
                .metadata(java.util.Map.of("sha256",expectedHash.toLowerCase(java.util.Locale.ROOT))).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromFile(file));
        } catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        finally { java.nio.file.Files.deleteIfExists(file); }
    }

    @Override
    public ObjectMetadata head(String objectKey) {
        HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket()).key(objectKey).build());
        String sha256 = response.checksumSHA256();
        if (sha256 != null && !sha256.isBlank()) {
            byte[] digest = java.util.Base64.getDecoder().decode(sha256);
            if (digest.length != 32) throw new MediaValidationException("invalid S3 checksum header");
            sha256 = HexFormat.of().formatHex(digest);
        }
        if (sha256 == null || sha256.isBlank()) {
            sha256 = response.metadata().get("sha256");
        }
        if (sha256 == null || sha256.isBlank()) {
            // OSS-compatible endpoints do not all expose a checksum header. The
            // object is capped by MediaService, so compute the digest as a safe
            // fallback instead of marking a valid upload READY without proof.
            try(var stream=open(objectKey)) {
                var digest=MessageDigest.getInstance("SHA-256");
                byte[] buffer=new byte[8192]; int count;
                while((count=stream.read(buffer))!=-1) digest.update(buffer,0,count);
                sha256=HexFormat.of().formatHex(digest.digest());
            } catch(Exception exception) { throw new IllegalStateException("could not verify S3 object",exception); }
        }
        return new ObjectMetadata(sha256, response.contentLength(), response.contentType());
    }

    @Override
    public byte[] read(String objectKey) {
        return client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(properties.bucket()).key(objectKey).build()).asByteArray();
    }

    @Override public java.io.InputStream open(String objectKey) {
        return client.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("could not hash S3 object", exception);
        }
    }
}

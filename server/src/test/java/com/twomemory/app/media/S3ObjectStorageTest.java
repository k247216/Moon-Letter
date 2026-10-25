package com.twomemory.app.media;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3ObjectStorageTest {
    @Test void onlyVerifiedBytesReachOssAndChecksumMetadataIsServerGenerated() throws Exception {
        var client=mock(S3Client.class);
        byte[] original={1,2,3};
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original));
        try(var presigner=S3Presigner.builder().endpointOverride(URI.create("http://localhost:9000"))
            .region(Region.AP_EAST_1).credentialsProvider(credentials()).build()) {
            var storage=new S3ObjectStorage(properties(),presigner,client);
            assertThatThrownBy(()->storage.putVerified("original",new java.io.ByteArrayInputStream(new byte[]{9,9,9}),3,hash,"image/png"))
                .isInstanceOf(MediaValidationException.class);
            org.mockito.Mockito.verifyNoInteractions(client);
            org.mockito.Mockito.when(client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenAnswer(invocation->{
                    software.amazon.awssdk.services.s3.model.PutObjectRequest request=invocation.getArgument(0);
                    software.amazon.awssdk.core.sync.RequestBody body=invocation.getArgument(1);
                    assertThat(request.metadata().get("sha256")).isEqualTo(hash);
                    try(var stream=body.contentStreamProvider().newStream()) { assertThat(stream.readAllBytes()).isEqualTo(original); }
                    return software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build();
                });
            storage.putVerified("original",new java.io.ByteArrayInputStream(original),3,hash,"image/png");
        }
    }

    @Test void checksumHeaderIsNormalizedToHexBeforeCompletionComparesIt() {
        var client=mock(S3Client.class);
        byte[] digest=new byte[32]; java.util.Arrays.fill(digest,(byte)0xaa);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
            .contentLength(4L).checksumSHA256(java.util.Base64.getEncoder().encodeToString(digest)).build());
        try(var presigner=S3Presigner.builder().endpointOverride(URI.create("http://localhost:9000"))
            .region(Region.AP_EAST_1).credentialsProvider(credentials()).build()) {
            assertThat(new S3ObjectStorage(properties(),presigner,client).head("original").sha256()).isEqualTo("a".repeat(64));
        }
    }

    @Test
    void rejectsIncompleteProductionConfiguration() {
        assertThatThrownBy(() -> new S3StorageProperties(
                "", "cn-hangzhou", "moon-letter", "access", "secret", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void neverHandsClientAnOssOverwriteUrl() {
        S3StorageProperties properties = properties();
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create("http://localhost:9000"))
                .region(Region.AP_EAST_1)
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            S3ObjectStorage storage = new S3ObjectStorage(properties, presigner, mock(S3Client.class));
            Instant expiresAt = Instant.now().plusSeconds(600);

            ObjectStorage.PresignedUpload ticket = storage.presignPut(
                    "couples/couple/assets/photo.jpg", "image/jpeg", 42, "a".repeat(64), expiresAt);

            assertThat(ticket.url()).startsWith("local://");
            assertThat(ticket.expiresAt()).isEqualTo(expiresAt);
        }
    }

    @Test
    void readsObjectMetadataThroughTheConfiguredClient() {
        S3StorageProperties properties = properties();
        S3Client client = mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentLength(42L).contentType("image/jpeg")
                        .metadata(java.util.Map.of("sha256", "a".repeat(64))).build());
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create("http://localhost:9000"))
                .region(Region.AP_EAST_1)
                .credentialsProvider(credentials())
                .build()) {
            S3ObjectStorage storage = new S3ObjectStorage(properties, presigner, client);

            ObjectStorage.ObjectMetadata metadata = storage.head("couples/couple/assets/photo.jpg");

            assertThat(metadata.byteSize()).isEqualTo(42L);
            assertThat(metadata.mimeType()).isEqualTo("image/jpeg");
            assertThat(metadata.sha256()).isEqualTo("a".repeat(64));
        }
    }

    private static S3StorageProperties properties() {
        return new S3StorageProperties(
                "http://localhost:9000", "cn-hangzhou", "moon-letter", "access", "secret", true);
    }

    private static StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("access", "secret"));
    }
}

package com.twomemory.app.media;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Standalone deployment check, not an HTTP endpoint. */
public final class OssDeploymentProbe {
    static void verify(ObjectStorage storage, String key, byte[] bytes) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        try(var input=new java.io.ByteArrayInputStream(bytes)) {
            storage.putVerified(key,input,bytes.length,digest,"image/png");
        }
        ObjectStorage.ObjectMetadata metadata = storage.head(key);
        if (metadata.byteSize() != bytes.length || !digest.equals(metadata.sha256()))
            throw new ProbeFailure("OBJECT_METADATA_MISMATCH");
        if (!Arrays.equals(bytes, storage.read(key))) throw new ProbeFailure("DOWNLOAD_CONTENT_MISMATCH");
    }

    public static void main(String[] args) {
        try {
            S3ObjectStorage storage = new S3ObjectStorage(env("S3_ENDPOINT"), env("S3_REGION"),
                    env("S3_BUCKET"), env("S3_ACCESS_KEY"), env("S3_SECRET_KEY"),
                    Boolean.parseBoolean(System.getenv("S3_PATH_STYLE_ACCESS")));
            byte[] png = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ioAAAAASUVORK5CYII=");
            String key = "couples/_deployment_probe/assets/" + UUID.randomUUID() + ".png";
            verify(storage,key,png);
            System.out.println("PASS: OSS verified streaming write, metadata checksum and identical download");
            System.out.println("TEST_OBJECT=" + key);
            System.out.println("Tiny test object retained; no user data or database modified.");
        } catch (Exception exception) {
            // Never print stack traces, messages or signed URLs containing credentials.
            String code = exception instanceof ProbeFailure ? exception.getMessage()
                    : exception instanceof S3Exception s3 ? "OSS_HTTP_" + s3.statusCode()
                    : exception.getClass().getSimpleName();
            System.err.println("FAIL: " + code);
            System.exit(1);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }

    private static final class ProbeFailure extends Exception {
        ProbeFailure(String code) {super(code);}
    }
}

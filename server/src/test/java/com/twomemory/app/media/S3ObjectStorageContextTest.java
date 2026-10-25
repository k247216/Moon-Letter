package com.twomemory.app.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class S3ObjectStorageContextTest {
    @Test
    void springConstructsConfiguredStorageAndIssuesUploadTicket() {
        new ApplicationContextRunner()
                .withUserConfiguration(S3ObjectStorage.class)
                .withPropertyValues(
                        "moon-letter.storage.mode=s3",
                        "moon-letter.storage.s3.endpoint=https://oss-cn-guangzhou.aliyuncs.com",
                        "moon-letter.storage.s3.region=oss-cn-guangzhou",
                        "moon-letter.storage.s3.bucket=moonletter-context-test",
                        "moon-letter.storage.s3.access-key=test-access",
                        "moon-letter.storage.s3.secret-key=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3ObjectStorage.class);
                    ObjectStorage storage = context.getBean(ObjectStorage.class);
                    ObjectStorage.PresignedUpload ticket = storage.presignPut(
                            "couples/test/assets/test", "image/png", 42,
                            "a".repeat(64), Instant.now().plusSeconds(300));
                    assertThat(ticket.url()).startsWith("local://");
                });
    }
}

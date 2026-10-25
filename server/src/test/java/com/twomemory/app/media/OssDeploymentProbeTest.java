package com.twomemory.app.media;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;

class OssDeploymentProbeTest {
    @Test void uploadsChecksMetadataAndReadsIdenticalBytes() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        byte[] bytes = {1, 2, 3};
        OssDeploymentProbe.verify(storage, "couples/probe/assets/test", bytes);
        assertThat(storage.bytes).containsExactly(bytes);
        assertThat(storage.reads).isEqualTo(1);
    }
    @Test void rejectsFailedUploadBeforeReading() {
        MemoryStorage storage = new MemoryStorage();
        storage.failWrite=true;
        assertThatThrownBy(() -> OssDeploymentProbe.verify(storage, "couples/probe/assets/test", new byte[]{1}))
                .hasMessage("UPLOAD_FAILED");
        assertThat(storage.reads).isZero();
    }
    @Test void rejectsChangedDownloadedContent() {
        MemoryStorage storage = new MemoryStorage();
        storage.changeRead = true;
        assertThatThrownBy(() -> OssDeploymentProbe.verify(storage, "couples/probe/assets/test", new byte[]{1}))
                .hasMessage("DOWNLOAD_CONTENT_MISMATCH");
    }
    private static class MemoryStorage implements ObjectStorage {
        byte[] bytes;
        int reads;
        boolean changeRead;
        boolean failWrite;
        public void putVerified(String key,java.io.InputStream input,long length,String hash,String mime) throws java.io.IOException {
            if(failWrite) throw new java.io.IOException("UPLOAD_FAILED");
            bytes=input.readAllBytes();
        }
        public PresignedUpload presignPut(String key, String mime, long length, String checksum, Instant expires) {
            return new PresignedUpload("https://example.invalid/private?secret=not-for-output", expires);
        }
        public ObjectMetadata head(String key) {
            try { return new ObjectMetadata(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), bytes.length, "image/png"); }
            catch(Exception e) {throw new IllegalStateException(e);}
        }
        public byte[] read(String key) {reads++; return changeRead ? new byte[]{2} : bytes.clone();}
    }
}

package com.twomemory.app.media;

import com.twomemory.app.auth.AuthenticatedUser;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaStreamingTest {
    @TempDir Path directory;
    @Test void authorizedOriginalUsesResourceWithExactBytesAndLengthRatherThanWholeResponseArray() throws Exception {
        var storage = new LocalObjectStorage(directory.toString());
        storage.put("original", new byte[]{1,2,3,4});
        var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var asset = new MediaAssetView(UUID.randomUUID(), actor.coupleId(), actor.userId(), MediaKind.IMAGE,
            MediaStatus.READY, "original", "image/png", 4, 1, 1, null, "a".repeat(64), null);
        var service = mock(MediaService.class);
        when(service.readAsset(actor.userId(), asset.id())).thenReturn(asset);
        var response = new MediaController(service, storage).downloadData(actor, asset.id());
        Object body = response.getBody();
        assertThat(body).isInstanceOf(Resource.class);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        try (var stream = ((Resource)body).getInputStream()) { assertThat(stream.readAllBytes()).containsExactly(1,2,3,4); }
    }
    @Test void brokenOrExcessUploadsLeavePreviousOriginalAndRemoveOnlyPartialFiles() throws Exception {
        var storage=new LocalObjectStorage(directory.toString());
        storage.put("original",new byte[]{1,2,3});
        for(byte[] wrong: new byte[][]{new byte[]{9},new byte[]{9,9,9,9}}) {
            assertThatThrownBy(()->storage.putVerified("original",new java.io.ByteArrayInputStream(wrong),3,"a".repeat(64)))
                .isInstanceOf(MediaValidationException.class);
            assertThat(storage.read("original")).containsExactly(1,2,3);
        }
        try(var files=java.nio.file.Files.list(directory)) { assertThat(files.map(p->p.getFileName().toString()).toList()).containsExactly("original"); }
    }
}

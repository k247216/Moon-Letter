package com.twomemory.app.media;

import com.twomemory.app.auth.AuthenticatedUser;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaSafetyTest {
    @TempDir Path directory;
    @Test void partnerCannotOverwriteExistingSharedOriginalBeforeCompletionRejectsThem() {
        var service = mock(MediaService.class);
        var storage = new LocalObjectStorage(directory.toString());
        storage.put("original", new byte[]{1,2,3});
        var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var asset = new MediaAssetView(UUID.randomUUID(), actor.coupleId(), UUID.randomUUID(),
            MediaKind.IMAGE, MediaStatus.READY, "original", "image/png", 3, 1, 1, null, "a".repeat(64), null);
        when(service.readAsset(actor.userId(), asset.id())).thenReturn(asset);
        var controller = new MediaController(service, storage);
        var request=new org.springframework.mock.web.MockHttpServletRequest(); request.setContent(new byte[]{9,9,9});
        assertThatThrownBy(() -> controller.uploadData(actor, asset.id(), request))
            .isInstanceOf(MediaAccessDeniedException.class);
        assertThat(storage.read("original")).containsExactly(1,2,3);
    }
    @Test void newLargeVideoTicketIsRejectedBeforeSmallServerCanBufferIt() {
        var command = new CreateMediaCommand(UUID.randomUUID(), UUID.randomUUID(), MediaKind.VIDEO,
            "video/mp4", 64L*1024*1024, 1, 1, 1000L, "a".repeat(64), UUID.randomUUID());
        assertThatThrownBy(() -> MediaService.validate(command)).isInstanceOf(MediaValidationException.class);
        MediaService.validate(command.withByteSize(32L*1024*1024));
    }
    @Test void lostReadyAcknowledgementCanRetryIdenticalBytesButNotChangeCompletedOriginal() throws Exception {
        var service=mock(MediaService.class); var storage=new LocalObjectStorage(directory.toString());
        byte[] original={1,2,3}; storage.put("original",original);
        var actor=new AuthenticatedUser(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var asset=new MediaAssetView(UUID.randomUUID(),actor.coupleId(),actor.userId(),MediaKind.IMAGE,
            MediaStatus.READY,"original","image/png",3,1,1,null,storage.sha256(original),null);
        when(service.readAsset(actor.userId(),asset.id())).thenReturn(asset);
        var controller=new MediaController(service,storage);
        var request=new org.springframework.mock.web.MockHttpServletRequest(); request.setContent(original);
        assertThat(controller.uploadData(actor,asset.id(),request)).isEqualTo(asset);
        var wrong=new org.springframework.mock.web.MockHttpServletRequest(); wrong.setContent(new byte[]{9,9,9});
        assertThatThrownBy(()->controller.uploadData(actor,asset.id(),wrong)).isInstanceOf(MediaValidationException.class);
        assertThat(storage.read("original")).containsExactly(1,2,3);
    }
}

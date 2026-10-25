package com.twomemory.app.media;

import com.twomemory.app.auth.AuthenticatedUser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class MediaThumbnailTest {
    @Test void rotatedJpegPreviewUsesExifWithoutCroppingOrChangingSource() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1500, 3000, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        byte[] jpeg = output.toByteArray();
        byte[] exif = new byte[] {(byte)255,(byte)225,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,
                1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};
        output.reset(); output.write(jpeg, 0, 2); output.write(exif); output.write(jpeg, 2, jpeg.length - 2);
        byte[] rotated = output.toByteArray(), copy = rotated.clone();
        var preview = ImageIO.read(new java.io.ByteArrayInputStream(ImageThumbnail.render(rotated)));
        assertThat(preview.getWidth()).isEqualTo(600); assertThat(preview.getHeight()).isEqualTo(300);
        assertThat(rotated).isEqualTo(copy);
    }

    @Test void undecodableFormatsFailExplicitlyInsteadOfSendingOriginalAsJpeg() {
        assertThatThrownBy(() -> ImageThumbnail.render(new byte[] {1,2,3,4}))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void thumbnailIsSmallUncroppedAndOriginalRemainsByteExact() throws Exception {
        var id = UUID.randomUUID(); var user = UUID.randomUUID(); var space = UUID.randomUUID();
        var source = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream(); ImageIO.write(source, "png", out);
        byte[] original = out.toByteArray();
        var storage = mock(ObjectStorage.class); when(storage.read("old/assets/photo")).thenReturn(original);
        when(storage.open("old/assets/photo")).thenAnswer(call -> new java.io.ByteArrayInputStream(original));
        var service = mock(MediaService.class);
        when(service.readAsset(user, id)).thenReturn(new MediaAssetView(id, space, user, MediaKind.IMAGE,
                MediaStatus.READY, "old/assets/photo", "image/png", (long) original.length, 3000, 1500, null, "a".repeat(64), null));
        var mvc = MockMvcBuilders.standaloneSetup(new MediaController(service, storage))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter p) { return p.getParameterType() == AuthenticatedUser.class; }
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer c, NativeWebRequest r, WebDataBinderFactory b) {
                        return new AuthenticatedUser(user, space, UUID.randomUUID());
                    }
                }).build();
        var response = mvc.perform(get("/api/v1/media/" + id + "/thumbnail")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        var preview = ImageIO.read(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        assertThat(preview.getWidth()).isEqualTo(600); assertThat(preview.getHeight()).isEqualTo(300);
        assertThat(response.getContentAsByteArray().length).isLessThan(200_000);
        assertThat(mvc.perform(get("/api/v1/media/" + id + "/data")).andReturn().getResponse().getContentAsByteArray()).isEqualTo(original);
        when(service.readAsset(user, id)).thenThrow(new MediaAccessDeniedException("not a member"));
        assertThat(mvc.perform(get("/api/v1/media/" + id + "/thumbnail")).andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}

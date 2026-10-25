package com.twomemory.app.media;

import com.twomemory.app.auth.AuthenticatedUser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Exercises real routes, cache and JPEG rendering; only external storage/access are replaced. */
class MediaThumbnailProfilesTest {
    private final UUID user = UUID.randomUUID(), space = UUID.randomUUID();
    private final MediaService service = mock(MediaService.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MediaController(service, storage))
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType() == AuthenticatedUser.class;
                }
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                              NativeWebRequest request, WebDataBinderFactory binder) {
                    return new AuthenticatedUser(user, space, UUID.randomUUID());
                }
            }).build();

    @Test
    void readingProfileReturns1200UncroppedPixelsAndOriginalStaysByteExact() throws Exception {
        byte[] original = image(2400, 1200, false), copy = original.clone();
        UUID id = asset(original, 2400, 1200);
        byte[] body = thumbnail(id, "1200");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(body));
        assertThat(decoded.getWidth()).isEqualTo(1200);
        assertThat(decoded.getHeight()).isEqualTo(600);
        assertThat(original).isEqualTo(copy);
        var response = mvc.perform(get("/api/v1/media/" + id + "/data")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(copy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "601", "2400", "garbage"})
    void unknownProfilesReturn400(String edge) throws Exception {
        UUID id = asset(image(24, 12, false), 24, 12);
        assertThat(mvc.perform(get(path(id)).param("maxEdge", edge)).andReturn().getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void readingProfileAppliesExifRotationWithoutCroppingOrMutatingJpeg() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1500, 3000, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        byte[] jpeg = output.toByteArray();
        byte[] exif = {(byte)255,(byte)225,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,
                1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};
        output.reset();
        output.write(jpeg, 0, 2); output.write(exif); output.write(jpeg, 2, jpeg.length - 2);
        byte[] original = output.toByteArray(), copy = original.clone();
        UUID id = asset(original, 1500, 3000);
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(thumbnail(id, "1200")));
        assertThat(preview.getWidth()).isEqualTo(1200);
        assertThat(preview.getHeight()).isEqualTo(600);
        assertThat(original).isEqualTo(copy);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cacheKeepsReadingAndListProfilesSeparateInEitherRequestOrder(boolean readingFirst) throws Exception {
        UUID id = asset(image(2400, 1200, false), 2400, 1200);
        byte[] first = thumbnail(id, readingFirst ? "1200" : "600");
        byte[] second = thumbnail(id, readingFirst ? "600" : "1200");
        assertThat(ImageIO.read(new ByteArrayInputStream(first)).getWidth()).isEqualTo(readingFirst ? 1200 : 600);
        assertThat(ImageIO.read(new ByteArrayInputStream(second)).getWidth()).isEqualTo(readingFirst ? 600 : 1200);
        assertThat(thumbnail(id, readingFirst ? "1200" : "600")).isEqualTo(first);
        assertThat(thumbnail(id, readingFirst ? "600" : "1200")).isEqualTo(second);
        assertThat(thumbnail(id, null)).isEqualTo(readingFirst ? second : first);
    }

    @Test
    void readingProfileRetainsMoreDetailWithoutUpscalingSmallOriginal() throws Exception {
        byte[] original = image(240, 120, true);
        UUID id = asset(original, 240, 120);
        byte[] list = thumbnail(id, "600"), reading = thumbnail(id, "1200");
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
        BufferedImage listImage = ImageIO.read(new ByteArrayInputStream(list));
        BufferedImage readingImage = ImageIO.read(new ByteArrayInputStream(reading));
        assertThat(readingImage.getWidth()).isEqualTo(240);
        assertThat(readingImage.getHeight()).isEqualTo(120);
        assertThat(error(source, readingImage)).as("reading JPEG must preserve more detail at the same dimensions")
                .isLessThan(error(source, listImage));
    }

    @ParameterizedTest
    @ValueSource(strings = {"600", "1200"})
    void cachedBytesStillRequireCurrentAccessAndReadyState(String edge) throws Exception {
        byte[] original = image(24, 12, false);
        UUID id = asset(original, 24, 12);
        thumbnail(id, edge);
        when(service.readAsset(user, id)).thenReturn(view(id, original, 24, 12, MediaStatus.PROCESSING));
        assertThat(mvc.perform(get(path(id)).param("maxEdge", edge)).andReturn().getResponse().getStatus()).isEqualTo(409);
        when(service.readAsset(user, id)).thenThrow(new MediaAccessDeniedException("revoked fixture access"));
        assertThat(mvc.perform(get(path(id)).param("maxEdge", edge)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void cachedHitCompletesWhileAnotherOriginalReadIsBlocked() throws Exception {
        byte[] original = image(24, 12, false);
        UUID warmId = asset(original, 24, 12), coldId = asset(original, 24, 12);
        byte[] warm = thumbnail(warmId, null);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(storage.read(key(coldId))).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("cold read was not released");
            return original;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var cold = executor.submit(() -> thumbnail(coldId, null));
            assertThat(entered.await(5, TimeUnit.SECONDS)).as("cold request reached storage").isTrue();
            var hit = executor.submit(() -> thumbnail(warmId, null));
            assertThatCode(() -> assertThat(hit.get(1, TimeUnit.SECONDS)).isEqualTo(warm))
                    .as("a cache hit must not wait for the cold storage/render generation lock")
                    .doesNotThrowAnyException();
            release.countDown();
            assertThat(cold.get(5, TimeUnit.SECONDS)).isNotEmpty();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void cacheEvictsOldestEntryAfter128Profiles() throws Exception {
        byte[] original = image(24, 12, false);
        AtomicInteger reads = new AtomicInteger();
        UUID oldest = null;
        for (int index = 0; index < 129; index++) {
            UUID id = asset(original, 24, 12);
            if (oldest == null) oldest = id;
            when(storage.read(key(id))).thenAnswer(call -> { reads.incrementAndGet(); return original; });
            thumbnail(id, null);
        }
        assertThat(reads.get()).isEqualTo(129);
        thumbnail(oldest, null);
        assertThat(reads.get()).as("evicted entry is regenerated from storage").isEqualTo(130);
    }

    @Test
    void readingCacheEvictsByEightMiBWellBeforeEntryLimit() throws Exception {
        byte[] original = image(1200, 1200, true);
        AtomicInteger reads = new AtomicInteger();
        UUID oldest = null;
        long totalBytes = 0;
        int entries = 0;
        while (totalBytes <= 8 * 1024 * 1024 && entries < 32) {
            UUID id = asset(original, 1200, 1200);
            if (oldest == null) oldest = id;
            when(storage.read(key(id))).thenAnswer(call -> { reads.incrementAndGet(); return original; });
            byte[] body = thumbnail(id, "1200");
            assertThat(ImageIO.read(new ByteArrayInputStream(body)).getWidth()).isEqualTo(1200);
            totalBytes += body.length;
            entries++;
        }
        assertThat(totalBytes).as("synthetic JPEG fixtures cross the byte budget with fewer than 128 entries")
                .isGreaterThan(8L * 1024 * 1024);
        int before = reads.get();
        thumbnail(oldest, "1200");
        assertThat(reads.get()).as("byte-budget eviction requires an original re-read").isEqualTo(before + 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void coldGenerationsAreSerializedAndSameProfileIsRecheckedAfterWaiting(boolean sameAsset) throws Exception {
        byte[] original = image(24, 12, false);
        UUID firstId = asset(original, 24, 12);
        UUID secondId = sameAsset ? firstId : asset(original, 24, 12);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch secondAuthorized = new CountDownLatch(1), secondRead = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger(), authorizations = new AtomicInteger();
        when(service.readAsset(user, secondId)).thenAnswer(call -> {
            if (!sameAsset || authorizations.incrementAndGet() == 2) secondAuthorized.countDown();
            return view(secondId, original, 24, 12, MediaStatus.READY);
        });
        when(storage.read(key(firstId))).thenAnswer(call -> {
            reads.incrementAndGet();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("first generation was not released");
            return original;
        });
        if (!sameAsset) when(storage.read(key(secondId))).thenAnswer(call -> {
            reads.incrementAndGet(); secondRead.countDown(); return original;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> thumbnail(firstId, null));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> thumbnail(secondId, null));
            assertThat(secondAuthorized.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondRead.await(250, TimeUnit.MILLISECONDS)).as("another cold original must wait").isFalse();
            assertThat(reads.get()).isEqualTo(1);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isNotEmpty();
            assertThat(second.get(5, TimeUnit.SECONDS)).isNotEmpty();
            assertThat(reads.get()).isEqualTo(sameAsset ? 1 : 2);
        } finally {
            release.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UUID asset(byte[] original, int width, int height) throws Exception {
        UUID id = UUID.randomUUID();
        when(service.readAsset(user, id)).thenReturn(view(id, original, width, height, MediaStatus.READY));
        when(storage.read(key(id))).thenReturn(original);
        when(storage.open(key(id))).thenAnswer(call -> new ByteArrayInputStream(original));
        return id;
    }

    private MediaAssetView view(UUID id, byte[] bytes, int width, int height, MediaStatus status) {
        return new MediaAssetView(id, space, user, MediaKind.IMAGE, status, key(id), "image/png",
                (long) bytes.length, width, height, null, "a".repeat(64), null);
    }

    private byte[] thumbnail(UUID id, String edge) throws Exception {
        var request = get(path(id));
        if (edge != null) request.param("maxEdge", edge);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        return response.getContentAsByteArray();
    }

    private static String key(UUID id) { return "synthetic/thumbnail/" + id; }
    private static String path(UUID id) { return "/api/v1/media/" + id + "/thumbnail"; }

    private static byte[] image(int width, int height, boolean detail) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        if (detail) {
            Random random = new Random(39);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int grey = random.nextInt(256);
                image.setRGB(x, y, (grey << 16) | (grey << 8) | grey);
            }
        }
        var output = new ByteArrayOutputStream();
        try { ImageIO.write(image, "png", output); return output.toByteArray(); }
        finally { image.flush(); }
    }

    private static long error(BufferedImage source, BufferedImage actual) {
        long sum = 0;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int difference = (source.getRGB(x, y) & 255) - (actual.getRGB(x, y) & 255);
            sum += (long) difference * difference;
        }
        return sum;
    }
}

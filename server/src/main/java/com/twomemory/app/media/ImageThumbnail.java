package com.twomemory.app.media;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

/** Bounded, uncropped preview. Subsample before allocating pixels on the 2GB ECS. */
final class ImageThumbnail {
    static byte[] render(byte[] original) throws IOException {
        return render(original, 600);
    }

    static byte[] render(byte[] original, int maxEdge) throws IOException {
        if (maxEdge != 600 && maxEdge != 1200) throw new IllegalArgumentException("unsupported thumbnail profile");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new UnsupportedOperationException("preview format unsupported");
            var reader = readers.next();
            BufferedImage source;
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 150_000_000L)
                    throw new UnsupportedOperationException("preview dimensions unsupported");
                var param = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / 1200);
                param.setSourceSubsampling(sample, sample, 0, 0);
                source = reader.read(0, param);
            } finally { reader.dispose(); }
            int orientation = jpegOrientation(original);
            boolean swap = orientation >= 5 && orientation <= 8;
            int orientedWidth = swap ? source.getHeight() : source.getWidth();
            int orientedHeight = swap ? source.getWidth() : source.getHeight();
            double ratio = Math.min(1, (double) maxEdge / Math.max(orientedWidth, orientedHeight));
            int width = Math.max(1, (int) Math.round(orientedWidth * ratio));
            int height = Math.max(1, (int) Math.round(orientedHeight * ratio));
            var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = result.createGraphics();
            try {
                graphics.setColor(new Color(255, 250, 240)); graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.scale(width / (double) orientedWidth, height / (double) orientedHeight);
                switch (orientation) {
                    case 2 -> { graphics.translate(source.getWidth(), 0); graphics.scale(-1, 1); }
                    case 3 -> { graphics.translate(source.getWidth(), source.getHeight()); graphics.rotate(Math.PI); }
                    case 4 -> { graphics.translate(0, source.getHeight()); graphics.scale(1, -1); }
                    case 5 -> { graphics.rotate(Math.PI / 2); graphics.scale(1, -1); }
                    case 6 -> { graphics.translate(source.getHeight(), 0); graphics.rotate(Math.PI / 2); }
                    case 7 -> { graphics.translate(source.getHeight(), source.getWidth()); graphics.rotate(Math.PI / 2); graphics.scale(-1, 1); }
                    case 8 -> { graphics.translate(0, source.getWidth()); graphics.rotate(-Math.PI / 2); }
                    default -> { }
                }
                graphics.drawImage(source, 0, 0, null);
            } finally { graphics.dispose(); source.flush(); }
            var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            var output = new ByteArrayOutputStream();
            try (var stream = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(stream);
                var param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(maxEdge == 1200 ? .85f : .72f);
                writer.write(null, new javax.imageio.IIOImage(result, null, null), param);
            } finally { writer.dispose(); result.flush(); }
            return output.toByteArray();
        }
    }

    /** Read only the EXIF orientation; output JPEG is already upright, with no EXIF. */
    static int jpegOrientation(byte[] bytes) {
        try {
            if (bytes.length < 4 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216) return 1;
            int p = 2;
            while (p + 4 < bytes.length && (bytes[p] & 255) == 255) {
                int marker = bytes[p + 1] & 255;
                if (marker == 218 || marker == 217) break;
                int length = ((bytes[p + 2] & 255) << 8) | (bytes[p + 3] & 255);
                if (length < 2 || p + 2 + length > bytes.length) return 1;
                if (marker == 225 && length >= 16 && bytes[p + 4] == 'E' && bytes[p + 5] == 'x'
                        && bytes[p + 6] == 'i' && bytes[p + 7] == 'f' && bytes[p + 8] == 0 && bytes[p + 9] == 0) {
                    int base = p + 10, end = p + 2 + length;
                    var buffer = java.nio.ByteBuffer.wrap(bytes).order(bytes[base] == 'I'
                            ? java.nio.ByteOrder.LITTLE_ENDIAN : java.nio.ByteOrder.BIG_ENDIAN);
                    int ifd = Math.addExact(base, buffer.getInt(base + 4));
                    if (ifd < base || ifd + 2 > end) return 1;
                    int count = buffer.getShort(ifd) & 65535;
                    for (int i = 0; i < count; i++) {
                        int at = ifd + 2 + i * 12; if (at + 12 > end) return 1;
                        if ((buffer.getShort(at) & 65535) == 274 && buffer.getShort(at + 2) == 3 && buffer.getInt(at + 4) == 1) {
                            int value = buffer.getShort(at + 8) & 65535;
                            return value >= 1 && value <= 8 ? value : 1;
                        }
                    }
                }
                p += length + 2;
            }
        } catch (RuntimeException ignored) { /* Malformed metadata does not alter the original. */ }
        return 1;
    }
}

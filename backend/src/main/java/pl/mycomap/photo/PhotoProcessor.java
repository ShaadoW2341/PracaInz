package pl.mycomap.photo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.mycomap.common.ApiException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public class PhotoProcessor {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public record Processed(byte[] image, byte[] thumbnail, int width, int height) {}

    public Processed process(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Zdjęcie może mieć maksymalnie 5 MB.");
        if (file.isEmpty()) throw invalid("Plik zdjęcia jest pusty.");
        if (!"image/jpeg".equals(file.getContentType()) && !"image/png".equals(file.getContentType())) throw unsupported();
        try {
            byte[] bytes = file.getBytes();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("Nie można odczytać zdjęcia. Wybierz poprawny plik JPG lub PNG.");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    String type = "JPEG".equalsIgnoreCase(format) ? "image/jpeg" : "PNG".equalsIgnoreCase(format) ? "image/png" : "";
                    if (!type.equals(file.getContentType())) throw unsupported();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long) width * height > 20_000_000) {
                        throw invalid("Zdjęcie może mieć maksymalnie 20 milionów pikseli i 10 000 pikseli na bok.");
                    }
                    BufferedImage full = orient(resize(reader.read(0), 2048), orientation(bytes, type));
                    return new Processed(jpeg(full), jpeg(resize(full, 360)), full.getWidth(), full.getHeight());
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw invalid("Nie można odczytać zdjęcia. Plik jest uszkodzony lub nieobsługiwany.");
        }
    }

    private BufferedImage resize(BufferedImage source, int max) {
        double scale = Math.min(1.0, (double) max / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return target;
    }

    private byte[] jpeg(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            var options = writer.getDefaultWriteParam();
            options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); options.setCompressionQuality(0.85f);
            // Zapisujemy wyłącznie nowe piksele, bez metadanych oryginału.
            writer.write(null, new javax.imageio.IIOImage(image, null, null), options);
        } finally { writer.dispose(); }
        return output.toByteArray();
    }

    // Z EXIF odczytujemy wyłącznie obrót obrazu. Nie odczytujemy współrzędnych GPS.
    private int orientation(byte[] bytes, String type) {
        if ("image/jpeg".equals(type)) {
            int offset = 2;
            while (offset + 4 <= bytes.length && (bytes[offset] & 255) == 255) {
                int marker = bytes[offset + 1] & 255;
                if (marker == 0xda || marker == 0xd9) break;
                int length = (bytes[offset + 2] & 255) * 256 + (bytes[offset + 3] & 255);
                if (length < 2 || offset + 2L + length > bytes.length) break;
                if (marker == 0xe1 && length >= 16 && bytes[offset+4] == 'E' && bytes[offset+5] == 'x'
                        && bytes[offset+6] == 'i' && bytes[offset+7] == 'f' && bytes[offset+8] == 0 && bytes[offset+9] == 0) {
                    return tiffOrientation(bytes, offset + 10, length - 8);
                }
                offset += length + 2;
            }
        } else {
            int offset = 8;
            while (offset + 12 <= bytes.length) {
                int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
                if (length < 0 || offset + 12L + length > bytes.length) break;
                if (bytes[offset+4] == 'e' && bytes[offset+5] == 'X' && bytes[offset+6] == 'I' && bytes[offset+7] == 'f') {
                    return tiffOrientation(bytes, offset + 8, length);
                }
                offset += length + 12;
            }
        }
        return 1;
    }

    private int tiffOrientation(byte[] bytes, int offset, int length) {
        if (length < 8) return 1;
        var tiff = ByteBuffer.wrap(bytes, offset, length).slice();
        if (tiff.get(0) == 'I' && tiff.get(1) == 'I') tiff.order(ByteOrder.LITTLE_ENDIAN);
        else if (tiff.get(0) != 'M' || tiff.get(1) != 'M') return 1;
        if (tiff.getShort(2) != 42) return 1;
        int directory = tiff.getInt(4);
        if (directory < 8 || directory > length - 2) return 1;
        int count = Short.toUnsignedInt(tiff.getShort(directory));
        for (int n = 0; n < count && directory + 2L + (n + 1L) * 12 <= length; n++) {
            int entry = directory + 2 + n * 12;
            if (Short.toUnsignedInt(tiff.getShort(entry)) == 0x112 && tiff.getShort(entry+2) == 3 && tiff.getInt(entry+4) == 1) {
                int value = Short.toUnsignedInt(tiff.getShort(entry+8));
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == 1) return source;
        int width = source.getWidth(), height = source.getHeight();
        var target = new BufferedImage(orientation >= 5 ? height : width, orientation >= 5 ? width : height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int dx = switch (orientation) { case 2, 3 -> width-1-x; case 5, 8 -> y; case 6, 7 -> height-1-y; default -> x; };
            int dy = switch (orientation) { case 3, 4 -> height-1-y; case 5, 6 -> x; case 7, 8 -> width-1-x; default -> y; };
            target.setRGB(dx, dy, source.getRGB(x, y));
        }
        return target;
    }
    private ApiException unsupported() { return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PHOTO_TYPE", "Wybierz zdjęcie JPG lub PNG zgodne z typem pliku."); }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_INVALID", message); }
}

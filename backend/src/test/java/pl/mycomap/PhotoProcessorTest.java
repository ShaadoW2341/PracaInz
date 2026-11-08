package pl.mycomap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pl.mycomap.common.ApiException;
import pl.mycomap.photo.PhotoProcessor;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import static org.assertj.core.api.Assertions.*;

class PhotoProcessorTest {
    private final PhotoProcessor processor = new PhotoProcessor();

    static byte[] image(String format, int width, int height) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
    static MockMultipartFile file(String type, byte[] bytes) {
        return new MockMultipartFile("files", "../../oryginal.jpg", type, bytes);
    }

    @Test void createsBoundedJpegAndThumbnailFromPng() throws Exception {
        var result = processor.process(file("image/png", image("png", 3000, 1000)));
        var full = ImageIO.read(new ByteArrayInputStream(result.image()));
        var thumbnail = ImageIO.read(new ByteArrayInputStream(result.thumbnail()));
        assertThat(full.getWidth()).isEqualTo(2048);
        assertThat(thumbnail.getWidth()).isEqualTo(360);
        assertThat(result.width()).isEqualTo(full.getWidth());
        assertThat(result.height()).isEqualTo(full.getHeight());
        assertThat(result.image()[0] & 255).isEqualTo(255);
        assertThat(result.image()[1] & 255).isEqualTo(216);
    }

    @Test void rejectsUnsupportedDisguisedAndBrokenImages() throws Exception {
        assertThatThrownBy(() -> processor.process(file("image/svg+xml", "<svg/>".getBytes())))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> processor.process(file("image/jpeg", image("png", 2, 2))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> processor.process(file("image/png", "not an image".getBytes())))
                .isInstanceOf(ApiException.class);
    }

    @Test void rejectsOversizedFilesAndImagesBeforeDecodingPixels() throws Exception {
        assertThatThrownBy(() -> processor.process(file("image/jpeg", new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(ApiException.class);
        byte[] png = image("png", 1, 1);
        ByteBuffer.wrap(png).putInt(16, 6000).putInt(20, 4000);
        var crc = new CRC32(); crc.update(png, 12, 17);
        ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
        assertThatThrownBy(() -> processor.process(file("image/png", png)))
                .isInstanceOf(ApiException.class).hasMessageContaining("pikseli");
    }

    @Test void appliesOrientationAndRemovesExifFromBothOutputs() throws Exception {
        byte[] original = image("jpeg", 30, 20);
        var exif = new ByteArrayOutputStream();
        exif.write("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
        var tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1).putShort((short) 0x112).putShort((short) 3).putInt(1).putShort((short) 6).putShort((short) 0).putInt(0);
        exif.write(tiff.array()); exif.write("GPS-private-location".getBytes(StandardCharsets.US_ASCII));
        var input = new ByteArrayOutputStream();
        input.write(original, 0, 2);
        var segment = new DataOutputStream(input);
        segment.writeShort(0xffe1); segment.writeShort(exif.size() + 2); segment.write(exif.toByteArray());
        input.write(original, 2, original.length - 2);
        var result = processor.process(file("image/jpeg", input.toByteArray()));
        assertThat(result.width()).isEqualTo(20);
        assertThat(result.height()).isEqualTo(30);
        for (byte[] bytes : new byte[][]{result.image(), result.thumbnail()}) {
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            assertThat(text).doesNotContain("Exif", "GPS-private-location");
            assertThat(ImageIO.read(new ByteArrayInputStream(bytes))).isNotNull();
        }
    }
}

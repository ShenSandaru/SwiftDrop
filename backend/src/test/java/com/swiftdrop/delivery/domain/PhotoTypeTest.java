package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PhotoTypeTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};

    @Test
    void recognisesJpegAndPngByTheirLeadingBytes() {
        assertThat(PhotoType.detect(JPEG)).contains("image/jpeg");
        assertThat(PhotoType.detect(PNG)).contains("image/png");
    }

    @Test
    void rejectsOtherFormatsAndRenamedFiles() {
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        byte[] pdf = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        byte[] html = "<html><script>".getBytes(StandardCharsets.US_ASCII);

        assertThat(PhotoType.detect(gif)).isEmpty();
        assertThat(PhotoType.detect(pdf)).isEmpty();
        assertThat(PhotoType.detect(html)).isEmpty();
    }

    @Test
    void tooShortOrMissingInputIsNotAnImage() {
        assertThat(PhotoType.detect(null)).isEmpty();
        assertThat(PhotoType.detect(new byte[0])).isEmpty();
        assertThat(PhotoType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(PhotoType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
    }
}

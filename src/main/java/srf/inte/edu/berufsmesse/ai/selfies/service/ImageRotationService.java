package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@RequiredArgsConstructor
@Service
@Slf4j
public class ImageRotationService {
    // Rotates a base64 image returning it as base64 again
    public String rotateImage(String base64Image) {
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        byte[] rotatedBytes = normalizeOrientation(imageBytes);
        return Base64.getEncoder().encodeToString(rotatedBytes);
    }

    /**
     * Cameras commonly store portrait photos as landscape pixel data plus an EXIF
     * orientation tag telling viewers how to rotate it for display. Neither the AI
     * model nor {@link ImageIO}/{@code Graphics2D} compositing honor that tag, so
     * without this the person ends up rotated 90 degrees in the final composite.
     * Bakes the rotation into the pixels once, up front, so every step downstream
     * can treat the image as already upright. Images without a tag stay untouched.
     */
    private byte[] normalizeOrientation(byte[] jpegBytes) {
        int orientation = readExifOrientation(jpegBytes);
        if (orientation <= 1) {
            return jpegBytes;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpegBytes));
            if (image == null) {
                return jpegBytes;
            }

            BufferedImage rotated = applyExifOrientation(image, orientation);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(rotated, "jpeg", output)) {
                log.warn("Could not re-encode orientation-corrected image, using original bytes");
                return jpegBytes;
            }

            log.info("Corrected EXIF orientation {} ({}x{} -> {}x{})",
                     orientation, image.getWidth(), image.getHeight(), rotated.getWidth(), rotated.getHeight());
            return output.toByteArray();
        } catch (IOException ex) {
            log.warn("Error normalizing EXIF orientation, using original bytes: {}", ex.getMessage());
            return jpegBytes;
        }
    }

    private static BufferedImage applyExifOrientation(BufferedImage image, int orientation) {
        int width = image.getWidth();
        int height = image.getHeight();

        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> null;
        };

        if (transform == null) {
            return image;
        }

        boolean swapDimensions = orientation >= 5;
        BufferedImage result = new BufferedImage(
                swapDimensions ? height : width,
                swapDimensions ? width : height,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, transform, null);
        g.dispose();
        return result;
    }

    /**
     * Manually walks the JPEG marker segments to find the APP1/Exif block and read
     * the Orientation tag (0x0112) from its TIFF header, since the project has no
     * EXIF library dependency. Returns 1 (no rotation needed) if anything about the
     * format doesn't match what's expected, rather than failing the request.
     */
    private static int readExifOrientation(byte[] jpegBytes) {
        try {
            if (jpegBytes.length < 4 || (jpegBytes[0] & 0xFF) != 0xFF || (jpegBytes[1] & 0xFF) != 0xD8) {
                return 1;
            }

            int i = 2;
            while (i + 4 <= jpegBytes.length) {
                int marker = ((jpegBytes[i] & 0xFF) << 8) | (jpegBytes[i + 1] & 0xFF);
                int segmentLength = ((jpegBytes[i + 2] & 0xFF) << 8) | (jpegBytes[i + 3] & 0xFF);

                if (marker == 0xFFE1 && matches(jpegBytes, i + 4, "Exif\0\0")) {
                    return parseTiffOrientation(jpegBytes, i + 4 + 6);
                }
                if (marker == 0xFFDA || (marker & 0xFF00) != 0xFF00) {
                    break;
                }
                i += 2 + segmentLength;
            }
        } catch (RuntimeException ex) {
            log.warn("Could not read EXIF orientation, assuming none: {}", ex.getMessage());
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] data, int tiffStart) {
        boolean littleEndian = data[tiffStart] == 'I' && data[tiffStart + 1] == 'I';
        int ifdOffset = readInt32(data, tiffStart + 4, littleEndian);
        int entryCount = readInt16(data, tiffStart + ifdOffset, littleEndian);

        for (int e = 0; e < entryCount; e++) {
            int entryOffset = tiffStart + ifdOffset + 2 + e * 12;
            int tag = readInt16(data, entryOffset, littleEndian);
            if (tag == 0x0112) {
                return readInt16(data, entryOffset + 8, littleEndian);
            }
        }
        return 1;
    }

    private static int readInt16(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private static int readInt32(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        return littleEndian ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
                            : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private static boolean matches(byte[] data, int offset, String s) {
        if (offset < 0 || offset + s.length() > data.length) {
            return false;
        }
        for (int k = 0; k < s.length(); k++) {
            if (data[offset + k] != (byte) s.charAt(k)) {
                return false;
            }
        }
        return true;
    }
}

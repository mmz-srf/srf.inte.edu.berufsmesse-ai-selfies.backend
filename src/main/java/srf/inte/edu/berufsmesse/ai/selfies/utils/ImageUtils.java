package srf.inte.edu.berufsmesse.ai.selfies.utils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
public class ImageUtils {

    /**
     * Alpha-composites a (possibly semi-transparent) foreground color over an opaque/semi-transparent background color, "over" operator.
     */
    public static int alphaBlend(Color fg, Color bg) {
        if (fg.getAlpha() >= 255) {
            return 0xFF000000 | fg.getRGB();
        }

        double fgA = fg.getAlpha() / 255.0;
        double bgA = bg.getAlpha() / 255.0;
        double outA = fgA + bgA * (1 - fgA);

        if (outA <= 0) {
            return 0;
        }

        int r = (int) Math.round((fg.getRed() * fgA + bg.getRed() * bgA * (1 - fgA)) / outA);
        int gr = (int) Math.round((fg.getGreen() * fgA + bg.getGreen() * bgA * (1 - fgA)) / outA);
        int b = (int) Math.round((fg.getBlue() * fgA + bg.getBlue() * bgA * (1 - fgA)) / outA);
        int a = (int) Math.round(outA * 255);

        return new Color(r, gr, b, a).getRGB();
    }

    public static String convertBufferedImageToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean written = ImageIO.write(image, "png", output);
            if (!written || output.size() == 0) {
                log.error("Could not encode result image. written={}, size={} bytes", written, output.size());
                return null;
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            log.error("Error writing result image", ex);
        }
        return null;
    }

    /**
     * Re-encodes a base64 image as JPEG. JPEG has no alpha channel, so the image is first drawn onto an opaque white canvas.
     */
    public static String convertBase64ToJpeg(String base64Image) {
        BufferedImage image = base64ToBufferedImage(base64Image);
        if (image == null) {
            log.error("Could not decode image for JPEG conversion");
            return null;
        }

        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean written = ImageIO.write(rgbImage, "jpeg", output);
            if (!written || output.size() == 0) {
                log.error("Could not encode JPEG image. written={}, size={} bytes", written, output.size());
                return null;
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            log.error("Error writing JPEG image", ex);
        }
        return null;
    }

    /**
     * Reads the raw bytes of a resource file, without decoding/re-encoding it.
     */
    public static byte[] readBytesFromResources(String path) {
        var classloader = Thread.currentThread().getContextClassLoader();

        try (InputStream stream = classloader.getResourceAsStream(path)) {
            if (stream == null) {
                log.error("Resource not found at {}", path);
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException ex) {
            log.error("Error reading resource {}", path, ex);
            return null;
        }
    }

    public static BufferedImage readImageFromResources(String path) {
        var classloader = Thread.currentThread().getContextClassLoader();
        BufferedImage image;

        try (InputStream imageStream = classloader.getResourceAsStream(path)) {
            if (imageStream == null) {
                log.error("Image not found at {}", path);
                return null;
            }
            image = ImageIO.read(imageStream);
            if (image == null) {
                log.error("Could not decode image from {}", path);
                return null;
            }
        } catch (IOException ex) {
            log.error("Error reading image from resources", ex);
            return null;
        }

        return image;
    }

    public static String getBackgroundImagePath(String background) {
        return String.format("images/%s/%s.jpg", background, background);
    }

    public static String getOverlayImagePath(String background) {
        return String.format("images/%s/overlay.png", background);
    }

    public static String getTextOverlayPath(String background) {
        return String.format("images/%s/text_overlay.svg", background);
    }

    public static BufferedImage base64ToBufferedImage(String base64Image) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException ex) {
            log.error("Error decoding base64 image", ex);
            return null;
        }
    }

    public static boolean isGreen(Color c) {
        int green = c.getGreen();
        int red = c.getRed();
        int blue = c.getBlue();

        return green > 100 &&
               green > red * 1.1 &&
               green > blue * 1.1;
    }

    /**
     * Scales an image by the given factor, keeping its alpha channel (needed for overlays with transparent areas).
     */
    public static BufferedImage scaleImage(BufferedImage image, double factor) {
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * factor));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resized;
    }
}

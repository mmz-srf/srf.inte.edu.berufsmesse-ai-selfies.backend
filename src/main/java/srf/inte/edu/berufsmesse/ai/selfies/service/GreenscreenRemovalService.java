package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.LocationOffsetConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;
import srf.inte.edu.berufsmesse.ai.selfies.utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;

@RequiredArgsConstructor
@Service
@Slf4j
public class GreenscreenRemovalService {
    // Keep background sharper than foreground, but cap extreme resolution gaps.
    private static final double MAX_BACKGROUND_TO_FOREGROUND_SCALE = 2.5;
    private final LocationOffsetConfiguration locationOffsetConfiguration;

    public String replaceGreenscreenAndMerge(StartGenerationRequest request, String imageBase64) {
        var offset = locationOffsetConfiguration.getForLocation(request.getBackground());
        log.info("Using location offset for {}: x={}, y={}, scale={}", request.getBackground(), offset.getX(),
                 offset.getY(), offset.getScale());

        try {
            if (imageBase64 == null || imageBase64.isBlank()) {
                log.error("replaceGreenscreen received empty base64 image");
                return null;
            }

            var foreground = ImageUtils.base64ToBufferedImage(imageBase64);
            var background = ImageUtils.readImageFromResources(ImageUtils.getBackgroundImagePath(request.getBackground()));

            if (background == null || foreground == null) {
                log.error("Failed to read background or foreground image for greenscreen replacement");
                return null;
            }

            log.info("Received person image of {}x{}", foreground.getWidth(), foreground.getHeight());

            // Bring the person to the resolution the offsets were tuned with, otherwise the background downscaling
            // below (and with it every pixel offset) depends on the camera resolution of the device
            if (offset.getReferenceHeight() != null && foreground.getHeight() != offset.getReferenceHeight()) {
                foreground = ImageUtils.scaleImage(foreground, (double) offset.getReferenceHeight() / foreground.getHeight());
                log.info("Normalized person image to {}x{}", foreground.getWidth(), foreground.getHeight());
            }

            // Downscale the background relative to the person's true (unscaled) resolution,
            // then apply the location's scale purely for framing within the final composite.
            var scaledBackground = downscaleBackgroundIfNeeded(background, foreground);
            if (offset.getScale() != 1.0) {
                foreground = ImageUtils.scaleImage(foreground, offset.getScale());
            }

            log.info("Composited image dimensions: foreground={}x{}, background={}x{}",
                     foreground.getWidth(), foreground.getHeight(),
                     scaledBackground.getWidth(), scaledBackground.getHeight()
            );

            return mergeImages(foreground, scaledBackground, offset.getX(), offset.getY(), true);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid base64 input for greenscreen replacement", ex);
            return null;
        }
    }

    /**
     * Merges a foreground image onto a background image at the specified offsets. If replaceGreenscreen is true, foreground pixels considered green (based on the isGreen method) are treated as transparent and the background shows through instead. If false, the foreground is alpha-composited onto the background using its real alpha channel (used for the prop/table overlay and the text caption, both of which are PNGs with transparent areas).
     * @param foreground The foreground image to be merged onto the background.
     * @param background The background image to which the foreground will be merged.
     * @param xOffset The horizontal offset at which to start merging the foreground image.
     * @param yOffset The vertical offset at which to start merging the foreground image.
     * @param replaceGreenscreen If true, green foreground pixels are skipped so the background shows through instead of the green screen. If false, the foreground's alpha channel is respected.
     * @return Base64-encoded merged image.
     */
    public String mergeImages(BufferedImage foreground, BufferedImage background, int xOffset, int yOffset, boolean replaceGreenscreen) {
        BufferedImage result = new BufferedImage(
                background.getWidth(),
                background.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = result.createGraphics();
        g.drawImage(background, 0, 0, null);
        g.dispose();

        for (int y = 0; y < background.getHeight(); y++) {
            for (int x = 0; x < background.getWidth(); x++) {
                int fgX = x - xOffset;
                int fgY = y - yOffset;

                if (fgX >= 0 && fgY >= 0 && fgX < foreground.getWidth() && fgY < foreground.getHeight()) {
                    Color fgColor = new Color(foreground.getRGB(fgX, fgY), true);

                    if (replaceGreenscreen) {
                        if (!ImageUtils.isGreen(fgColor)) {
                            result.setRGB(x, y, 0xFF000000 | fgColor.getRGB());
                        }
                        // green foreground pixel: leave the background pixel already drawn in place
                    } else if (fgColor.getAlpha() > 0) {
                        int blended = ImageUtils.alphaBlend(fgColor, new Color(result.getRGB(x, y), true));
                        result.setRGB(x, y, blended);
                    }
                }
            }
        }

        return ImageUtils.convertBufferedImageToBase64(result);
    }

    private BufferedImage downscaleBackgroundIfNeeded(BufferedImage background, BufferedImage foreground) {
        int maxAllowedWidth = (int) Math.round(foreground.getWidth() * MAX_BACKGROUND_TO_FOREGROUND_SCALE);
        int maxAllowedHeight = (int) Math.round(foreground.getHeight() * MAX_BACKGROUND_TO_FOREGROUND_SCALE);

        if (background.getWidth() <= maxAllowedWidth && background.getHeight() <= maxAllowedHeight) {
            return background;
        }

        double widthScale = (double) maxAllowedWidth / background.getWidth();
        double heightScale = (double) maxAllowedHeight / background.getHeight();
        double scale = Math.min(widthScale, heightScale);

        int targetWidth = Math.max(1, (int) Math.round(background.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(background.getHeight() * scale));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(background, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        log.info("Downscaled background from {}x{} to {}x{}", background.getWidth(), background.getHeight(), targetWidth, targetHeight);
        return resized;
    }
}

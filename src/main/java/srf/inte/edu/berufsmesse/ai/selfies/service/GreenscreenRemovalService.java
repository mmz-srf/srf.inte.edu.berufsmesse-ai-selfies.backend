package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.LocationOffsetConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;
import srf.inte.edu.berufsmesse.ai.selfies.utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

@RequiredArgsConstructor
@Service
@Slf4j
public class GreenscreenRemovalService {
    // Keep background sharper than foreground, but cap extreme resolution gaps.
    private static final double MAX_BACKGROUND_TO_FOREGROUND_SCALE = 2.5;

    // Share of the image width/height along each edge that is sampled to find the greenscreen color.
    private static final double KEY_SAMPLE_BORDER = 0.03;
    // Used when the border contains (almost) no green, e.g. the person fills the whole frame.
    private static final int FALLBACK_KEY_COLOR = 0x00B140;

    // "Greenness" of a pixel relative to the greenscreen color (0 = no green at all, 1 = exactly the screen color).
    // Below the first value a pixel stays fully visible, above the second it becomes fully transparent,
    // in between it is blended softly. Raise the second value if shadows on the screen stay visible,
    // lower the first value if edges of hair/clothing turn see-through.
    private static final double OPAQUE_BELOW_GREENNESS = 0.15;
    private static final double TRANSPARENT_ABOVE_GREENNESS = 0.45;
    // Pixels this close (in px) to a see-through pixel are half person, half screen and get the stronger despill.
    private static final int EDGE_DESPILL_RADIUS = 2;
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

            // Without a reference size the background downscaling below (and with it every pixel offset and the
            // overlays) depends on the resolution of the camera / AI output, which differs between devices
            int sizingWidth = foreground.getWidth();
            int sizingHeight = foreground.getHeight();
            int xCorrection = 0;
            if (offset.hasReferenceSize()) {
                sizingWidth = offset.getReferenceWidth();
                sizingHeight = offset.getReferenceHeight();
                if (foreground.getHeight() != sizingHeight) {
                    foreground = ImageUtils.scaleImage(foreground, (double) sizingHeight / foreground.getHeight());
                    log.info("Normalized person image to {}x{}", foreground.getWidth(), foreground.getHeight());
                }
                // a narrower/wider image than the reference would shift the (centered) person sideways
                xCorrection = (int) Math.round((sizingWidth - foreground.getWidth()) / 2.0 * offset.getScale());
            }

            // Downscale the background relative to the person's (reference) resolution,
            // then apply the location's scale purely for framing within the final composite.
            var scaledBackground = downscaleBackgroundIfNeeded(background, sizingWidth, sizingHeight);
            if (offset.getScale() != 1.0) {
                foreground = ImageUtils.scaleImage(foreground, offset.getScale());
            }

            log.info("Composited image dimensions: foreground={}x{}, background={}x{}",
                     foreground.getWidth(), foreground.getHeight(),
                     scaledBackground.getWidth(), scaledBackground.getHeight()
            );

            return mergeImages(foreground, scaledBackground, offset.getX() + xCorrection, offset.getY(), true);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid base64 input for greenscreen replacement", ex);
            return null;
        }
    }

    /**
     * Merges a foreground image onto a background image at the specified offsets. If replaceGreenscreen is true, the greenscreen is keyed out first (see {@link #removeGreenscreen(BufferedImage)}) so the background shows through with soft edges. In both cases the foreground is alpha-composited onto the background using its alpha channel (also used for the prop/table overlay and the text caption, both of which are PNGs with transparent areas).
     * @param foreground The foreground image to be merged onto the background.
     * @param background The background image to which the foreground will be merged.
     * @param xOffset The horizontal offset at which to start merging the foreground image.
     * @param yOffset The vertical offset at which to start merging the foreground image.
     * @param replaceGreenscreen If true, the greenscreen of the foreground is removed before merging. If false, the foreground's alpha channel is used as is.
     * @return Base64-encoded merged image.
     */
    public String mergeImages(BufferedImage foreground, BufferedImage background, int xOffset, int yOffset, boolean replaceGreenscreen) {
        if (replaceGreenscreen) {
            foreground = removeGreenscreen(foreground);
        }

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

                    if (fgColor.getAlpha() > 0) {
                        int blended = ImageUtils.alphaBlend(fgColor, new Color(result.getRGB(x, y), true));
                        result.setRGB(x, y, blended);
                    }
                }
            }
        }

        return ImageUtils.convertBufferedImageToBase64(result);
    }

    /**
     * Chroma key: turns the greenscreen of an image transparent with soft edges instead of a hard green/not-green cut.
     * <ol>
     *     <li>The screen color is measured from the image border, so it adapts to the actual green of every photo.</li>
     *     <li>Every pixel gets a "greenness" in the color plane (Cb/Cr of YCbCr) relative to that screen color. Brightness
     *     is ignored, so darker shadows and folds on the screen still count as screen.</li>
     *     <li>The greenness is mapped to a transparency between the two thresholds, which gives smooth edges on hair
     *     and pixels that are half person, half screen.</li>
     *     <li>Despill: green light reflected onto the person is removed by capping green at the stronger of red and blue.
     *     Pixels along the cut-out edge are mixed with the screen color, so there green is capped harder at the average
     *     of red and blue (otherwise they leave a thin olive/teal outline). Inside the person the milder cap is kept so
     *     e.g. yellow clothing doesn't turn orange.</li>
     * </ol>
     *
     * @return A new ARGB image with the screen transparent.
     */
    public BufferedImage removeGreenscreen(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        int keyColor = estimateKeyColor(pixels, width, height);
        double keyCb = chromaBlue(keyColor);
        double keyCr = chromaRed(keyColor);
        double keyChroma = Math.hypot(keyCb, keyCr);
        // unit vector pointing from neutral gray towards the screen color
        double keyDirCb = keyCb / keyChroma;
        double keyDirCr = keyCr / keyChroma;

        log.info("Keying greenscreen with screen color #{}", String.format("%06X", keyColor & 0xFFFFFF));

        int[] alphas = new int[pixels.length];
        boolean[] seeThrough = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            double cb = chromaBlue(argb);
            double cr = chromaRed(argb);

            // how far the pixel's color points in the direction of the screen color, minus how far it points elsewhere
            double alongKey = cb * keyDirCb + cr * keyDirCr;
            double acrossKey = Math.abs(cr * keyDirCb - cb * keyDirCr);
            double greenness = (alongKey - acrossKey) / keyChroma;

            double opacity = 1.0 - clamp((greenness - OPAQUE_BELOW_GREENNESS) /
                                         (TRANSPARENT_ABOVE_GREENNESS - OPAQUE_BELOW_GREENNESS));
            alphas[i] = (int) Math.round(opacity * ((argb >>> 24) & 0xFF));
            seeThrough[i] = alphas[i] < 255;
        }

        boolean[] nearEdge = dilate(seeThrough, width, height, EDGE_DESPILL_RADIUS);

        for (int i = 0; i < pixels.length; i++) {
            int alpha = alphas[i];
            if (alpha == 0) {
                pixels[i] = 0;
                continue;
            }

            int argb = pixels[i];
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            int greenLimit = nearEdge[i] ? (r + b) / 2 : Math.max(r, b);
            g = Math.min(g, greenLimit);

            pixels[i] = (alpha << 24) | (r << 16) | (g << 8) | b;
        }

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    /**
     * Median color of the greenish pixels along the image border, where the screen is visible behind the person.
     */
    private int estimateKeyColor(int[] pixels, int width, int height) {
        int borderX = Math.max(1, (int) Math.round(width * KEY_SAMPLE_BORDER));
        int borderY = Math.max(1, (int) Math.round(height * KEY_SAMPLE_BORDER));

        int[] reds = new int[pixels.length];
        int[] greens = new int[pixels.length];
        int[] blues = new int[pixels.length];
        int count = 0;

        for (int y = 0; y < height; y++) {
            boolean inBorderRow = y < borderY || y >= height - borderY;
            for (int x = 0; x < width; x++) {
                if (!inBorderRow && x >= borderX && x < width - borderX) {
                    continue;
                }
                int argb = pixels[y * width + x];
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (g > r * 1.1 && g > b * 1.1) {
                    reds[count] = r;
                    greens[count] = g;
                    blues[count] = b;
                    count++;
                }
            }
        }

        if (count < 100) {
            log.warn("Found only {} green pixels along the image border, using default greenscreen color", count);
            return FALLBACK_KEY_COLOR;
        }

        return (median(reds, count) << 16) | (median(greens, count) << 8) | median(blues, count);
    }

    /**
     * Marks every pixel that has a marked pixel within the given radius (square neighbourhood), done as a horizontal
     * and a vertical pass.
     */
    private static boolean[] dilate(boolean[] mask, int width, int height, int radius) {
        boolean[] horizontal = new boolean[mask.length];
        for (int y = 0; y < height; y++) {
            int lastMarked = Integer.MIN_VALUE / 2;
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x]) {
                    lastMarked = x;
                }
                horizontal[y * width + x] = x - lastMarked <= radius;
            }
            lastMarked = Integer.MAX_VALUE / 2;
            for (int x = width - 1; x >= 0; x--) {
                if (mask[y * width + x]) {
                    lastMarked = x;
                }
                horizontal[y * width + x] |= lastMarked - x <= radius;
            }
        }

        boolean[] result = new boolean[mask.length];
        for (int x = 0; x < width; x++) {
            int lastMarked = Integer.MIN_VALUE / 2;
            for (int y = 0; y < height; y++) {
                if (horizontal[y * width + x]) {
                    lastMarked = y;
                }
                result[y * width + x] = y - lastMarked <= radius;
            }
            lastMarked = Integer.MAX_VALUE / 2;
            for (int y = height - 1; y >= 0; y--) {
                if (horizontal[y * width + x]) {
                    lastMarked = y;
                }
                result[y * width + x] |= lastMarked - y <= radius;
            }
        }
        return result;
    }

    private static int median(int[] values, int count) {
        int[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return sorted[count / 2];
    }

    // Cb and Cr of YCbCr (ITU-R BT.601) without the +128 offset, so neutral gray is 0
    private static double chromaBlue(int rgb) {
        return -0.168736 * ((rgb >> 16) & 0xFF) - 0.331264 * ((rgb >> 8) & 0xFF) + 0.5 * (rgb & 0xFF);
    }

    private static double chromaRed(int rgb) {
        return 0.5 * ((rgb >> 16) & 0xFF) - 0.418688 * ((rgb >> 8) & 0xFF) - 0.081312 * (rgb & 0xFF);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private BufferedImage downscaleBackgroundIfNeeded(BufferedImage background, int foregroundWidth, int foregroundHeight) {
        int maxAllowedWidth = (int) Math.round(foregroundWidth * MAX_BACKGROUND_TO_FOREGROUND_SCALE);
        int maxAllowedHeight = (int) Math.round(foregroundHeight * MAX_BACKGROUND_TO_FOREGROUND_SCALE);

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

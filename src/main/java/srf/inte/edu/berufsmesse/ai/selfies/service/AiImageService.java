package srf.inte.edu.berufsmesse.ai.selfies.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.AiConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.LocationOffsetConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.OverlayOffsetConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class AiImageService {

    // Keep background sharper than foreground, but cap extreme resolution gaps.
    private static final double MAX_BACKGROUND_TO_FOREGROUND_SCALE = 2.5;

    private final AiConfiguration aiConfiguration;
    private final LocationOffsetConfiguration locationOffsetConfiguration;
    private final OverlayOffsetConfiguration overlayOffsetConfiguration;
    private final ObjectMapper objectMapper;

    public String createImage(StartGenerationRequest request) {

        // image of the captured person with AI adjustments
        var personImage = generateImage(request);
        if (personImage == null || personImage.isBlank()) {
            log.error("Generated person image is empty");
            return null;
        }

        // person in front of the background with greenscreen removed
        var greenscreenImage = replaceGreenscreen(request, personImage);

        // image with overlayed objects over the person
        var overlayImage = addOverlay(greenscreenImage, request.getBackground());

        // the final image with a text overlay displaying the name
        var textOverlayImage = addTextOverlay(overlayImage, request);

        log.info("Generated image of size {} bytes", textOverlayImage != null ? Base64.getDecoder().decode(textOverlayImage).length : "null");
        return textOverlayImage;
    }

    private String generateImage(StartGenerationRequest userRequest) {

        log.info("Generating image at {}", aiConfiguration.getUrl());
        var promptConfig = aiConfiguration.getPrompt(userRequest.getGender(), userRequest.getBackground());

        try {
            // Force HTTP/1.1: the default h2c upgrade attempt makes the AI server drop the multipart body
            var client = HttpClient.newBuilder()
                                   .version(HttpClient.Version.HTTP_1_1)
                                   .connectTimeout(aiConfiguration.getConnectTimeout())
                                   .build();
            var boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
            var body = new ByteArrayOutputStream();
            var prompt = promptConfig.getPrompt() + "\r\n";

            // prompt
            body.write(("--" + boundary + "\r\n").getBytes());
            body.write("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n".getBytes());
            body.write(prompt.getBytes());

            // seed
            body.write(("--" + boundary + "\r\n").getBytes());
            body.write("Content-Disposition: form-data; name=\"seed\"\r\n\r\n".getBytes());
            body.write(String.valueOf(promptConfig.getSeed()).getBytes());
            body.write("\r\n".getBytes());

            // steps
            body.write(("--" + boundary + "\r\n").getBytes());
            body.write("Content-Disposition: form-data; name=\"num_inference_steps\"\r\n\r\n".getBytes());
            body.write(String.valueOf(promptConfig.getSteps()).getBytes());
            body.write("\r\n".getBytes());

            // image
            body.write(("--" + boundary + "\r\n").getBytes());
            body.write("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpeg\"\r\n".getBytes());
            body.write("Content-Type: image/jpeg\r\n\r\n".getBytes());

            byte[] imageBytes = normalizeOrientation(Base64.getDecoder().decode(userRequest.getImage()));
            body.write(imageBytes);

            body.write("\r\n".getBytes());
            body.write(("--" + boundary + "--\r\n").getBytes());

            var request = HttpRequest.newBuilder()
                                     .uri(URI.create(aiConfiguration.getUrl()))
                                     .timeout(aiConfiguration.getRequestTimeout())
                                     .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                                     .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                                     .build();

            log.info("Wrote request body ({} bytes), sending request with connect timeout {} and request timeout {}...",
                     body.size(), aiConfiguration.getConnectTimeout(), aiConfiguration.getRequestTimeout());

            log.info("Sending request with prompt: {}", promptConfig.getPrompt().replace("\n", "\\n"));

            Instant startedAt = Instant.now();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

            log.info("Received response with status code {} in {} ms", response.statusCode(), durationMs);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                var bodyText = new String(response.body(), StandardCharsets.UTF_8);
                if (bodyText.length() > 500) {
                    bodyText = bodyText.substring(0, 500) + "...";
                }
                log.error("Image generation failed with status {} and body: {}", response.statusCode(), bodyText);
                return null;
            }

            return Base64.getEncoder().encodeToString(response.body());
        } catch (IOException e) {
            log.error("Error generating image", e);
            return null;
        } catch (IllegalArgumentException e) {
            log.error("Invalid base64 input image: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            log.error("Image generation interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
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

    private String replaceGreenscreen(StartGenerationRequest request, String imageBase64) {
        var offset = locationOffsetConfiguration.getForLocation(request.getBackground());
        log.info("Using location offset for {}: x={}, y={}, scale={}", request.getBackground(), offset.getX(),
                 offset.getY(), offset.getScale());

        try {
            if (imageBase64 == null || imageBase64.isBlank()) {
                log.error("replaceGreenscreen received empty base64 image");
                return null;
            }

            var foreground = base64ToBufferedImage(imageBase64);
            var background = readImageFromResources(getBackgroundImagePath(request.getBackground()));

            if (background == null || foreground == null) {
                log.error("Failed to read background or foreground image for greenscreen replacement");
                return null;
            }

            // Downscale the background relative to the person's true (unscaled) resolution,
            // then apply the location's scale purely for framing within the final composite.
            var scaledBackground = downscaleBackgroundIfNeeded(background, foreground);
            if (offset.getScale() != 1.0) {
                foreground = scaleImage(foreground, offset.getScale());
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

    private String addTextOverlay(String base64Image, StartGenerationRequest request) {
        var classloader = Thread.currentThread().getContextClassLoader();

        String textOverlaySvg;
        var overlayPath = getTextOverlayPath(request.getBackground());
        log.info("Getting text overlay from {}", classloader.getResource(overlayPath));

        try (InputStream textOverlayStream = classloader.getResourceAsStream(overlayPath)) {
            if (textOverlayStream == null) {
                log.error("Text overlay SVG not found for background {}", request.getBackground());
                return base64Image;
            }
            textOverlaySvg = new String(textOverlayStream.readAllBytes(), StandardCharsets.UTF_8);
            var escapedName = HtmlUtils.htmlEscape(request.getName());
            textOverlaySvg = textOverlaySvg.replace("VORNAME", escapedName);
            textOverlaySvg = textOverlaySvg.replace("Vorname", escapedName);
        } catch (IOException ex) {
            log.error("Error reading text overlay SVG from resources", ex);
            return base64Image;
        }

        // Force HTTP/1.1: Next.js treats the default h2c upgrade as a websocket request and drops the connection
        var client = HttpClient.newBuilder()
                               .version(HttpClient.Version.HTTP_1_1)
                               .connectTimeout(Duration.ofSeconds(5))
                               .build();

        try {
            var requestHttp = HttpRequest.newBuilder()
                                         .uri(URI.create("http://localhost:3000/api/svg2png"))
                                         .timeout(Duration.ofSeconds(15))
                                         .header("Content-Type", "application/json")
                                         .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("html", textOverlaySvg))))
                                         .build();

            var response = client.send(requestHttp, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Text overlay generation failed with status {} and body: {}", response.statusCode(), response.body());
                return base64Image;
            }
            var textOverlayBase64 = objectMapper.readTree(response.body()).path("image").asText(null);
            if (textOverlayBase64 == null || textOverlayBase64.isBlank()) {
                log.error("Text overlay response contained no image: {}", response.body());
                return base64Image;
            }

            return mergeImages(
                    base64ToBufferedImage(textOverlayBase64),
                    Objects.requireNonNull(base64ToBufferedImage(base64Image)),
                    0,
                    0,
                    false
            );
        } catch (IOException ex) {
            log.error("Error generating text overlay image", ex);
            return base64Image;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return base64Image;
        }
    }

    /**
     * Scales an image by the given factor, keeping its alpha channel (needed for overlays with transparent areas).
     */
    private static BufferedImage scaleImage(BufferedImage image, double factor) {
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

    private String addOverlay(String base64Image, String backgroundImage) {
        if (backgroundImage.equals("Radio")) return base64Image; // Radio has no overlay

        BufferedImage overlay = readImageFromResources(getOverlayImagePath(backgroundImage));
        BufferedImage background = base64ToBufferedImage(base64Image);

        if (overlay == null || background == null) {
            log.error("Failed to read overlay or background image for overlay");
            return null;
        }

        // Props like the Sport microphone are cut-outs, not full-frame overlays, so they need their own size and position
        var offset = overlayOffsetConfiguration.getForLocation(backgroundImage);
        if (offset.getScale() != 1.0) {
            overlay = scaleImage(overlay, offset.getScale());
        }

        return mergeImages(overlay, background, offset.getX(), offset.getY(), false);
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
    private String mergeImages(BufferedImage foreground, BufferedImage background, int xOffset, int yOffset, boolean replaceGreenscreen) {
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
                        if (!isGreen(fgColor)) {
                            result.setRGB(x, y, 0xFF000000 | fgColor.getRGB());
                        }
                        // green foreground pixel: leave the background pixel already drawn in place
                    } else if (fgColor.getAlpha() > 0) {
                        int blended = alphaBlend(fgColor, new Color(result.getRGB(x, y), true));
                        result.setRGB(x, y, blended);
                    }
                }
            }
        }

        return convertBufferedImageToBase64(result);
    }

    /**
     * Alpha-composites a (possibly semi-transparent) foreground color over an opaque/semi-transparent background color, "over" operator.
     */
    private static int alphaBlend(Color fg, Color bg) {
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

    private String convertBufferedImageToBase64(BufferedImage image) {
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

    private BufferedImage readImageFromResources(String path) {
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

    private String getBackgroundImagePath(String background) {
        return String.format("images/%s/%s.jpg", background, background);
    }

    private String getOverlayImagePath(String background) {
        return String.format("images/%s/overlay.png", background);
    }

    private String getTextOverlayPath(String background) {
        return String.format("images/%s/text_overlay.svg", background);
    }

    private BufferedImage base64ToBufferedImage(String base64Image) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException ex) {
            log.error("Error decoding base64 image", ex);
            return null;
        }
    }

    private static boolean isGreen(Color c) {
        int green = c.getGreen();
        int red = c.getRed();
        int blue = c.getBlue();

        return green > 100 &&
               green > red * 1.1 &&
               green > blue * 1.1;
    }
}

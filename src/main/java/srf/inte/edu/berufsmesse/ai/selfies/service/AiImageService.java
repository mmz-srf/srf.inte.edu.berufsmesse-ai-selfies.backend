package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.AiConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.LocationOffsetConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;

import javax.imageio.ImageIO;
import java.awt.*;
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
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class AiImageService {

    // Keep background sharper than foreground, but cap extreme resolution gaps.
    private static final double MAX_BACKGROUND_TO_FOREGROUND_SCALE = 2.5;

    private final AiConfiguration aiConfiguration;
    private final LocationOffsetConfiguration locationOffsetConfiguration;

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
            var client = HttpClient.newBuilder()
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

            byte[] imageBytes = Base64.getDecoder().decode(userRequest.getImage());
            InputStream imageStream = new ByteArrayInputStream(imageBytes);
            imageStream.transferTo(body);

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

    private String replaceGreenscreen(StartGenerationRequest request, String imageBase64) {
        var offset = locationOffsetConfiguration.getForLocation(request.getBackground());
        log.info("Using location offset for {}: x={}, y={}", request.getBackground(), offset.getX(), offset.getY());

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

            var scaledBackground = downscaleBackgroundIfNeeded(background, foreground);

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
        try (InputStream textOverlayStream = classloader.getResourceAsStream(getOverlayImagePath(request.getBackground()) + "/text_overlay.svg")) {
            if (textOverlayStream == null) {
                log.error("Text overlay SVG not found for background {}", request.getBackground());
                return base64Image;
            }
            textOverlaySvg = new String(textOverlayStream.readAllBytes(), StandardCharsets.UTF_8);
            textOverlaySvg = textOverlaySvg.replace("VORNAME", request.getName());
            textOverlaySvg = textOverlaySvg.replace("Vorname", request.getName());
        } catch (IOException ex) {
            log.error("Error reading text overlay SVG from resources", ex);
            return base64Image;
        }

        var client = HttpClient.newBuilder()
                               .connectTimeout(Duration.ofSeconds(5))
                               .build();
        var requestHttp = HttpRequest.newBuilder()
                                     .uri(URI.create("http://localhost:3000/api/html2png"))
                                     .timeout(Duration.ofSeconds(10))
                                     .header("Content-Type", "application/json")
                                     .POST(HttpRequest.BodyPublishers.ofString("{\"html\": \"" + textOverlaySvg.replace("\n", "") + "\"}"))
                                     .build();

        try {
            var response = client.send(requestHttp, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Text overlay generation failed with status {} and body: {}", response.statusCode(), response.body());
                return base64Image;
            }
            var textOverlayBase64 = response.body();

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

        return mergeImages(overlay, background, 0, 0, false);
    }

    /**
     * Merges a foreground image onto a background image at the specified offsets. If replaceGreenscreen is true, only pixels in the foreground that are considered green (based on the isGreen method) will replace the background pixels. If false, all non-green pixels in the foreground will replace the background.
     * @param foreground The foreground image to be merged onto the background.
     * @param background The background image to which the foreground will be merged.
     * @param xOffset The horizontal offset at which to start merging the foreground image.
     * @param yOffset The vertical offset at which to start merging the foreground image.
     * @param replaceGreenscreen If true, only pixels in the foreground that are considered green (based on the isGreen method) will replace the background pixels. If false, all non-green pixels in the foreground will replace the background.
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
                    Color fgColor = new Color(foreground.getRGB(fgX, fgY));
                    if (!replaceGreenscreen || isGreen(fgColor)) {
                        result.setRGB(x, y, fgColor.getRGB());
                    }
                    if (!isGreen(fgColor)) {
                        result.setRGB(x, y, fgColor.getRGB());
                    }
                }
            }
        }

        return convertBufferedImageToBase64(result);
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

        return green > 120 &&
               green > red * 1.3 &&
               green > blue * 1.3;
    }
}

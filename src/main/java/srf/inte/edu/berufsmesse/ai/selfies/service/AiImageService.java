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

@RequiredArgsConstructor
@Service
@Slf4j
public class AiImageService {

    private final AiConfiguration aiConfiguration;
    private final LocationOffsetConfiguration locationOffsetConfiguration;
    private final UploadS3Service uploadS3Service;

    public String createImage(StartGenerationRequest request) {
        var personImage = generateImage(request);
        if (personImage == null || personImage.isBlank()) {
            log.error("Generated person image is empty");
            return null;
        }
        var greenscreenImage = replaceGreenscreen(request, personImage);
        log.info("Generated image of size {} bytes", greenscreenImage != null ? Base64.getDecoder().decode(greenscreenImage).length : "null");

        return greenscreenImage;
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

            var foregroundImageBytes = Base64.getDecoder().decode(imageBase64);
            var foreground = ImageIO.read(new ByteArrayInputStream(foregroundImageBytes));
            if (foreground == null) {
                log.error("Could not decode foreground image bytes");
                return null;
            }

            var classloader = Thread.currentThread().getContextClassLoader();
            var backgroundPath = getBackgroundImagePath(request.getBackground());
            var backgroundStream = classloader.getResourceAsStream(backgroundPath);
            if (backgroundStream == null) {
                log.error("Background image not found at {}", backgroundPath);
                return null;
            }
            var background = ImageIO.read(backgroundStream);
            if (background == null) {
                log.error("Could not decode background image from {}", backgroundPath);
                return null;
            }

            BufferedImage result = new BufferedImage(
                    foreground.getWidth(),
                    foreground.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            for (int y = 0; y < foreground.getHeight(); y++) {
                for (int x = 0; x < foreground.getWidth(); x++) {

                    Color fgColor = new Color(foreground.getRGB(x, y));

                    if (isGreen(fgColor)) {

                        int bgX = x + offset.getX();
                        int bgY = y + offset.getY();

                        if (bgX >= 0 && bgY >= 0 &&
                            bgX < background.getWidth() &&
                            bgY < background.getHeight()) {

                            result.setRGB(x, y, background.getRGB(bgX, bgY));
                        } else {
                            result.setRGB(x, y, 0); // transparent
                        }

                    } else {
                        result.setRGB(x, y, fgColor.getRGB());
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(result, "png", baos);
            if (!written || baos.size() == 0) {
                log.error("Could not encode result image. written={}, size={} bytes", written, baos.size());
                return null;
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException ex) {
            log.error("Error reading greenscreen image to bytes", ex);
            return null;
        } catch (IllegalArgumentException ex) {
            log.error("Invalid base64 input for greenscreen replacement", ex);
            return null;
        }
    }

    private String getBackgroundImagePath(String background) {
        return String.format("images/%s/%s.jpg", background, background);
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

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
import srf.inte.edu.berufsmesse.ai.selfies.utils.ImageUtils;

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
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class AiImageService {
    private final AiConfiguration aiConfiguration;
    private final OverlayOffsetConfiguration overlayOffsetConfiguration;
    private final ObjectMapper objectMapper;

    // services
    private final GreenscreenRemovalService greenscreenRemovalService;
    private final ImageRotationService imageRotationService;
    private final SvgOverlayService svgOverlayService;

    public String createImage(StartGenerationRequest request) {

        // image of the captured person with AI adjustments
        var personImage = generateImage(request);
        if (personImage == null || personImage.isBlank()) {
            log.error("Generated person image is empty");
            return null;
        }

        // person in front of the background with greenscreen removed
        var greenscreenImage = greenscreenRemovalService.replaceGreenscreenAndMerge(request, personImage);

        // image with overlayed objects over the person
        var overlayImage = svgOverlayService.addOverlay(greenscreenImage, request.getBackground());

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

            byte[] imageBytes = imageRotationService.normalizeOrientation(Base64.getDecoder().decode(userRequest.getImage()));
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

    private String addTextOverlay(String base64Image, StartGenerationRequest request) {
        var classloader = Thread.currentThread().getContextClassLoader();

        String textOverlaySvg;
        var overlayPath = ImageUtils.getTextOverlayPath(request.getBackground());
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

            return greenscreenRemovalService.mergeImages(
                    ImageUtils.base64ToBufferedImage(textOverlayBase64),
                    Objects.requireNonNull(ImageUtils.base64ToBufferedImage(base64Image)),
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
}

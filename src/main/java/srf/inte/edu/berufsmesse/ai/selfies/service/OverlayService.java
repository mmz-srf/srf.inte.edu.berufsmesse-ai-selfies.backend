package srf.inte.edu.berufsmesse.ai.selfies.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.OverlayOffsetConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;
import srf.inte.edu.berufsmesse.ai.selfies.utils.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class OverlayService {
    private final OverlayOffsetConfiguration overlayOffsetConfiguration;
    private final GreenscreenRemovalService greenscreenRemovalService;
    private final ObjectMapper objectMapper;

    public String addOverlay(String base64Image, String backgroundImage) {
        if (backgroundImage.equals("Radio")) return base64Image; // Radio has no overlay

        BufferedImage overlay = ImageUtils.readImageFromResources(ImageUtils.getOverlayImagePath(backgroundImage));
        BufferedImage background = ImageUtils.base64ToBufferedImage(base64Image);

        if (overlay == null || background == null) {
            log.error("Failed to read overlay or background image for overlay");
            return null;
        }

        // Props like the Sport microphone are cut-outs, not full-frame overlays, so they need their own size and position
        var offset = overlayOffsetConfiguration.getForLocation(backgroundImage);
        if (offset.getScale() != 1.0) {
            overlay = ImageUtils.scaleImage(overlay, offset.getScale());
        }

        return greenscreenRemovalService.mergeImages(overlay, background, offset.getX(), offset.getY(), false);
    }

    public String addTextOverlay(String base64Image, StartGenerationRequest request) {
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
                                         .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                                                 Map.of("html", textOverlaySvg))))
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

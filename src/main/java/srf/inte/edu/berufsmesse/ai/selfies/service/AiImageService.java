package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.AiConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    // services
    private final GreenscreenRemovalService greenscreenRemovalService;
    private final ImageRotationService imageRotationService;
    private final OverlayService overlayService;

    public String createImage(StartGenerationRequest request) {

        // image of the captured person with AI adjustments
        var personImage = request.getImage();

        // radio background needs no AI generation, person keeps their normal clothing
        if (!request.getBackground().equals("Radio"))
            personImage = generateImage(request);

        if (personImage == null || personImage.isBlank()) {
            log.error("Generated person image is empty");
            return null;
        }

        // person in front of the background with greenscreen removed
        var greenscreenImage = greenscreenRemovalService.replaceGreenscreenAndMerge(request, personImage);

        // image with overlayed objects over the person
        var overlayImage = overlayService.addOverlay(greenscreenImage, request.getBackground());

        // the final image with a text overlay displaying the name
        var textOverlayImage = overlayService.addTextOverlay(overlayImage, request);

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
}

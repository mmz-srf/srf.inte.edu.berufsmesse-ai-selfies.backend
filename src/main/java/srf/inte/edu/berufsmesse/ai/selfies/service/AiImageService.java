package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.AiConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@RequiredArgsConstructor
@Service
@Slf4j
public class AiImageService {

    private final AiConfiguration aiConfiguration;

    public String createImage(StartGenerationRequest request) {
        var personImage = generateImage(request.getImage());

        return personImage;
    }

    private String generateImage(String inputImageBase64) {

        log.info("Generating image at {}", aiConfiguration.getUrl());

        try {
            var client = HttpClient.newBuilder()
                                   .connectTimeout(aiConfiguration.getConnectTimeout())
                                   .build();
            var boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
            var body = new ByteArrayOutputStream();
            var prompt = aiConfiguration.getExamplePrompt() + "\r\n";

            body.write(("--" + boundary + "\r\n").getBytes());
            body.write("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n".getBytes());
            body.write(prompt.getBytes());

            body.write(("--" + boundary + "\r\n").getBytes());
            body.write("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpeg\"\r\n".getBytes());
            body.write("Content-Type: image/jpeg\r\n\r\n".getBytes());

            byte[] imageBytes = Base64.getDecoder().decode(inputImageBase64);
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
            log.error("Error generating image: {}", e.getMessage());
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

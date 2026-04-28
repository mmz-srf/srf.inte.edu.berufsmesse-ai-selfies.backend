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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
            var client = HttpClient.newHttpClient();
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
                                     .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                                     .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                                     .build();

            log.info("Wrote request body, sending request...");

            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            log.info("Received response with status code: {}", response.statusCode());

            return Base64.getEncoder().encodeToString(response.body());
        } catch (IOException e) {
            log.error("Error generating image: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            log.error("Image generation interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }
}

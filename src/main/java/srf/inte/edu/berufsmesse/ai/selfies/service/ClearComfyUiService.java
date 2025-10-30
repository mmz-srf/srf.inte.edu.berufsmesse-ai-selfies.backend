package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ClearComfyUiService {
    private static final Logger log = LoggerFactory.getLogger(ClearComfyUiService.class);
    private static final Path outputDir = Path.of("/Users/sleipnir03/Documents/ComfyUI/output");
    private static final Path inputDir = Path.of("/Users/sleipnir03/Documents/ComfyUI/input");

    private final DocumentProperties documentProperties;

    public void clearComfyUi(String imagePath, String selfieId) {
        clearWorkflow();
        clearAiImage(imagePath);
        clearUploadImage(selfieId);
    }

    private void clearUploadImage(String selfieId) {
        if (selfieId == null || selfieId.isBlank()) {
            log.debug("clearUploadImage: selfieId blank -> nothing to delete");
            return;
        }

        String filename = Paths.get(selfieId).getFileName().toString();
        if (!filename.toLowerCase().endsWith(".png")) {
            filename = filename + ".png";
        }

        Path file = inputDir.resolve(filename);
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Deleted upload image from inputDir: {}", file);
            } else {
                log.debug("Upload image not found (nothing to delete): {}", file);
            }
        } catch (IOException e) {
            log.warn("Failed to delete upload image {}: {}", file, e.toString());
        }
    }

    private void clearAiImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            log.debug("clearAiImage: imagePath blank -> nothing to delete");
            return;
        }

        String filename = Paths.get(imagePath).getFileName().toString();
        Path file = outputDir.resolve(filename);
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Deleted AI image from outputDir: {}", file);
            } else {
                log.debug("AI image not found (nothing to delete): {}", file);
            }
        } catch (IOException e) {
            log.warn("Failed to delete AI image {}: {}", file, e.toString());
        }
    }

    private void clearWorkflow() {
        String url = documentProperties.getComfyuiUrl() + "/history";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"clear\": true}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            log.debug("Requested ComfyUI to clear all workflows via {}", url);
        } catch (RestClientException e) {
            log.warn("Failed to clear ComfyUI workflows at {}: {}", url, e.toString());
        }
    }
}

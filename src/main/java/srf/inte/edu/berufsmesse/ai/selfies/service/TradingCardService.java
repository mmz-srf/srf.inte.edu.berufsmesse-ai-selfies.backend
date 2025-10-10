package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import srf.inte.edu.berufsmesse.ai.selfies.model.TradingCardRequest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class TradingCardService {

    private static final String COMFY_UI_BASE_URL = "http://192.168.2.1:8188";

    public String createCard(TradingCardRequest request) {
        log.info("Creating AI trading card for '{}', theme '{}', selfie '{}'",
                 request.getName(), request.getTheme(), request.getSelfieId());

        String selfiePath =
                "uploads/" + request.getSelfieId() + "_" + request.getName().replaceAll("\\s+", "_") + ".png";

        String generatedImagePath = callComfyUI(selfiePath, request.getName(), request.getTheme());

        // TODO: SVG befüllen mit generiertem Bild + Name
        // TODO: Upload zu S3 (Rückgabe der öffentlichen URL)

        log.info("Generated AI image saved at: {}", generatedImagePath);
        return generatedImagePath;
    }

    private String callComfyUI(String selfiePath, String name, String theme) {
        RestTemplate restTemplate = new RestTemplate();

        String uploadUrl = COMFY_UI_BASE_URL + "/upload/image";
        File imageFile = new File(selfiePath);
        if (!imageFile.exists()) {
            throw new RuntimeException("Selfie file not found: " + selfiePath);
        }

        MultiValueMap<String, Object> uploadData = new LinkedMultiValueMap<>();
        uploadData.add("image", new FileSystemResource(imageFile));

        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> uploadRequest =
                new HttpEntity<>(uploadData, uploadHeaders);

        try {
            ResponseEntity<String> uploadResponse =
                    restTemplate.postForEntity(uploadUrl, uploadRequest, String.class);
            log.info("Uploaded image to ComfyUI: {}", uploadResponse.getBody());
        } catch (Exception e) {
            log.error("Upload to ComfyUI failed", e);
            throw new RuntimeException("Failed to upload image to ComfyUI");
        }

        String comfyPromptUrl = COMFY_UI_BASE_URL + "/prompt";

        Map<String, Object> workflowPayload = new HashMap<>();
        workflowPayload.put("prompt", Map.of(
                "1", Map.of(
                        "inputs", Map.of(
                                "image", selfiePath,
                                "style", theme,
                                "name", name
                        ),
                        "class_type", "AI_Card_Generator"
                )
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity =
                new HttpEntity<>(workflowPayload, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(comfyPromptUrl, requestEntity, Map.class);
            log.info("ComfyUI prompt started: {}", response.getBody());
        } catch (Exception e) {
            log.error("ComfyUI prompt call failed", e);
            throw new RuntimeException("AI generation failed");
        }

        String outputFile = "generated/pokemon_" + UUID.randomUUID() + ".png";
        log.info("Expected output file: {}", outputFile);
        return outputFile;
    }
}

package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;
import srf.inte.edu.berufsmesse.ai.selfies.model.PromptResponse;
import srf.inte.edu.berufsmesse.ai.selfies.model.CardImageRequest;
import srf.inte.edu.berufsmesse.ai.selfies.model.UploadResponse;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CardImageService {

    private final DocumentProperties documentProperties;

    public String createCard(CardImageRequest request) {
        log.info("Creating AI trading card for {}", request.getSelfieId());

        // Return workflowId
        return callComfyUI(request.getSelfieId(), request.getImageBase64());
    }

    private String callComfyUI(String selfieId, String base64) {
        String comfyBaseUrl = documentProperties.getComfyuiUrl();
         String promptPath = documentProperties.getPromptPath();
        RestTemplate restTemplate = new RestTemplate();

        // Request to upload the image to comfyUI
        String uploadUrl = comfyBaseUrl + "/upload/image";

        // Base64 -> byte[]
        byte[] imageBytes = convertBase64ToBytes(base64);

        MultiValueMap<String, Object> uploadData = new LinkedMultiValueMap<>();
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return selfieId + ".png";
            }
        };
        uploadData.add("image", imageResource); // Set body value


        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> uploadRequest =
                new HttpEntity<>(uploadData, uploadHeaders);

        String workflowImagePath = selfieId + ".png";
        try {
            ResponseEntity<UploadResponse> uploadResponse =
                    restTemplate.postForEntity(uploadUrl, uploadRequest, UploadResponse.class);
            log.info("Uploaded image to ComfyUI: {}", uploadResponse.getBody());
            UploadResponse uploadResponseBody = uploadResponse.getBody();

            // ComfyUI may change the filename of the uploaded image
            String subfolder = uploadResponseBody != null ? uploadResponseBody.getSubfolder() : "";
            String fileName = Objects.requireNonNull(uploadResponseBody).getName();
            if (subfolder != null && !subfolder.isBlank()) {
                workflowImagePath = String.format("%s/%s", subfolder, fileName);
            } else if (fileName != null && !fileName.isBlank()) {
                workflowImagePath = fileName;
            }

        } catch (Exception e) {
            log.error("Upload to ComfyUI failed", e);
            throw new RuntimeException("Failed to upload image to ComfyUI");
        }

        // Start ComfyUI workflow
        String workflowId;
        try (var inputStream = CardImageService.class.getResourceAsStream(promptPath)) {
            Assert.notNull(inputStream, "no input-stream present");

            var prompt = new String(inputStream.readAllBytes());
            prompt = prompt.replace("{{IMAGE}}", workflowImagePath);

            var response = restTemplate.postForEntity(comfyBaseUrl + "/prompt", new HttpEntity<>(prompt), PromptResponse.class);
            workflowId = Objects.requireNonNull(response.getBody()).getPromptId();
        } catch (IOException ioException) {
            throw new IllegalStateException("failed to load prompt-template", ioException);
        }

        log.info("Workflow workflowId: {}", workflowId);
        return workflowId;
    }

    private byte[] convertBase64ToBytes(String base64) {
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Base64 input", e);
            throw new RuntimeException("Invalid Base64 image data");
        }

        return imageBytes;
    }
}

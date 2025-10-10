package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
public class SelfieService {

    public String saveBase64Image(String base64, String name) {
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Base64 input", e);
            throw new RuntimeException("Invalid Base64 image data");
        }

        String selfieId = UUID.randomUUID().toString();
        String fileName = "uploads/" + selfieId + "_" + name.replaceAll("\\s+", "_") + ".png";

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            fos.write(imageBytes);
            log.info("Saved Base64 selfie to {}", fileName);
        } catch (IOException e) {
            log.error("Error saving selfie", e);
            throw new RuntimeException("Could not save selfie", e);
        }

        return selfieId;
    }
}
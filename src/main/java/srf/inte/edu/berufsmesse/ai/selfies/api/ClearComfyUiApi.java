package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clear-comfy-ui")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Slf4j
public class ClearComfyUiApi {

    @PostMapping("/clear-image")
    public ResponseEntity<Void> clearImageFromCache(@RequestParam String imagePath) {
        if (imagePath.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Clearing image from cache: {}", imagePath);
        // TODO Implement cache clearing
        return ResponseEntity.ok().build();
    }

    @PostMapping("/clear-queue")
    public ResponseEntity<Void> clearImageFromQueue(@RequestParam String workflowId) {
        if (workflowId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Clearing {} from queue", workflowId);
        // TODO Implement queue clearing
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<Void> clearUploadImage(@RequestBody String selfieId) {
        if (selfieId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Clearing image for {}", selfieId);
        // TODO Implement image clearing
        return ResponseEntity.ok().build();
    }
}

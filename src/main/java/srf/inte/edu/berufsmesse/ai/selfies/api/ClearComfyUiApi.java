package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.ClearComfyUiRequest;

@RestController
@RequestMapping("/api/v1/clear-comfy-ui")
@RequiredArgsConstructor
@Slf4j
public class ClearComfyUiApi {

    @PostMapping("/clear-all")
    @CrossOrigin(origins = "https://192.168.2.1:3000")
    public ResponseEntity<Void> clearComfyUiCache(@RequestBody ClearComfyUiRequest request) {
        String workflowId = request.getWorkflowId();
        String imagePath = request.getImagePath();
        String selfieId = request.getSelfieId();

        if (workflowId.isBlank() || imagePath.isBlank() || selfieId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // TODO Implemet clearing all caches
        return ResponseEntity.ok().build();
    }
}

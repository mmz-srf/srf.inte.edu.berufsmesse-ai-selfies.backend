package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.ClearComfyUiRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.ClearComfyUiService;

@RestController
@RequestMapping("/api/v1/clear-comfy-ui")
@RequiredArgsConstructor
@Slf4j
public class ClearComfyUiApi {

    private final ClearComfyUiService clearComfyUiService;

    @PostMapping("/clear-all")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<Void> clearComfyUiCache(@RequestBody ClearComfyUiRequest request) {
        String workflowId = request.getWorkflowId();
        String imagePath = request.getImagePath();
        String selfieId = request.getSelfieId();

        if (workflowId.isBlank() || imagePath.isBlank() || selfieId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        clearComfyUiService.clearComfyUi(workflowId, imagePath, selfieId);
        return ResponseEntity.ok().build();
    }
}

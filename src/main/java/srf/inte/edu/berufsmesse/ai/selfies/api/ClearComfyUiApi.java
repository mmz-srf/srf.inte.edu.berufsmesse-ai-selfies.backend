package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.ClearComfyUiRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.ClearComfyUiService;

@RestController
@RequestMapping("/api/v1/clear-comfy-ui")
@RequiredArgsConstructor
@Slf4j
public class ClearComfyUiApi {

    private final ClearComfyUiService clearComfyUiService;

    @PostMapping
    public ResponseEntity<Void> clearComfyUiCache(@RequestBody ClearComfyUiRequest request) {
        String imagePath = request.getImagePath();
        String selfieId = request.getSelfieId();

        if (imagePath.isBlank() || selfieId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        clearComfyUiService.clearComfyUi(imagePath, selfieId);
        return ResponseEntity.ok().build();
    }
}

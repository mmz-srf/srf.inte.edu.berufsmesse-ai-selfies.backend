package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.AiImageService;

@RestController
@RequestMapping("/api/v1/card-image")
@RequiredArgsConstructor
@Slf4j
public class StartGenerationApi {

    private final AiImageService aiImageService;

    @PostMapping
    public ResponseEntity<Void> createCardImage(@RequestBody StartGenerationRequest request) {
        log.info("Received request: {}", request);
        aiImageService.createImage(request);
        return ResponseEntity.ok(null);
    }
}
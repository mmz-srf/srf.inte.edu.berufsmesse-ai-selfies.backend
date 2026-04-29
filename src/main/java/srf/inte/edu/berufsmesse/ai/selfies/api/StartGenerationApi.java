package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.StartGenerationRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.AiImageService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/card-image")
@RequiredArgsConstructor
@Slf4j
public class StartGenerationApi {

    private final AiImageService aiImageService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createCardImage(@RequestBody StartGenerationRequest request) {
        log.info("Received request for background {}", request.getBackground());
        String base64OutputImage = aiImageService.createImage(request);
        if (base64OutputImage == null || base64OutputImage.isBlank()) {
            log.error("Image generation returned empty result, returning 502");
            return new ResponseEntity<>(
                    Map.of("error", "Image generation failed. The AI service may be unavailable."),
                    HttpStatus.BAD_GATEWAY
            );
        }
        return new ResponseEntity<>(
                Map.of("image", base64OutputImage),
                HttpStatus.OK
        );
    }
}
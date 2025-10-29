package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.CardImageRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.CardImageService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/card-image")
@RequiredArgsConstructor
@Slf4j
public class CardImageApi {

    private final CardImageService tradingCardService;

    @PostMapping
    @CrossOrigin(origins = "https://192.168.2.1:3000")
    public ResponseEntity<Map<String, String>> createCardImage(@RequestBody CardImageRequest request) {
        log.info("Generating image for {}", request.getSelfieId());
        Map<String, String> workflowId = new HashMap<>();
        workflowId.put("workflowId", tradingCardService.createCard(request));
        return ResponseEntity.ok(workflowId);
    }
}
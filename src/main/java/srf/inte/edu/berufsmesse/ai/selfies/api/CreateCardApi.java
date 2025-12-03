package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.request.CreateCardRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.CreateCardService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/create-card")
@RequiredArgsConstructor
@Slf4j
public class CreateCardApi {

    private final CreateCardService createCardService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createCard(@RequestBody CreateCardRequest request) {
        log.info("Creating trading card for {}, with template {}", request.getImagePath(), request.getTemplate());

        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_JSON)
                             .body(Map.of("html", createCardService.createCard(request)));
    }
}

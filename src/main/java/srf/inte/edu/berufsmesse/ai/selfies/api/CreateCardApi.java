package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import srf.inte.edu.berufsmesse.ai.selfies.model.CreateCardRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.CreateCardService;

@RestController
@RequestMapping("/api/v1/create-card")
@RequiredArgsConstructor
@Slf4j
public class CreateCardApi {

    private final CreateCardService createCardService;

    @PostMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> createCard(@RequestBody CreateCardRequest request) {
        log.info("Creating trading card for {}, with template {}", request.getImagePath(), request.getTemplate());

        return ResponseEntity.ok()
                             .contentType(MediaType.IMAGE_PNG)
                             .body(createCardService.createCard(request));
    }
}

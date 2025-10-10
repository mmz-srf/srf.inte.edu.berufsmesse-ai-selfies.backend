package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import srf.inte.edu.berufsmesse.ai.selfies.model.SelfieUploadRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.SelfieService;

@RestController
@RequestMapping("/api/v1/selfies")
@RequiredArgsConstructor
@Slf4j
public class SelfieApi {

    private final SelfieService selfieService;

    @PostMapping
    public ResponseEntity<String> uploadSelfie(@RequestBody SelfieUploadRequest request) {
        log.info("Received selfie for: {}", request.getName());
        String selfieId = selfieService.saveBase64Image(request.getImageBase64(), request.getName());
        return ResponseEntity.ok(selfieId);
    }
}
package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.UploadCardRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.UploadS3Service;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/upload-card")
@RequiredArgsConstructor
@Slf4j
public class UploadCardApi {

    private final UploadS3Service uploadS3Service;

    @PostMapping
    @CrossOrigin(origins = "https://192.168.2.1:3000")
    public ResponseEntity<Map<String, String>> uploadCard(@RequestBody UploadCardRequest request) {
        return ResponseEntity.ok()
                             .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                             .body(Map.of("url", uploadS3Service.upload(request.getImage(), request.getSelfieId())));
    }

}

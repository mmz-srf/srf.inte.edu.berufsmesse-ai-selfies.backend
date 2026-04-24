package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/card-image")
@RequiredArgsConstructor
@Slf4j
public class StartGenerationApi {


    @PostMapping
    public ResponseEntity<Void> createCardImage(@RequestBody StartGenerationApi request) {
        return ResponseEntity.ok(null);
    }
}
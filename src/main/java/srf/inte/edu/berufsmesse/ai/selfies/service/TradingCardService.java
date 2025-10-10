package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.model.TradingCardRequest;

import java.util.UUID;

@Service
@Slf4j
public class TradingCardService {

    public String createCard(TradingCardRequest request) {
        log.info("Generating AI image for theme '{}' and selfie '{}'", request.getTheme(), request.getSelfieId());

        // TODO: ComfyUI-Call
        // TODO: SVG mit Bild + Name befüllen
        // TODO: Upload zu S3

        return UUID.randomUUID().toString(); // Dummy ID
    }
}
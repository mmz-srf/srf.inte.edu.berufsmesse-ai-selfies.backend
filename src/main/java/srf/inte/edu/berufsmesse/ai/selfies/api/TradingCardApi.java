package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import srf.inte.edu.berufsmesse.ai.selfies.model.TradingCardRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.TradingCardService;

@RestController
@RequestMapping("/api/v1/trading-cards")
@RequiredArgsConstructor
@Slf4j
public class TradingCardApi {

    private final TradingCardService tradingCardService;

    @PostMapping
    public ResponseEntity<String> createTradingCard(@RequestBody TradingCardRequest request) {
        log.info("Creating trading card for {}", request.getName());
        String cardId = tradingCardService.createCard(request);
        return ResponseEntity.ok(cardId);
    }
}
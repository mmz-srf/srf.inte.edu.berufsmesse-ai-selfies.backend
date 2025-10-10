package srf.inte.edu.berufsmesse.ai.selfies.model;

import lombok.Data;

@Data
public class TradingCardRequest {
    private String name;       //  "Pikachu Dario"
    private String selfieId;   // Reference to saved Selfie
    private String theme;      // "electric", "fire"
}
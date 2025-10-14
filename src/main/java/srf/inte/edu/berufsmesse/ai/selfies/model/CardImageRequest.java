package srf.inte.edu.berufsmesse.ai.selfies.model;

import lombok.Data;

@Data
public class CardImageRequest {
    private String selfieId;    // Reference to the selfie
    private String imageBase64; // Selfie as base64 string
}

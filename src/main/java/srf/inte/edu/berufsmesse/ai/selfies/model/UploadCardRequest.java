package srf.inte.edu.berufsmesse.ai.selfies.model;

import lombok.Data;

@Data
public class UploadCardRequest {
    private String selfieId;
    private String image;
}

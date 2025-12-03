package srf.inte.edu.berufsmesse.ai.selfies.model.request;

import lombok.Data;

@Data
public class UploadCardRequest {
    private String selfieId;
    private String image;
}

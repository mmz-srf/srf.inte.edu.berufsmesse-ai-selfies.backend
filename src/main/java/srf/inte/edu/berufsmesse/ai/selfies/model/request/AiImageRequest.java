package srf.inte.edu.berufsmesse.ai.selfies.model.request;

import lombok.Data;

@Data
public class AiImageRequest {
    private String prompt;
    private long seed;
    private String inputImagePath;
    private String outputImagePath;
}

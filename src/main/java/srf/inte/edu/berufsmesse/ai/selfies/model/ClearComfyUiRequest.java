package srf.inte.edu.berufsmesse.ai.selfies.model;

import lombok.Data;

@Data
public class ClearComfyUiRequest {
    private String workflowId;
    private String imagePath;
    private String selfieId;
}

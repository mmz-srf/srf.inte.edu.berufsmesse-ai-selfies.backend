package srf.inte.edu.berufsmesse.ai.selfies.model.request;

import lombok.Data;

@Data
public class StartGenerationRequest {
    private String gender;
    private String name;
    private String background;
    private String image;
}

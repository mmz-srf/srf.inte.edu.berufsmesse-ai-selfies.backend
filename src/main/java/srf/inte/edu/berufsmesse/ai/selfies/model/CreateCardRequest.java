package srf.inte.edu.berufsmesse.ai.selfies.model;

import lombok.Data;

@Data
public class CreateCardRequest {
    private String imagePath;
    private String selfieId;
    private String name;
    private String template;
}

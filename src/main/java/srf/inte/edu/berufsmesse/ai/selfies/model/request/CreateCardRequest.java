package srf.inte.edu.berufsmesse.ai.selfies.model.request;

import lombok.Data;

@Data
public class CreateCardRequest {
    private String imagePath;
    private String name;
    private String template;
    private int age;
}

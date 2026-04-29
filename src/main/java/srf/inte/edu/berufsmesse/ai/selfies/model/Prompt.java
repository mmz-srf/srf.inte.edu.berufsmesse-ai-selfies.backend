package srf.inte.edu.berufsmesse.ai.selfies.model;

import lombok.Data;

@Data
public class Prompt {
    private String prompt;
    private String gender;
    private String location;
    private long seed;
    private int steps;
}

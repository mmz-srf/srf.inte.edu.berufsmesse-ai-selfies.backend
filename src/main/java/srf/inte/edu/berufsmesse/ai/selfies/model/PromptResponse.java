package srf.inte.edu.berufsmesse.ai.selfies.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PromptResponse {
    @JsonProperty("prompt_id")
    private String promptId;
}

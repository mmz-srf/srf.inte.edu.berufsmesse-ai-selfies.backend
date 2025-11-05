package srf.inte.edu.berufsmesse.ai.selfies.model.cards;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class CardJobsModel {
    private int id;
    private String name;
    @JsonProperty("font-size")
    private int fontSize;
}

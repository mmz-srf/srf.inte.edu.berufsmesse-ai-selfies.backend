package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardAura;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardJobsModel;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "values")
@Data
public class CardValueProperties {
    private List<CardJobsModel> jobs;
    private List<String> superpowers;
    private CardAura aura;
    private List<String> ick;
}

package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import srf.inte.edu.berufsmesse.ai.selfies.model.Prompt;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ai-config")
@Data
public class AiConfiguration {
    private List<Prompt> prompts;
    private String url;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(180);

    public Prompt getPrompt(String gender, String location) {
        for (Prompt prompt : prompts) {
            if (prompt.getGender().equalsIgnoreCase(gender) && prompt.getLocation().equalsIgnoreCase(location)) {
                return prompt;
            }
        }
        return prompts.getFirst();
    }
}

package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai-config")
@Getter
public class AiConfiguration {
    private String url;
    private String examplePrompt;
}

package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ai-config")
@Data
public class AiConfiguration {
    private String url;
    private String examplePrompt;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(180);
}

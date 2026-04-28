package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "settings")
@Getter
public class DocumentProperties {
    private int maxNameLength;
}

package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "settings")
@Data
public class DocumentProperties {
    private List<String> allowedTemplates;
    private int maxNameLength;
    private ComfyUiProperties comfyUiProperties;
    private String promptPath;
}

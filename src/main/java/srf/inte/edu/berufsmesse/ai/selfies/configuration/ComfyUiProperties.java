package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "settings.comfyui")
@Data
public class ComfyUiProperties {
    private String url;
    private String inputDir;
    private  String outputDir;
}

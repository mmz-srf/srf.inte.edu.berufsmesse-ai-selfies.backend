package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "s3")
@Data
public class S3Properties {
    private String url;
    private String userPath;
    private String bucket;
    private String accessKey;
    private String secretKey;
}

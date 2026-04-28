package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "s3")
@Getter
public class S3Properties {
    private String url;
    private String userPath;
    private String bucket;
    private String accessKey;
    private String secretKey;
}

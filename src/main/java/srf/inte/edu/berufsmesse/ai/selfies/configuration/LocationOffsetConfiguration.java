package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "location-offset")
@Data
public class LocationOffsetConfiguration {
    private Map<String, Offset> values = new LinkedHashMap<>();

    public Offset getForLocation(String location) {
        if (location == null || location.isBlank()) {
            return new Offset();
        }
        var exact = values.get(location);
        if (exact != null) {
            return exact;
        }
        return values.entrySet()
                     .stream()
                     .filter(entry -> entry.getKey().equalsIgnoreCase(location))
                     .map(Map.Entry::getValue)
                     .findFirst()
                     .orElseGet(Offset::new);
    }

    @Data
    public static class Offset {
        private int x = 0;
        private int y = 0;
        private double scale = 1.0;
        // Height (px) of the person image the offsets were tuned with. The person is resized to this height first,
        // so the result looks the same regardless of the camera resolution. Not set = no normalization.
        private Integer referenceHeight;
    }
}

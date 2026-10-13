package srf.inte.edu.berufsmesse.ai.selfies.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-location scale/position for the studio prop overlay (e.g. the Sport microphone PNG).
 * Needed because not every overlay.png is a full-frame image the size of its background;
 * some are cut-outs that have to be shrunk and placed in front of the person.
 */
@Component
@ConfigurationProperties(prefix = "overlay-offset")
@Data
public class OverlayOffsetConfiguration {
    private Map<String, LocationOffsetConfiguration.Offset> values = new LinkedHashMap<>();

    public LocationOffsetConfiguration.Offset getForLocation(String location) {
        if (location == null || location.isBlank()) {
            return new LocationOffsetConfiguration.Offset();
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
                     .orElseGet(LocationOffsetConfiguration.Offset::new);
    }
}

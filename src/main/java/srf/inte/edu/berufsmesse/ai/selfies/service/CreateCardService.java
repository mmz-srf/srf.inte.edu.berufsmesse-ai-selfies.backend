package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;
import srf.inte.edu.berufsmesse.ai.selfies.model.CreateCardRequest;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateCardService {

    private final DocumentProperties documentProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public String createCard(CreateCardRequest request) {
        String comfyBaseUrl = documentProperties.getComfyuiUrl();
        List<String> validTemplates = documentProperties.getAllowedTemplates();
        int maxNameLength = documentProperties.getMaxNameLength();
        // Validate request
        if (!validTemplates.contains(request.getTemplate())) {
            throw new IllegalArgumentException("Invalid template");
        }
        if (request.getName().length() > maxNameLength) {
            throw new IllegalArgumentException("Name too long");
        }

        // Get comfyUI image as base64
        String url = comfyBaseUrl + "/view?filename=" + request.getImagePath() + "&type=output";
        byte[] imageBytes = restTemplate.getForObject(url, byte[].class);
        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        String templatePath = "/templates/" + request.getTemplate();

        // Load svg template
        try (var inputStream = CreateCardService.class.getResourceAsStream(templatePath)) {
            assert inputStream != null;
            var svg = new String(inputStream.readAllBytes());

            // Replace placeholders
            svg = svg.replace("{{NAME}}", request.getName());
            svg = svg.replace("{{BASE64}}", "data:image/png;base64," + base64);
            svg = fixSvg(svg);

            // Return svg string to frontend
            return svg;
        } catch (IOException e) {
            log.error("Failed to load template", e);
            throw new RuntimeException(e);
        }
    }

    private String fixSvg(String svg) {
        // Stop tag requires an offset attribute
        svg = svg.replaceAll(
                "<stop(?![^>]*offset)([^>]*)>",
                "<stop offset=\"0%\"$1>"
        );

        // Conversion does not support rgba, use rgb and flood-opacity instead
        svg = svg.replaceAll(
                "flood-color=\"rgba\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*([0-9.]+)\\)\"",
                "flood-color=\"rgb($1,$2,$3)\" flood-opacity=\"$4\""
        );

        return svg;
    }

}

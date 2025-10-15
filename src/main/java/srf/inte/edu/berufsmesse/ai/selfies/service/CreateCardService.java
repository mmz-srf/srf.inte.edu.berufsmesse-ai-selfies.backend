package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;
import srf.inte.edu.berufsmesse.ai.selfies.model.CreateCardRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateCardService {

    private final DocumentProperties documentProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] createCard(CreateCardRequest request) {
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

        String TEMPLATE_PATH = "/templates/" + request.getTemplate();

        // Load svg template
        try (var inputStream = CreateCardService.class.getResourceAsStream(TEMPLATE_PATH)) {
            assert inputStream != null;
            var svg = new String(inputStream.readAllBytes());

            // Replace placeholders
            svg = svg.replace("{{NAME}}", request.getName());
            svg = svg.replace("{{BASE64}}", base64);

            // Convert svg to png
            try {
                return convertSvgToPng(svg);
            } catch (Exception e) {
                log.error("Failed to convert svg to png", e);
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            log.error("Failed to load template", e);
            throw new RuntimeException(e);
        }
    }

    public byte[] convertSvgToPng(String svgContent) throws Exception {
        PNGTranscoder transcoder = new PNGTranscoder();
        svgContent = fixSvg(svgContent);
        // Input from SVG string
        TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8)));

        // Output to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(outputStream);

        transcoder.transcode(input, output);
        outputStream.flush();

        return outputStream.toByteArray();
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

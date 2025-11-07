package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.CardValueProperties;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;
import srf.inte.edu.berufsmesse.ai.selfies.model.CreateCardRequest;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardAura;
import srf.inte.edu.berufsmesse.ai.selfies.model.cards.CardJobsModel;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateCardService {

    private final DocumentProperties documentProperties;
    private final CardValueProperties cardValueProperties;
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

        String templatePath = "/templates/" + request.getTemplate() + ".html";

        // Load html template
        try (var inputStream = CreateCardService.class.getResourceAsStream(templatePath)) {
            assert inputStream != null;
            var html = replacePlaceholders(new String(inputStream.readAllBytes()), base64, request);

            // TODO: convert html to png and upload to s3 directly, no frontend in between
            return html;
        } catch (IOException e) {
            log.error("Failed to load template", e);
            throw new RuntimeException(e);
        }
    }

    private CardJobsModel getRandomJob() {
        List<CardJobsModel> jobs = cardValueProperties.getJobs();
        return jobs.get((int) (Math.random() * jobs.size()));
    }

    private int getRandomAura() {
        CardAura auraSettings = cardValueProperties.getAura();
        int min = auraSettings.getMin();
        int max = auraSettings.getMax();
        int step = auraSettings.getStep();

        return (int) ((Math.random() * (max - min)) + min) / step * step;
    }

    private String getRandomSuperpower() {
        List<String> superpowers = cardValueProperties.getSuperpowers();
        return superpowers.get((int) (Math.random() * superpowers.size()));
    }

    private String getRandomIck() {
        List<String> ick = cardValueProperties.getIck();
        return ick.get((int) (Math.random() * ick.size()));
    }

    private int getAuraWidth(int aura) {
        int minAura = cardValueProperties.getAura().getMin();
        int maxAura = cardValueProperties.getAura().getMax();
        int possibilities = (maxAura - minAura) / cardValueProperties.getAura().getStep();
        int minWidth = 147;
        int maxWidth = 242;
        return minWidth + (aura * (maxWidth - minWidth)) / possibilities;
    }

    /**
     * Replace placeholders in the template with the given data
     */
    private String replacePlaceholders(String html, String base64, CreateCardRequest request) {
        // Replace placeholders
        CardJobsModel job = getRandomJob();
        int aura = getRandomAura();
        html = html.replace("{{IMAGE}}", "data:image/png;base64," + base64);
        html = html.replace("{{NAME}}", request.getName());
        html = html.replace("{{LEVEL}}", String.valueOf(request.getAge()));

        // job
        html = html.replace("{{JOB}}", job.getName());
        html = html.replace("{{JOB_FONT_SIZE}}", String.valueOf(job.getFontSize()));

        // aura
        html = html.replace("{{AURA}}", String.valueOf(aura));
        html = html.replace("{{AURA_WIDTH}}", String.valueOf(getAuraWidth(aura)));

        html = html.replace("{{SUPERPOWER}}", getRandomSuperpower());
        html = html.replace("{{ICK}}", getRandomIck());
        return html;
    }
}

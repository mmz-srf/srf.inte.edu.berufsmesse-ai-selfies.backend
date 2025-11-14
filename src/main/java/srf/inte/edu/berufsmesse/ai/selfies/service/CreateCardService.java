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
        String base64 = getComfyUiImage(request.getImagePath(), documentProperties.getComfyuiUrl());

        String templatePath = "/templates/" + request.getTemplate() + ".html";

        // Load HTML template
        try (var inputStream = CreateCardService.class.getResourceAsStream(templatePath)) {
            assert inputStream != null;
            return replacePlaceholders(new String(inputStream.readAllBytes()), base64, request);
        } catch (IOException e) {
            log.error("Failed to load template", e);
            throw new RuntimeException(e);
        }
    }

    public String getComfyUiImage(String imagePath, String comfyBaseUrl) {
        String url = comfyBaseUrl + "/view?filename=" + imagePath + "&type=output";
        byte[] imageBytes = restTemplate.getForObject(url, byte[].class);
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    public CardJobsModel getRandomJob() {
        List<CardJobsModel> jobs = cardValueProperties.getJobs();
        return jobs.get((int) (Math.random() * jobs.size()));
    }

    public int getRandomAura() {
        CardAura auraSettings = cardValueProperties.getAura();
        int min = auraSettings.getMin();
        int max = auraSettings.getMax();
        int step = auraSettings.getStep();

        return (int) ((Math.random() * (max - min)) + min) / step * step;
    }

    public String getRandomSuperpower() {
        List<String> superpowers = cardValueProperties.getSuperpowers();
        return superpowers.get((int) (Math.random() * superpowers.size()));
    }

    public String getRandomIck() {
        List<String> ick = cardValueProperties.getIck();
        return ick.get((int) (Math.random() * ick.size()));
    }

    private int getAuraWidth(int aura) {
        int minAura = cardValueProperties.getAura().getMin();
        int maxAura = cardValueProperties.getAura().getMax();
        int minWidth = 147;
        int maxWidth = 242;

        double relativePosition = (double) (aura - minAura) / (maxAura - minAura);
        int width = (int) (minWidth + relativePosition * (maxWidth - minWidth));
        if (width < minWidth) {
            return minWidth;
        } else if (width > maxWidth) {
            return maxWidth;
        } else {
            return width;
        }
    }

    /**
     * Replace placeholders in the template with the given data
     */
    private String replacePlaceholders(String html, String base64, CreateCardRequest request) {
        // Replace placeholders
        CardJobsModel job = getRandomJob();
        int aura = getRandomAura();
        html = html.replace("{{IMAGE}}", "data:image/png;base64," + base64);

        String serializedName = request.getName().replace("<", "").replace(">", "");
        html = html.replace("{{NAME}}", serializedName);
        html = html.replace("{{LEVEL}}", String.valueOf(request.getAge()));

        // job
        html = html.replace("{{JOB}}", job.getName());
        html = html.replace("{{JOB_FONT_SIZE}}", String.valueOf(job.getFontSize()));
        log.debug("Job: {}", job);

        // aura
        html = html.replace("{{AURA}}", String.valueOf(aura));
        html = html.replace("{{AURA_WIDTH}}", String.valueOf(getAuraWidth(aura)));

        html = html.replace("{{SUPERPOWER}}", getRandomSuperpower());
        html = html.replace("{{ICK}}", getRandomIck());
        return html;
    }
}

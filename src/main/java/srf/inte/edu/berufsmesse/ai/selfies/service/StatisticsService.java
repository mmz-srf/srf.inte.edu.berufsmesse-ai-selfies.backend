package srf.inte.edu.berufsmesse.ai.selfies.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.model.Statistics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class StatisticsService {

    private final ObjectMapper objectMapper;
    private static final String STATS_FILE_PATH = "src/main/resources/statistics/stats.json";

    public StatisticsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        File statsFile = new File(STATS_FILE_PATH);
        log.info("=================================================");
        log.info("Statistics file location: {}", statsFile.getAbsolutePath());
        log.info("File exists: {}", statsFile.exists());
        log.info("File writable: {}", statsFile.canWrite());
        log.info("Working directory: {}", System.getProperty("user.dir"));
        log.info("=================================================");
    }

    public synchronized void increaseRequests() {
        try {
            Statistics stats = loadStatistics();
            stats.setTotalRequests(stats.getTotalRequests() + 1);
            saveStatistics(stats);
            log.info("✓ Increased total requests to {}", stats.getTotalRequests());
        } catch (IOException e) {
            log.error("Failed to increase requests counter", e);
        }
    }

    public synchronized void increaseTemplates(String templateName) {
        try {
            Statistics stats = loadStatistics();
            stats.getTemplates().merge(templateName, 1, Integer::sum);
            saveStatistics(stats);
            log.info("Increased template '{}' to {}", templateName, stats.getTemplates().get(templateName));
        } catch (IOException e) {
            log.error("Failed to increase template counter for {}", templateName, e);
        }
    }

    public synchronized void increaseGender(String gender) {
        try {
            Statistics stats = loadStatistics();
            stats.getGender().merge(gender, 1, Integer::sum);
            saveStatistics(stats);
            log.info("Increased gender '{}' to {}", gender, stats.getGender().get(gender));
        } catch (IOException e) {
            log.error("Failed to increase gender counter for {}", gender, e);
        }
    }

    private Statistics loadStatistics() throws IOException {
        File statsFile = new File(STATS_FILE_PATH);
        if (statsFile.exists()) {
            return objectMapper.readValue(statsFile, Statistics.class);
        } else {
            try (InputStream is = getClass().getResourceAsStream("/statistics/stats.json")) {
                if (is != null) {
                    return objectMapper.readValue(is, Statistics.class);
                }
            }
            log.warn("Statistics file not found, creating new statistics object");
            return new Statistics();
        }
    }

    private void saveStatistics(Statistics stats) throws IOException {
        File statsFile = new File(STATS_FILE_PATH);
        // Ensure parent directory exists
        if (!statsFile.getParentFile().exists()) {
            boolean created = statsFile.getParentFile().mkdirs();
            log.info("Created parent directory: {} (success: {})", statsFile.getParentFile(), created);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(statsFile, stats);
        log.info("Saved statistics to: {}", statsFile.getAbsolutePath());
    }

    public Statistics getStatistics() {
        try {
            return loadStatistics();
        } catch (IOException e) {
            log.error("Failed to load statistics", e);
            return new Statistics();
        }
    }
}


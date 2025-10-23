package srf.inte.edu.berufsmesse.ai.selfies.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ClearComfyUiService {
    private static final Logger log = LoggerFactory.getLogger(ClearComfyUiService.class);
    private static final Path outputDir = Path.of("/Users/sleipnir03/Documents/ComfyUI/output");
    private static final Path inputDir = Path.of("/Users/sleipnir03/Documents/ComfyUI/input");

    public void clearComfyUi(String workflowId, String imagePath, String selfieId) {
        clearWorkflow(workflowId);
        clearAiImage(imagePath);
        clearUploadImage(selfieId);
    }

    private void clearUploadImage(String selfieId) {
        if (selfieId == null || selfieId.isBlank()) {
            log.debug("clearUploadImage: selfieId blank -> nothing to delete");
            return;
        }

        String filename = Paths.get(selfieId).getFileName().toString();
        if (!filename.toLowerCase().endsWith(".png")) {
            filename = filename + ".png";
        }

        Path file = inputDir.resolve(filename);
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Deleted upload image from inputDir: {}", file);
            } else {
                log.debug("Upload image not found (nothing to delete): {}", file);
            }
        } catch (IOException e) {
            log.warn("Failed to delete upload image {}: {}", file, e.toString());
        }
    }

    private void clearAiImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            log.debug("clearAiImage: imagePath blank -> nothing to delete");
            return;
        }

        String filename = Paths.get(imagePath).getFileName().toString();

        Path file = outputDir.resolve(filename);
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Deleted AI image from outputDir: {}", file);
            } else {
                log.debug("AI image not found (nothing to delete): {}", file);
            }
        } catch (IOException e) {
            log.warn("Failed to delete AI image {}: {}", file, e.toString());
        }
    }

    // TODO: clear workflow from history via comfyUi endpoint
    private void clearWorkflow(String workflowId) {
        // no-op by default; implement if you have workflow-scoped files to remove
        if (workflowId == null || workflowId.isBlank()) {
            return;
        }
        log.debug("clearWorkflow called for {}, no filesystem artifacts to remove by default", workflowId);
    }
}

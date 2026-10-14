package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.OverlayOffsetConfiguration;
import srf.inte.edu.berufsmesse.ai.selfies.utils.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;

@RequiredArgsConstructor
@Service
@Slf4j
public class SvgOverlayService {
    private final OverlayOffsetConfiguration overlayOffsetConfiguration;
    private final GreenscreenRemovalService greenscreenRemovalService;

    public String addOverlay(String base64Image, String backgroundImage) {
        if (backgroundImage.equals("Radio")) return base64Image; // Radio has no overlay

        BufferedImage overlay = ImageUtils.readImageFromResources(ImageUtils.getOverlayImagePath(backgroundImage));
        BufferedImage background = ImageUtils.base64ToBufferedImage(base64Image);

        if (overlay == null || background == null) {
            log.error("Failed to read overlay or background image for overlay");
            return null;
        }

        // Props like the Sport microphone are cut-outs, not full-frame overlays, so they need their own size and position
        var offset = overlayOffsetConfiguration.getForLocation(backgroundImage);
        if (offset.getScale() != 1.0) {
            overlay = ImageUtils.scaleImage(overlay, offset.getScale());
        }

        return greenscreenRemovalService.mergeImages(overlay, background, offset.getX(), offset.getY(), false);
    }
}

package com.borrowbuddy.config;

import com.borrowbuddy.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads the demo listing photos bundled in the jar into the database on startup
 * when they are missing, so a fresh production database serves them from the
 * same place as user uploads.
 */
@Component
@ConditionalOnProperty(name = "app.upload.import-demo-images", havingValue = "true", matchIfMissing = true)
public class DemoImageImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoImageImporter.class);

    private final ImageStorageService imageStorageService;

    public DemoImageImporter(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int imported = imageStorageService.importBundledDemoImages();
            if (imported > 0) {
                log.info("Imported {} bundled demo images into the database", imported);
            }
        } catch (RuntimeException ex) {
            // Never block startup: the classpath fallback still serves the photos.
            log.warn("Could not import bundled demo images: {}", ex.getMessage());
        }
    }
}

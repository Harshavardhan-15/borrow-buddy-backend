package com.borrowbuddy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = uploadPath.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        // Runtime uploads are written to the filesystem and take precedence.
        // Demo listing photos shipped with the app are bundled on the classpath
        // (src/main/resources/static/uploads) so they survive a container
        // deploy where the upload directory starts empty.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location, "classpath:/static/uploads/")
                .setCachePeriod(3600);
    }
}

package com.mungjaguk.app.config;

import com.mungjaguk.app.service.PetImageStorage;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PetImageWebConfig implements WebMvcConfigurer {
    private final PetImageStorage storage;

    public PetImageWebConfig(PetImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storage.getDirectory().toUri().toString();
        registry.addResourceHandler("/images/pets/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}

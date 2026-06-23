package com.lavendercode.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private static final Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    public static LlmConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            throw new ConfigException(
                "Config file not found: " + configPath,
                "file"
            );
        }

        LlmConfig config;
        try {
            config = mapper.readValue(configPath.toFile(), LlmConfig.class);
        } catch (IOException e) {
            throw new ConfigException(
                "Failed to parse YAML config: " + e.getMessage(),
                "format"
            );
        }

        if (config.providers().isEmpty()) {
            throw new ConfigException(
                "Config error: providers list is empty",
                "providers"
            );
        }

        for (int i = 0; i < config.providers().size(); i++) {
            ProviderConfig pc = config.providers().get(i);
            int index = i;
            Set<ConstraintViolation<ProviderConfig>> violations = validator.validate(pc);
            if (!violations.isEmpty()) {
                String fields = violations.stream()
                    .map(v -> "providers[" + index + "]." + v.getPropertyPath().toString())
                    .collect(Collectors.joining(", "));
                throw new ConfigException(
                    "Config error: missing required fields: " + fields,
                    fields
                );
            }
        }

        return config;
    }
}

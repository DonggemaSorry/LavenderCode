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

        Set<ConstraintViolation<LlmConfig>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            String fields = violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.joining(", "));
            throw new ConfigException(
                "Missing required config fields: " + fields,
                fields
            );
        }

        return config;
    }
}

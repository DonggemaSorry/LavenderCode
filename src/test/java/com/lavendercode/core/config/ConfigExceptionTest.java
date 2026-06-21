package com.lavendercode.core.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigExceptionTest {

    @Test
    void shouldStoreMessageAndFieldName() {
        var ex = new ConfigException("Missing required field", "apiKey");

        assertThat(ex.getMessage()).isEqualTo("Missing required field");
        assertThat(ex.getFieldName()).isEqualTo("apiKey");
    }

    @Test
    void shouldExtendRuntimeException() {
        var ex = new ConfigException("test", "field");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldSupportFormatErrorType() {
        var ex = new ConfigException("Failed to parse YAML config", "format");

        assertThat(ex.getFieldName()).isEqualTo("format");
        assertThat(ex.getMessage()).contains("YAML");
    }
}

package io.consenthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main entry point for ConsentHub — Privacy, Data Rights & Server-Driven Consent Management Platform.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("io.consenthub.config")
public class ConsentHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsentHubApplication.class, args);
    }
}

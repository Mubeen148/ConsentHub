package io.consenthub.web;

import io.consenthub.config.AppProperties;
import io.consenthub.domain.dto.HealthResponse;
import io.consenthub.domain.dto.TimestampFormatter;
import java.sql.Connection;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight database-aware health endpoint ({@code GET /api/v1/health}) complementing Spring Boot Actuator.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final AppProperties appProperties;

    public HealthController(DataSource dataSource, AppProperties appProperties) {
        this.dataSource = dataSource;
        this.appProperties = appProperties;
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<HealthResponse> health() {
        boolean dbHealthy = checkDatabase();
        String version = (appProperties.version() != null && !appProperties.version().isBlank())
                ? appProperties.version()
                : "1.0.0";
        HealthResponse body = new HealthResponse(
                dbHealthy ? "UP" : "DEGRADED",
                dbHealthy ? "UP" : "DOWN",
                version,
                TimestampFormatter.format(Instant.now())
        );
        return ResponseEntity
                .status(dbHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private boolean checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }
}

package io.consenthub.web;

import io.consenthub.domain.dto.PrivacyExportResponse;
import io.consenthub.service.DsarService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-Service Data Portability Controller implementing {@code GET /api/v1/privacy/export}
 * (and {@code /api/v1/export} alias) per {@code docs/contracts/rest-endpoints.md §3}.
 */
@RestController
public class PrivacyExportController {

    private final DsarService dsarService;

    public PrivacyExportController(DsarService dsarService) {
        this.dsarService = dsarService;
    }

    @GetMapping(value = {"/api/v1/privacy/export", "/api/v1/export"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PrivacyExportResponse> downloadPrivacyExport() {
        PrivacyExportResponse payload = dsarService.generateSelfServiceExport();
        String datePart = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now(ZoneOffset.UTC));
        String filename = "consenthub-data-export-" + datePart + ".json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl(CacheControl.noStore().mustRevalidate().getHeaderValue());
        headers.add(HttpHeaders.PRAGMA, "no-cache");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);
    }
}

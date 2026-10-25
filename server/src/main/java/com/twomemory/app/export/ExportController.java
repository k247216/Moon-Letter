package com.twomemory.app.export;

import com.twomemory.app.auth.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final ExportService exportService;
    private final ObjectMapper objectMapper;

    public ExportController(ExportService exportService, ObjectMapper objectMapper) {
        this.exportService = exportService;
        this.objectMapper = objectMapper;
    }

    /** Selective export of one space as readable JSON; no secrets included. */
    @GetMapping
    public ResponseEntity<String> export(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam UUID coupleId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) throws Exception {
        Instant fromInstant = parseBound(from);
        Instant toInstant = parseBound(to);
        Map<String, Object> payload = exportService.exportCouple(actor.userId(), coupleId, fromInstant, toInstant);
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=moon-letter-export-" + coupleId + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    private Instant parseBound(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException invalid) {
            throw new com.twomemory.app.entry.EntryValidationException("导出日期需要有效的 ISO 时间格式");
        }
    }
}

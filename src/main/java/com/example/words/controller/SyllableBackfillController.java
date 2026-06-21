package com.example.words.controller;

import com.example.words.dto.SyllableBackfillResponse;
import com.example.words.service.SyllableBackfillService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/syllables")
@PreAuthorize("hasRole('ADMIN')")
public class SyllableBackfillController {

    private final SyllableBackfillService syllableBackfillService;

    public SyllableBackfillController(SyllableBackfillService syllableBackfillService) {
        this.syllableBackfillService = syllableBackfillService;
    }

    @PostMapping("/backfill")
    public ResponseEntity<SyllableBackfillResponse> backfill(
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(syllableBackfillService.backfillPublishedPlanWords(limit));
    }
}

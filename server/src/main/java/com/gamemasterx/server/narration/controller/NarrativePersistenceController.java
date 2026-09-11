package com.gamemasterx.server.narration.controller;

import com.gamemasterx.server.narration.model.NarrativeRecord;
import com.gamemasterx.server.narration.service.NarrativePersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for retrieving durable narrative records with visibility enforcement.
 */
@RestController
@RequestMapping("/api/narration/persist")
public class NarrativePersistenceController {

    private final NarrativePersistenceService service;

    public NarrativePersistenceController(NarrativePersistenceService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<NarrativeRecord> get(@PathVariable String id, @RequestParam(defaultValue = "false") boolean gm) {
        NarrativeRecord record = service.readWithVisibility(id, gm);
        return ResponseEntity.ok(record);
    }
}

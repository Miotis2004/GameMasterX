package com.gamemasterx.server.tactical.controller;

import com.gamemasterx.server.filter.AuthFilter;
import com.gamemasterx.server.tactical.model.TacticalMap;
import com.gamemasterx.server.tactical.model.TacticalMapDto;
import com.gamemasterx.server.tactical.model.Token;
import com.gamemasterx.server.tactical.service.TacticalMapService;
import com.gamemasterx.server.tactical.service.TacticalMapService.RangeCell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * REST controller for tactical map persistence.
 */
@RestController
@RequestMapping("/api/tactical/maps")
public class TacticalMapController {

    private final TacticalMapService service;

    @Autowired
    public TacticalMapController(TacticalMapService service) {
        this.service = service;
    }

    private String getCurrentUserId(HttpServletRequest request) {
        Object user = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (user == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        return user.toString();
    }

    @PostMapping
    public ResponseEntity<TacticalMapDto> create(@RequestParam String campaignId,
                                                 @RequestParam String name,
                                                 @RequestBody TacticalMap map,
                                                 HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        TacticalMap created = service.create(campaignId, name, map, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(TacticalMapDto.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TacticalMapDto> get(@PathVariable String id, HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        Optional<TacticalMap> opt = service.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TacticalMap filtered = service.filterForUser(opt.get(), userId);
        return ResponseEntity.ok(TacticalMapDto.from(filtered));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TacticalMapDto> update(@PathVariable String id,
                                                 @RequestBody TacticalMap map,
                                                 HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        TacticalMap updated = service.update(id, map, userId);
        TacticalMap filtered = service.filterForUser(updated, userId);
        return ResponseEntity.ok(TacticalMapDto.from(filtered));
    }

    @PatchMapping("/{mapId}/tokens/{tokenId}")
    public ResponseEntity<TacticalMapDto> updateToken(@PathVariable String mapId,
                                                       @PathVariable String tokenId,
                                                       @RequestBody Token token,
                                                       HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        service.updateToken(mapId, tokenId, token, userId);
        Optional<TacticalMap> opt = service.findById(mapId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TacticalMap filtered = service.filterForUser(opt.get(), userId);
        return ResponseEntity.ok(TacticalMapDto.from(filtered));
    }

    @GetMapping("/{mapId}/tokens/{tokenId}/range")
    public ResponseEntity<java.util.List<RangeCell>> getRangeOverlay(@PathVariable String mapId,
                                                                      @PathVariable String tokenId,
                                                                      @RequestParam int range) {
        java.util.List<RangeCell> cells = service.computeRangeOverlay(mapId, tokenId, range);
        return ResponseEntity.ok(cells);
    }
}

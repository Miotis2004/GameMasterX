package com.gamemasterx.server.tactical.validation;

import com.gamemasterx.server.tactical.model.GridDefinition;
import com.gamemasterx.server.tactical.model.TacticalMap;
import com.gamemasterx.server.tactical.model.Token;
import org.springframework.stereotype.Component;

/**
 * Validates tactical map grid definitions and token coordinates.
 */
@Component
public class TacticalMapValidator {

    public void validateGridDefinition(GridDefinition grid) {
        if (grid == null) {
            throw new TacticalMapValidationException("Grid definition is required");
        }
        if (grid.getWidth() <= 0) {
            throw new TacticalMapValidationException("Grid width must be positive");
        }
        if (grid.getHeight() <= 0) {
            throw new TacticalMapValidationException("Grid height must be positive");
        }
        if (grid.getCellSize() <= 0) {
            throw new TacticalMapValidationException("Cell size must be positive");
        }
        if (grid.getGridType() == null) {
            throw new TacticalMapValidationException("Grid type is required");
        }
    }

    public void validateTokenCoordinates(TacticalMap map, Token token) {
        GridDefinition grid = map.getGridDefinition();
        if (grid == null) {
            throw new TacticalMapValidationException("Map has no grid definition");
        }
        int x = token.getX();
        int y = token.getY();
        if (x < 0 || x >= grid.getWidth()) {
            throw new TacticalMapValidationException("Token x coordinate out of bounds");
        }
        if (y < 0 || y >= grid.getHeight()) {
            throw new TacticalMapValidationException("Token y coordinate out of bounds");
        }
    }

    public void validateMap(TacticalMap map) {
        validateGridDefinition(map.getGridDefinition());
        if (map.getTokens() != null) {
            for (Token token : map.getTokens()) {
                validateTokenCoordinates(map, token);
            }
        }
    }
}

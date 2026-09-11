package com.gamemasterx.server.tactical.model;

/**
 * API representation of a grid definition.
 */
public class GridDefinitionDto {
    private int width;
    private int height;
    private double cellSize;
    private GridType gridType;

    public GridDefinitionDto() {
    }

    public static GridDefinitionDto from(GridDefinition gd) {
        GridDefinitionDto dto = new GridDefinitionDto();
        dto.width = gd.getWidth();
        dto.height = gd.getHeight();
        dto.cellSize = gd.getCellSize();
        dto.gridType = gd.getGridType();
        return dto;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public double getCellSize() {
        return cellSize;
    }

    public void setCellSize(double cellSize) {
        this.cellSize = cellSize;
    }

    public GridType getGridType() {
        return gridType;
    }

    public void setGridType(GridType gridType) {
        this.gridType = gridType;
    }
}

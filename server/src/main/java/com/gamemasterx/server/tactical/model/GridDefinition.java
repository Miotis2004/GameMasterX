package com.gamemasterx.server.tactical.model;

/**
 * Definition of the grid layout for a tactical map.
 */
public class GridDefinition {
    private int width;
    private int height;
    private double cellSize;
    private GridType gridType;

    public GridDefinition() {
    }

    public GridDefinition(int width, int height, double cellSize, GridType gridType) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.gridType = gridType;
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

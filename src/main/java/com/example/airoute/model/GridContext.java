package com.example.airoute.model;

/**
 * 网格上下文 —— 坐标原点和步长，用于 EncryptedGrid 反算中心点坐标。
 */
public class GridContext {

    public final double minLon, minLat, minAlt;
    public final double cellLon, cellLat, cellAlt;

    public GridContext(double minLon, double minLat, double minAlt,
                       double cellLon, double cellLat, double cellAlt) {
        this.minLon = minLon; this.minLat = minLat; this.minAlt = minAlt;
        this.cellLon = cellLon; this.cellLat = cellLat; this.cellAlt = cellAlt;
    }
}

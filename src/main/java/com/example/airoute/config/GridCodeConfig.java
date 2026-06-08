package com.example.airoute.config;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @DateTime: 2025/12/30
 * @Description: 网格编码配置
 * @Author: 阿涛
 **/
@Data
@AllArgsConstructor
public class GridCodeConfig {

    /**
     * 网格编码的作用场景
     */
    private int scene;

    /**
     * 基点坐标（经度、纬度、高度）
     */
    private double baseLon;
    private double baseLat;
    private double baseH;

    /**
     * 极点坐标（经度、纬度、高度）
     */
    private double maxLon;
    private double maxLat;
    private double maxH;

    /**
     * 一级网格X方向大小（m）
     */
    private int firstLevelX;
    private int firstLevelY;
    private int firstLevelZ;

    /**
     * 二级网格边长（m）
     */
    private int secondLevelSize;
}

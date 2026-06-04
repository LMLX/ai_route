package com.example.airoute.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Grid {
    /** 网格唯一标识 */
    private String id;

    /** 中心点坐标（经纬度+海拔） */
    private GeoPoint centerPoint;

    // ====== 网格风险因素 ======

    /**
     * 网格风险因素集（1~9，值越高越适合飞行）
     * 示例：{"crowd":5, "temperature":8, "wind":3, "weather":7}
     * 任一因素为 0 → 该网格默认不可通行（必经点网格除外）
     */
    private Map<String, Integer> factors;
}

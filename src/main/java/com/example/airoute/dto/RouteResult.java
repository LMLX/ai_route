package com.example.airoute.dto;

import com.example.airoute.model.GeoPoint;
import com.example.airoute.model.EncryptedGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResult {

    /** 是否找到路径 */
    private boolean success;

    /** 路径经过的网格序列 */
    private List<EncryptedGrid> pathGrids;

    /** 路径坐标点序列（每个网格中心点） */
    private List<GeoPoint> waypoints;

    /** 总路径长度（米） */
    private double totalDistance;

    /** 路径平均评分（0~9，越高越好） */
    private double avgRouteScore;

    /** 综合覆盖率（取所有规则中的最小值） */
    private double actualCoverageRate;

    /** 各规则覆盖率详情（factorName → coverage） */
    private Map<String, Double> ruleCoverages;

    /** 消息 */
    private String message;
}

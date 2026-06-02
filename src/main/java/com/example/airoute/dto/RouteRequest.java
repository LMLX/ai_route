package com.example.airoute.dto;

import com.example.airoute.model.GeoPoint;
import com.example.airoute.model.Grid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {

    /** 全量网格数据 */
    private List<Grid> grids;

    /** 起始点坐标 */
    private GeoPoint startPoint;

    /** 终点坐标 */
    private GeoPoint endPoint;

    /** 中途必经点（按顺序依次经过） */
    private List<GeoPoint> midPoints;

    /** 禁飞区列表（格式同网格：basePoint / maxPoint 定义 3D 包围盒） */
    private List<Grid> noFlyZones;

    /** 航线宽度（单位：米，会自动换算为网格数） */
    private double routeWidth;

    /** 航线高度上限（单位：米，超过此海拔的网格不可用） */
    private double routeHeight;

    /** 可插拔规则列表：每个规则对某个因素设置达标条件 + 最低覆盖率 */
    private List<RouteRule> rules;
}

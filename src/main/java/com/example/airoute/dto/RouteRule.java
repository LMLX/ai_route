package com.example.airoute.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 航线规则：对某因素设置取值区间 + 覆盖率上下限。
 *
 * <p>示例：
 * <pre>{@code
 * {
 *   "factorName": "crowd",
 *   "minThreshold": 5,    // 因素下限（含）
 *   "maxThreshold": 9,    // 因素上限（含）
 *   "minCoverageRate": 0.3, // 最低覆盖率 30%
 *   "maxCoverageRate": 0.6   // 最高覆盖率 60%
 * }
 * }</pre>
 * 含义：crowd 在 [5, 9] 之间的网格，需占路径总网格的 30% ~ 60%。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRule {

    /** 因素名（对应 Grid.factors 中的 key，如 crowd/temperature/wind/weather） */
    private String factorName;

    /** 因素值下限（含），网格满足 minThreshold ≤ factor ≤ maxThreshold 即命中 */
    private double minThreshold;

    /** 因素值上限（含） */
    private double maxThreshold;

    /** 最低覆盖率（0~1），如 0.3 = 30% */
    private double minCoverageRate;

    /** 最高覆盖率（0~1），如 0.6 = 60%。默认 1.0 表示不设上限 */
    private double maxCoverageRate = 1.0;
}

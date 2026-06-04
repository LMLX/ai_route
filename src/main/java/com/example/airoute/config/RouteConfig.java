package com.example.airoute.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 航线评分配置（统一在 application.properties 管理）
 */
@Data
@Component
@ConfigurationProperties(prefix = "route")
public class RouteConfig {

    /** 因素权重 Map */
    private Map<String, Double> factorWeights = new HashMap<>();

    /** 评分惩罚因子（米），得分 0 的网格等效多走 N 米 */
    private double scorePenaltyFactor = 200.0;

    /** 规则覆盖率不达标时，因素权重倍增的最大重试次数 */
    private int ruleRetryMaxTimes = 2;

    /** 网格边长（米），默认 100m。影响线宽换算和高度步长 */
    private double gridSizeMeters = 100.0;

    /** 浮点舍入保护 epsilon，避免边界值舍入错误 */
    private double eps = 1e-8;

    /** 严格禁飞模式：禁飞区网格不计入规则覆盖率（途经点豁免除外），默认 true */
    private boolean strictNoFlyZone = true;
}

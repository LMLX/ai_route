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
}

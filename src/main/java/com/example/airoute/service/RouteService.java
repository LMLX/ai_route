package com.example.airoute.service;

import com.example.airoute.config.RouteConfig;
import com.example.airoute.dto.RouteResult;
import com.example.airoute.dto.RouteRule;
import com.example.airoute.model.GeoPoint;
import com.example.airoute.model.Grid;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能航线核心服务。
 *
 * <h3>整体流程</h3>
 * <pre>
 * 输入: 网格 + 起终点 + 中途点 + 禁飞区 + 规则
 *   ↓
 * ① 构建 3D 空间索引（HashMap，O(1) 邻接查找）
 * ② 构建禁飞区映射（zone ↔ grid 双向索引）
 * ③ BFS 洪泛：必经点所在连续封锁区全豁免
 * ④ A* 最短路径搜索（带评分惩罚 + 自动权重重试）
 * ⑤ 走廊扩展（线宽 > 1 时） + 规则覆盖率计算
 *   ↓
 * 输出: 路径网格序列 + 总距离 + 评分 + 各规则覆盖率
 * </pre>
 *
 * <h3>核心算法</h3>
 * <ul>
 *   <li><b>A*</b>: f(n)=g(n)+h(n)，g=距离+评分惩罚，h=纯距离（可采纳）</li>
 *   <li><b>评分系统</b>: 网格因素加权得分 → 转化为米制惩罚加入 g(n)，高分网格代价低</li>
 *   <li><b>规则引擎</b>: 每条规则指定"某因素 ≥ 阈值 占 覆盖率%"，不达标时自动倍增权重重试</li>
 *   <li><b>BFS 豁免</b>: 必经点在封锁区内部时，沿被封锁邻居扩散到底，整片连续封锁区全豁免</li>
 * </ul>
 */
@Service
public class RouteService {

    // ====== 常量 ======

    /** 每度纬度 ≈ 111320 米（用于经纬度 → 米制转换） */
    private static final double METERS_PER_DEGREE_LAT = 111320.0;

    /**
     * 3D 网格 26 邻接方向：6 面 + 12 棱 + 8 角。
     * 索引含义：[Δlon, Δlat, Δalt]，各维 ±1 或 0。
     */
    private static final int[][] NEIGHBOR_DIRECTIONS = {
            // 6 面相邻（单维变化）
            { 1,  0,  0}, {-1,  0,  0},
            { 0,  1,  0}, { 0, -1,  0},
            { 0,  0,  1}, { 0,  0, -1},
            // 12 棱相邻（双维变化）
            { 1,  1,  0}, { 1, -1,  0}, {-1,  1,  0}, {-1, -1,  0},
            { 1,  0,  1}, { 1,  0, -1}, {-1,  0,  1}, {-1,  0, -1},
            { 0,  1,  1}, { 0,  1, -1}, { 0, -1,  1}, { 0, -1, -1},
            // 8 角相邻（三维变化）
            { 1,  1,  1}, { 1,  1, -1}, { 1, -1,  1}, { 1, -1, -1},
            {-1,  1,  1}, {-1,  1, -1}, {-1, -1,  1}, {-1, -1, -1}
    };

    /** 全局配置（权重、惩罚因子等，来自 application.properties） */
    private final RouteConfig routeConfig;

    public RouteService(RouteConfig routeConfig) {
        this.routeConfig = routeConfig;
    }

    // ======================================================================
    //  公开 API
    // ======================================================================

    /**
     * 计算智能航线最短路径。
     *
     * <h3>参数说明</h3>
     * @param grids       全量 100m 网格（含 factors 1~9 评分）
     * @param startPoint  起点坐标（经纬度+海拔）
     * @param endPoint    终点坐标
     * @param midPoints   中途必经点（可选，按顺序依次经过）
     * @param noFlyZones  禁飞区包围盒列表（可选，格式同 Grid）
     * @param routeWidth  航线走廊宽度（米，≥0 时按 100m/格换算）
     * @param routeHeight 航线高度上限（米，超此海拔的网格不可用）
     * @param rules       可插拔规则列表（可选，每条规则控制某因素的覆盖率）
     *
     * <h3>执行步骤</h3>
     * <ol>
     *   <li>构建 3D 空间索引（HashMap，key="i_j_k"）</li>
     *   <li>构建禁飞区双向映射（zone→grids, grid→zones）</li>
     *   <li>必经点定位 + BFS 洪泛豁免</li>
     *   <li>逐段 A* 搜索（起→中1→中2→...→终）</li>
     *   <li>评估规则覆盖率；不达标则倍增权重重试（最多 N 次）</li>
     *   <li>合并多段 + 走廊扩展 + 计算总距离和评分</li>
     * </ol>
     *
     * @return 路径结果（成功/失败 + 路径 + 距离 + 评分 + 各规则覆盖率）
     */
    public RouteResult findShortestRoute(List<Grid> grids,
                                         GeoPoint startPoint,
                                         GeoPoint endPoint,
                                         List<GeoPoint> midPoints,
                                         List<Grid> noFlyZones,
                                         double routeWidth,
                                         double routeHeight,
                                         List<RouteRule> rules) {
        // ===== 0. 前置校验 =====
        if (grids == null || grids.isEmpty()) {
            return fail("网格数据为空");
        }

        int widthInGrids = Math.max(1, (int) Math.ceil(routeWidth / routeConfig.getGridSizeMeters()));
        double penaltyFactor = routeConfig.getScorePenaltyFactor();
        boolean hasRules = rules != null && !rules.isEmpty();

        // ===== 1. 构建 3D 空间索引 =====
        // 将网格按平均尺寸分配到整数索引 (i,j,k)，
        // 存入 HashMap 实现 O(1) 邻接查找。
        GridIndex index = buildGridIndex(grids);

        // ===== 2. 构建禁飞区数据 =====
        // zoneGridMap: zoneId → {属于该区的网格ID}
        // gridZoneMap: gridId → {该网格所属的zoneId...}(支持嵌套)
        NoFlyData noFlyData = buildNoFlyData(noFlyZones, index);

        // ===== 3. 构建必经点序列 =====
        List<GeoPoint> waypointCoords = new ArrayList<>();
        waypointCoords.add(startPoint);
        if (midPoints != null) waypointCoords.addAll(midPoints);
        waypointCoords.add(endPoint);

        // 每个必经点定位到具体网格
        List<Grid> waypointGrids = new ArrayList<>();
        for (GeoPoint wp : waypointCoords) {
            Grid g = findGridByPoint(grids, wp, index);
            if (g == null) return fail("必经点不在任何网格内: " + wp);
            waypointGrids.add(g);
        }

        // 必经点所在禁飞区 → 标记为可通行
        Set<String> passableZones = findPassableZones(waypointCoords, noFlyZones, index);

        // BFS 洪泛：必经点所在连续封锁区 → 全豁免
        Set<String> exemptGridIds = computeExemptRegion(waypointGrids, index, noFlyData, passableZones);

        // 高度走廊：以必经点海拔为中心，±routeHeight/2
        double halfH = routeHeight / 2;
        double minAlt = waypointGrids.stream().mapToDouble(g -> g.getCenterPoint().getAltitude()).min().orElse(0) - halfH;
        double maxAlt = waypointGrids.stream().mapToDouble(g -> g.getCenterPoint().getAltitude()).max().orElse(0) + halfH;

        // ===== 4. 规则匹配集（用于给匹配格折扣、不匹配格惩罚） =====
        Set<String> ruleMatchIds = new HashSet<>();
        if (hasRules) {
            for (Grid g : grids) {
                if (rules.stream().anyMatch(r -> evaluateRule(g, r))) ruleMatchIds.add(g.getId());
            }
        }
        double ruleBonus = penaltyFactor * 2.0; // 匹配格回扣（米）

        // ===== 5. 基础因素权重 =====
        Map<String, Double> baseWeights = new LinkedHashMap<>(routeConfig.getFactorWeights());

        // ===== 5. 规则引擎：自动重试循环 =====
        // 每轮用当前权重跑 A*；评估各规则覆盖率；
        // 不达标的因素权重倍增（×2, ×4, ...），重新跑，直到全部达标或用尽重试次数。
        int maxRetry = hasRules ? routeConfig.getRuleRetryMaxTimes() : 0;
        RouteResult bestResult = null; // 保留覆盖率最高的结果

        for (int retry = 0; retry <= maxRetry; retry++) {
            // 5.1 构造本轮权重 + 惩罚因子倍增（避免单因素时权重抵消）
            Map<String, Double> weights = buildRetryWeights(baseWeights, rules, retry, bestResult);
            double effectivePenalty = penaltyFactor * (1 + retry * 2); // 每次重试惩罚×3/5/7...

            // 5.2 逐段 A* 搜索：[start→mid1], [mid1→mid2], ..., [midN→end]
            List<List<Grid>> segments = new ArrayList<>();
            boolean segmentFailed = false;
            for (int i = 0; i < waypointGrids.size() - 1; i++) {
                List<Grid> seg = aStarSearch(index,
                        waypointGrids.get(i), waypointGrids.get(i + 1),
                        minAlt, maxAlt, noFlyData, passableZones,
                        exemptGridIds, weights, effectivePenalty,
                        ruleMatchIds, ruleBonus);
                if (seg == null) { segmentFailed = true; break; }
                segments.add(seg);
            }

            // 首轮即失败 → 完全无路；后续轮失败 → 跳过（保留旧结果）
            if (segmentFailed) {
                if (retry == 0) return fail("第 1 段路径无法抵达，请检查禁飞区/因素是否完全封死");
                continue;
            }

            // 5.3 合并多段 + 走廊扩展
            List<Grid> fullPath = mergeSegments(segments);
            if (widthInGrids > 1) {
                fullPath = expandCorridor(index, fullPath, widthInGrids, minAlt, maxAlt,
                        noFlyData, passableZones, exemptGridIds);
            }

            // 5.4 计算结果指标
            List<GeoPoint> waypoints = fullPath.stream().map(Grid::getCenterPoint).collect(Collectors.toList());
            double totalDistance = calculatePathDistance(waypoints, index);
            double avgScore = computeAvgScore(fullPath, weights);

            // 5.5 评估所有规则覆盖率
            // ruleCoverages: {factorName → 该因素在路径上的达标占比}
            Map<String, Double> ruleCoverages = hasRules
                    ? evaluateRuleCoverages(fullPath, rules) : new LinkedHashMap<>();
            double minCoverage = ruleCoverages.isEmpty() ? 1.0
                    : ruleCoverages.values().stream().min(Double::compare).orElse(1.0);

            // 5.6 构造消息
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("路径 %d 格 / %.0f 米 | 评分 %.1f",
                    fullPath.size(), totalDistance, avgScore));
            if (hasRules) {
                sb.append(" | 覆盖率 ").append(String.format("%.0f%%", minCoverage * 100));
                if (retry > 0) sb.append("（重试").append(retry).append("次）");
            }

            RouteResult result = RouteResult.builder()
                    .success(true).pathGrids(fullPath).waypoints(waypoints)
                    .totalDistance(totalDistance).avgRouteScore(avgScore)
                    .actualCoverageRate(minCoverage).ruleCoverages(ruleCoverages)
                    .message(sb.toString()).build();

            // 5.7 所有规则达标 → 直接返回，无需再重试
            if (allRulesMet(ruleCoverages, rules)) return result;

            // 5.8 保留本轮为最优（按覆盖率）
            if (bestResult == null || minCoverage > bestResult.getActualCoverageRate()) {
                bestResult = result;
            }
        }

        // 6. 所有重试用尽，返回最优结果（附带不达标警告）
        if (bestResult != null) {
            bestResult.setMessage(bestResult.getMessage()
                    + buildRuleWarning(bestResult.getRuleCoverages(), rules));
        }
        return bestResult != null ? bestResult : fail("无法找到满足所有规则的路径");
    }

    // ======================================================================
    //  规则引擎（可插拔核心）
    // ======================================================================

    /**
     * 根据重试轮次构造本轮权重 Map，支持双向调整。
     *
     * <p>每一轮对上轮不达标的因素单独调整：</p>
     * <ul>
     *   <li>覆盖率 < minCoverageRate → 权重 ×2（增强，迫使 A* 偏重这些网格）</li>
     *   <li>覆盖率 > maxCoverageRate → 权重 /2（减弱，释放给距离优化或其他因素）</li>
     * </ul>
     *
     * @param base     基础权重（来自配置）
     * @param rules    规则列表
     * @param retry    当前重试轮次（0=首轮，用 base）
     * @param previous 上一轮结果（null=首轮）
     * @return 本轮使用的权重 Map
     */
    private Map<String, Double> buildRetryWeights(Map<String, Double> base,
                                                   List<RouteRule> rules, int retry,
                                                   RouteResult previous) {
        Map<String, Double> weights = new LinkedHashMap<>(base);
        if (rules == null || retry == 0) return weights;

        if (previous != null && previous.getRuleCoverages() != null) {
            for (RouteRule r : rules) {
                Double cov = previous.getRuleCoverages().get(r.getFactorName());
                if (cov == null) continue;
                String factor = r.getFactorName();
                double orig = weights.getOrDefault(factor, 1.0);

                if (cov < r.getMinCoverageRate()) {
                    // 覆盖率太低 → 倍增权重
                    weights.put(factor, orig * 2);
                } else if (cov > r.getMaxCoverageRate() && cov > 0) {
                    // 覆盖率太高 → 权重减半，释放空间
                    weights.put(factor, Math.max(0.01, orig / 2));
                }
            }
        }
        return weights;
    }

    /**
     * 评估路径上所有规则的覆盖率。
     *
     * <p>对每条规则，遍历路径中所有网格，统计满足条件的网格数，
     * 覆盖率 = 满足数 / 路径总网格数。</p>
     *
     * @return {factorName → 覆盖率(0~1)}
     */
    private Map<String, Double> evaluateRuleCoverages(List<Grid> path, List<RouteRule> rules) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (path.isEmpty() || rules == null) return result;

        for (RouteRule rule : rules) {
            int matched = 0;
            for (Grid g : path) {
                if (evaluateRule(g, rule)) matched++;
            }
            result.put(rule.getFactorName(), (double) matched / path.size());
        }
        return result;
    }

    /**
     * 单规则判定：网格因素值是否落在 [minThreshold, maxThreshold] 区间内。
     */
    private boolean evaluateRule(Grid g, RouteRule rule) {
        Map<String, Integer> factors = g.getFactors();
        if (factors == null) return false;
        Integer val = factors.get(rule.getFactorName());
        if (val == null) return false;
        return val >= rule.getMinThreshold() && val <= rule.getMaxThreshold();
    }

    /** @return true 如果所有规则的覆盖率都在 [min, max] 区间内 */
    private boolean allRulesMet(Map<String, Double> coverages, List<RouteRule> rules) {
        if (rules == null || rules.isEmpty()) return true;
        for (RouteRule r : rules) {
            Double cov = coverages.get(r.getFactorName());
            if (cov == null || cov < r.getMinCoverageRate() || cov > r.getMaxCoverageRate()) return false;
        }
        return true;
    }

    /** 生成不达标规则的警告信息 */
    private String buildRuleWarning(Map<String, Double> coverages, List<RouteRule> rules) {
        if (rules == null || rules.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" | ⚠ 未达标:");
        for (RouteRule r : rules) {
            Double cov = coverages.get(r.getFactorName());
            double c = cov != null ? cov : 0;
            if (c < r.getMinCoverageRate()) {
                sb.append(String.format(" [%s %.0f%%<%.0f%%]",
                        r.getFactorName(), c * 100, r.getMinCoverageRate() * 100));
            } else if (c > r.getMaxCoverageRate()) {
                sb.append(String.format(" [%s %.0f%%>%.0f%%]",
                        r.getFactorName(), c * 100, r.getMaxCoverageRate() * 100));
            }
        }
        return sb.toString();
    }

    // ======================================================================
    //  禁飞区逻辑 —— 禁飞区 = 全量网格的子集，每个 100m 格
    // ======================================================================

    /**
     * 构建禁飞区映射。
     *
     * <p>{@code noFlyZones} 中的每个 Grid 就是一个 100m 网格条目。
     * 匹配方式（按优先级）：</p>
     * <ol>
     *   <li><b>ID 精确匹配</b>：nfz.id 等于全量网格中的某个 grid.id → 直接封锁该格</li>
     *   <li><b>坐标兜底</b>：nfz 的中心点落在全量网格的包围盒内 → 封锁该格</li>
     * </ol>
     *
     * <p>每个禁飞区条目自成一个 zone（zoneId = 其 gridId）。
     * 如果未来需要多格合成一个大 zone，可给 Grid 加 groupId 字段。</p>
     */
    private NoFlyData buildNoFlyData(List<Grid> noFlyZones, GridIndex index) {
        Map<String, Set<String>> zoneGridMap = new LinkedHashMap<>();
        Map<String, Set<String>> gridZoneMap = new HashMap<>();

        if (noFlyZones == null || noFlyZones.isEmpty()) {
            return new NoFlyData(zoneGridMap, gridZoneMap);
        }

        // 先建全量网格 ID 集合，用于精确匹配
        Set<String> fullGridIds = index.gridMap.values().stream()
                .map(Grid::getId).collect(Collectors.toSet());

        for (Grid nfz : noFlyZones) {
            String matchedGridId = null;

            // 1. 精确 ID 匹配
            if (fullGridIds.contains(nfz.getId())) {
                matchedGridId = nfz.getId();
            } else {
                // 2. 坐标兜底：找中心点落入哪个全量网格
                for (Grid full : index.gridMap.values()) {
                    if (isPointInGrid(full, nfz.getCenterPoint(), index)) {
                        matchedGridId = full.getId();
                        break;
                    }
                }
            }

            if (matchedGridId != null) {
                zoneGridMap.put(matchedGridId, new HashSet<>(List.of(matchedGridId)));
                gridZoneMap.computeIfAbsent(matchedGridId, k -> new LinkedHashSet<>()).add(matchedGridId);
            }
        }
        return new NoFlyData(zoneGridMap, gridZoneMap);
    }

    /**
     * 找出所有必经点所在的禁飞区网格 ID → 标记为可通行。
     * <p>必经点的经纬度落在哪个禁飞区网格内，那个网格就放行。</p>
     */
    private Set<String> findPassableZones(List<GeoPoint> waypoints, List<Grid> noFlyZones, GridIndex index) {
        Set<String> passable = new HashSet<>();
        if (noFlyZones == null) return passable;
        for (GeoPoint wp : waypoints) {
            for (Grid nfz : noFlyZones) {
                if (isPointInGrid(nfz, wp, index)) {
                    passable.add(nfz.getId());
                    break;
                }
            }
        }
        return passable;
    }

    // ======================================================================
    //  BFS 洪泛豁免

    /**
     * 从每个必经点出发，BFS 沿所有<b>被封锁</b>的 26 邻接扩散，
     * 返回整片连续封锁区中所有需要豁免的网格 ID 集合。
     *
     * <h3>为什么需要？</h3>
     * 必经点在禁飞区或因素=0 的网格中时，必须能走出去。
     * 如果封锁区很大（N×N×N 多方格），仅豁免必经点自己不够——
     * 必须沿着封锁区一路扩散到边界，整片豁免。
     *
     * <h3>停止条件</h3>
     * BFS 遇到<b>不被封锁</b>的网格时停止该方向扩散。
     * 这意味着封锁区边界 = 正常规则的起点，不会被过度豁免。
     *
     * <h3>复杂度</h3>
     * O(K)，K = 封锁区内网格数。一次性预计算，不进入 A* 循环。
     */
    private Set<String> computeExemptRegion(List<Grid> waypointGrids, GridIndex index,
                                             NoFlyData noFlyData, Set<String> passableZones) {
        Set<String> exempt = new LinkedHashSet<>();
        Queue<Grid> queue = new LinkedList<>();

        // 必经点永远豁免；但仅当必经点自己被封锁时才 BFS 扩散
        for (Grid wp : waypointGrids) {
            exempt.add(wp.getId());
            if (isGridBlocked(wp, noFlyData, passableZones)) {
                queue.add(wp);
            }
        }

        while (!queue.isEmpty()) {
            Grid current = queue.poll();
            // 检查 26 个方向
            for (int[] dir : NEIGHBOR_DIRECTIONS) {
                int ni = current.getIndexLon() + dir[0];
                int nj = current.getIndexLat() + dir[1];
                int nk = current.getIndexAlt() + dir[2];
                Grid neighbor = index.gridMap.get(indexKey(ni, nj, nk));

                if (neighbor == null || exempt.contains(neighbor.getId())) continue;

                // 只有被封锁的网格才纳入豁免扩散
                // "被封锁" = 禁飞区未放行 OR 因素=0
                if (isGridBlocked(neighbor, noFlyData, passableZones)) {
                    exempt.add(neighbor.getId());
                    queue.add(neighbor);
                }
                // 不被封锁 → 不扩散，这方向到此为止
            }
        }
        // 垂直豁免：Waypoint 同一 XY 位置的所高度层全放行（构成“垂直电梯井”），
        // 这样即使 routeHeight 限制中间高度，A* 也能沿着电梯顺升/降到达目标层。
//        for (Grid wp : waypointGrids) {
//            for (int k = 0; k < 100; k++) {
//                String key = indexKey(wp.getIndexLon(), wp.getIndexLat(), k);
//                Grid g = index.gridMap.get(key);
//                if (g == null) break; // 这层没有 → 上面也不会再 高度步长固定 100m
//                exempt.add(g.getId());
//            }
//        }
        return exempt;
    }

    /**
     * 判断网格是否被封锁（不考虑必经点豁免）。
     *
     * <p>封锁条件（任一满足即封锁）：
     * <ol>
     *   <li>在禁飞区内 且 该禁飞区未被标记为可通行</li>
     *   <li>任一因素值为 0</li>
     * </ol>
     *
     * 注意：此方法<b>不</b>包含必经点豁免检查。
     * 因为 BFS 扩散时必经点本身已入队，邻居如果被封锁则纳入豁免扩散；
     * 必经点豁免检查在调用方 {@link #isGridPassable} 中处理。
     */
    private boolean isGridBlocked(Grid g, NoFlyData noFlyData, Set<String> passableZones) {
        // 禁飞区检查：属于某个未放行的禁飞区 → 封锁
        Set<String> zones = noFlyData.gridZoneMap.get(g.getId());
        if (zones != null && !zones.isEmpty()) {
            boolean inPassable = false;
            for (String zid : zones) {
                if (passableZones.contains(zid)) { inPassable = true; break; }
            }
            if (!inPassable) return true;
        }
        // 因素检查：任一因素=0 → 封锁
        return !hasAllFactorsPassable(g);
    }

    // ======================================================================
    //  可通行判定（A* 每步调用）
    // ======================================================================

    /**
     * 三合一定：豁免集 → 禁飞区规则 → 因素检查。
     *
     * <p>优先级（短路逻辑）：
     * <ol>
     *   <li>在豁免集（必经点 + BFS 扩散区）→ ✅ 无条件放行</li>
     *   <li>在未放行禁飞区 → ❌ 拦截</li>
     *   <li>任一因素 = 0 → ❌ 拦截</li>
     *   <li>否则 → ✅ 放行</li>
     * </ol>
     */
    private boolean isGridPassable(Grid g, NoFlyData noFlyData, Set<String> passableZones,
                                    Set<String> exemptGridIds) {
        // 豁免集最高优先级
        if (exemptGridIds.contains(g.getId())) return true;

        // 禁飞区检查
        Set<String> zones = noFlyData.gridZoneMap.get(g.getId());
        if (zones != null && !zones.isEmpty()) {
            boolean inPassable = false;
            for (String zoneId : zones) {
                if (passableZones.contains(zoneId)) { inPassable = true; break; }
            }
            if (!inPassable) return false;
        }

        // 因素检查
        return hasAllFactorsPassable(g);
    }

    /** @return true 如果网格所有因素值都 > 0 */
    private boolean hasAllFactorsPassable(Grid g) {
        Map<String, Integer> factors = g.getFactors();
        if (factors == null || factors.isEmpty()) return true;
        for (int val : factors.values()) {
            if (val == 0) return false;
        }
        return true;
    }

    // ======================================================================
    //  评分系统 —— 将因素值转化为 A* 代价
    // ======================================================================

    /**
     * 网格加权评分（0~9）。
     * <pre>score = Σ(factorValue × weight) / Σ(weight)</pre>
     * 无因素或无权重 → 默认 9（满分，不产生惩罚）。
     */
    private double computeGridScore(Grid g, Map<String, Double> weights) {
        Map<String, Integer> factors = g.getFactors();
        if (factors == null || factors.isEmpty() || weights == null || weights.isEmpty()) return 9.0;
        double weightedSum = 0, weightSum = 0;
        for (Map.Entry<String, Double> w : weights.entrySet()) {
            Integer val = factors.get(w.getKey());
            if (val != null) {
                weightedSum += val * w.getValue();
                weightSum += w.getValue();
            }
        }
        return weightSum == 0 ? 9.0 : weightedSum / weightSum;
    }

    /**
     * 评分惩罚（米）：分数越低，惩罚越大。
     * <pre>penalty = (9 - score) / 9 × penaltyFactor</pre>
     * 满分 9 → 0m | 5 分 → 89m | 0 分 → 200m（默认）。
     */
    private double scorePenalty(Grid g, Map<String, Double> weights, double penaltyFactor) {
        return (9.0 - computeGridScore(g, weights)) / 9.0 * penaltyFactor;
    }

    /** 路径所有网格平均评分 */
    private double computeAvgScore(List<Grid> path, Map<String, Double> weights) {
        if (path == null || path.isEmpty()) return 0;
        double sum = 0;
        for (Grid g : path) sum += computeGridScore(g, weights);
        return sum / path.size();
    }

    // ======================================================================
    //  3D 空间索引构建
    // ======================================================================

    /**
     * 构建 3D 网格空间索引。
     *
     * <h3>经纬度处理</h3>
     * 经纬度在这里被当作<b>正交线性轴</b>处理，lon→X, lat→Y, alt→Z。
     * 对 100km 以内区域，地球曲率可忽略。
     *
     * <h3>稳健性保证</h3>
     * 不直接用浮点除法反算索引（会因 0.5 边界值不稳定），
     * 而是用最小中心点为锚点 + {@code 1e-10} epsilon 保证 round 一致性。
     *
     * <h3>步骤</h3>
     * <ol>
     *   <li>计算网格平均尺寸（度/米）和最小中心点</li>
     *   <li>以最小中心点格为锚点，其余格用偏移 / 步长反算索引</li>
     *   <li>用 epsilon 消除浮点累积误差</li>
     *   <li>存入 HashMap：key="i_j_k" → Grid（O(1) 查找）</li>
     * </ol>
     */
    private GridIndex buildGridIndex(List<Grid> grids) {
        // 从网格真实数据计算实际步长（兼容测试和实际数据）
        double sumCenterLat = 0;
        double minCenterLon = Double.MAX_VALUE, minCenterLat = Double.MAX_VALUE, minCenterAlt = Double.MAX_VALUE;
        double maxCenterLon = -Double.MAX_VALUE, maxCenterLat = -Double.MAX_VALUE;

        for (Grid g : grids) {
            sumCenterLat += g.getCenterPoint().getLatitude();
            double cl = g.getCenterPoint().getLongitude();
            double ct = g.getCenterPoint().getLatitude();
            double ca = g.getCenterPoint().getAltitude();
            if (cl < minCenterLon) minCenterLon = cl;
            if (ct < minCenterLat) minCenterLat = ct;
            if (ca < minCenterAlt) minCenterAlt = ca;
            if (cl > maxCenterLon) maxCenterLon = cl;
            if (ct > maxCenterLat) maxCenterLat = ct;
        }

        int n = grids.size();
        double avgCenterLat = sumCenterLat / n;
        double metersPerDegLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(avgCenterLat));

        // 唯一经/纬度数（3D 网格不受高度层数干扰）
        Set<Double> ul = new HashSet<>(), ut = new HashSet<>();
        for (Grid g : grids) {
            ul.add(g.getCenterPoint().getLongitude());
            ut.add(g.getCenterPoint().getLatitude());
        }
        int cols = ul.size(), rows = ut.size();

        double cellLonDeg = cols > 1 ? (maxCenterLon - minCenterLon) / (cols - 1) : 0.001;
        double cellLatDeg = rows > 1 ? (maxCenterLat - minCenterLat) / (rows - 1) : 0.001;
        double cellAlt = routeConfig.getGridSizeMeters();

        // 防止除零
        if (cellLonDeg < 1e-12) cellLonDeg = 0.001;
        if (cellLatDeg < 1e-12) cellLatDeg = 0.001;

        // 半格尺寸（用于 isPointInGrid 包围盒判断）
        double halfLonDeg = cellLonDeg / 2;
        double halfLatDeg = cellLatDeg / 2;
        double halfAlt = cellAlt / 2;

        // 以最小中心点为锚点 (0,0,0)，EPS 留足够余量消除浮点累计误差
        final double EPS = 1e-8;
        Map<String, Grid> indexMap = new HashMap<>();
        for (Grid g : grids) {
            int iLon = (int) Math.round((g.getCenterPoint().getLongitude() - minCenterLon) / cellLonDeg + EPS);
            int iLat = (int) Math.round((g.getCenterPoint().getLatitude() - minCenterLat) / cellLatDeg + EPS);
            int iAlt = (int) Math.round((g.getCenterPoint().getAltitude() - minCenterAlt) / cellAlt + EPS);
            g.setIndexLon(iLon);
            g.setIndexLat(iLat);
            g.setIndexAlt(iAlt);
            indexMap.put(indexKey(iLon, iLat, iAlt), g);
        }

        return new GridIndex(indexMap, cellLonDeg, cellLatDeg, cellAlt,
                halfLonDeg, halfLatDeg, halfAlt, metersPerDegLon);
    }

    private String indexKey(int i, int j, int k) {
        return i + "_" + j + "_" + k;
    }

    // ======================================================================
    //  网格定位
    // ======================================================================

    /**
     * 根据坐标查找所属网格。
     * <ol>
     *   <li>精确命中：点是否在中心 ± 半格范围内</li>
     *   <li>退化：找最近中心点，但仅当距离 < 半格尺寸时才有效</li>
     * </ol>
     * @return 找到的网格，或 null（坐标完全不在任何网格范围内）
     */
    private Grid findGridByPoint(List<Grid> grids, GeoPoint point, GridIndex index) {
        for (Grid g : grids) {
            if (isPointInGrid(g, point, index)) return g;
        }
        Grid nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Grid g : grids) {
            double dist = g.getCenterPoint().distanceTo(point);
            if (dist < minDist) { minDist = dist; nearest = g; }
        }
        // 最近格也必须在半格范围内才算有效；否则坐标完全在网格空间外
        if (nearest != null && isPointInGrid(nearest, point, index)) return nearest;
        return null;
    }

    /**
     * 判断点是否在网格包围盒内（中心点 ± 半格尺寸）
     */
    private boolean isPointInGrid(Grid grid, GeoPoint point, GridIndex index) {
        GeoPoint c = grid.getCenterPoint();
        return point.getLongitude() >= c.getLongitude() - index.halfLonDeg
                && point.getLongitude() <= c.getLongitude() + index.halfLonDeg
                && point.getLatitude() >= c.getLatitude() - index.halfLatDeg
                && point.getLatitude() <= c.getLatitude() + index.halfLatDeg
                && point.getAltitude() >= c.getAltitude() - index.halfAlt
                && point.getAltitude() <= c.getAltitude() + index.halfAlt;
    }

    // ======================================================================
    //  A* 最短路径搜索（核心算法）
    // ======================================================================

    /**
     * A* 最短路径搜索。
     *
     * <h3>公式</h3>
     * <pre>
     * f(n) = g(n) + h(n)
     * g(n) = 累积移动代价 = Σ(距离(i,j) + 评分惩罚(j))
     * h(n) = 启发函数 = 纯欧几里得距离（不含惩罚，保证可采纳）
     * </pre>
     *
     * <h3>为什么 h(n) 不含惩罚？</h3>
     * A* 要求启发函数<b>可采纳</b>（永不高估）。
     * 评分惩罚 ≥ 0，所以纯距离 ≤ 实际代价，满足条件。
     *
     * <h3>26 方向邻接</h3>
     * 面邻接（6 方向）→ 直线距离
     * 棱邻接（12 方向）→ √2 × 100m ≈ 141m
     * 角邻接（8 方向）→ √3 × 100m ≈ 173m
     *
     * @param index         空间索引
     * @param start         起点网格
     * @param end           终点网格
     * @param maxAltitude   高度上限
     * @param noFlyData     禁飞区映射
     * @param passableZones 已放行禁飞区 ID 集合
     * @param exemptGridIds BFS 豁免集
     * @param factorWeights 因素权重
     * @param penaltyFactor 评分惩罚因子
     * @return 最短路径（网格列表），找不到返回 null
     */
    private List<Grid> aStarSearch(GridIndex index, Grid start, Grid end,
                                    double minAltitude, double maxAltitude,
                                    NoFlyData noFlyData, Set<String> passableZones,
                                    Set<String> exemptGridIds,
                                    Map<String, Double> factorWeights,
                                    double penaltyFactor,
                                    Set<String> ruleMatchIds, double ruleBonus) {
        Map<String, Grid> gridMap = index.gridMap;

        // openSet: 待探索节点，按 fScore 排序（最小优先）
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        // nodeMap: 已生成节点的快速查找（用于更新更优路径）
        Map<String, Node> nodeMap = new HashMap<>();

        // 起点入队（g=0, h=到终点距离）
        Node startNode = new Node(start, 0, heuristicMeters(start, end, index), null);
        openSet.add(startNode);
        nodeMap.put(gridKey(start), startNode);

        // closedSet: 已处理节点，不重复访问
        Set<String> closedSet = new HashSet<>();

        while (!openSet.isEmpty()) {
            // 取出 fScore 最小的节点
            Node current = openSet.poll();
            String currentKey = gridKey(current.grid);

            // 到达终点 → 回溯路径
            if (current.grid.getId().equals(end.getId())) {
                return reconstructPath(current);
            }

            // 已处理过 → 跳过（可能队列中有旧版本）
            if (!closedSet.add(currentKey)) continue;

            // 遍历 26 个邻接方向
            for (int[] dir : NEIGHBOR_DIRECTIONS) {
                int ni = current.grid.getIndexLon() + dir[0];
                int nj = current.grid.getIndexLat() + dir[1];
                int nk = current.grid.getIndexAlt() + dir[2];
                Grid neighbor = gridMap.get(indexKey(ni, nj, nk));

                // 不存在 → 跳过
                if (neighbor == null) continue;
                // 高度不在走廊范围且不在豁免集 → 跳过
                double alt = neighbor.getCenterPoint().getAltitude();
                if (!exemptGridIds.contains(neighbor.getId())
                        && (alt < minAltitude || alt > maxAltitude)) continue;
                // 禁飞区/因素 → 跳过
                if (!isGridPassable(neighbor, noFlyData, passableZones, exemptGridIds)) continue;

                String neighborKey = gridKey(neighbor);
                if (closedSet.contains(neighborKey)) continue;

                // 移动代价 = 实际距离 + 评分惩罚
                double baseDist = distanceMeters(current.grid.getCenterPoint(), neighbor.getCenterPoint(), index);
                double penalty = scorePenalty(neighbor, factorWeights, penaltyFactor);
                double moveCost = baseDist + penalty;
                // 规则奖励：匹配格享受折扣，不匹配格接受惩罚
                if (!ruleMatchIds.isEmpty()) {
                    moveCost += ruleMatchIds.contains(neighbor.getId()) ? -ruleBonus : ruleBonus;
                }
                double tentativeG = current.gScore + moveCost;

                // 更新或创建邻居节点
                Node neighborNode = nodeMap.get(neighborKey);
                if (neighborNode == null) {
                    // 首次发现 → 创建节点并入队
                    double h = heuristicMeters(neighbor, end, index);
                    neighborNode = new Node(neighbor, tentativeG, tentativeG + h, current);
                    openSet.add(neighborNode);
                    nodeMap.put(neighborKey, neighborNode);
                } else if (tentativeG < neighborNode.gScore) {
                    // 找到更优路径 → 更新并重新入队
                    neighborNode.gScore = tentativeG;
                    neighborNode.fScore = tentativeG + heuristicMeters(neighbor, end, index);
                    neighborNode.parent = current;
                    openSet.remove(neighborNode);
                    openSet.add(neighborNode);
                }
            }
        }

        return null; // 无路可达
    }

    /** 启发函数：纯欧几里得距离（不含评分惩罚，保证可采纳） */
    private double heuristicMeters(Grid a, Grid b, GridIndex index) {
        return distanceMeters(a.getCenterPoint(), b.getCenterPoint(), index);
    }

    /**
     * 两点间米制距离。
     * <pre>
     * dLat(m) = Δlat × 111320
     * dLon(m) = Δlon × 111320 × cos(avgLat)
     * dAlt(m) = Δalt
     * </pre>
     */
    private double distanceMeters(GeoPoint a, GeoPoint b, GridIndex index) {
        double dLat = (a.getLatitude() - b.getLatitude()) * METERS_PER_DEGREE_LAT;
        double dLon = (a.getLongitude() - b.getLongitude()) * index.metersPerDegLon;
        double dAlt = a.getAltitude() - b.getAltitude();
        return Math.sqrt(dLat * dLat + dLon * dLon + dAlt * dAlt);
    }

    /** 从目标节点沿 parent 链回溯出完整路径 */
    private List<Grid> reconstructPath(Node node) {
        LinkedList<Grid> path = new LinkedList<>();
        while (node != null) {
            path.addFirst(node.grid);
            node = node.parent;
        }
        return path;
    }

    // ======================================================================
    //  多段合并 & 走廊扩展
    // ======================================================================

    /**
     * 合并多段 A* 结果，交界处去重。
     * <p>seg[0]=[A,B,C], seg[1]=[C,D,E] → [A,B,C,D,E]</p>
     */
    private List<Grid> mergeSegments(List<List<Grid>> segments) {
        List<Grid> result = new ArrayList<>(segments.get(0));
        for (int s = 1; s < segments.size(); s++) {
            List<Grid> seg = segments.get(s);
            for (int i = 1; i < seg.size(); i++) result.add(seg.get(i));
        }
        return result;
    }

    /**
     * 走廊扩展：以核心路径每个网格为中心，向经纬度方向扩展 {@code routeWidth} 格宽度。
     * 同样遵循禁飞区和因素检查。
     */
    private List<Grid> expandCorridor(GridIndex index, List<Grid> corePath,
                                       int routeWidth, double minAlt, double maxAlt,
                                       NoFlyData noFlyData, Set<String> passableZones,
                                       Set<String> exemptGridIds) {
        Set<String> corridorKeys = new HashSet<>();
        List<Grid> corridor = new ArrayList<>();
        Map<String, Grid> gridMap = index.gridMap;
        int halfWidth = (routeWidth - 1) / 2;

        for (Grid g : corePath) {
            for (int di = -halfWidth; di <= halfWidth; di++) {
                for (int dj = -halfWidth; dj <= halfWidth; dj++) {
                    int ni = g.getIndexLon() + di;
                    int nj = g.getIndexLat() + dj;
                    Grid neighbor = gridMap.get(indexKey(ni, nj, g.getIndexAlt()));
                    double alt = neighbor.getCenterPoint().getAltitude();
                    if (neighbor != null
                            && alt >= minAlt && alt <= maxAlt
                            && isGridPassable(neighbor, noFlyData, passableZones, exemptGridIds)
                            && corridorKeys.add(gridKey(neighbor))) {
                        corridor.add(neighbor);
                    }
                }
            }
        }
        return corridor;
    }

    // ======================================================================
    //  辅助方法
    // ======================================================================

    private double calculatePathDistance(List<GeoPoint> waypoints, GridIndex index) {
        double dist = 0;
        for (int i = 1; i < waypoints.size(); i++) {
            dist += distanceMeters(waypoints.get(i - 1), waypoints.get(i), index);
        }
        return dist;
    }

    private String gridKey(Grid g) {
        return g.getId();
    }

    private RouteResult fail(String msg) {
        return RouteResult.builder().success(false).message(msg).build();
    }

    // ======================================================================
    //  内部类
    // ======================================================================

    /** A* 搜索节点 */
    private static class Node {
        Grid grid;           // 所在网格
        double gScore;       // 起点到当前的累积代价（距离+评分惩罚）
        double fScore;       // gScore + heuristic（用于优先队列排序）
        Node parent;         // 父节点（用于回溯路径）
        Node(Grid grid, double gScore, double fScore, Node parent) {
            this.grid = grid; this.gScore = gScore; this.fScore = fScore; this.parent = parent;
        }
    }

    /**
     * 3D 空间索引。
     * <ul>
     *   <li><b>gridMap</b>: HashMap，key="i_j_k" → Grid，O(1) 邻接查找</li>
     *   <li><b>cellLonDeg/cellLatDeg/cellAlt</b>: 网格步长（度/度/米）</li>
     *   <li><b>halfLonDeg/halfLatDeg/halfAlt</b>: 半格尺寸，用于包围盒判断</li>
     *   <li><b>metersPerDegLon</b>: 经度→米转换因子</li>
     * </ul>
     */
    private static class GridIndex {
        Map<String, Grid> gridMap;
        double cellLonDeg, cellLatDeg, cellAlt;
        double halfLonDeg, halfLatDeg, halfAlt;
        double metersPerDegLon;
        GridIndex(Map<String, Grid> gridMap, double cellLonDeg, double cellLatDeg, double cellAlt,
                  double halfLonDeg, double halfLatDeg, double halfAlt, double metersPerDegLon) {
            this.gridMap = gridMap;
            this.cellLonDeg = cellLonDeg; this.cellLatDeg = cellLatDeg; this.cellAlt = cellAlt;
            this.halfLonDeg = halfLonDeg; this.halfLatDeg = halfLatDeg; this.halfAlt = halfAlt;
            this.metersPerDegLon = metersPerDegLon;
        }
    }

    /**
     * 禁飞区双向映射数据。
     * <ul>
     *   <li><b>zoneGridMap</b>: zoneId → {该区内所有 gridId}</li>
     *   <li><b>gridZoneMap</b>: gridId → {该网格所属的 zoneId...}（支持嵌套）</li>
     * </ul>
     */
    private static class NoFlyData {
        Map<String, Set<String>> zoneGridMap;
        Map<String, Set<String>> gridZoneMap;
        NoFlyData(Map<String, Set<String>> zoneGridMap, Map<String, Set<String>> gridZoneMap) {
            this.zoneGridMap = zoneGridMap; this.gridZoneMap = gridZoneMap;
        }
    }
}

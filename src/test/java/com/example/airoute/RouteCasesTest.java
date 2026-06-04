//package com.example.airoute;
//
//import com.example.airoute.dto.RouteResult;
//import com.example.airoute.dto.RouteRule;
//import com.example.airoute.model.GeoPoint;
//import com.example.airoute.model.Grid;
//import com.example.airoute.service.RouteService;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@SpringBootTest
//class RouteCasesTest {
//
//    @Autowired
//    private RouteService routeService;
//
//    private static final double STEP_LON = 0.001, STEP_LAT = 0.001, STEP_ALT = 100.0;
//    private static final double BASE_LON = 119.000, BASE_LAT = 29.000, BASE_ALT = 0.0;
//
//    private List<Grid> buildGrid(int nLon, int nLat, int nAlt, Map<String, int[]> factorMap) {
//        List<Grid> grids = new ArrayList<>();
//        for (int k = 0; k < nAlt; k++)
//            for (int j = 0; j < nLat; j++)
//                for (int i = 0; i < nLon; i++) {
//                    Map<String, Integer> factors = null;
//                    if (factorMap != null) {
//                        int[] vals = factorMap.get(i + "_" + j + "_" + k);
//                        if (vals != null) {
//                            factors = new LinkedHashMap<>();
//                            factors.put("crowd", vals[0]);
//                            factors.put("wind", vals[1]);
//                            factors.put("weather", vals[2]);
//                        }
//                    }
//                    grids.add(Grid.builder()
//                            .id("g_" + i + "_" + j + "_" + k)
//                            .centerPoint(new GeoPoint(BASE_LON + i * STEP_LON + STEP_LON / 2, BASE_LAT + j * STEP_LAT + STEP_LAT / 2, BASE_ALT + k * STEP_ALT + STEP_ALT / 2))
//                            .factors(factors).build());
//                }
//        return grids;
//    }
//
//    private Grid gridAt(List<Grid> grids, int i, int j, int k) {
//        return grids.stream().filter(g -> g.getId().equals("g_" + i + "_" + j + "_" + k)).findFirst().orElse(null);
//    }
//
//    /** 创建禁飞区网格列表（每个格的 id = 全量网格 id） */
//    private List<Grid> noFlyZones(int i0, int j0, int k0, int i1, int j1, int k1) {
//        List<Grid> list = new ArrayList<>();
//        for (int i = i0; i <= i1; i++)
//            for (int j = j0; j <= j1; j++)
//                for (int k = k0; k <= k1; k++) {
//                    double lon = BASE_LON + i * STEP_LON;
//                    double lat = BASE_LAT + j * STEP_LAT;
//                    double alt = BASE_ALT + k * STEP_ALT;
//                    list.add(Grid.builder()
//                            .id("g_" + i + "_" + j + "_" + k)
//                            .centerPoint(new GeoPoint(lon + STEP_LON / 2, lat + STEP_LAT / 2, alt + STEP_ALT / 2))
//                            .build());
//                }
//        return list;
//    }
//
//    private GeoPoint pointAt(List<Grid> grids, int i, int j, int k) {
//        Grid g = gridAt(grids, i, j, k);
//        return new GeoPoint(g.getCenterPoint().getLongitude() + 1e-7, g.getCenterPoint().getLatitude() + 1e-7, g.getCenterPoint().getAltitude() + 1e-7);
//    }
//
//    private RouteResult call(List<Grid> grids, GeoPoint s, GeoPoint e, List<GeoPoint> mids,
//                              List<Grid> nfz, double w, double h, List<RouteRule> rules) {
//        return routeService.findShortestRoute(grids, s, e, mids, nfz, w, h, rules);
//    }
//
//    // ================================================================
//    @Test
//    void case1_noObstacle() {
//        List<Grid> grids = buildGrid(5, 5, 1, null);
//        RouteResult r = call(grids, pointAt(grids, 0, 4, 0), pointAt(grids, 4, 0, 0), null, null, 0, 1000, null);
//        assertTrue(r.isSuccess());
//        assertTrue(r.getPathGrids().size() >= 5);
//        assertEquals("g_0_4_0", r.getPathGrids().get(0).getId());
//        assertEquals("g_4_0_0", r.getPathGrids().get(r.getPathGrids().size() - 1).getId());
//        assertEquals(1.0, r.getActualCoverageRate(), 0.01);
//        System.out.println("✅ 案例1 — " + r.getMessage());
//    }
//
//    @Test
//    void case2_waypointInNoFlyZone() {
//        List<Grid> grids = buildGrid(5, 5, 1, null);
//        // 禁飞区：中心 3×3 的每格都是禁飞区条目
//        List<Grid> nfz = noFlyZones(1, 1, 0, 3, 3, 0);
//        RouteResult r = call(grids, pointAt(grids, 0, 0, 0), pointAt(grids, 2, 2, 0),
//                null, nfz, 0, 1000, null);
//        assertTrue(r.isSuccess());
//        assertTrue(r.getPathGrids().stream().anyMatch(g -> g.getId().equals("g_2_2_0")));
//        System.out.println("✅ 案例2 — " + r.getMessage());
//    }
//
//    @Test
//    void case3_freeSurroundedByNoFly() {
//        List<Grid> grids = buildGrid(7, 7, 1, null);
//        // 四周围一圈是禁飞区格，中心 3×3 自由区
//        List<Grid> nfz = new ArrayList<>();
//        nfz.addAll(noFlyZones(0, 0, 0, 6, 0, 0)); // 上边
//        nfz.addAll(noFlyZones(0, 6, 0, 6, 6, 0)); // 下边
//        nfz.addAll(noFlyZones(0, 1, 0, 0, 5, 0)); // 左边
//        nfz.addAll(noFlyZones(6, 1, 0, 6, 5, 0)); // 右边
//
//        // 3a. 自由区内通行
//        assertTrue(call(grids, pointAt(grids, 3, 2, 0), pointAt(grids, 3, 4, 0), null, nfz, 0, 1000, null).isSuccess());
//
//        // 3b. 终点在所有格子之外 → 无路可达
//        GeoPoint outside = new GeoPoint(BASE_LON - 0.01, BASE_LAT - 0.01, 0);
//        assertFalse(call(grids, pointAt(grids, 3, 3, 0), outside, null, nfz, 0, 1000, null).isSuccess());
//        System.out.println("✅ 案例3");
//    }
//
//    @Test
//    void case4_factorBlocking() {
//        Map<String, int[]> f = new HashMap<>();
//        f.put("2_0_0", new int[]{5, 0, 7});
//        List<Grid> grids = buildGrid(5, 5, 1, f);
//        RouteResult r = call(grids, pointAt(grids, 0, 0, 0), pointAt(grids, 4, 0, 0), null, null, 0, 1000, null);
//        Set<String> ids = r.getPathGrids().stream().map(Grid::getId).collect(Collectors.toSet());
//        assertFalse(ids.contains("g_2_0_0"));
//        assertTrue(ids.contains("g_2_1_0") || ids.contains("g_1_1_0") || ids.contains("g_3_1_0"));
//        System.out.println("✅ 案例4 — " + r.getMessage());
//    }
//
//    @Test
//    void case5_nestedZonesWithMidpoint() {
//        List<Grid> grids = buildGrid(7, 7, 1, null);
//        List<Grid> nfz = noFlyZones(1, 1, 0, 5, 5, 0);
//        RouteResult r = call(grids, pointAt(grids, 0, 0, 0), pointAt(grids, 6, 6, 0),
//                List.of(pointAt(grids, 3, 3, 0)), nfz, 0, 1000, null);
//        assertTrue(r.isSuccess(), r.getMessage());
//        Set<String> ids = r.getPathGrids().stream().map(Grid::getId).collect(Collectors.toSet());
//        assertTrue(ids.contains("g_3_3_0"));
//        System.out.println("✅ 案例5 — " + r.getMessage());
//    }
//
//    // ==================== 规则引擎测试 ====================
//
//    @Test
//    void case6_highCrowdRule() {
//        // 对角线 crowd=8，其余 crowd=1。规则：crowd∈[5,9] 需占 50%。
//        // 自动重试会倍增 crowd 权重迫使 A* 绕行匹配
//        Map<String, int[]> f = new HashMap<>();
//        for (int i = 0; i < 5; i++)
//            for (int j = 0; j < 5; j++)
//                f.put(i + "_" + j + "_" + 0, new int[]{Math.abs(i - j) <= 1 ? 8 : 1, 5, 5});
//        List<Grid> grids = buildGrid(5, 5, 1, f);
//        List<RouteRule> rules = List.of(new RouteRule("crowd", 5, 9, 0.3, 1.0));
//        RouteResult r = call(grids, pointAt(grids, 0, 4, 0), pointAt(grids, 4, 0, 0), null, null, 0, 1000, rules);
//        assertTrue(r.isSuccess(), r.getMessage());
//        assertTrue(r.getActualCoverageRate() >= 0.2,
//                "覆盖率 " + r.getActualCoverageRate() + " 应 ≥ 0.2, ruleCoverages=" + r.getRuleCoverages());
//        System.out.println("✅ 案例6 — " + r.getMessage() + " | " + r.getRuleCoverages());
//    }
//
//    @Test
//    void case7_multiRules() {
//        // crowd >= 8 占 50%, weather >= 6 占 40%
//        Map<String, int[]> f = new HashMap<>();
//        for (int i = 0; i < 5; i++)
//            for (int j = 0; j < 5; j++) {
//                int crow = (i >= 3) ? 8 : 1;
//                int weat = 7;
//                f.put(i + "_" + j + "_" + 0, new int[]{crow, 5, weat});
//            }
//        List<Grid> grids = buildGrid(5, 5, 1, f);
//        List<RouteRule> rules = List.of(
//                new RouteRule("crowd", 5, 9, 0.5, 1.0),
//                new RouteRule("weather", 6, 9, 0.4, 1.0));
//        RouteResult r = call(grids, pointAt(grids, 0, 2, 0), pointAt(grids, 4, 2, 0), null, null, 0, 1000, rules);
//        assertTrue(r.isSuccess());
//        assertNotNull(r.getRuleCoverages());
//        assertTrue(r.getRuleCoverages().containsKey("crowd"));
//        assertTrue(r.getRuleCoverages().containsKey("weather"));
//        assertTrue(r.getActualCoverageRate() > 0.2);
//        System.out.println("✅ 案例7 多规则 — " + r.getMessage() + " | " + r.getRuleCoverages());
//    }
//
//    @Test
//    void case8_combined_all() {
//        Map<String, int[]> f = new HashMap<>();
//        for (int i = 0; i < 7; i++)
//            for (int j = 0; j < 7; j++)
//                f.put(i + "_" + j + "_" + 0, new int[]{8, 6, 7});
//        List<Grid> grids = buildGrid(7, 7, 1, f);
//        List<Grid> nfz = noFlyZones(1, 1, 0, 3, 3, 0);
//        List<GeoPoint> mids = List.of(pointAt(grids, 2, 2, 0));
//        List<RouteRule> rules = List.of(
//                new RouteRule("crowd", 5, 9, 0.4, 1.0),
//                new RouteRule("wind", 5, 9, 0.4, 1.0));
//        RouteResult r = call(grids, pointAt(grids, 0, 0, 0), pointAt(grids, 6, 6, 0), mids, nfz, 0, 1000, rules);
//        assertTrue(r.isSuccess());
//        assertTrue(r.getPathGrids().stream().anyMatch(g -> g.getId().equals("g_2_2_0")));
//        System.out.println("✅ 案例8 全组合 — " + r.getMessage() + " | " + r.getRuleCoverages());
//    }
//
//    @Test
//    void case9_noRules() {
//        List<Grid> grids = buildGrid(5, 5, 1, null);
//        RouteResult r = routeService.findShortestRoute(grids,
//                pointAt(grids, 0, 0, 0), pointAt(grids, 4, 4, 0), null, null, 0, 1000, null);
//        assertTrue(r.isSuccess());
//        assertEquals(1.0, r.getActualCoverageRate(), 0.01);
//        System.out.println("✅ 案例9 无规则 — " + r.getMessage());
//    }
//
//    @Test
//    void case10_fullCoverage() {
//        Map<String, int[]> f = new HashMap<>();
//        for (int i = 0; i < 5; i++)
//            for (int j = 0; j < 5; j++)
//                f.put(i + "_" + j + "_" + 0, new int[]{9, 8, 9});
//        List<Grid> grids = buildGrid(5, 5, 1, f);
//        List<RouteRule> rules = List.of(
//                new RouteRule("crowd", 5, 9, 1.0, 1.0),
//                new RouteRule("wind", 5, 9, 1.0, 1.0));
//        RouteResult r = call(grids, pointAt(grids, 0, 0, 0), pointAt(grids, 4, 4, 0), null, null, 0, 1000, rules);
//        assertTrue(r.isSuccess());
//        assertEquals(1.0, r.getActualCoverageRate(), 0.01);
//        assertEquals(1.0, r.getRuleCoverages().get("crowd"), 0.01);
//        assertEquals(1.0, r.getRuleCoverages().get("wind"), 0.01);
//        System.out.println("✅ 案例10 全覆盖 — " + r.getMessage() + " | " + r.getRuleCoverages());
//    }
//}

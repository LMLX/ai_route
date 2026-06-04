package com.example.airoute.controller;

import com.example.airoute.dto.RouteRequest;
import com.example.airoute.dto.RouteResult;
import com.example.airoute.model.EncryptedGrid;
import com.example.airoute.model.GeoPoint;
import com.example.airoute.config.RouteConfig;
import com.example.airoute.model.Grid;
import com.example.airoute.model.GridContext;
import com.example.airoute.service.RouteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RouteService routeService;
    private final RouteConfig routeConfig;
    private final ObjectMapper mapper = new ObjectMapper();


    public RouteController(RouteService routeService, RouteConfig routeConfig) {
        this.routeService = routeService;
        this.routeConfig = routeConfig;
    }

    @PostMapping("/shortest")
    public RouteResult findShortest(@RequestBody RouteRequest request) {
        return doRoute(request.getGrids(), request.getNoFlyZones(), request.getRules(),
                request.getStartPoint(), request.getEndPoint(), request.getMidPoints(),
                request.getRouteWidth(), request.getRouteHeight());
    }

    @PostMapping("/shortest2")
    public RouteResult findShortest2(@RequestBody RouteRequest request) throws IOException {
        return doRoute(request.getNoFlyZones(), request.getRules(),
                request.getStartPoint(), request.getEndPoint(), request.getMidPoints(),
                request.getRouteWidth(), request.getRouteHeight());
    }

    @GetMapping("/batch")
    public Map<String, Object> batchTest(@RequestParam(defaultValue = "batch-requests.txt") String file) {
        List<Map<String, Object>> results = new ArrayList<>();
        int total = 0, success = 0;
        try {
            ClassPathResource resource = new ClassPathResource(file);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue; total++;
                    try {
                        RouteRequest req = mapper.readValue(line, RouteRequest.class);
                        RouteResult result = doRoute(req.getGrids(), req.getNoFlyZones(), req.getRules(),
                                req.getStartPoint(), req.getEndPoint(), req.getMidPoints(),
                                req.getRouteWidth(), req.getRouteHeight());
                        if (result.isSuccess()) success++;
                        results.add(Map.of("line", total, "success", result.isSuccess(), "message", result.getMessage()));
                    } catch (Exception e) {
                        results.add(Map.of("line", total, "success", false, "message", e.getMessage()));
                    }
                }
            }
        } catch (IOException e) {
            return Map.of("error", e.getMessage());
        }
        return Map.of("total", total, "success", success, "results", results);
    }

    private RouteResult doRoute(List<Grid> grids, List<Grid> noFlyZones, List<com.example.airoute.dto.RouteRule> rules,
                                 GeoPoint start, GeoPoint end, List<GeoPoint> mids, double w, double h) {
        // 1. 收集因素名（从规则列表）
        List<String> factorNames = new ArrayList<>();
        if (rules != null) {
            for (var r : rules) {
                if (!factorNames.contains(r.getFactorName())) factorNames.add(r.getFactorName());
            }
        }
        // 2. 扫描网格得 GridContext
        Set<Double> lonSet = new HashSet<>(), latSet = new HashSet<>();
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minAlt = Double.MAX_VALUE;
        for (Grid g : grids) {
            double lon = g.getCenterPoint().getLongitude();
            double lat = g.getCenterPoint().getLatitude();
            double alt = g.getCenterPoint().getAltitude();
            lonSet.add(lon); latSet.add(lat);
            if (lon < minLon) minLon = lon; if (lon > maxLon) maxLon = lon;
            if (lat < minLat) minLat = lat; if (lat > maxLat) maxLat = lat;
            if (alt < minAlt) minAlt = alt;
        }
        double cellLon = lonSet.size() > 1 ? (maxLon - minLon) / (lonSet.size() - 1) : 0.001;
        double cellLat = latSet.size() > 1 ? (maxLat - minLat) / (latSet.size() - 1) : 0.001;
        double cellAlt = 100;
        GridContext ctx = new GridContext(minLon, minLat, minAlt, cellLon, cellLat, cellAlt);

        // 3. 转换
        return routeService.findShortestRoute(
                toEncrypted(grids, ctx, factorNames),
                ctx, factorNames,
                start, end, mids,
                toEncrypted(noFlyZones, ctx, factorNames),
                w, h, rules);
    }


    private RouteResult doRoute(List<Grid> noFlyZones, List<com.example.airoute.dto.RouteRule> rules,
                                GeoPoint start, GeoPoint end, List<GeoPoint> mids, double w, double h) throws IOException {
        List<String> factorNames = new ArrayList<>();
        if (rules != null) for (var r : rules) if (!factorNames.contains(r.getFactorName())) factorNames.add(r.getFactorName());

        String inputPath = "/Users/jinjiahao/IdeaProjects/ai-route/dim_grid_data2";
        final double CELL_LON = 0.001, CELL_LAT = 0.001, CELL_ALT = 100;
        final double eps = routeConfig.getEps();

        List<EncryptedGrid> grids = new ArrayList<>();
        double refLon = 0, refLat = 0, refAlt = 0;
        double minLon = Double.MAX_VALUE, minLat = Double.MAX_VALUE, minAlt = Double.MAX_VALUE;
        int minI = 0, minJ = 0, minK = 0;
        boolean first = true;

        try (BufferedReader r = Files.newBufferedReader(Paths.get(inputPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                int c1 = line.indexOf(','), c2 = line.indexOf(',', c1 + 1);
                double lon = Double.parseDouble(line.substring(0, c1));
                double lat = Double.parseDouble(line.substring(c1 + 1, c2));
                double alt = Double.parseDouble(line.substring(c2 + 1));

                if (first) {
                    refLon = lon; refLat = lat; refAlt = alt;
                    first = false;
                }

                int i = (int) Math.round((lon - refLon) / CELL_LON + eps);
                int j = (int) Math.round((lat - refLat) / CELL_LAT + eps);
                int k = (int) Math.round((alt - refAlt) / CELL_ALT + eps);

                if (i < minI) minI = i;
                if (j < minJ) minJ = j;
                if (k < minK) minK = k;
                if (lon < minLon) minLon = lon;
                if (lat < minLat) minLat = lat;
                if (alt < minAlt) minAlt = alt;

                grids.add(new EncryptedGrid(i, j, k));
            }
        }

        // 如果索引出现负数，整体平移到非负
        if (minI < 0 || minJ < 0 || minK < 0) {
            for (EncryptedGrid g : grids) {
                if (minI < 0) g.setIndexLon(g.getIndexLon() - minI);
                if (minJ < 0) g.setIndexLat(g.getIndexLat() - minJ);
                if (minK < 0) g.setIndexAlt(g.getIndexAlt() - minK);
            }
            if (minI < 0) refLon += minI * CELL_LON;
            if (minJ < 0) refLat += minJ * CELL_LAT;
            if (minK < 0) refAlt += minK * CELL_ALT;
        }

        GridContext ctx = new GridContext(refLon, refLat, refAlt, CELL_LON, CELL_LAT, CELL_ALT);

        return routeService.findShortestRoute(grids, ctx, factorNames,
                start, end, mids, toEncrypted(noFlyZones, ctx, factorNames), w, h, rules);
    }

    private List<EncryptedGrid> toEncrypted(List<Grid> grids, GridContext ctx, List<String> names) {
        if (grids == null) return null;
        final double eps = routeConfig.getEps();
        return grids.stream().map(g -> {
            int i = (int) Math.round((g.getCenterPoint().getLongitude() - ctx.minLon) / ctx.cellLon + eps);
            int j = (int) Math.round((g.getCenterPoint().getLatitude()  - ctx.minLat) / ctx.cellLat + eps);
            int k = (int) Math.round((g.getCenterPoint().getAltitude()  - ctx.minAlt) / ctx.cellAlt + eps);
            return new EncryptedGrid(i, j, k, names, g.getFactors());
        }).collect(Collectors.toList());
    }
}

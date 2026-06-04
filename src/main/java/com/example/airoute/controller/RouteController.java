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

        String inputPath = "/Users/jinjiahao/IdeaProjects/ai-route/dim_grid_data1";
        final double eps = routeConfig.getEps();

        // 第一趟：只收集边界信息
        Set<Double> lonSet = new HashSet<>(), latSet = new HashSet<>();
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minAlt = Double.MAX_VALUE;
        int totalLines = 0;

        try (BufferedReader r = Files.newBufferedReader(Paths.get(inputPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] c = line.split(",");
                double lon = Double.parseDouble(c[0]), lat = Double.parseDouble(c[1]), alt = Double.parseDouble(c[2]);
                lonSet.add(lon); latSet.add(lat);
                if (lon < minLon) minLon = lon; if (lon > maxLon) maxLon = lon;
                if (lat < minLat) minLat = lat; if (lat > maxLat) maxLat = lat;
                if (alt < minAlt) minAlt = alt;
                totalLines++;
            }
        }

        double cellLon = lonSet.size() > 1 ? (maxLon - minLon) / (lonSet.size() - 1) : 0.001;
        double cellLat = latSet.size() > 1 ? (maxLat - minLat) / (latSet.size() - 1) : 0.001;
        double cellAlt = 100;
        GridContext ctx = new GridContext(minLon, minLat, minAlt, cellLon, cellLat, cellAlt);

        // 第二趟：直接创建 EncryptedGrid
        List<EncryptedGrid> grids = new ArrayList<>(totalLines);
        try (BufferedReader r = Files.newBufferedReader(Paths.get(inputPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] c = line.split(",");
                int i = (int) Math.round((Double.parseDouble(c[0]) - minLon) / cellLon + eps);
                int j = (int) Math.round((Double.parseDouble(c[1]) - minLat) / cellLat + eps);
                int k = (int) Math.round((Double.parseDouble(c[2]) - minAlt) / cellAlt + eps);
                grids.add(new EncryptedGrid(i, j, k));
            }
        }

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

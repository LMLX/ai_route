package com.example.airoute.controller;

import com.example.airoute.dto.RouteRequest;
import com.example.airoute.dto.RouteResult;
import com.example.airoute.service.RouteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RouteService routeService;
    private final ObjectMapper mapper = new ObjectMapper();

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/shortest")
    public RouteResult findShortest(@RequestBody RouteRequest request) {
        return routeService.findShortestRoute(
                request.getGrids(),
                request.getStartPoint(),
                request.getEndPoint(),
                request.getMidPoints(),
                request.getNoFlyZones(),
                request.getRouteWidth(),
                request.getRouteHeight(),
                request.getRules()
        );
    }

    @GetMapping("/batch")
    public Map<String, Object> batchTest(@RequestParam(defaultValue = "batch-requests.txt") String file) {
        List<Map<String, Object>> results = new ArrayList<>();
        int total = 0, success = 0;
        try {
            ClassPathResource resource = new ClassPathResource(file);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    total++;
                    try {
                        RouteRequest req = mapper.readValue(line, RouteRequest.class);
                        RouteResult r = routeService.findShortestRoute(
                                req.getGrids(), req.getStartPoint(), req.getEndPoint(),
                                req.getMidPoints(), req.getNoFlyZones(),
                                req.getRouteWidth(), req.getRouteHeight(), req.getRules());
                        if (r.isSuccess()) success++;
                        results.add(Map.of("line", total, "success", r.isSuccess(), "message", r.getMessage()));
                    } catch (Exception e) {
                        results.add(Map.of("line", total, "success", false, "message", "parse/exec error: " + e.getMessage()));
                    }
                }
            }
        } catch (IOException e) {
            return Map.of("error", "文件读取失败: " + e.getMessage());
        }
        return Map.of("total", total, "success", success, "results", results);
    }
}

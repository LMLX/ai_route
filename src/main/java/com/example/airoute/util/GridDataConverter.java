package com.example.airoute.util;

import com.example.airoute.model.GeoPoint;
import com.example.airoute.model.Grid;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jol.info.GraphLayout;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 网格数据转换器
 * <p>输入: dim_grid_data (CSV，逗号分隔，带双引号)</p>
 * <p>输出: dim_grid_data_a (0x01 分隔)</p>
 *
 * <p>字段: grid_id, center_point(lon,lat), grid_height(顶高?) - 50 → 中心高度</p>
 */
public class GridDataConverter {

    private static final char SEPARATOR = 0x01; // SOH 分隔符


    /**
     * 写为 0x01 分隔文件: grid_id ^A lon,lat,alt
     * <p>使用 BigDecimal.toPlainString() 保留完整精度，避免 double 默认格式化丢尾数</p>
     */
    public static void write(List<Grid> grids, String outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), StandardCharsets.UTF_8)) {
            for (Grid g : grids) {
                GeoPoint cp = g.getCenterPoint();
                writer.write(g.getId() + SEPARATOR
                        + cp.getLongitude() + "," + cp.getLatitude() + "," + cp.getAltitude());
                writer.newLine();
            }
        }
        System.out.println("Wrote " + grids.size() + " grids → " + outputPath);
    }

    /**
     * 写为 0x01 分隔文件，使用 BigDecimal 保证不丢精度。
     * 当坐标有 15+ 位有效数字时建议用此方法。
     */
    public static void writePrecise(List<Grid> grids, String outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), StandardCharsets.UTF_8)) {
            for (Grid g : grids) {
                GeoPoint cp = g.getCenterPoint();
                writer.write(g.getId() + SEPARATOR
                        + new java.math.BigDecimal(cp.getLongitude()).toPlainString() + ","
                        + new java.math.BigDecimal(cp.getLatitude()).toPlainString() + ","
                        + new java.math.BigDecimal(cp.getAltitude()).toPlainString());
                writer.newLine();
            }
        }
        System.out.println("Wrote " + grids.size() + " grids (precise) → " + outputPath);
    }


    // ---- main ----

    public static void main(String[] args) throws IOException {

        long t1 = System.currentTimeMillis();
        String inputPath = "/Users/jinjiahao/IdeaProjects/ai-route/dim_grid_data";
        List<Grid> grids = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(String.valueOf(SEPARATOR));
                String gridId = parts[0];
                String centerPointStr = parts[1];
                // 数据格式: [longitude,latitude,altitude]
                String[] centerPointArray = centerPointStr.substring(1, centerPointStr.length() - 1).split(",");
                Grid grid = new Grid();
                grid.setId(gridId);
                GeoPoint geoPoint = new GeoPoint();
                geoPoint.setLongitude(Double.parseDouble(centerPointArray[0]));
                geoPoint.setLatitude(Double.parseDouble(centerPointArray[1]));
                geoPoint.setAltitude(Double.parseDouble(centerPointArray[2]));
                grid.setCenterPoint(geoPoint);
                grids.add(grid);
            }
        }
        // 浅大小：ArrayList 对象本身 + 底层数组引用的“槽位”，但不包含数组元素指向的 Integer 对象

        // 深大小：包括所有 Integer 对象以及底层数组的全部内容
        long deepSize = GraphLayout.parseInstance(grids).totalSize();
        System.out.println("Deep size: " + deepSize + " bytes");
        long t2 = System.currentTimeMillis();
        System.out.println(t2 - t1);
    }
}
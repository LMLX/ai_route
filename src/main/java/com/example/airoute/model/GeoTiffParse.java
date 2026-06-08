package com.example.airoute.model;

import lombok.extern.slf4j.Slf4j;
import org.geotools.api.coverage.grid.GridEnvelope;
import org.geotools.api.geometry.Bounds;
import org.geotools.api.geometry.Position;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.Position2D;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author hanburger
 * Date: 2026/5/9 16:54
 * Describe：
 */
@Slf4j
public class GeoTiffParse {
    public static double[] getPixelValueByLatLon(File tifFile, double longitude, double latitude)
            throws IOException, TransformException {
        if (!tifFile.exists()) {
            throw new IOException("文件不存在: " + tifFile.getName());
        }

        GeoTiffReader reader = null;
        GridCoverage2D coverage = null;

        try {
            reader = new GeoTiffReader(tifFile);
            coverage = reader.read(null);

            // 获取TIF文件的坐标系统
            CoordinateReferenceSystem fileCrs = coverage.getCoordinateReferenceSystem();

            // 创建输入坐标（WGS84经纬度）
            Position2D wgs84Coord = new Position2D(longitude, latitude);

            // 如果文件使用的是WGS84坐标系，直接使用
            String crsCode = fileCrs.getIdentifiers().toString();
            boolean isWgs84 = crsCode.contains("4326") || crsCode.contains("WGS84");
            Position2D targetCoord;
            if (isWgs84) {
                targetCoord = wgs84Coord;
            } else {
                // 需要坐标转换：从WGS84转换到文件的坐标系统
                MathTransform transform = org.geotools.referencing.CRS.findMathTransform(
                        org.geotools.referencing.crs.DefaultGeographicCRS.WGS84,
                        fileCrs,
                        true
                );
                targetCoord = new Position2D();
                transform.transform(wgs84Coord, targetCoord);
            }

            // 检查坐标是否在文件范围内
            if (!isCoordinateInCoverage(coverage, targetCoord)) {
                log.warn("警告: 坐标 ({}, {}) 超出TIF文件范围", longitude, latitude);
                return null;
            }

            // 获取像素值
            int bands = coverage.getNumSampleDimensions();
            double[] dest = new double[bands];
            double[] values = coverage.evaluate((Position) targetCoord, dest);
            return values;

        } catch (FactoryException e) {
            throw new RuntimeException(e);
        } finally {
            if (coverage != null) {
                coverage.dispose(true);
            }
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    public static double[] getPixelValueByLatLon(GridCoverage2D coverage, double longitude, double latitude)
            throws IOException, TransformException {
        try {
            // 获取TIF文件的坐标系统
            CoordinateReferenceSystem fileCrs = coverage.getCoordinateReferenceSystem();

            // 创建输入坐标（WGS84经纬度）
            Position2D wgs84Coord = new Position2D(longitude, latitude);

            // 如果文件使用的是WGS84坐标系，直接使用
            String crsCode = fileCrs.getIdentifiers().toString();
            boolean isWgs84 = crsCode.contains("4326") || crsCode.contains("WGS84");
            Position2D targetCoord;
            if (isWgs84) {
                targetCoord = wgs84Coord;
            } else {
                // 需要坐标转换：从WGS84转换到文件的坐标系统
                MathTransform transform = org.geotools.referencing.CRS.findMathTransform(
                        org.geotools.referencing.crs.DefaultGeographicCRS.WGS84,
                        fileCrs,
                        true
                );
                targetCoord = new Position2D();
                transform.transform(wgs84Coord, targetCoord);
            }

            // 检查坐标是否在文件范围内
            if (!isCoordinateInCoverage(coverage, targetCoord)) {
                log.warn("警告: 坐标 ({}, {}) 超出TIF文件范围", longitude, latitude);
                return null;
            }

            // 获取像素值
            int bands = coverage.getNumSampleDimensions();
            double[] dest = new double[bands];
            double[] values = coverage.evaluate((Position) targetCoord, dest);
            return values;

        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 通过经纬度批量获取像素值
     *
     * @param tifFile TIF文件
     * @param coordinates 坐标数组，每个元素为[经度, 纬度]
     * @return 像素值数组，与输入坐标一一对应
     * @throws IOException 读取文件异常
     * @throws TransformException 坐标转换异常
     */
    public static double[][] getPixelValuesByLatLons(File tifFile, double[][] coordinates)
            throws IOException, TransformException {

        if (!tifFile.exists()) {
            throw new IOException("文件不存在: " + tifFile.getName());
        }

        GeoTiffReader reader = null;
        GridCoverage2D coverage = null;

        try {
            reader = new GeoTiffReader(tifFile);
            coverage = reader.read(null);

            CoordinateReferenceSystem fileCrs = coverage.getCoordinateReferenceSystem();
            String crsCode = fileCrs.getIdentifiers().toString();
            boolean isWgs84 = crsCode.contains("4326") || crsCode.contains("WGS84");

            MathTransform transform = null;
            if (!isWgs84) {
                transform = org.geotools.referencing.CRS.findMathTransform(
                        org.geotools.referencing.crs.DefaultGeographicCRS.WGS84,
                        fileCrs,
                        true
                );
            }

            double[][] results = new double[coordinates.length][];

            for (int i = 0; i < coordinates.length; i++) {
                double lon = coordinates[i][0];
                double lat = coordinates[i][1];
                Position2D wgs84Coord = new Position2D(lon, lat);
                Position2D targetCoord;
                if (isWgs84) {
                    targetCoord = wgs84Coord;
                } else {
                    targetCoord = new Position2D();
                    transform.transform(wgs84Coord, targetCoord);
                }
                if (isCoordinateInCoverage(coverage, targetCoord)) {
                    int bands = coverage.getNumSampleDimensions();
                    double[] dest = new double[bands];
                    results[i] = coverage.evaluate((Position) targetCoord, dest);
                } else {
                    results[i] = null;
                }
            }
            return results;
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        } finally {
            if (coverage != null) {
                coverage.dispose(true);
            }
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    /**
     * 解析TIF文件基本信息
     *
     * @param tifFile TIF文件
     * @return TIF文件信息
     * @throws IOException 读取文件异常
     */
    public static Map<String, Object> parseGeoTiffInfo(File tifFile) throws IOException {
        if (!tifFile.exists()) {
            throw new IOException("文件不存在: " + tifFile.getName());
        }

        GeoTiffReader reader = null;
        GridCoverage2D coverage = null;

        try {
            reader = new GeoTiffReader(tifFile);
            coverage = reader.read(null);

            Map<String, Object> result = new HashMap<>();

            result.put("fileName", tifFile.getName());
            result.put("filePath", tifFile.getPath());

            CoordinateReferenceSystem crs = coverage.getCoordinateReferenceSystem();
            result.put("crs", crs != null ? crs.toWKT() : "Unknown");
            result.put("crsName", crs != null ? crs.getName().toString() : "Unknown");
            result.put("isWgs84", crs != null && crs.getIdentifiers().toString().contains("4326"));

            Bounds envelope = coverage.getEnvelope();
            result.put("minX", envelope.getMinimum(0));
            result.put("maxX", envelope.getMaximum(0));
            result.put("minY", envelope.getMinimum(1));
            result.put("maxY", envelope.getMaximum(1));

            GridEnvelope gridRange = coverage.getGridGeometry().getGridRange();
            int width = gridRange.getSpan(0);
            int height = gridRange.getSpan(1);
            result.put("width", width);
            result.put("height", height);
            result.put("numBands", coverage.getNumSampleDimensions());

            return result;

        } finally {
            if (coverage != null) {
                coverage.dispose(true);
            }
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    /**
     * 检查坐标是否在覆盖范围内
     */
    public static boolean isCoordinateInCoverage(GridCoverage2D coverage, Position2D coord) {
        try {
            Bounds envelope = coverage.getEnvelope();
            double minX = envelope.getMinimum(0);
            double maxX = envelope.getMaximum(0);
            double minY = envelope.getMinimum(1);
            double maxY = envelope.getMaximum(1);

            return coord.x >= minX && coord.x <= maxX &&
                    coord.y >= minY && coord.y <= maxY;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 打印TIF文件信息
     */
    public static void printTiffInfo(Map<String, Object> info) {
        System.out.println("=== TIF文件信息 ===");
        System.out.println("文件名: " + info.get("fileName"));
        System.out.println("坐标系统: " + info.get("crsName"));
        System.out.println("是否WGS84: " + info.get("isWgs84"));
        System.out.println("边界范围: X[" + info.get("minX") + ", " + info.get("maxX") + "]");
        System.out.println("边界范围: Y[" + info.get("minY") + ", " + info.get("maxY") + "]");
        System.out.println("图像尺寸: " + info.get("width") + " x " + info.get("height"));
        System.out.println("波段数: " + info.get("numBands"));
        System.out.println("==================");
    }
}

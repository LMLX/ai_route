package com.example.airoute.model;

import com.example.airoute.config.GridCodeConfig;
import com.example.airoute.util.GridCodeUtil;
import org.geotools.api.referencing.operation.TransformException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author hanburger
 * Date: 2026/6/4 16:16
 * Describe：
 */
public class GeneratorDemo {
    public static void main(String[] args) throws TransformException, IOException {
//        GridCodeConfig config = new GridCodeConfig(
//                1,
//                118.304172,
//                29.150848,
//                0.0,
//                120.759837,
//                30.612884,
//                600,
//                100,
//                100,
//                100,
//                100
//        );
//        // 创建一个网格编码工具
//        GridCodeUtil gridCodeUtil = new GridCodeUtil(config);
//        // 生成所有网格码
//        List<String> allGridCodes = gridCodeUtil.getAllGridCodes();
//        for (String gridCode : allGridCodes) {
//
//
//        }
//    }
//        Map<String, double[]> points = gridCodeUtil.getGridBoundaryPoints(gridCode);
//        double[] centerPoint = points.get("center_point");
        double longitude = 118.3046900292;
        double latitude = 29.153093798111733;
        double altitude = 100;
        double[] value = GeoTiffParse.getPixelValueByLatLon(new File("/Users/jinjiahao/IdeaProjects/ai-route/geo.tif"),longitude,latitude);
        double height = value[0];
        if (height > altitude) {
            System.out.println("这个数据就是山体数据，不需要");
        }else {
            System.out.println("这个数据不是山体数据，需要");
        }
    }
}

package com.example.airoute.model;

/**
 * @author hanburger
 * Date: 2026/6/4 16:16
 * Describe：
 */
public class GeneratorDemo1 {
    public static void main(String[] args) throws Exception {


//        String outPath = "/Users/jinjiahao/IdeaProjects/ai-route/dim_grid_data_100*100*100";
//        try (
//             BufferedWriter writer = Files.newBufferedWriter(Paths.get(outPath), StandardCharsets.UTF_8)
//        ) {
//
//            GridCodeConfig config = new GridCodeConfig(
//                    1,
//                    118.304172,
//                    29.150848,
//                    0.0,
//                    120.759837,
//                    30.612884,
//                    600,
//                    100,
//                    100,
//                    100,
//                    100
//            );
//            // 创建一个网格编码工具
//            GridCodeUtil gridCodeUtil = new GridCodeUtil(config);
//            GeoTiffReader reader = new GeoTiffReader(new File("/Users/jinjiahao/IdeaProjects/ai-route/geo.tif"));
//            GridCoverage2D coverage = reader.read(null);
//            // 获取TIF文件的坐标系统
//            CoordinateReferenceSystem fileCrs = coverage.getCoordinateReferenceSystem();
//            // 生成所有网格码
//            List<String> allGridCodes = gridCodeUtil.getAllGridCodes();
//            int i= 0;
//            for (String gridCode : allGridCodes) {
//                Map<String, double[]> points = gridCodeUtil.getGridBoundaryPoints(gridCode);
//                double[] centerPoint = points.get("center_point");
//                double longitude = centerPoint[0];
//                double latitude = centerPoint[1];
//                double altitude = centerPoint[2];
//
//
//                // 创建输入坐标（WGS84经纬度）
//                Position2D wgs84Coord = new Position2D(longitude, latitude);
//
//                // 如果文件使用的是WGS84坐标系，直接使用
//                String crsCode = fileCrs.getIdentifiers().toString();
//                boolean isWgs84 = crsCode.contains("4326") || crsCode.contains("WGS84");
//                Position2D targetCoord;
//                if (isWgs84) {
//                    targetCoord = wgs84Coord;
//                } else {
//                    // 需要坐标转换：从WGS84转换到文件的坐标系统
//                    MathTransform transform = org.geotools.referencing.CRS.findMathTransform(
//                            org.geotools.referencing.crs.DefaultGeographicCRS.WGS84,
//                            fileCrs,
//                            true
//                    );
//                    targetCoord = new Position2D();
//                    transform.transform(wgs84Coord, targetCoord);
//                }
//
//                // 获取像素值
//                int bands = coverage.getNumSampleDimensions();
//                double[] dest = new double[bands];
//                double[] values = coverage.evaluate((Position) targetCoord, dest);
//
//                double height = values[0];
//                if (height > altitude) {
//                }else {
//                    writer.write(longitude+","+latitude+","+altitude);
//                    writer.newLine();
//                     i++;
//                }
//            }
//            writer.flush();
//            if (coverage != null) {
//                coverage.dispose(true);
//            }
//            if (reader != null) {
//                reader.dispose();
//            }
//            System.out.println(i);
//        }





    }
}

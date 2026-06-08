package com.example.airoute.util;

import com.example.airoute.config.GridCodeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网格管理工具类
 * 网格划分：一级网格默认220m×220m×50m，二级网格默认5m×5m×5m
 * 编码格式：i-j-k-xxx-yyy-zzz
 * @author 11064
 */
public class GridCodeUtil {

    /**
     * 网格配置
     */
    private final GridCodeConfig config;

    /**
     * 一级网格包含的二级网格数
     */
    private int subGridCountX;
    private int subGridCountY;
    private int subGridCountZ;

    /**
     * 构造函数，通过配置对象初始化
     */
    public GridCodeUtil(GridCodeConfig config) {
        this.config = config;
        this.init();
    }


    private void init() {
        subGridCountX = config.getFirstLevelX() / config.getSecondLevelSize();
        subGridCountY = config.getFirstLevelY() / config.getSecondLevelSize();
        subGridCountZ = config.getFirstLevelZ() / config.getSecondLevelSize();
    }


    public void printConfig(){
        System.out.println(config);
    }
    /**
     * 经纬高转网格码
     * @param longitude 经度
     * @param latitude  纬度
     * @param altitude  高度
     * @return 网格码字符串（格式：i-j-k-xxx-yyy-zzz）
     * @throws IllegalArgumentException 当输入参数超出范围时抛出
     */
    public String convertPointToGridCode(double longitude, double latitude, double altitude) {
        // 校验输入范围
        validateLonLatH(longitude, latitude, altitude);

        // 计算相对于基点的偏移量（单位：米）
        double[] offsets = calculateOffsets(longitude, latitude, altitude);
        double offsetX = offsets[0];
        double offsetY = offsets[1];
        double offsetZ = offsets[2];
        // 计算一级网格索引
        int i = computeIndex(offsetX, config.getFirstLevelX());
        int j = computeIndex(offsetY, config.getFirstLevelY());
        int k = computeIndex(offsetZ, config.getFirstLevelZ());
        // 计算二级网格索引（向下取整）
        int xIdx = computeIndex(offsetX, config.getSecondLevelSize());
        int yIdx = computeIndex(offsetY, config.getSecondLevelSize());
        int zIdx = computeIndex(offsetZ, config.getSecondLevelSize());
        // 格式化拼接
        return String.format("%d-%d-%d-%d-%d-%d", i, j, k, xIdx, yIdx, zIdx);
    }

    /**
     * @param offset 相对于基点的偏移量（单位：米）
     * @param length 标准长度（单位：米）
     * @return index 返回网格索引
     */
    private static int computeIndex(double offset, double length ) {
        int roundOffset = (int)Math.round(offset);
        int index = (int) Math.floor(roundOffset / length);
        //计算到最边缘时,计算出来的编码需要进行减1
        if (roundOffset != 0 && (int) Math.floor(roundOffset % length) == 0) {
            index =  index - 1 ;
        }
        return index;
    }


    /**
     * 网格码转中心点、基点、极点经纬高
     * @param gridCode 网格码（格式：i-j-k-xxx-yyy-zzz）
     * @return 中心点坐标数组 [经度, 纬度, 高度]
     * @throws IllegalArgumentException 当网格码格式错误时抛出
     */
    public Map<String, double[]> getGridBoundaryPoints(String gridCode) {
        // 解析网格码
        String[] parts = gridCode.split("-");
        if (parts.length != 6) {
            throw new IllegalArgumentException("无效网格码格式，需满足：i-j-k-xxx-yyy-zzz");
        }

        int i = Integer.parseInt(parts[0]);
        int j = Integer.parseInt(parts[1]);
        int k = Integer.parseInt(parts[2]);
        int xIdx = Integer.parseInt(parts[3]);
        int yIdx = Integer.parseInt(parts[4]);
        int zIdx = Integer.parseInt(parts[5]);

        // 计算中心点相对于基点的偏移量
        double centerOffsetX = xIdx * config.getSecondLevelSize() + config.getSecondLevelSize() / 2.0;
        double centerOffsetY = yIdx * config.getSecondLevelSize() + config.getSecondLevelSize() / 2.0;
        double centerOffsetZ = zIdx * config.getSecondLevelSize() + config.getSecondLevelSize() / 2.0;

        // 偏移量转换为经纬高（基于基点叠加）
        double centerLon = config.getBaseLon() + metersToLon(centerOffsetX, config.getBaseLat());
        double centerLat = config.getBaseLat() + metersToLat(centerOffsetY);
        double centerH = config.getBaseH() + centerOffsetZ;

        // 计算当前网格基点相对于全局基点的偏移量
        double xStartOffset = xIdx  * config.getSecondLevelSize();
        double yStartOffset = yIdx  * config.getSecondLevelSize();
        double zStartOffset = zIdx  * config.getSecondLevelSize();

        // 计算当前网格极点点相对于全局基点的偏移量
        double xEndOffset = (xIdx + 1)  * config.getSecondLevelSize();
        double yEndOffset = (yIdx + 1)  * config.getSecondLevelSize();
        double zEndOffset = (zIdx + 1)  * config.getSecondLevelSize();

        // 转换当前网格基点偏移量为经纬度（基于全局基点）
        double gridBaseLon = config.getBaseLon() + metersToLon(xStartOffset, config.getBaseLat());
        double gridBaseLat = config.getBaseLat() + metersToLat(yStartOffset);
        double gridBaseH = config.getBaseH() + zStartOffset;

        // 转换当前网格极点偏移量为经纬度（基于全局基点）
        double maxLon = config.getBaseLon() + metersToLon(xEndOffset, config.getBaseLat());
        double maxLat = config.getBaseLat() + metersToLat(yEndOffset);
        double maxH = config.getBaseH() + zEndOffset;

        // 封装结果
        Map<String, double[]> result = new HashMap<>(3);
        result.put("base_point", new double[]{gridBaseLon, gridBaseLat, gridBaseH});
        result.put("max_point", new double[]{maxLon, maxLat, maxH});
        result.put("center_point", new double[]{centerLon, centerLat, centerH});

        return result;
    }

    /**
     * 获取所有一级网格码（数量较少，不会OOM）
     * @return 一级网格码列表
     */
    public List<String> getAllFirstLevelCodes() {
        List<String> firstLevelCodes = new ArrayList<>();

        // 计算相对于基点的总偏移量（单位：米）
        double[] offsets = calculateOffsets(config.getMaxLon(), config.getMaxLat(), config.getMaxH());
        double totalOffsetX = offsets[0];
        double totalOffsetY = offsets[1];
        double totalOffsetZ = offsets[2];

        // 计算一级网格的数量
        int firstLevelCountX = (int) Math.ceil(totalOffsetX / config.getFirstLevelX());
        int firstLevelCountY = (int) Math.ceil(totalOffsetY / config.getFirstLevelY());
        int firstLevelCountZ = (int) Math.ceil(totalOffsetZ / config.getFirstLevelZ());

        // 遍历所有一级网格
        for (int i = 0; i < firstLevelCountX; i++) {
            for (int j = 0; j < firstLevelCountY; j++) {
                for (int k = 0; k < firstLevelCountZ; k++) {
                    firstLevelCodes.add(String.format("%d-%d-%d", i, j, k));
                }
            }
        }

        return firstLevelCodes;
    }

    /**
     * 获取指定一级网格内的所有二级网格码
     * @param firstLevelCode 一级网格码（格式：i-j-k）
     * @return 该一级网格下的所有二级网格码列表
     * @throws IllegalArgumentException 一级网格码格式错误时抛出
     */
    public List<String> getSecondLevelGridCodes(String firstLevelCode) {
        List<String> secondLevelCodes = new ArrayList<>();

        // 解析一级网格码
        String[] parts = firstLevelCode.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("一级网格码格式错误，需为i-j-k");
        }

        int i, j, k;
        try {
            i = Integer.parseInt(parts[0]);
            j = Integer.parseInt(parts[1]);
            k = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("一级网格码索引必须为整数");
        }

        // 获取该一级网格的边界网格码
        Map<String, String> boundaryCodes = getGridBoundaryCodes(String.format("%d-%d-%d", i, j, k));
        String minCode = boundaryCodes.get("minGridCode");
        String maxCode = boundaryCodes.get("maxGridCode");

        // 解析最小和最大网格码
        String[] minParts = minCode.split("-");
        String[] maxParts = maxCode.split("-");

        int minXIdx = Integer.parseInt(minParts[3]);
        int minYIdx = Integer.parseInt(minParts[4]);
        int minZIdx = Integer.parseInt(minParts[5]);

        int maxXIdx = Integer.parseInt(maxParts[3]);
        int maxYIdx = Integer.parseInt(maxParts[4]);
        int maxZIdx = Integer.parseInt(maxParts[5]);

        // 遍历所有二级网格
        for (int xIdx = minXIdx; xIdx <= maxXIdx; xIdx++) {
            for (int yIdx = minYIdx; yIdx <= maxYIdx; yIdx++) {
                for (int zIdx = minZIdx; zIdx <= maxZIdx; zIdx++) {
                    String gridCode = String.format("%d-%d-%d-%d-%d-%d", i, j, k, xIdx, yIdx, zIdx);
                    secondLevelCodes.add(gridCode);
                }
            }
        }

        return secondLevelCodes;
    }



    /**
     * 根据一级网格码计算该网格的中心点、基点、极点坐标
     *
     * @param firstLevelCode 一级网格码，格式为i-j-k
     * @return 包含基点(base_point)、极点(max_point)、中心点(center_point)的Map
     */
    public Map<String, double[]> getFirstLevelGridBoundaryPoints(String firstLevelCode) {
        // 解析一级网格码
        String[] parts = firstLevelCode.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("一级网格码格式错误，需为i-j-k");
        }

        int i, j, k;
        try {
            i = Integer.parseInt(parts[0]);
            j = Integer.parseInt(parts[1]);
            k = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("一级网格码索引必须为整数");
        }

        if (i < 0 || j < 0 || k < 0) {
            throw new IllegalArgumentException("一级网格码索引不能为负数");
        }

        // 计算一级网格在X/Y/Z方向的起始和结束偏移（米）
        double xStartOffset = i * config.getFirstLevelX();
        double xEndOffset = (i + 1) * config.getFirstLevelX();
        double yStartOffset = j * config.getFirstLevelY();
        double yEndOffset = (j + 1) * config.getFirstLevelY();
        double zStartOffset = k * config.getFirstLevelZ();
        double zEndOffset = (k + 1) * config.getFirstLevelZ();

        // 计算中心点偏移
        double centerXOffset = xStartOffset + config.getFirstLevelX() / 2.0;
        double centerYOffset = yStartOffset + config.getFirstLevelY() / 2.0;
        double centerZOffset = zStartOffset + config.getFirstLevelZ() / 2.0;

        // 转换基点偏移量为经纬度（基于全局基点）
        double baseLon = config.getBaseLon() + metersToLon(xStartOffset, config.getBaseLat());
        double baseLat = config.getBaseLat() + metersToLat(yStartOffset);
        double baseH = config.getBaseH() + zStartOffset;

        // 转换极点偏移量为经纬度（基于全局基点）
        double maxLon = config.getBaseLon() + metersToLon(xEndOffset, config.getBaseLat());
        double maxLat = config.getBaseLat() + metersToLat(yEndOffset);
        double maxH = config.getBaseH() + zEndOffset;

        // 转换中心点偏移量为经纬度（基于全局基点）
        double centerLon = config.getBaseLon() + metersToLon(centerXOffset, config.getBaseLat());
        double centerLat = config.getBaseLat() + metersToLat(centerYOffset);
        double centerH = config.getBaseH() + centerZOffset;

        // 封装结果
        Map<String, double[]> result = new HashMap<>(3);
        result.put("base_point", new double[]{baseLon, baseLat, baseH});
        result.put("max_point", new double[]{maxLon, maxLat, maxH});
        result.put("center_point", new double[]{centerLon, centerLat, centerH});

        return result;
    }


    /**
     * 后三位网格码补全完整网格码
     *
     * @param gridCode 后三位网格码
     * @return 完整网格码
     */
    public String completeGridCode(String gridCode) {
        String[] array = gridCode.split("-");
        int xIdx = Integer.parseInt(array[0]);
        int yIdx = Integer.parseInt(array[1]);
        int zIdx = Integer.parseInt(array[2]);
        // 计算一级索引
        int finalI = xIdx / this.subGridCountX;
        int finalJ = yIdx / this.subGridCountY;
        int finalK = zIdx / this.subGridCountZ;

        // 格式化拼接
        return String.format("%d-%d-%d-%d-%d-%d", finalI, finalJ, finalK, xIdx, yIdx, zIdx);
    }

    /**
     * 获取基点和顶点的网格码
     * @param firstLevelCode 一级网格码（格式i-j-k，为null时返回完整网格码）
     * @return 包含基点和顶点网格码的Map
     * @throws IllegalArgumentException 一级网格码格式错误时抛出
     */
    public Map<String, String> getGridBoundaryCodes(String firstLevelCode) {
        Map<String, String> result = new HashMap<>(2);
        // 解析一级网格码
        String[] parts = firstLevelCode.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("一级网格码格式错误，需为i-j-k");
        }
        int i, j, k;
        try {
            i = Integer.parseInt(parts[0]);
            j = Integer.parseInt(parts[1]);
            k = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("一级网格码索引必须为整数");
        }
        //计算2级网格编码
        int minIdx = (int)(i * config.getFirstLevelX() / config.getSecondLevelSize());
        int minIdy = (int)(j * config.getFirstLevelY() / config.getSecondLevelSize());
        int minIdz = (int)(k * config.getFirstLevelZ() / config.getSecondLevelSize());
        int maxIdx = (int)((i+1) * config.getFirstLevelX() / config.getSecondLevelSize())-1;
        int maxIdy = (int)((j+1) * config.getFirstLevelY() / config.getSecondLevelSize())-1;
        int maxIdz = (int)((k+1) * config.getFirstLevelZ() / config.getSecondLevelSize())-1;
        result.put("minGridCode", String.format("%d-%d-%d-%d-%d-%d", i, j, k, minIdx, minIdy, minIdz));
        result.put("maxGridCode", String.format("%d-%d-%d-%d-%d-%d", i, j, k, maxIdx, maxIdy, maxIdz));
        return result;
    }
    /**
     * 无入参，返回整个区域内的额基点和顶点网格码
     * @return 包含基点和顶点网格码的Map
     * @throws IllegalArgumentException 一级网格码格式错误时抛出
     */
    public Map<String, String> getGridBoundaryCodes() {
        Map<String, String> result = new HashMap<>(2);
        //默认使用基点和极点经纬度
        // 处理基点（即长方体左下角顶点：[baseLon, baseLat, baseH]）
        String minGridCode = convertPointToGridCode(config.getBaseLon(), config.getBaseLat(), config.getBaseH());
        String maxGridCode = convertPointToGridCode(config.getMaxLon(), config.getMaxLat(), config.getMaxH());
        result.put("minGridCode", minGridCode);
        result.put("maxGridCode", maxGridCode);
        return result;
    }

    public List<String> getAllGridCodes() {
        List<String> allGridCodes = new ArrayList<>();

        // 计算相对于基点的总偏移量（单位：米）
        double[] offsets = calculateOffsets(config.getMaxLon(), config.getMaxLat(), config.getMaxH());
        double totalOffsetX = offsets[0];
        double totalOffsetY = offsets[1];
        double totalOffsetZ = offsets[2];

        // 计算一级网格的数量
        int firstLevelCountX = (int) Math.ceil(totalOffsetX / config.getFirstLevelX());
        int firstLevelCountY = (int) Math.ceil(totalOffsetY / config.getFirstLevelY());
        int firstLevelCountZ = (int) Math.ceil(totalOffsetZ / config.getFirstLevelZ());

        // 遍历所有一级网格
        for (int i = 0; i < firstLevelCountX; i++) {
            for (int j = 0; j < firstLevelCountY; j++) {
                for (int k = 0; k < firstLevelCountZ; k++) {
                    // 获取该一级网格下的最小和最大二级网格索引
                    Map<String, String> boundaryCodes = getGridBoundaryCodes(String.format("%d-%d-%d", i, j, k));
                    String minCode = boundaryCodes.get("minGridCode");
                    String maxCode = boundaryCodes.get("maxGridCode");

                    // 解析最小和最大网格码
                    String[] minParts = minCode.split("-");
                    String[] maxParts = maxCode.split("-");

                    int minXIdx = Integer.parseInt(minParts[3]);
                    int minYIdx = Integer.parseInt(minParts[4]);
                    int minZIdx = Integer.parseInt(minParts[5]);

                    int maxXIdx = Integer.parseInt(maxParts[3]);
                    int maxYIdx = Integer.parseInt(maxParts[4]);
                    int maxZIdx = Integer.parseInt(maxParts[5]);

                    // 遍历该一级网格下的所有二级网格
                    for (int xIdx = minXIdx; xIdx <= maxXIdx; xIdx++) {
                        for (int yIdx = minYIdx; yIdx <= maxYIdx; yIdx++) {
                            for (int zIdx = minZIdx; zIdx <= maxZIdx; zIdx++) {
                                String gridCode = String.format("%d-%d-%d-%d-%d-%d", i, j, k, xIdx, yIdx, zIdx);
                                allGridCodes.add(gridCode);
                            }
                        }
                    }
                }
            }
        }

        return allGridCodes;
    }



    // ------------------------------ 辅助方法 ------------------------------

    /**
     * 校验经纬度和高度是否在范围内
     */
    private void validateLonLatH(double lon, double lat, double h) {
        if (lon < config.getBaseLon() || lon > config.getMaxLon()) {
            throw new IllegalArgumentException("经度超出范围: " + lon + " 应在 [" + config.getBaseLon() + ", " + config.getMaxLon() + "] 之间");
        }
        if (lat < config.getBaseLat() || lat > config.getMaxLat()) {
            throw new IllegalArgumentException("纬度超出范围: " + lat + " 应在 [" + config.getBaseLat() + ", " + config.getMaxLat() + "] 之间");
        }
        if (h < config.getBaseH() || h > config.getMaxH()) {
            throw new IllegalArgumentException("高度超出范围: " + h + " 应在 [" + config.getBaseH() + ", " + config.getMaxH() + "] 之间");
        }
    }

    /**
     * 计算相对于基点的偏移量（米）
     * 注：经纬度转米的近似计算（基于WGS84椭球，简化为局部平面）
     */
    private double[] calculateOffsets(double lon, double lat, double h) {
        // 纬度每度对应的米数（约111319m）
        double latPerMeter = 1.0 / 111319.0;
        // 经度每度对应的米数（随纬度变化，北纬30°约为111319×cos(30°)）
        double lonPerMeter = 1.0 / (111319.0 * Math.cos(Math.toRadians(config.getBaseLat())));

        // 计算经纬度偏移（米）
        // X方向（经度）偏移
        double offsetX = (lon - config.getBaseLon()) / lonPerMeter;
        // Y方向（纬度）偏移
        double offsetY = (lat - config.getBaseLat()) / latPerMeter;
        // Z方向（高度）偏移
        double offsetZ = h - config.getBaseH();

        return new double[]{offsetX, offsetY, offsetZ};
    }

    /**
     * 米转换为经度（基于基点纬度）
     */
    private static double metersToLon(double meters, double baseLat) {
        double lonPerMeter = 1.0 / (111319.0 * Math.cos(Math.toRadians(baseLat)));
        return meters * lonPerMeter;
    }

    /**
     * 米转换为纬度
     */
    private static double metersToLat(double meters) {
        double latPerMeter = 1.0 / 111319.0;
        return meters * latPerMeter;
    }

    /**
     * 根据一级网格码（i-j-k）计算该网格的基点和极点坐标
     *
     * @param firstLevelCode 一级网格码，格式为i-j-k
     * @return 包含基点（base_point）和极点（max_point）的Map，坐标格式为[经度, 纬度, 高度]
     * @throws IllegalArgumentException 网格码格式错误或索引为负时抛出
     */
    private Map<String, double[]> firstLevelCodeToCoordinates(String firstLevelCode) {
        // 解析一级网格码
        String[] parts = firstLevelCode.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("一级网格码格式错误，需为i-j-k");
        }

        int i, j, k;
        try {
            i = Integer.parseInt(parts[0]);
            j = Integer.parseInt(parts[1]);
            k = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("一级网格码索引必须为整数");
        }

        if (i < 0 || j < 0 || k < 0) {
            throw new IllegalArgumentException("一级网格码索引不能为负数");
        }

        // 计算一级网格在X/Y/Z方向的起始和结束偏移（米）
        double xStartOffset = i * config.getFirstLevelX();
        double xEndOffset = (i + 1) * config.getFirstLevelX();
        double yStartOffset = j * config.getFirstLevelY();
        double yEndOffset = (j + 1) * config.getFirstLevelY();
        double zStartOffset = k * config.getFirstLevelZ();
        double zEndOffset = (k + 1) * config.getFirstLevelZ();

        // 转换偏移量为经纬度（基于全局基点）
        double baseLon = config.getBaseLon() + metersToLon(xStartOffset, config.getBaseLat());
        double baseLat = config.getBaseLat() + metersToLat(yStartOffset);
        double baseH = config.getBaseH() + zStartOffset;

        double maxLon = config.getBaseLon() + metersToLon(xEndOffset, config.getBaseLat());
        double maxLat = config.getBaseLat() + metersToLat(yEndOffset);
        double maxH = config.getBaseH() + zEndOffset;

        // 封装结果
        Map<String, double[]> result = new HashMap<>(2);
        result.put("base_point", new double[]{baseLon, baseLat, baseH});
        result.put("max_point", new double[]{maxLon, maxLat, maxH});
        return result;
    }
}

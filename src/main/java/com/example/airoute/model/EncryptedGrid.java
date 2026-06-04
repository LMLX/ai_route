package com.example.airoute.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加密网格 —— 只有 {@code long bits}（8 字节），可逆编码。
 *
 * <pre>64-bit layout:
 * bits 47-63: i (17b)      bits 24-26: F0 (3b)
 * bits 32-46: j (15b)      bits 21-23: F1 (3b)
 * bits 27-31: k (5b)       ... 共 9 个因素 ...
 *                          bits 0-2:   F8 (3b)
 * </pre>
 *
 * <p>因素名不硬编码，由外部 {@code FactorSchema} 提供位→名映射。</p>
 */
@NoArgsConstructor
public class EncryptedGrid {

    @JsonProperty("c")
    private long bits;

    /** 因素名按位顺序排列 */
    public record FactorSchema(List<String> names) {}

    // ====== 构造 ======

    /** 仅位置，因素默认为 0 */
    public EncryptedGrid(int i, int j, int k) {
        this.bits = pack(i, j, k, null, null);
    }

    /** 位置 + 因素 */
    public EncryptedGrid(int i, int j, int k, Map<String, Integer> factors, FactorSchema schema) {
        this.bits = pack(i, j, k, factors, schema);
    }

    public static long pack(int i, int j, int k, Map<String, Integer> factors, FactorSchema schema) {
        long b = ((long) (i & 0x1FFFF) << 47)
               | ((long) (j & 0x7FFF)  << 32)
               | ((long) (k & 0x1F)    << 27);
        if (factors != null && schema != null) {
            for (int slot = 0; slot < schema.names.size() && slot < 9; slot++) {
                String name = schema.names.get(slot);
                int v = factors.containsKey(name) ? factors.get(name) & 0x7 : 0;
                b |= ((long) v) << (24 - slot * 3);
            }
        }
        return b;
    }

    // ====== 因素写入（后补） ======

    /** 按 slot 写入单个因素值 (0-7) */
    public void setFactor(int slot, int val) {
        bits = (bits & ~(0x7L << (24 - slot * 3))) | ((val & 0x7L) << (24 - slot * 3));
    }

    /** 批量写入因素 */
    public void setFactors(Map<String, Integer> factors, FactorSchema schema) {
        for (int slot = 0; slot < schema.names.size() && slot < 9; slot++) {
            String name = schema.names.get(slot);
            Integer v = factors.get(name);
            if (v != null) setFactor(slot, v);
        }
    }

    // ====== 解算（需要 FactorSchema） ======

    public int i() { return (int) ((bits >>> 47) & 0x1FFFF); }
    public int j() { return (int) ((bits >>> 32) & 0x7FFF); }
    public int k() { return (int) ((bits >>> 27) & 0x1F); }

    public int factorVal(int slot) { return (int) ((bits >>> (24 - slot * 3)) & 0x7); }

    public Map<String, Integer> factors(FactorSchema schema) {
        Map<String, Integer> f = new LinkedHashMap<>();
        for (int slot = 0; slot < schema.names.size() && slot < 9; slot++) {
            f.put(schema.names.get(slot), factorVal(slot));
        }
        return f;
    }

    // ====== 核心方法 ======

    public long getCompressed() { return bits; }

    @JsonIgnore
    public String getId() { return Long.toHexString(bits); }

    @JsonIgnore
    public String locationKey() { return i() + "_" + j() + "_" + k(); }

    @JsonIgnore
    public GeoPoint centerPoint(GridContext ctx) {
        GeoPoint p = new GeoPoint();
        p.setLongitude(ctx.minLon + i() * ctx.cellLon + ctx.cellLon / 2);
        p.setLatitude(ctx.minLat + j() * ctx.cellLat + ctx.cellLat / 2);
        p.setAltitude(ctx.minAlt + k() * ctx.cellAlt + ctx.cellAlt / 2);
        return p;
    }

    // ====== 网格上下文 ======

    public record GridContext(double minLon, double minLat, double minAlt,
                               double cellLon, double cellLat, double cellAlt) {}

    // ====== 索引读写（直接操作 bits） ======

    @JsonIgnore
    public int getIndexLon() { return i(); }
    @JsonIgnore
    public int getIndexLat() { return j(); }
    @JsonIgnore
    public int getIndexAlt() { return k(); }

    @JsonIgnore
    public void setIndexLon(int v) { bits = (bits & ~(0x1FFFFL << 47)) | ((v & 0x1FFFFL) << 47); }
    @JsonIgnore
    public void setIndexLat(int v) { bits = (bits & ~(0x7FFFL << 32))  | ((v & 0x7FFFL) << 32); }
    @JsonIgnore
    public void setIndexAlt(int v) { bits = (bits & ~(0x1FL << 27))   | ((v & 0x1FL) << 27); }

    public void seal(FactorSchema schema) {
        this.bits = pack(getIndexLon(), getIndexLat(), getIndexAlt(), factors(schema), schema);
    }
}

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
 * bits 47-63: i (17b)     bits 24-26: F0 (3b)
 * bits 32-46: j (15b)     bits 21-23: F1 (3b)
 * bits 27-31: k (5b)      ... 共 9 个 slot ...
 *                          bits 0-2:  F8 (3b)
 * </pre>
 *
 * <p>因素映射由调用方传入有序 {@code List<String>} 确定 slot 顺序。</p>
 */
@NoArgsConstructor
public class EncryptedGrid {

    public static final int MAX_FACTORS = 9;

    @JsonProperty("c")
    private long bits;

    // ====== 构造 ======

    /** 仅位置 */
    public EncryptedGrid(int i, int j, int k) {
        this.bits = pack(i, j, k, null, null);
    }

    /** 位置 + 因素（nameList 决定 slot 顺序，factors 提供值） */
    public EncryptedGrid(int i, int j, int k, List<String> nameList, Map<String, Integer> factors) {
        this.bits = pack(i, j, k, nameList, factors);
    }

    static long pack(int i, int j, int k, List<String> nameList, Map<String, Integer> factors) {
        long b = ((long) (i & 0x1FFFF) << 47)
               | ((long) (j & 0x7FFF)  << 32)
               | ((long) (k & 0x1F)    << 27);
        if (nameList != null && factors != null) {
            for (int s = 0; s < nameList.size() && s < MAX_FACTORS; s++) {
                Integer v = factors.get(nameList.get(s));
                if (v != null) b |= ((long) (v & 0x7)) << (24 - s * 3);
            }
        }
        return b;
    }

    // ====== 解算 ======

    public int i() { return (int) ((bits >>> 47) & 0x1FFFF); }
    public int j() { return (int) ((bits >>> 32) & 0x7FFF); }
    public int k() { return (int) ((bits >>> 27) & 0x1F); }

    /** 读 slot 因素值 (0-7) */
    public int factor(int slot) { return (int) ((bits >>> (24 - slot * 3)) & 0x7); }

    /** 按 nameList 顺序返回因素 Map */
    public Map<String, Integer> factors(List<String> nameList) {
        Map<String, Integer> f = new LinkedHashMap<>();
        if (nameList != null) {
            for (int s = 0; s < nameList.size() && s < MAX_FACTORS; s++) {
                f.put(nameList.get(s), factor(s));
            }
        }
        return f;
    }

    // ====== 因素写入（后补） ======

    public void setFactor(int slot, int val) {
        bits = (bits & ~(0x7L << (24 - slot * 3))) | ((val & 0x7L) << (24 - slot * 3));
    }

    public void setFactor(String name, int val, List<String> nameList) {
        int s = nameList.indexOf(name);
        if (s >= 0 && s < MAX_FACTORS) setFactor(s, val);
    }

    public void setFactors(Map<String, Integer> factors, List<String> nameList) {
        if (nameList == null || factors == null) return;
        for (int s = 0; s < nameList.size() && s < MAX_FACTORS; s++) {
            Integer v = factors.get(nameList.get(s));
            if (v != null) setFactor(s, v);
        }
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

    // ====== 索引读写 ======

    @JsonIgnore
    public int getIndexLon() { return i(); }
    @JsonIgnore
    public int getIndexLat() { return j(); }
    @JsonIgnore
    public int getIndexAlt() { return k(); }

    @JsonIgnore
    public void setIndexLon(int v) { bits = (bits & ~(0x1FFFFL << 47)) | ((v & 0x1FFFFL) << 47); }
    @JsonIgnore
    public void setIndexLat(int v) { bits = (bits & ~(0x7FFFL  << 32)) | ((v & 0x7FFFL)  << 32); }
    @JsonIgnore
    public void setIndexAlt(int v) { bits = (bits & ~(0x1FL    << 27)) | ((v & 0x1FL)    << 27); }

    @JsonIgnore
    public void seal(List<String> nameList) {
        this.bits = pack(getIndexLon(), getIndexLat(), getIndexAlt(), nameList, factors(nameList));
    }
}

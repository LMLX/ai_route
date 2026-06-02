package com.example.airoute.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {
    private double longitude;
    private double latitude;
    private double altitude;

    /**
     * 计算与另一点的欧几里得距离
     */
    public double distanceTo(GeoPoint other) {
        double dLon = this.longitude - other.longitude;
        double dLat = this.latitude - other.latitude;
        double dAlt = this.altitude - other.altitude;
        return Math.sqrt(dLon * dLon + dLat * dLat + dAlt * dAlt);
    }
}

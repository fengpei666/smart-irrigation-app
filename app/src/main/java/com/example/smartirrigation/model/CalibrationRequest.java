package com.example.smartirrigation.model;

/**
 * POST /api/app/calibration
 * {"device_id":1,"kind":"soil","dry_raw":850,"wet_raw":320}
 * kind: soil|light|water；dry_raw=0% 读数，wet_raw=100% 读数，允许 wet_raw < dry_raw。
 */
public class CalibrationRequest {
    public long device_id;
    public String kind;
    public int dry_raw;
    public int wet_raw;

    public CalibrationRequest(long deviceId, String kind, int dryRaw, int wetRaw) {
        this.device_id = deviceId;
        this.kind = kind;
        this.dry_raw = dryRaw;
        this.wet_raw = wetRaw;
    }
}

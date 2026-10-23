package com.example.smartirrigation.model;

/**
 * POST /api/app/device/control
 * {"device_id":1,"action":"pump_on","duration_sec":15}
 * action: pump_on | pump_off | emergency_stop | auto | manual
 */
public class ControlRequest {
    public long device_id;
    public String action;
    public Integer duration_sec;

    public ControlRequest(long deviceId, String action, Integer durationSec) {
        this.device_id = deviceId;
        this.action = action;
        this.duration_sec = durationSec;
    }
}

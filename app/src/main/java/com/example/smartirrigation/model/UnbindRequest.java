package com.example.smartirrigation.model;

/**
 * POST /api/app/unbind → {"device_id":1}
 */
public class UnbindRequest {
    public long device_id;

    public UnbindRequest(long deviceId) {
        this.device_id = deviceId;
    }
}

package com.example.smartirrigation.model;

/**
 * 删除计划：POST /api/app/schedules {"id":1,"device_id":3,"delete":true}
 */
public class ScheduleDeleteRequest {
    public long id;
    public long device_id;
    public boolean delete = true;

    public ScheduleDeleteRequest(long id, long deviceId) {
        this.id = id;
        this.device_id = deviceId;
    }
}

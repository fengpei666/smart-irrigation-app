package com.example.smartirrigation.model;

/**
 * GET /api/app/command/status → {cmd_id,action,duration_sec,status,sent_at,ack_at,result}
 * status: pending（等设备拉取）→ sent（设备已拉走）→ done / failed。
 * App 每 2 秒轮询，最多 30 秒；超时提示“设备未响应，请检查在线状态”。
 */
public class CommandStatusData {
    public long cmd_id;
    public String action;
    public Integer duration_sec;
    public String status;
    public String sent_at;
    public String ack_at;
    public String result;

    public boolean isTerminal() {
        return "done".equals(status) || "failed".equals(status);
    }

    public String statusText() {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "pending":
                return "已入队（等待设备拉取）";
            case "sent":
                return "设备已拉取，执行中";
            case "ack":
                return "设备已确认，执行中";
            case "done":
                return "执行完成";
            case "failed":
                return "执行失败";
            default:
                return status;
        }
    }
}

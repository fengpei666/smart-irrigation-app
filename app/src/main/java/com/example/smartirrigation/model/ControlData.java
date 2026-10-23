package com.example.smartirrigation.model;

/**
 * POST /api/app/device/control
 * - 生成命令：{"cmd_id":204,"action":"pump_on","duration_sec":15,"status":"pending","expire_at":"..."}
 * - 模式切换 auto/manual：返回 {"mode":"auto"}，不生成命令，也不应轮询命令状态。
 */
public class ControlData {
    public long cmd_id;
    public String action;
    public Integer duration_sec;
    public String status;
    public String expire_at;
    public String mode;

    /** 是否需要轮询命令回执 */
    public boolean hasCommand() {
        return cmd_id > 0;
    }
}

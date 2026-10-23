package com.example.smartirrigation.network;

import android.text.TextUtils;

/**
 * 服务端统一响应：{"code":0,"msg":"ok","data":{...}}
 * code != 0 即失败（服务端同时把业务码作为 HTTP 状态返回，App 两侧都要兼容）。
 */
public class ApiResult<T> {
    public int code;
    public String msg;
    public T data;

    public boolean isSuccess() {
        return code == 0;
    }

    /** 业务错误码 → 中文提示（服务端 msg 优先） */
    public static String codeText(int code) {
        switch (code) {
            case 0:
                return "成功";
            case 400:
                return "参数错误";
            case 401:
                return "登录已过期，请重新登录";
            case 403:
                return "无权限访问该设备";
            case 429:
                return "请求过于频繁，请稍后再试";
            case 1001:
                return "账号或密码错误";
            case 1002:
                return "账号已存在，请换一个";
            case 1003:
                return "手机号已注册";
            case 1004:
                return "邮箱已注册";
            case 2001:
                return "设备不存在";
            case 2002:
                return "绑定码错误或已失效，请重新获取";
            case 2003:
                return "设备已被其他账号绑定，请联系管理员解绑";
            case 2004:
                return "该设备未绑定到当前账号";
            case 2005:
                return "设备已停用";
            case 3001:
                return "低水位，禁止启泵";
            case 3002:
                return "两次启泵间隔不足";
            case 3003:
                return "超过单次最大运行时长";
            case 3004:
                return "自动模式已暂停（数据过期或已关闭）";
            case 4001:
                return "命令不存在";
            default:
                return "请求失败（" + code + "）";
        }
    }

    public static String messageOf(int code, String serverMsg) {
        if (!TextUtils.isEmpty(serverMsg)) {
            return serverMsg;
        }
        return codeText(code);
    }
}

package com.example.smartirrigation.model;

/**
 * 多轮对话中的一轮：role 仅 user / assistant，服务端只取最后 6 条、每条 ≤500 字。
 */
public class ChatTurn {
    public String role;
    public String content;

    public ChatTurn(String role, String content) {
        this.role = role;
        this.content = content;
    }
}

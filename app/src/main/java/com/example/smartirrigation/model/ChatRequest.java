package com.example.smartirrigation.model;

import java.util.List;

/**
 * POST /api/app/ai/chat
 * {device_id, question, history?:[{role,content}], new_session?}
 * new_session=true 时服务端强制忽略 history，从零开始（双保险，一般清空 history 即可）。
 */
public class ChatRequest {
    public long device_id;
    public String question;
    public List<ChatTurn> history;
    public Boolean new_session;

    public ChatRequest(long deviceId, String question, List<ChatTurn> history) {
        this(deviceId, question, history, false);
    }

    public ChatRequest(long deviceId, String question, List<ChatTurn> history,
                       boolean newSession) {
        this.device_id = deviceId;
        this.question = question;
        this.history = history;
        this.new_session = newSession ? Boolean.TRUE : null;
    }
}

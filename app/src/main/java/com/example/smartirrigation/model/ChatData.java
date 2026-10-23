package com.example.smartirrigation.model;

/**
 * POST /api/app/ai/chat 响应 data。
 * reply 为纯文本（含 \n），只能当文本渲染；source=rule 表示 AI 不可用，reply 是数据摘要。
 */
public class ChatData {
    public String reply;
    public String source;
    public int latency_ms;
    public boolean readonly;
    public long device_id;
    public String question;
    public StatusData.Metrics metrics;
    /** 模型判断需要执行操作时为 true，此时 action 非空 */
    public boolean need_confirm;
    /** 待用户确认的操作提案；纯问答时为 null */
    public AiAction action;
    /** 服务端确认本次请求按“全新会话”处理（请求带 new_session 时返回） */
    public boolean new_session;

    public boolean fromRule() {
        return "rule".equalsIgnoreCase(source);
    }
}

package com.example.smartirrigation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地会话（服务端无状态，不保存聊天记录）。
 * App 自己维护多个会话，每个会话只保留最近几轮用于上下文。
 */
public class ChatSession {
    public String id;
    /** 列表标题：取该会话第一条提问，截断展示 */
    public String title;
    public long updated_at;
    public List<ChatTurn> turns = new ArrayList<>();

    /** 问答轮数 */
    public int rounds() {
        return turns == null ? 0 : turns.size() / 2;
    }
}

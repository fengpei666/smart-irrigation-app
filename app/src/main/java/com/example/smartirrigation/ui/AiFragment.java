package com.example.smartirrigation.ui;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.ChatSession;
import com.example.smartirrigation.session.SessionManager;

import java.util.List;

/**
 * 底部导航「AI」一级页：只有两件事——
 * 1) 直接开启对话；
 * 2) 下方「历史对话」列表，点一条接着聊（会话都存本机，服务端无状态）。
 */
public class AiFragment extends BaseFragment {

    private RecyclerView rvSessions;
    private TextView tvSessionCount;
    private TextView tvEmptyHistory;
    private ChatSessionAdapter adapter;
    private List<ChatSession> sessions;

    @Override
    protected int layoutId() {
        return R.layout.fragment_ai;
    }

    @Override
    protected String pageTitle() {
        return "AI 助手";
    }

    @Override
    protected String pageSubtitle() {
        return "对话 · 历史记录";
    }

    @Override
    protected void onScreenReady(@NonNull View view) {
        rvSessions = find(R.id.rvSessions);
        tvSessionCount = find(R.id.tvSessionCount);
        tvEmptyHistory = find(R.id.tvEmptyHistory);

        adapter = new ChatSessionAdapter(new ChatSessionAdapter.Listener() {
            @Override
            public void onOpen(ChatSession session) {
                openSession(session.id);
            }
        });
        rvSessions.setLayoutManager(new LinearLayoutManager(ctx()));
        rvSessions.setAdapter(adapter);

        View.OnClickListener startChat = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 不带 session id → 全新会话
                go(ChatActivity.class);
            }
        };
        find(R.id.cardHero).setOnClickListener(startChat);
        find(R.id.btnAiChat).setOnClickListener(startChat);

        renderSessions();
    }

    private void renderSessions() {
        if (root == null) {
            return;
        }
        sessions = SessionManager.get().loadChatSessions();
        adapter.submit(sessions);
        boolean empty = sessions == null || sessions.isEmpty();
        show(tvEmptyHistory, empty);
        rvSessions.setVisibility(empty ? View.GONE : View.VISIBLE);
        txtSessionCount(empty ? "" : sessions.size() + " 条");
    }

    private void txtSessionCount(String text) {
        if (tvSessionCount != null) {
            tvSessionCount.setText(text);
        }
    }

    private void openSession(String sessionId) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_SESSION_ID, sessionId);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.hold);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        renderSessions();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            renderSessions();
        }
    }
}

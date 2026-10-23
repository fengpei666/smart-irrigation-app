package com.example.smartirrigation.ui;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.ChatSession;

import java.util.ArrayList;
import java.util.List;

/** 「历史对话」列表：一条一个本地会话，点击继续聊 */
class ChatSessionAdapter extends RecyclerView.Adapter<ChatSessionAdapter.VH> {

    interface Listener {
        void onOpen(ChatSession session);
    }

    private final List<ChatSession> items = new ArrayList<>();
    private final Listener listener;

    ChatSessionAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<ChatSession> sessions) {
        items.clear();
        if (sessions != null) {
            items.addAll(sessions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_session, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final ChatSession session = items.get(position);
        holder.tvTitle.setText(TextUtils.isEmpty(session.title) ? "未命名对话" : session.title);
        holder.tvMeta.setText(buildMeta(session));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onOpen(session);
                }
            }
        });
    }

    private String buildMeta(ChatSession session) {
        String rounds = session.rounds() + " 轮";
        if (session.updated_at <= 0) {
            return rounds;
        }
        CharSequence time = DateUtils.getRelativeTimeSpanString(
                session.updated_at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        return rounds + " · " + time;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvMeta;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvMeta = itemView.findViewById(R.id.tvSessionMeta);
        }
    }
}

package com.example.smartirrigation.ui;

import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.AiAction;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话气泡适配器：
 * - 用户消息右对齐，AI 消息左对齐；
 * - AI 消息可带一段说明（rule 兜底 / 耗时 / 错误）；
 * - AI 返回操作提案时，在气泡下方渲染确认卡片，必须用户点“确认执行”才会提交。
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;

    /** 操作卡片的按钮回调，position 为列表下标 */
    public interface ActionListener {
        void onAction(int position, AiAction action, boolean confirm);
    }

    public static class Msg {
        public int type;
        public String content;
        /** AI 气泡下方的灰字说明（rule 兜底、思考中、错误提示） */
        public String note;
        public boolean pending;

        /** 待确认操作（无则为 null） */
        public AiAction action;
        /** pending / loading / done / failed */
        public String actionState;
        /** 卡片底部的执行结果文案 */
        public String actionResult;
        /** 已点确认、正在提交服务器：进度条走不确定动画 */
        public boolean actionBusy;
        /** 执行后的浇水倒计时：总秒数与结束时刻（elapsedRealtime） */
        public int countdownSec;
        public long countdownEndAt;

        public Msg(int type, String content) {
            this.type = type;
            this.content = content;
        }

        public Msg(int type, String content, String note) {
            this.type = type;
            this.content = content;
            this.note = note;
        }
    }

    private final List<Msg> items = new ArrayList<>();
    private final ActionListener actionListener;

    public ChatAdapter() {
        this(null);
    }

    public ChatAdapter(ActionListener listener) {
        this.actionListener = listener;
    }

    public void submit(List<Msg> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public void add(Msg msg) {
        items.add(msg);
        notifyItemInserted(items.size() - 1);
    }

    public void update(int index, Msg msg) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        items.set(index, msg);
        notifyItemChanged(index);
    }

    public int lastIndex() {
        return items.size() - 1;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        }
        return new AiHolder(inflater.inflate(R.layout.item_chat_ai, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Msg msg = items.get(position);
        if (holder instanceof UserHolder) {
            ((UserHolder) holder).content.setText(msg.content);
        } else {
            AiHolder ai = (AiHolder) holder;
            ai.content.setText(msg.content);
            if (TextUtils.isEmpty(msg.note)) {
                ai.note.setVisibility(View.GONE);
            } else {
                ai.note.setVisibility(View.VISIBLE);
                ai.note.setText(msg.note);
            }
            bindAction(ai, msg);
        }
    }

    private void bindAction(AiHolder holder, Msg msg) {
        AiAction action = msg.action;
        if (action == null || TextUtils.isEmpty(action.summary)) {
            holder.actionCard.setVisibility(View.GONE);
            return;
        }
        holder.actionCard.setVisibility(View.VISIBLE);
        holder.actionSummary.setText(action.summary);

        String state = msg.actionState == null ? "pending" : msg.actionState;
        boolean pending = "pending".equals(state);
        boolean failed = "failed".equals(state);
        boolean showButtons = pending || failed;

        holder.actionExpire.setVisibility(pending ? View.VISIBLE : View.GONE);
        holder.actionButtons.setVisibility(showButtons ? View.VISIBLE : View.GONE);
        holder.actionConfirm.setEnabled(showButtons);
        holder.actionCancel.setEnabled(showButtons);
        holder.actionConfirm.setAlpha(showButtons ? 1f : 0.5f);
        holder.actionCancel.setAlpha(showButtons ? 1f : 0.5f);

        if (TextUtils.isEmpty(msg.actionResult)) {
            holder.actionState.setVisibility(View.GONE);
        } else {
            holder.actionState.setVisibility(View.VISIBLE);
            holder.actionState.setText(msg.actionResult);
            int color;
            if ("done".equals(state)) {
                color = R.color.online;
            } else if (failed) {
                color = R.color.offline;
            } else {
                color = R.color.text_hint;
            }
            holder.actionState.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), color));
        }

        bindCountdown(holder, msg);

        holder.actionConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatch(holder, action, true);
            }
        });
        holder.actionCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatch(holder, action, false);
            }
        });
    }

    /** 提交中 → 不确定进度条；执行后 → 按浇水时长倒计时 */
    private void bindCountdown(AiHolder holder, Msg msg) {
        if (holder.countdownTick != null) {
            holder.itemView.removeCallbacks(holder.countdownTick);
            holder.countdownTick = null;
        }
        if (msg.actionBusy) {
            holder.actionCountdown.setVisibility(View.VISIBLE);
            holder.pbCountdown.setIndeterminate(true);
            holder.tvCountdown.setText("正在提交服务器…");
            return;
        }
        if (msg.countdownEndAt <= 0) {
            holder.actionCountdown.setVisibility(View.GONE);
            return;
        }
        holder.actionCountdown.setVisibility(View.VISIBLE);
        holder.pbCountdown.setIndeterminate(false);
        tickCountdown(holder, msg);
    }

    private void tickCountdown(final AiHolder holder, final Msg msg) {
        int totalMs = Math.max(1, msg.countdownSec * 1000);
        long remain = msg.countdownEndAt - SystemClock.elapsedRealtime();
        if (remain <= 0) {
            holder.pbCountdown.setProgress(100);
            holder.tvCountdown.setText("浇水已结束（" + msg.countdownSec + " 秒）");
            holder.countdownTick = null;
            return;
        }
        holder.pbCountdown.setProgress((int) ((totalMs - remain) * 100 / totalMs));
        holder.tvCountdown.setText("正在浇水 · 剩余 " + ((remain + 999) / 1000) + " 秒");
        holder.countdownTick = new Runnable() {
            @Override
            public void run() {
                tickCountdown(holder, msg);
            }
        };
        holder.itemView.postDelayed(holder.countdownTick, 200);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof AiHolder) {
            AiHolder ai = (AiHolder) holder;
            if (ai.countdownTick != null) {
                ai.itemView.removeCallbacks(ai.countdownTick);
                ai.countdownTick = null;
            }
        }
        super.onViewRecycled(holder);
    }

    private void dispatch(AiHolder holder, AiAction action, boolean confirm) {
        if (actionListener == null) {
            return;
        }
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        actionListener.onAction(position, action, confirm);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class UserHolder extends RecyclerView.ViewHolder {
        final TextView content;

        UserHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.tvContent);
        }
    }

    static class AiHolder extends RecyclerView.ViewHolder {
        final TextView content;
        final TextView note;
        final View actionCard;
        final TextView actionSummary;
        final TextView actionState;
        final TextView actionExpire;
        final View actionButtons;
        final TextView actionConfirm;
        final TextView actionCancel;
        final View actionCountdown;
        final TextView tvCountdown;
        final ProgressBar pbCountdown;
        Runnable countdownTick;

        AiHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.tvContent);
            note = itemView.findViewById(R.id.tvNote);
            actionCard = itemView.findViewById(R.id.actionCard);
            actionSummary = itemView.findViewById(R.id.tvActionSummary);
            actionState = itemView.findViewById(R.id.tvActionState);
            actionExpire = itemView.findViewById(R.id.tvActionExpire);
            actionButtons = itemView.findViewById(R.id.actionButtons);
            actionConfirm = itemView.findViewById(R.id.btnActionConfirm);
            actionCancel = itemView.findViewById(R.id.btnActionCancel);
            actionCountdown = itemView.findViewById(R.id.actionCountdown);
            tvCountdown = itemView.findViewById(R.id.tvActionCountdown);
            pbCountdown = itemView.findViewById(R.id.pbActionCountdown);
        }
    }
}

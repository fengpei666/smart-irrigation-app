package com.example.smartirrigation.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.AlertItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 报警列表：level 决定色条与标题颜色，未读显示角标。
 */
public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.VH> {

    public interface OnItemClickListener {
        void onClick(AlertItem alert);
    }

    private List<AlertItem> data = new ArrayList<>();
    private final OnItemClickListener listener;

    public AlertAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AlertItem> list) {
        data = list == null ? new ArrayList<AlertItem>() : list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alert, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final AlertItem alert = data.get(position);
        int colorRes;
        if ("critical".equals(alert.level)) {
            colorRes = R.color.offline;
        } else if ("warning".equals(alert.level)) {
            colorRes = R.color.warning;
        } else {
            colorRes = R.color.info;
        }
        int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);

        holder.viewLevel.setBackgroundColor(color);
        holder.tvTitle.setText(alert.title == null ? "报警" : alert.title);
        holder.tvTitle.setTextColor(color);
        holder.tvMessage.setText(alert.message == null ? "--" : alert.message);
        holder.tvTime.setText((alert.created_at == null ? "--" : alert.created_at)
                + " · " + typeText(alert.type));
        holder.tvUnread.setVisibility(alert.isRead() ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(alert);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private String typeText(String type) {
        if (type == null) {
            return "报警";
        }
        switch (type) {
            case "low_water":
                return "低水位";
            case "offline":
                return "设备离线";
            case "sensor":
                return "传感器异常";
            default:
                return type;
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        View viewLevel;
        TextView tvTitle;
        TextView tvMessage;
        TextView tvTime;
        TextView tvUnread;

        VH(View itemView) {
            super(itemView);
            viewLevel = itemView.findViewById(R.id.viewLevel);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvUnread = itemView.findViewById(R.id.tvUnread);
        }
    }
}

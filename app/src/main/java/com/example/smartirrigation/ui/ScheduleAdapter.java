package com.example.smartirrigation.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.Schedule;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划列表：启用开关（保存为 enabled）、点击进入编辑、展示 next_run_at / last_run_at。
 * 契约未提供删除接口，停用即等价于下线（避免误删历史执行记录）。
 */
public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.VH> {

    public interface OnScheduleActionListener {
        void onToggle(Schedule schedule, boolean enabled);

        void onClick(Schedule schedule);
    }

    private List<Schedule> data = new ArrayList<>();
    private final OnScheduleActionListener listener;

    public ScheduleAdapter(OnScheduleActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Schedule> list) {
        data = list == null ? new ArrayList<Schedule>() : list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Schedule s = data.get(position);
        holder.tvName.setText("#" + s.id + " " + s.name + (s.enabled ? "" : "（已停用）"));

        StringBuilder meta = new StringBuilder(typeText(s.type));
        if ("time".equals(s.type)) {
            meta.append(" · 每天 ").append(s.time_of_day == null ? "--" : s.time_of_day)
                    .append(" · 周开关 ").append(s.weekdays == null ? "-------" : s.weekdays);
        } else if ("condition".equals(s.type)) {
            meta.append(" · ").append(conditionText(s.condition_type))
                    .append(" ").append(s.condition_value == null ? "--" : String.valueOf(s.condition_value));
        } else {
            meta.append(" · 每 ").append(s.interval_min == null ? "--" : String.valueOf(s.interval_min))
                    .append(" 分钟");
        }
        meta.append(" · ").append(s.duration_sec).append(" 秒");
        holder.tvMeta.setText(meta.toString());

        holder.tvLastRun.setText("下次：" + safe(s.next_run_at) + " · 上次：" + safe(s.last_run_at));

        holder.swEnabled.setOnCheckedChangeListener(null);
        holder.swEnabled.setChecked(s.enabled);
        holder.swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (listener != null) {
                    listener.onToggle(s, isChecked);
                }
            }
        });
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(s);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "--" : value;
    }

    private String typeText(String type) {
        if (type == null) {
            return "计划";
        }
        switch (type) {
            case "time":
                return "定时";
            case "condition":
                return "条件";
            case "rotation":
                return "轮灌";
            default:
                return type;
        }
    }

    private String conditionText(String conditionType) {
        if (conditionType == null) {
            return "土壤低于";
        }
        switch (conditionType) {
            case "temp_above":
                return "温度高于";
            case "humi_below":
                return "湿度低于";
            case "light_below":
                return "光照低于";
            default:
                return "土壤低于";
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvMeta;
        TextView tvLastRun;
        MaterialSwitch swEnabled;

        VH(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvLastRun = itemView.findViewById(R.id.tvLastRun);
            swEnabled = itemView.findViewById(R.id.swEnabled);
        }
    }
}

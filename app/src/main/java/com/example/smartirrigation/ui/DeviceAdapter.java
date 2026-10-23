package com.example.smartirrigation.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.DeviceItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 已绑定设备列表（device_id / sn / 在线状态 / 绑定时间）。
 */
public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {

    public interface OnUnbindClickListener {
        void onUnbind(DeviceItem device);
    }

    private List<DeviceItem> data = new ArrayList<>();
    private final OnUnbindClickListener listener;

    public DeviceAdapter(OnUnbindClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<DeviceItem> list) {
        data = list == null ? new ArrayList<DeviceItem>() : list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final DeviceItem item = data.get(position);
        holder.tvSn.setText(item.sn);
        holder.tvName.setText("device_id: " + item.device_id + " · 绑定于 " + item.bound_at);
        holder.tvOnline.setText(item.online == 1 ? "在线" : "离线");
        holder.tvOnline.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                item.online == 1 ? R.color.online : R.color.offline));
        holder.btnUnbind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onUnbind(item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvSn;
        TextView tvName;
        TextView tvOnline;
        Button btnUnbind;

        VH(View itemView) {
            super(itemView);
            tvSn = itemView.findViewById(R.id.tvSn);
            tvName = itemView.findViewById(R.id.tvName);
            tvOnline = itemView.findViewById(R.id.tvOnline);
            btnUnbind = itemView.findViewById(R.id.btnUnbind);
        }
    }
}

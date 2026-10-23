package com.example.smartirrigation.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * 分区列表：点击进入编辑。
 */
public class ZoneAdapter extends RecyclerView.Adapter<ZoneAdapter.VH> {

    public interface OnZoneClickListener {
        void onClick(Zone zone);
    }

    private List<Zone> data = new ArrayList<>();
    private final OnZoneClickListener listener;

    public ZoneAdapter(OnZoneClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Zone> list) {
        data = list == null ? new ArrayList<Zone>() : list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_zone, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Zone zone = data.get(position);
        holder.tvName.setText("#" + (zone.id == null ? "-" : zone.id) + " " + zone.name
                + (zone.enabled ? "" : "（已停用）"));
        holder.tvMeta.setText(zone.area_m2 + " m² · " + safe(zone.soil_type) + " · " + safe(zone.plant));
        holder.tvNote.setText(safe(zone.note));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClick(zone);
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

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvMeta;
        TextView tvNote;

        VH(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvNote = itemView.findViewById(R.id.tvNote);
        }
    }
}

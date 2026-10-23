package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.Zone;
import com.example.smartirrigation.model.ZonesData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * 分区管理：GET /api/app/zones、POST /api/app/zones（带 id 更新，不带 id 新建）。
 */
public class ZonesActivity extends BaseActivity implements ApiCallback<ZonesData> {

    private RecyclerView rvZones;
    private ZoneAdapter adapter;
    private TextView tvEmpty;
    private ProgressBar progress;

    private TextInputEditText etName;
    private TextInputEditText etArea;
    private TextInputEditText etSoilType;
    private TextInputEditText etPlant;
    private TextInputEditText etNote;
    private MaterialSwitch swEnabled;
    private MaterialButton btnSave;
    private MaterialButton btnCancel;
    private TextView tvFormTitle;

    private Long editingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zones);
        setupPage("分区管理", "按区域记录作物与用水", 0);

        rvZones = findViewById(R.id.rvZones);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);
        etName = findViewById(R.id.etZoneName);
        etArea = findViewById(R.id.etArea);
        etSoilType = findViewById(R.id.etSoilType);
        etPlant = findViewById(R.id.etPlant);
        etNote = findViewById(R.id.etNote);
        swEnabled = findViewById(R.id.swZoneEnabled);
        btnSave = findViewById(R.id.btnSaveZone);
        btnCancel = findViewById(R.id.btnCancelZone);
        tvFormTitle = findViewById(R.id.tvZoneFormTitle);

        rvZones.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ZoneAdapter(new ZoneAdapter.OnZoneClickListener() {
            @Override
            public void onClick(Zone zone) {
                fillForm(zone);
            }
        });
        rvZones.setAdapter(adapter);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveZone();
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearForm();
            }
        });

        loadZones();
    }

    private void loadZones() {
        if (deviceId() <= 0) {
            return;
        }
        progress.setVisibility(View.VISIBLE);
        NetworkModule.enqueue(NetworkModule.api().zones(deviceId()), this);
    }

    @Override
    public void onSuccess(ZonesData data) {
        progress.setVisibility(View.GONE);
        if (data == null) {
            onError("服务器返回为空");
            return;
        }
        SessionManager.get().saveZones(data);
        render(data);
    }

    @Override
    public void onError(String message) {
        progress.setVisibility(View.GONE);
        ZonesData cached = SessionManager.get().loadZones();
        if (cached != null) {
            render(cached);
            if (!isDemo()) {
                toast("网络异常，显示缓存分区");
            }
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("无法获取分区：" + message);
        }
    }

    private void render(ZonesData data) {
        List<Zone> list = data.zones == null ? new ArrayList<Zone>() : data.zones;
        adapter.submitList(list);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void fillForm(Zone zone) {
        editingId = zone.id;
        etName.setText(zone.name);
        etArea.setText(String.valueOf(zone.area_m2));
        etSoilType.setText(zone.soil_type);
        etPlant.setText(zone.plant);
        etNote.setText(zone.note);
        swEnabled.setChecked(zone.enabled);
        tvFormTitle.setText("编辑分区 #" + zone.id);
        btnCancel.setVisibility(View.VISIBLE);
    }

    private void clearForm() {
        editingId = null;
        etName.setText("");
        etArea.setText("");
        etSoilType.setText("");
        etPlant.setText("");
        etNote.setText("");
        swEnabled.setChecked(true);
        tvFormTitle.setText("新建分区");
        btnCancel.setVisibility(View.GONE);
    }

    private void saveZone() {
        if (deviceId() <= 0) {
            toast("请先绑定设备");
            return;
        }
        String name = text(etName);
        if (TextUtils.isEmpty(name)) {
            toast("请填写分区名称");
            return;
        }
        Zone zone = new Zone();
        zone.id = editingId;
        zone.device_id = deviceId();
        zone.name = name;
        try {
            zone.area_m2 = Double.parseDouble(text(etArea));
        } catch (NumberFormatException e) {
            zone.area_m2 = 0;
        }
        zone.soil_type = text(etSoilType);
        zone.plant = text(etPlant);
        zone.note = text(etNote);
        zone.enabled = swEnabled.isChecked();

        progress.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        NetworkModule.enqueue(NetworkModule.api().saveZone(zone), new ApiCallback<Zone>() {
            @Override
            public void onSuccess(Zone data) {
                progress.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                toast("分区已保存到服务器");
                clearForm();
                loadZones();
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                toast("保存失败：" + message);
            }
        });
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

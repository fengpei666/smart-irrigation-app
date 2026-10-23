package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.BindData;
import com.example.smartirrigation.model.BindRequest;
import com.example.smartirrigation.model.DeviceItem;
import com.example.smartirrigation.model.DevicesData;
import com.example.smartirrigation.model.UnbindRequest;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备绑定：POST /api/app/bind（必须 bound==true 才算成功）、POST /api/app/unbind。
 * 同步规则：绑定成功 → 立刻 GET /api/app/devices 刷新；解绑成功 → 清本地 device_id 再刷新。
 */
public class BindActivity extends BaseActivity implements ApiCallback<BindData> {

    private TextInputEditText etSn;
    private TextInputEditText etBindCode;
    private MaterialButton btnBind;
    private MaterialButton btnEnterDashboard;
    private RecyclerView rvDevices;
    private DeviceAdapter adapter;
    private TextView tvEmpty;
    private TextView tvError;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bind);
        setupPage("设备绑定", "SN + 6 位绑定码 · 绑定后自动同步", 0);

        etSn = findViewById(R.id.etSn);
        etBindCode = findViewById(R.id.etBindCode);
        btnBind = findViewById(R.id.btnBind);
        btnEnterDashboard = findViewById(R.id.btnEnterDashboard);
        rvDevices = findViewById(R.id.rvDevices);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvError = findViewById(R.id.tvError);
        progress = findViewById(R.id.progress);

        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(new DeviceAdapter.OnUnbindClickListener() {
            @Override
            public void onUnbind(DeviceItem device) {
                unbind(device);
            }
        });
        rvDevices.setAdapter(adapter);

        btnBind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptBind();
            }
        });
        btnEnterDashboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deviceId() <= 0) {
                    toast("请先绑定设备");
                    return;
                }
                go(MainActivity.class);
                finish();
            }
        });

        renderDevices();
        refreshDevices();
    }

    private void attemptBind() {
        String sn = etSn.getText() == null ? "" : etSn.getText().toString().trim();
        String code = etBindCode.getText() == null ? "" : etBindCode.getText().toString().trim();
        if (TextUtils.isEmpty(sn)) {
            showError("请输入设备 SN");
            return;
        }
        if (!code.matches("\\d{6}")) {
            showError("请输入 6 位绑定码（绑定码用一次即失效，失败请重新获取）");
            return;
        }
        show(tvError, false);
        show(progress, true);
        btnBind.setEnabled(false);
        NetworkModule.enqueue(NetworkModule.api().bind(new BindRequest(sn, code)), this);
    }

    @Override
    public void onSuccess(BindData data) {
        show(progress, false);
        btnBind.setEnabled(true);
        // 契约：bound==true 才算成功，不能只判 code==0
        if (data == null || !data.bound) {
            onError("绑定未生效（服务端未返回 bound=true），请重新获取绑定码");
            return;
        }
        SessionManager.get().setDeviceId(data.device_id);
        etSn.setText("");
        etBindCode.setText("");
        toast("绑定成功：device_id=" + data.device_id);
        refreshDevices();
    }

    @Override
    public void onError(String message) {
        show(progress, false);
        btnBind.setEnabled(true);
        showError(message);
    }

    private void showError(String message) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(message);
    }

    private void unbind(final DeviceItem device) {
        show(progress, true);
        NetworkModule.enqueue(NetworkModule.api().unbind(new UnbindRequest(device.device_id)),
                new ApiCallback<Object>() {
                    @Override
                    public void onSuccess(Object data) {
                        show(progress, false);
                        if (SessionManager.get().getDeviceId() == device.device_id) {
                            SessionManager.get().clearDeviceId();
                        }
                        toast("已解绑 " + device.sn);
                        refreshDevices();
                    }

                    @Override
                    public void onError(String message) {
                        show(progress, false);
                        showError("解绑失败：" + message);
                        refreshDevices();
                    }
                });
    }

    /** 设备列表以服务端为准 */
    private void refreshDevices() {
        if (isDemo()) {
            // 离线演示：不请求服务端，直接展示本地缓存设备
            renderDevices();
            return;
        }
        NetworkModule.enqueue(NetworkModule.api().devices(), new ApiCallback<DevicesData>() {
            @Override
            public void onSuccess(DevicesData data) {
                List<DeviceItem> devices = data == null ? null : data.devices;
                SessionManager.get().saveDevices(devices);
                if (devices != null && !devices.isEmpty() && !SessionManager.get().hasDevice(deviceId())) {
                    SessionManager.get().setDeviceId(devices.get(0).device_id);
                }
                if (devices == null || devices.isEmpty()) {
                    SessionManager.get().clearDeviceId();
                }
                renderDevices();
            }

            @Override
            public void onError(String message) {
                renderDevices();
                toast("设备列表获取失败：" + message);
            }
        });
    }

    private void renderDevices() {
        List<DeviceItem> devices = SessionManager.get().loadDevices();
        adapter.submitList(devices == null ? new ArrayList<DeviceItem>() : devices);
        tvEmpty.setVisibility(devices == null || devices.isEmpty() ? View.VISIBLE : View.GONE);
    }
}

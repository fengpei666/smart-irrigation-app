package com.example.smartirrigation.ui;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.AdviceData;
import com.example.smartirrigation.model.DeviceItem;
import com.example.smartirrigation.model.DevicesData;
import com.example.smartirrigation.model.StatusData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.example.smartirrigation.ui.widget.RingGaugeView;
import com.example.smartirrigation.util.Poller;
import com.example.smartirrigation.util.SimpleOnItemSelectedListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 首页仪表盘（常驻 Fragment）：GET /api/app/device/status。
 * tab 切走时停止 5 秒轮询，切回自动恢复；下拉刷新；断网展示缓存。
 */
public class DashboardFragment extends BaseFragment implements ApiCallback<StatusData> {

    private static final long REFRESH_INTERVAL_MS = 5000L;

    private Spinner spDevices;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private TextView tvOffline;
    private TextView tvSync;
    private TextView tvHeroStatus;

    private RingGaugeView ringSoil;
    private RingGaugeView ringTemp;
    private RingGaugeView ringHumi;
    private RingGaugeView ringLight;
    private ProgressBar pbWater;
    private ImageButton btnRefresh;
    private ValueAnimator waterAnimator;

    private TextView tvSoilValue;
    private TextView tvTempValue;
    private TextView tvHumiValue;
    private TextView tvLightValue;

    private TextView tvWater;
    private TextView tvLowWater;
    private TextView tvPump;
    private TextView tvMode;
    private TextView tvOnline;
    private TextView tvLastSeen;
    private TextView tvReportedAt;
    private TextView tvSafety;
    private TextView tvAdvice;

    private final Poller poller = new Poller();
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private long nextRefreshAt = 0L;
    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            long left = Math.max(0L, nextRefreshAt - System.currentTimeMillis());
            long seconds = (left + 999L) / 1000L;
            if (tvSync != null) {
                tvSync.setText(seconds + " 秒后刷新");
            }
            ticker.postDelayed(this, 1000L);
        }
    };
    private final List<DeviceItem> devices = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;
    private boolean firstLoad = true;
    private boolean spinnerReady = false;

    @Override
    protected int layoutId() {
        return R.layout.fragment_dashboard;
    }

    @Override
    protected String pageTitle() {
        return "灌溉仪表盘";
    }

    @Override
    protected String pageSubtitle() {
        return "实时监测 · 服务器调度";
    }

    @Override
    protected void onScreenReady(@NonNull View view) {
        spDevices = find(R.id.spDevices);
        swipe = find(R.id.swipe);
        progress = find(R.id.progress);
        tvEmpty = find(R.id.tvEmpty);
        tvOffline = find(R.id.tvOffline);
        tvSync = find(R.id.tvSync);
        tvHeroStatus = find(R.id.tvHeroStatus);
        btnRefresh = find(R.id.btnRefresh);
        ringSoil = find(R.id.ringSoil);
        ringTemp = find(R.id.ringTemp);
        ringHumi = find(R.id.ringHumi);
        ringLight = find(R.id.ringLight);
        pbWater = find(R.id.pbWater);
        tvWater = find(R.id.tvWater);
        tvSoilValue = find(R.id.tvSoilValue);
        tvTempValue = find(R.id.tvTempValue);
        tvHumiValue = find(R.id.tvHumiValue);
        tvLightValue = find(R.id.tvLightValue);

        setupGauge(ringSoil, "土壤含水", "%", R.color.primary);
        setupGauge(ringTemp, "空气温度", "℃", R.color.warning);
        setupGauge(ringHumi, "空气湿度", "%", R.color.water_blue);
        setupGauge(ringLight, "光照强度", "%", R.color.accent);
        tvLowWater = find(R.id.tvLowWater);
        tvPump = find(R.id.tvPump);
        tvMode = find(R.id.tvMode);
        tvOnline = find(R.id.tvOnline);
        tvLastSeen = find(R.id.tvLastSeen);
        tvReportedAt = find(R.id.tvReportedAt);
        tvSafety = find(R.id.tvSafety);
        tvAdvice = find(R.id.tvAdvice);

        deviceAdapter = new ArrayAdapter<>(ctx(), R.layout.item_spinner_dark, new ArrayList<String>());
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDevices.setAdapter(deviceAdapter);

        spDevices.setOnItemSelectedListener(new SimpleOnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnerReady || position < 0 || position >= devices.size()) {
                    return;
                }
                long deviceId = devices.get(position).device_id;
                if (deviceId != SessionManager.get().getDeviceId()) {
                    SessionManager.get().setDeviceId(deviceId);
                    refreshStatus();
                    refreshAdvice();
                }
            }
        });
        spinnerReady = true;

        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshStatus();
                refreshAdvice();
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spinRefreshIcon();
                if (isDemo()) {
                    toast("离线演示模式：展示本地缓存数据");
                    return;
                }
                if (deviceId() <= 0) {
                    toast("请先绑定设备");
                    return;
                }
                swipe.setRefreshing(true);
                refreshStatus();
                refreshAdvice();
            }
        });

        // 一级 tab 直接切换，不再新开页面
        bindTab(R.id.btnControl, R.id.nav_control);
        bindTab(R.id.btnAlarm, R.id.nav_alerts);
        bindTab(R.id.btnSettings, R.id.nav_settings);
        // 首页的 AI 浇水建议仍进建议详情页（AI 助手页只保留对话与历史）
        bindNav(R.id.btnAdvice, AdviceActivity.class);
        bindNav(R.id.btnAiChat, ChatActivity.class);
        bindNav(R.id.btnSchedule, ScheduleActivity.class);
        bindNav(R.id.btnHistory, HistoryActivity.class);
        bindNav(R.id.btnZones, ZonesActivity.class);
        bindNav(R.id.btnCalibration, CalibrationActivity.class);
        bindNav(R.id.btnReport, WaterReportActivity.class);
        bindNav(R.id.btnBind, BindActivity.class);

        renderDevices();
        renderAdvice(SessionManager.get().loadAdvice());
        StatusData cached = SessionManager.get().loadStatus();
        if (cached != null) {
            renderStatus(cached);
        }
    }

    private void bindNav(int viewId, final Class<?> target) {
        View view = find(viewId);
        if (view != null) {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    go(target);
                }
            });
        }
    }

    private void bindTab(int viewId, final int navItemId) {
        View view = find(viewId);
        if (view != null) {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).openTab(navItemId);
                    }
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            startLiveUpdates();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLiveUpdates();
    }

    @Override
    public void onDestroyView() {
        if (waterAnimator != null) {
            waterAnimator.cancel();
            waterAnimator = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            stopLiveUpdates();
        } else {
            startLiveUpdates();
        }
    }

    private void startLiveUpdates() {
        if (root == null) {
            return;
        }
        renderDevices();
        if (isDemo()) {
            // 离线演示：只展示本地缓存，不发起网络请求（假 token 会 401）
            tvSync.setText("演示模式");
            StatusData demo = SessionManager.get().loadStatus();
            if (demo != null) {
                renderStatus(demo);
            }
            return;
        }
        if (deviceId() > 0) {
            poller.start(REFRESH_INTERVAL_MS, new Runnable() {
                @Override
                public void run() {
                    refreshStatus();
                }
            });
            nextRefreshAt = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
            ticker.removeCallbacks(countdown);
            ticker.post(countdown);
        } else {
            tvSync.setText("未选择设备");
        }
    }

    private void stopLiveUpdates() {
        poller.stop();
        ticker.removeCallbacks(countdown);
    }

    private void renderDevices() {
        if (spDevices == null) {
            return;
        }
        devices.clear();
        devices.addAll(SessionManager.get().loadDevices());

        List<String> names = new ArrayList<>();
        for (DeviceItem item : devices) {
            names.add(item.sn == null ? ("设备 " + item.device_id) : item.sn);
        }
        deviceAdapter.clear();
        deviceAdapter.addAll(names);
        deviceAdapter.notifyDataSetChanged();

        if (devices.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("尚未绑定设备\n请点击下方「设备绑定」添加你的灌溉设备");
            show(tvHeroStatus, false);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        long current = SessionManager.get().getDeviceId();
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).device_id == current) {
                spDevices.setSelection(i);
                return;
            }
        }
    }

    /** 2004 / 解绑后：以 /api/app/devices 为准重建设备列表并校正 device_id */
    public void refreshDevicesFromServer() {
        if (isDemo()) {
            renderDevices();
            return;
        }
        NetworkModule.enqueue(NetworkModule.api().devices(), new ApiCallback<DevicesData>() {
            @Override
            public void onSuccess(DevicesData data) {
                List<DeviceItem> list = data == null ? null : data.devices;
                SessionManager.get().saveDevices(list);
                SessionManager sm = SessionManager.get();
                if (list == null || list.isEmpty()) {
                    sm.clearDeviceId();
                } else if (!sm.hasDevice(sm.getDeviceId())) {
                    sm.setDeviceId(list.get(0).device_id);
                }
                if (root == null) {
                    return;
                }
                renderDevices();
                if (deviceId() > 0) {
                    refreshStatus();
                    refreshAdvice();
                }
            }

            @Override
            public void onError(String message) {
                if (root != null) {
                    renderDevices();
                }
            }
        });
    }

    private void refreshStatus() {
        if (isDemo() || deviceId() <= 0 || root == null) {
            if (swipe != null) {
                swipe.setRefreshing(false);
            }
            return;
        }
        if (firstLoad) {
            progress.setVisibility(View.VISIBLE);
        }
        nextRefreshAt = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
        NetworkModule.enqueue(NetworkModule.api().status(deviceId()), this);
    }

    private void refreshAdvice() {
        if (isDemo() || deviceId() <= 0 || root == null) {
            return;
        }
        NetworkModule.enqueue(NetworkModule.api().advice(deviceId()), new ApiCallback<AdviceData>() {
            @Override
            public void onSuccess(AdviceData data) {
                SessionManager.get().saveAdvice(data);
                if (root != null) {
                    renderAdvice(data);
                }
            }

            @Override
            public void onError(String message) {
                if (root != null) {
                    renderAdvice(SessionManager.get().loadAdvice());
                }
            }
        });
    }

    @Override
    public void onSuccess(StatusData status) {
        firstLoad = false;
        if (root == null) {
            return;
        }
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        if (status == null) {
            onError("服务器返回为空");
            return;
        }
        SessionManager.get().saveStatus(status);
        tvOffline.setVisibility(View.GONE);
        renderStatus(status);
    }

    @Override
    public void onError(String message) {
        firstLoad = false;
        if (root == null) {
            return;
        }
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);

        StatusData cached = SessionManager.get().loadStatus();
        if (cached != null) {
            renderStatus(cached);
            tvOffline.setVisibility(View.VISIBLE);
            tvOffline.setText("网络异常 / 设备离线，以下为最后上报值（" + message + "）");
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("无法连接服务器\n" + message);
        }
    }

    private void renderStatus(StatusData s) {
        if (root == null || s == null) {
            return;
        }
        show(tvEmpty, false);

        StatusData.DeviceInfo device = s.device;
        StatusData.Metrics metrics = s.metrics;
        StatusData.Safety safety = s.safety;

        if (device != null) {
            boolean online = device.online == 1;
            tvOnline.setText(online ? "在线" : "离线");
            renderHeroStatus(online);
            styleChip(tvOnline, online ? R.color.primary_soft : R.color.banner_bg,
                    online ? R.color.online : R.color.warning);
            tvMode.setText("auto".equals(device.mode) ? "自动模式" : "手动模式");
            styleChip(tvMode, R.color.chip_bg, R.color.text_secondary);
            tvLastSeen.setText(device.last_seen_at == null ? "--" : device.last_seen_at);
        }

        if (metrics == null || metrics.isEmpty()) {
            ringSoil.setValue(null);
            ringTemp.setMetric(null, 0d, 50d, 1);
            ringHumi.setValue(null);
            ringLight.setValue(null);
            tvSoilValue.setText("--");
            tvTempValue.setText("--");
            tvHumiValue.setText("--");
            tvLightValue.setText("--");
            animateWater(null);
            tvPump.setText("--");
            styleChip(tvPump, R.color.chip_bg, R.color.text_secondary);
            tvReportedAt.setText("--");
            show(tvLowWater, false);
        } else {
            boolean lowWater = safety != null && safety.low_water;
            ringSoil.setValue(metrics.soil_pct);
            // 温度按 0~50 ℃ 映射到环形弧度，读数保留 1 位小数
            ringTemp.setMetric(metrics.temp, 0d, 50d, 1);
            ringHumi.setMetric(metrics.humi, 0d, 100d, 1);
            ringLight.setValue(metrics.light_pct);
            tvSoilValue.setText(metrics.soil_pct == null ? "--" : metrics.soil_pct + "%");
            tvTempValue.setText(metrics.temp == null ? "--"
                    : String.format(Locale.CHINA, "%.1f℃", metrics.temp));
            tvHumiValue.setText(metrics.humi == null ? "--"
                    : String.format(Locale.CHINA, "%.1f%%", metrics.humi));
            tvLightValue.setText(metrics.light_pct == null ? "--" : metrics.light_pct + "%");
            tvWater.setTextColor(ContextCompat.getColor(ctx(),
                    lowWater ? R.color.offline : R.color.text_primary));
            animateWater(metrics.water_pct);
            boolean pumping = metrics.pump != null && metrics.pump == 1;
            tvPump.setText(pumping ? "运行中" : "已停止");
            styleChip(tvPump, pumping ? R.color.primary_soft : R.color.chip_bg,
                    pumping ? R.color.online : R.color.text_secondary);
            tvReportedAt.setText(metrics.reported_at == null ? "--" : metrics.reported_at);
            show(tvLowWater, lowWater);
        }

        if (safety == null) {
            tvSafety.setText("--");
        } else if (safety.auto_paused) {
            tvSafety.setText("自动模式已暂停：" + (safety.pause_reason == null ? "传感器异常" : safety.pause_reason));
            tvSafety.setTextColor(ContextCompat.getColor(ctx(), R.color.offline));
        } else {
            tvSafety.setText("水位报警线 " + safety.low_water_pct + "% · 单次最长 "
                    + safety.pump_max_duration + " 秒 · 安全策略正常");
            tvSafety.setTextColor(ContextCompat.getColor(ctx(), R.color.text_secondary));
        }
    }

    /** 顶部标题栏右侧的在线状态胶囊，避免头部大面积留白 */
    private void renderHeroStatus(boolean online) {
        if (tvHeroStatus == null) {
            return;
        }
        tvHeroStatus.setVisibility(View.VISIBLE);
        tvHeroStatus.setText(online ? "在线" : "离线");
        Drawable background = tvHeroStatus.getBackground();
        if (background != null) {
            background = background.mutate();
            background.setTint(online ? 0x4DFFFFFF : 0x66EF9A9A);
            tvHeroStatus.setBackground(background);
        }
        tvHeroStatus.setTextColor(online ? Color.WHITE : 0xFFFFCDD2);
    }

    private void setupGauge(RingGaugeView gauge, String label, String unit, int colorRes) {
        if (gauge == null) {
            return;
        }
        gauge.setLabel(label);
        gauge.setUnit(unit);
        // 数值与名称放在格子右侧，环只负责图形
        gauge.setTextVisible(false);
        gauge.setProgressColor(ContextCompat.getColor(ctx(), colorRes));
    }

    /** 水位进度条与百分比读数一起做缓动动画 */
    private void animateWater(Integer target) {
        if (waterAnimator != null) {
            waterAnimator.cancel();
            waterAnimator = null;
        }
        if (target == null) {
            pbWater.setProgress(0);
            tvWater.setText("--");
            return;
        }
        final int goal = Math.max(0, Math.min(100, target));
        waterAnimator = ValueAnimator.ofInt(pbWater.getProgress(), goal);
        waterAnimator.setDuration(700L);
        waterAnimator.setInterpolator(new DecelerateInterpolator());
        waterAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();
                pbWater.setProgress(value);
                tvWater.setText(value + " %");
            }
        });
        waterAnimator.start();
    }

    private void spinRefreshIcon() {
        btnRefresh.animate()
                .rotationBy(360f)
                .setDuration(600L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void styleChip(TextView view, int bgColorRes, int textColorRes) {
        Drawable background = view.getBackground();
        if (background != null) {
            background = background.mutate();
            background.setTint(ContextCompat.getColor(ctx(), bgColorRes));
            view.setBackground(background);
        }
        view.setTextColor(ContextCompat.getColor(ctx(), textColorRes));
    }

    private void renderAdvice(AdviceData advice) {
        if (tvAdvice == null) {
            return;
        }
        if (advice == null) {
            tvAdvice.setText("暂无 AI 建议");
            return;
        }
        // 单行摘要：来源由 AI 页展示，这里只放结论理由
        tvAdvice.setText(advice.reason == null ? "--" : advice.reason);
    }
}

package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.AdviceData;
import com.example.smartirrigation.model.ControlData;
import com.example.smartirrigation.model.ControlRequest;
import com.example.smartirrigation.model.SettingsData;
import com.example.smartirrigation.model.StatusData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.button.MaterialButton;

/**
 * AI 建议卡片：GET /api/app/ai/advice → {water,duration_sec,reason,source,latency_ms}。
 * 降级策略（答辩重点）：
 * 1) 正常：展示服务端建议，source=ai 或 rule（服务端 AI 超时兜底）；
 * 2) 网络异常：展示上次缓存，并显式标注“缓存”；
 * 3) 无缓存：App 用本地安全规则兜底（source=local），绝不空白；
 * 4) 一键采纳仍调用 control 接口，由 PHP 安全策略（低水位/间隔/最大时长）二次校验。
 */
public class AdviceActivity extends BaseActivity implements ApiCallback<AdviceData> {

    private TextView tvDecision;
    private TextView tvDuration;
    private TextView tvReason;
    private TextView tvSource;
    private TextView tvLatency;
    private TextView tvFallbackHint;
    private MaterialButton btnAdopt;
    private MaterialButton btnRefresh;
    private ProgressBar progress;

    private AdviceData current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advice);
        setupPage("AI 浇水建议", "模型建议 · 规则兜底", 0);

        tvDecision = findViewById(R.id.tvDecision);
        tvDuration = findViewById(R.id.tvDuration);
        tvReason = findViewById(R.id.tvReason);
        tvSource = findViewById(R.id.tvSource);
        tvLatency = findViewById(R.id.tvLatency);
        tvFallbackHint = findViewById(R.id.tvFallbackHint);
        btnAdopt = findViewById(R.id.btnAdopt);
        btnRefresh = findViewById(R.id.btnRefresh);
        progress = findViewById(R.id.progress);

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadAdvice(true);
            }
        });
        btnAdopt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adopt();
            }
        });
        findViewById(R.id.btnAskAi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                go(ChatActivity.class);
            }
        });

        // 先用本机缓存铺底，避免刷新期间页面空白
        AdviceData cached = SessionManager.get().loadAdvice();
        if (cached != null && SessionManager.get().getAdviceDeviceId() == deviceId()) {
            render(cached, true);
        }
        loadAdvice(false);
    }

    /**
     * @param force true 表示用户主动点“重新生成”（60 秒节流）；
     *              false 表示进页面，命中缓存窗口时直接用缓存，不调用模型。
     */
    private void loadAdvice(boolean force) {
        if (deviceId() <= 0) {
            renderLocalFallback();
            return;
        }
        SessionManager session = SessionManager.get();
        if (force) {
            if (!session.canForceRefreshAdvice()) {
                toast("刚刚已生成过建议，请稍后再试");
                return;
            }
        } else if (!session.shouldAutoRefreshAdvice(deviceId())) {
            // 命中缓存窗口（10 分钟内 / 指标未明显变化），不消耗模型额度
            AdviceData cached = session.loadAdvice();
            if (cached != null) {
                render(cached, true);
                showHint("已使用 " + ageText(session.getAdviceAgeMs())
                        + "前的建议，指标无明显变化；如需最新请点“重新生成”");
            }
            return;
        }
        progress.setVisibility(View.VISIBLE);
        tvFallbackHint.setVisibility(View.GONE);
        NetworkModule.enqueue(NetworkModule.api().advice(deviceId()), this);
    }

    private String ageText(long ageMs) {
        if (ageMs == Long.MAX_VALUE) {
            return "较久";
        }
        long minutes = ageMs / 60000L;
        return minutes <= 0 ? "1 分钟" : minutes + " 分钟";
    }

    private void showHint(String text) {
        tvFallbackHint.setVisibility(View.VISIBLE);
        tvFallbackHint.setText(text);
    }

    @Override
    public void onSuccess(AdviceData data) {
        progress.setVisibility(View.GONE);
        if (data == null) {
            onError("服务器未返回建议");
            return;
        }
        // 记录设备 + 生成时间 + 依据指标快照，作为下次是否刷新的判断依据
        StatusData status = SessionManager.get().loadStatus();
        SessionManager.get().saveAdvice(deviceId(), data,
                status == null ? null : status.metrics);
        render(data, false);
    }

    @Override
    public void onError(String message) {
        progress.setVisibility(View.GONE);
        AdviceData cached = SessionManager.get().loadAdvice();
        if (cached != null) {
            render(cached, true);
            tvFallbackHint.setVisibility(View.VISIBLE);
            tvFallbackHint.setText("网络异常，展示上次建议（缓存）");
        } else {
            renderLocalFallback();
        }
    }

    /** 无网络无缓存：按本地安全规则给出保守建议，内容不空白 */
    private void renderLocalFallback() {
        StatusData status = SessionManager.get().loadStatus();
        SettingsData settings = SessionManager.get().loadSettings();

        int soilLow = settings == null ? 35 : settings.soil_low;
        int pumpMax = settings == null ? 60 : settings.pump_max_duration;
        Integer soil = null;
        if (status != null && status.metrics != null) {
            soil = status.metrics.soil_pct;
        }

        AdviceData advice = new AdviceData();
        advice.source = "local";
        advice.latency_ms = 0;
        if (soil != null && soil < soilLow) {
            advice.water = 1;
            advice.duration_sec = pumpMax;
            advice.reason = "网络不可用，按本地安全规则：土壤含水 " + soil + "% 低于阈值 "
                    + soilLow + "%，建议浇水 " + pumpMax + " 秒（仍需服务器安全校验）。";
        } else {
            advice.water = 0;
            advice.duration_sec = 0;
            advice.reason = soil == null
                    ? "网络不可用，且无缓存数据，按安全策略暂不浇水。"
                    : "网络不可用，按本地安全规则：土壤含水 " + soil + "% 不低于阈值 " + soilLow + "%，暂不浇水。";
        }
        render(advice, false);
        tvFallbackHint.setVisibility(View.VISIBLE);
        tvFallbackHint.setText("AI 接口不可用，已切换为本地安全规则兜底");
    }

    private void render(AdviceData advice, boolean fromCache) {
        current = advice;
        boolean water = advice.water == 1;
        tvDecision.setText(water ? "建议浇水" : "暂不浇水");
        tvDecision.setTextColor(ContextCompat.getColor(this, water ? R.color.online : R.color.info));
        tvDuration.setText(water ? "建议时长：" + advice.duration_sec + " 秒" : "建议时长：--");
        tvReason.setText(advice.reason == null ? "--" : advice.reason);
        tvSource.setText("来源：" + sourceText(advice.source) + (fromCache ? "（缓存）" : ""));
        tvLatency.setText("服务端耗时：" + advice.latency_ms + " ms");
        btnAdopt.setEnabled(water);
    }

    private String sourceText(String source) {
        if (source == null) {
            return "未知";
        }
        switch (source) {
            case "ai":
                return "AI 模型";
            case "rule":
                return "规则兜底";
            case "local":
                return "本地安全规则";
            default:
                return source;
        }
    }

    private void adopt() {
        if (current == null || current.water != 1) {
            toast("当前建议不需要浇水");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        btnAdopt.setEnabled(false);
        // 采纳仍走服务端控制接口，由 PHP 做安全校验
        ControlRequest request = new ControlRequest(deviceId(), "pump_on", current.duration_sec);
        NetworkModule.enqueue(NetworkModule.api().control(request), new ApiCallback<ControlData>() {
            @Override
            public void onSuccess(ControlData data) {
                progress.setVisibility(View.GONE);
                btnAdopt.setEnabled(true);
                toast("已采纳，命令已下发（cmd_id=" + (data == null ? "-" : data.cmd_id)
                        + "），请到控制页查看真实执行状态");
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                btnAdopt.setEnabled(true);
                toast("采纳失败：" + message);
            }
        });
    }
}

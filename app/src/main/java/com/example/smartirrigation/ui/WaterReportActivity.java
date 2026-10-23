package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.WaterReportData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.example.smartirrigation.util.DemoData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

/**
 * 用水报表：GET /api/app/water-report?device_id=&from=yyyy-MM-dd&to=yyyy-MM-dd。
 * 汇总总运行秒数 / 次数 / 估算升数，并列出每日明细。
 */
public class WaterReportActivity extends BaseActivity implements ApiCallback<WaterReportData> {

    private TextInputEditText etFrom;
    private TextInputEditText etTo;
    private MaterialButton btnQuery;
    private TextView tvTotal;
    private TextView tvDays;
    private TextView tvEmpty;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_report);
        setupPage("用水报表", "按时间段统计泵水时长", 0);

        etFrom = findViewById(R.id.etFromDate);
        etTo = findViewById(R.id.etToDate);
        btnQuery = findViewById(R.id.btnQueryReport);
        tvTotal = findViewById(R.id.tvTotal);
        tvDays = findViewById(R.id.tvDays);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);

        etFrom.setText(DemoData.date(6));
        etTo.setText(DemoData.date(0));

        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });

        WaterReportData cached = SessionManager.get().loadWaterReport();
        if (cached != null) {
            render(cached);
        }
        load();
    }

    private void load() {
        if (deviceId() <= 0) {
            return;
        }
        String from = text(etFrom);
        String to = text(etTo);
        if (!from.matches("\\d{4}-\\d{2}-\\d{2}") || !to.matches("\\d{4}-\\d{2}-\\d{2}")) {
            toast("日期格式应为 yyyy-MM-dd");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        NetworkModule.enqueue(NetworkModule.api().waterReport(deviceId(), from, to), this);
    }

    @Override
    public void onSuccess(WaterReportData data) {
        progress.setVisibility(View.GONE);
        if (data == null) {
            onError("服务器返回为空");
            return;
        }
        SessionManager.get().saveWaterReport(data);
        render(data);
    }

    @Override
    public void onError(String message) {
        progress.setVisibility(View.GONE);
        WaterReportData cached = SessionManager.get().loadWaterReport();
        if (cached != null) {
            render(cached);
            if (!isDemo()) {
                toast("网络异常，显示缓存报表");
            }
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("无法获取报表：" + message);
        }
    }

    private void render(WaterReportData data) {
        tvTotal.setText("总运行 " + data.total_pump_seconds + " 秒 · 共浇水 " + data.total_times
                + " 次 · 估算用水 " + String.format(Locale.CHINA, "%.2f", data.est_liters) + " L");

        List<WaterReportData.Day> days = data.days;
        if (days == null || days.isEmpty()) {
            tvDays.setText("");
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("该时段暂无用水记录");
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        StringBuilder sb = new StringBuilder();
        for (WaterReportData.Day d : days) {
            sb.append(d.date)
                    .append("    ").append(d.pump_seconds).append(" 秒")
                    .append("    ").append(d.times).append(" 次")
                    .append("    ").append(String.format(Locale.CHINA, "%.2f", d.est_liters)).append(" L\n");
        }
        tvDays.setText(sb.toString().trim());
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.HistoryData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.example.smartirrigation.util.SimpleOnItemSelectedListener;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史曲线：GET /api/app/device/history?device_id=&from=&to=&interval=。
 * interval: raw（最多 2000 点）/ 5m / 1h（服务端聚合）；浇水时刻（pump=1）用垂直标注线表示。
 */
public class HistoryActivity extends BaseActivity implements ApiCallback<HistoryData> {

    private LineChart chart;
    private ProgressBar progress;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipe;
    private Spinner spInterval;

    private final List<HistoryData.Point> currentPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        setupPage("历史曲线", "土壤湿度 · 温湿度 · 水位趋势", 0);

        chart = findViewById(R.id.chart);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);
        swipe = findViewById(R.id.swipe);
        spInterval = findViewById(R.id.spInterval);

        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.getAxisRight().setEnabled(false);

        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setAxisMaximum(100f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < currentPoints.size()) {
                    return timeLabel(currentPoints.get(index).t);
                }
                return "";
            }
        });

        spInterval.setOnItemSelectedListener(new SimpleOnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadHistory();
            }
        });
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadHistory();
            }
        });

        loadHistory();
    }

    private void loadHistory() {
        if (deviceId() <= 0) {
            return;
        }
        progress.setVisibility(View.VISIBLE);
        String interval = intervalValue(spInterval.getSelectedItemPosition());
        NetworkModule.enqueue(NetworkModule.api().history(deviceId(), from(), to(), interval), this);
    }

    private String intervalValue(int position) {
        switch (position) {
            case 1:
                return "5m";
            case 2:
                return "1h";
            default:
                return "raw";
        }
    }

    /** 默认查当天 00:00:00 ~ 现在 */
    private String from() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(c.getTime());
    }

    private String to() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    @Override
    public void onSuccess(HistoryData data) {
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        if (data == null) {
            onError("服务器返回为空");
            return;
        }
        SessionManager.get().saveHistory(data);
        render(data);
    }

    @Override
    public void onError(String message) {
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        HistoryData cached = SessionManager.get().loadHistory();
        if (cached != null) {
            render(cached);
            if (!isDemo()) {
                toast("网络异常，显示缓存曲线");
            }
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("无法获取历史数据：" + message);
        }
    }

    private void render(HistoryData data) {
        currentPoints.clear();
        if (data.points != null) {
            currentPoints.addAll(data.points);
        }
        if (currentPoints.size() < 2) {
            chart.clear();
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("暂无历史数据");
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        List<Entry> entries = new ArrayList<>();
        int lastIndex = 0;
        for (int i = 0; i < currentPoints.size(); i++) {
            Integer soil = currentPoints.get(i).soil_pct;
            if (soil != null) {
                entries.add(new Entry(i, soil));
                lastIndex = i;
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "土壤含水(%)");
        dataSet.setColor(getResources().getColor(R.color.primary));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        XAxis xAxis = chart.getXAxis();
        xAxis.removeAllLimitLines();
        for (int i = 0; i < currentPoints.size(); i++) {
            Integer pump = currentPoints.get(i).pump;
            if (pump != null && pump == 1) {
                LimitLine line = new LimitLine(i, "浇水");
                line.setLineColor(getResources().getColor(R.color.warning));
                line.setLineWidth(1.5f);
                line.setTextSize(9f);
                xAxis.addLimitLine(line);
            }
        }

        chart.setData(new LineData(dataSet));
        chart.setVisibleXRangeMaximum(Math.max(10f, Math.min(60f, lastIndex + 1f)));
        chart.invalidate();
    }

    private String timeLabel(String t) {
        if (t == null) {
            return "";
        }
        if (t.length() >= 16) {
            return t.substring(11, 16);
        }
        return t;
    }
}

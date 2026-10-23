package com.example.smartirrigation.ui;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.AlertItem;
import com.example.smartirrigation.model.AlertsData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.example.smartirrigation.util.SimpleOnItemSelectedListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 报警页（常驻 Fragment）：GET /api/app/alerts。
 * 切回 tab 自动刷新；离线回退缓存。
 */
public class AlarmFragment extends BaseFragment implements ApiCallback<AlertsData> {

    private Spinner spStatus;
    private RecyclerView rvAlerts;
    private AlertAdapter adapter;
    private TextView tvEmpty;
    private TextView tvSummary;
    private ProgressBar progress;
    private SwipeRefreshLayout swipe;

    private boolean firstLoad = true;

    @Override
    protected int layoutId() {
        return R.layout.fragment_alarm;
    }

    @Override
    protected String pageTitle() {
        return "报警记录";
    }

    @Override
    protected String pageSubtitle() {
        return "按状态筛选 · 下拉刷新";
    }

    @Override
    protected void onScreenReady(@NonNull View view) {
        spStatus = find(R.id.spStatus);
        rvAlerts = find(R.id.rvAlerts);
        tvEmpty = find(R.id.tvEmpty);
        tvSummary = find(R.id.tvSummary);
        progress = find(R.id.progress);
        swipe = find(R.id.swipe);

        rvAlerts.setLayoutManager(new LinearLayoutManager(ctx()));
        adapter = new AlertAdapter(new AlertAdapter.OnItemClickListener() {
            @Override
            public void onClick(AlertItem alert) {
                showDetail(alert);
            }
        });
        rvAlerts.setAdapter(adapter);

        spStatus.setOnItemSelectedListener(new SimpleOnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadAlerts();
            }
        });

        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadAlerts();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            loadAlerts();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadAlerts();
        }
    }

    private void loadAlerts() {
        if (deviceId() <= 0 || root == null || isDemo()) {
            if (root != null) {
                render(SessionManager.get().loadAlerts());
                swipe.setRefreshing(false);
            }
            return;
        }
        if (firstLoad) {
            progress.setVisibility(View.VISIBLE);
        }
        String status = spStatus.getSelectedItemPosition() == 0 ? "open" : null;
        NetworkModule.enqueue(NetworkModule.api().alerts(deviceId(), status, 1, 50), this);
    }

    @Override
    public void onSuccess(AlertsData data) {
        firstLoad = false;
        if (root == null) {
            return;
        }
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        if (data == null) {
            onError("服务器返回为空");
            return;
        }
        SessionManager.get().saveAlerts(data);
        render(data);
    }

    @Override
    public void onError(String message) {
        firstLoad = false;
        if (root == null) {
            return;
        }
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        AlertsData cached = SessionManager.get().loadAlerts();
        if (cached != null) {
            render(cached);
            if (!isDemo()) {
                toast("网络异常，显示缓存报警");
            }
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("无法获取报警：" + message);
        }
    }

    private void render(AlertsData data) {
        if (data == null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("暂无报警数据");
            return;
        }
        List<AlertItem> list = data.list == null ? new ArrayList<AlertItem>() : data.list;
        adapter.submitList(list);
        tvSummary.setText("未读 " + data.unread_count + " 条 · 共 " + data.total + " 条");
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        if (list.isEmpty()) {
            tvEmpty.setText("当前没有需要处理的报警");
        }
    }

    private void showDetail(AlertItem alert) {
        String level = "critical".equals(alert.level) ? "严重"
                : ("warning".equals(alert.level) ? "警告" : "提示");
        new AlertDialog.Builder(ctx())
                .setTitle(alert.title == null ? "报警详情" : alert.title)
                .setMessage("等级：" + level
                        + "\n类型：" + typeText(alert.type)
                        + "\n状态：" + (alert.isRead() ? "已读" : "未读")
                        + "\n时间：" + (alert.created_at == null ? "--" : alert.created_at)
                        + "\n\n" + (alert.message == null ? "--" : alert.message))
                .setPositiveButton("关闭", null)
                .show();
    }

    private String typeText(String type) {
        if (type == null) {
            return "--";
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
}

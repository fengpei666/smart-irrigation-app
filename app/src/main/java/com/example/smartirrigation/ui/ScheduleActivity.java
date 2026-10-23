package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.Schedule;
import com.example.smartirrigation.model.SchedulesData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.example.smartirrigation.util.SimpleOnItemSelectedListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * 灌溉计划：GET/POST /api/app/schedules。
 * type=time（time_of_day + weekdays）/ condition（condition_type + condition_value）/ rotation（interval_min）。
 * 重要口径：计划只保存到 PHP，由服务器 cron/tick.php 调度执行；App 不做本地定时。
 */
public class ScheduleActivity extends BaseActivity implements ApiCallback<SchedulesData> {

    private RecyclerView rvSchedules;
    private ScheduleAdapter adapter;
    private TextView tvEmpty;
    private ProgressBar progress;
    private SwipeRefreshLayout swipe;

    private TextInputEditText etName;
    private TextInputEditText etTimeOfDay;
    private TextInputEditText etWeekdays;
    private TextInputEditText etInterval;
    private TextInputEditText etConditionValue;
    private TextInputEditText etDuration;
    private Spinner spType;
    private Spinner spConditionType;
    private MaterialSwitch swEnabled;
    private MaterialButton btnSave;
    private MaterialButton btnCancelEdit;
    private TextView tvFormTitle;

    private Long editingId;
    private boolean firstLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        setupPage("灌溉计划", "由服务端按时调度执行", 0);

        rvSchedules = findViewById(R.id.rvSchedules);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);
        swipe = findViewById(R.id.swipe);

        etName = findViewById(R.id.etName);
        etTimeOfDay = findViewById(R.id.etTimeOfDay);
        etWeekdays = findViewById(R.id.etWeekdays);
        etInterval = findViewById(R.id.etInterval);
        etConditionValue = findViewById(R.id.etConditionValue);
        etDuration = findViewById(R.id.etDuration);
        spType = findViewById(R.id.spType);
        spConditionType = findViewById(R.id.spConditionType);
        swEnabled = findViewById(R.id.swEnabled);
        btnSave = findViewById(R.id.btnSaveSchedule);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
        tvFormTitle = findViewById(R.id.tvFormTitle);

        rvSchedules.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter(new ScheduleAdapter.OnScheduleActionListener() {
            @Override
            public void onToggle(Schedule schedule, boolean enabled) {
                schedule.enabled = enabled;
                saveScheduleInternal(schedule, false);
            }

            @Override
            public void onClick(Schedule schedule) {
                fillForm(schedule);
            }
        });
        rvSchedules.setAdapter(adapter);

        spType.setOnItemSelectedListener(new SimpleOnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyType(position);
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collectAndSave();
            }
        });
        btnCancelEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearForm();
            }
        });
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadSchedules();
            }
        });

        etWeekdays.setText("1111111");
        loadSchedules();
    }

    /** 0=time 1=condition 2=rotation：按类型启用对应输入项 */
    private void applyType(int position) {
        etTimeOfDay.setEnabled(position == 0);
        etWeekdays.setEnabled(position == 0);
        spConditionType.setEnabled(position == 1);
        etConditionValue.setEnabled(position == 1);
        etInterval.setEnabled(position == 2);
    }

    private void loadSchedules() {
        if (deviceId() <= 0) {
            swipe.setRefreshing(false);
            toast("请先绑定并选择设备");
            return;
        }
        if (firstLoad) {
            progress.setVisibility(View.VISIBLE);
        }
        NetworkModule.enqueue(NetworkModule.api().schedules(deviceId()), this);
    }

    @Override
    public void onSuccess(SchedulesData data) {
        firstLoad = false;
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        if (data == null) {
            // 服务端可能用 data:null 表示“没有计划”，按空列表处理，不能当成网络异常
            data = new SchedulesData();
            data.schedules = new ArrayList<>();
        }
        SessionManager.get().saveSchedules(data);
        render(data);
    }

    @Override
    public void onError(String message) {
        firstLoad = false;
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        SchedulesData cached = SessionManager.get().loadSchedules();
        if (cached != null) {
            render(cached);
            if (!isDemo()) {
                // 带上真实原因，方便定位是超时、参数还是解析问题
                toast("计划同步失败：" + brief(message) + "，当前显示缓存");
            }
        } else {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("无法获取计划：" + message);
        }
    }

    private String brief(String message) {
        if (TextUtils.isEmpty(message)) {
            return "网络异常";
        }
        return message.length() > 40 ? message.substring(0, 40) + "…" : message;
    }

    private void render(SchedulesData data) {
        List<Schedule> list = data.schedules == null ? new ArrayList<Schedule>() : data.schedules;
        adapter.submitList(list);
        boolean empty = list.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            tvEmpty.setText("还没有灌溉计划，可在上方新建");
        }
    }

    private void fillForm(Schedule s) {
        editingId = s.id;
        etName.setText(s.name);
        spType.setSelection("condition".equals(s.type) ? 1 : ("rotation".equals(s.type) ? 2 : 0));
        etTimeOfDay.setText(s.time_of_day == null ? "" : s.time_of_day);
        etWeekdays.setText(TextUtils.isEmpty(s.weekdays) ? "1111111" : s.weekdays);
        etInterval.setText(s.interval_min == null ? "" : String.valueOf(s.interval_min));
        spConditionType.setSelection(conditionIndex(s.condition_type));
        etConditionValue.setText(s.condition_value == null ? "" : String.valueOf(s.condition_value));
        etDuration.setText(String.valueOf(s.duration_sec));
        swEnabled.setChecked(s.enabled);
        tvFormTitle.setText("编辑计划 #" + s.id);
        btnCancelEdit.setVisibility(View.VISIBLE);
    }

    private void clearForm() {
        editingId = null;
        etName.setText("");
        etTimeOfDay.setText("");
        etWeekdays.setText("1111111");
        etInterval.setText("");
        etConditionValue.setText("");
        etDuration.setText("");
        spType.setSelection(0);
        spConditionType.setSelection(0);
        swEnabled.setChecked(true);
        tvFormTitle.setText("新建计划");
        btnCancelEdit.setVisibility(View.GONE);
    }

    private int conditionIndex(String conditionType) {
        if (conditionType == null) {
            return 0;
        }
        switch (conditionType) {
            case "temp_above":
                return 1;
            case "humi_below":
                return 2;
            case "light_below":
                return 3;
            default:
                return 0;
        }
    }

    private String conditionValue(int index) {
        switch (index) {
            case 1:
                return "temp_above";
            case 2:
                return "humi_below";
            case 3:
                return "light_below";
            default:
                return "soil_below";
        }
    }

    private void collectAndSave() {
        if (deviceId() <= 0) {
            toast("请先绑定设备");
            return;
        }
        String name = text(etName);
        if (TextUtils.isEmpty(name)) {
            toast("请填写计划名称");
            return;
        }
        int duration;
        try {
            duration = Integer.parseInt(text(etDuration));
        } catch (NumberFormatException e) {
            toast("请填写有效浇水时长（秒）");
            return;
        }
        if (duration < 1) {
            toast("浇水时长必须大于 0");
            return;
        }

        Schedule s = new Schedule();
        s.id = editingId;
        s.device_id = deviceId();
        s.name = name;
        s.type = typeValue(spType.getSelectedItemPosition());
        s.duration_sec = duration;
        s.enabled = swEnabled.isChecked();

        if ("time".equals(s.type)) {
            String time = text(etTimeOfDay);
            if (!time.matches("\\d{2}:\\d{2}(:\\d{2})?")) {
                toast("定时时间格式应为 HH:mm[:ss]");
                return;
            }
            s.time_of_day = time.length() == 5 ? time + ":00" : time;
            String weekdays = text(etWeekdays);
            if (!weekdays.matches("[01]{7}")) {
                toast("weekdays 需为 7 位 0/1（周一~周日）");
                return;
            }
            s.weekdays = weekdays;
        } else if ("condition".equals(s.type)) {
            s.condition_type = conditionValue(spConditionType.getSelectedItemPosition());
            try {
                s.condition_value = Double.parseDouble(text(etConditionValue));
            } catch (NumberFormatException e) {
                toast("请填写有效的条件阈值");
                return;
            }
        } else {
            try {
                s.interval_min = Integer.parseInt(text(etInterval));
            } catch (NumberFormatException e) {
                toast("请填写轮灌间隔（分钟）");
                return;
            }
            if (s.interval_min < 1) {
                toast("轮灌间隔必须大于 0 分钟");
                return;
            }
        }
        saveScheduleInternal(s, true);
    }

    private String typeValue(int position) {
        switch (position) {
            case 1:
                return "condition";
            case 2:
                return "rotation";
            default:
                return "time";
        }
    }

    private void saveScheduleInternal(Schedule schedule, final boolean closeForm) {
        progress.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        NetworkModule.enqueue(NetworkModule.api().saveSchedule(schedule), new ApiCallback<Schedule>() {
            @Override
            public void onSuccess(Schedule data) {
                progress.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                // 强制口径：服务器调度，而非 App 到点执行
                toast("已保存到服务器，由服务器调度执行");
                if (closeForm) {
                    clearForm();
                }
                loadSchedules();
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                toast("保存失败：" + message);
                loadSchedules();
            }
        });
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

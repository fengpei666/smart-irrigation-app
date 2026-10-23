package com.example.smartirrigation.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.CommandStatusData;
import com.example.smartirrigation.model.ControlData;
import com.example.smartirrigation.model.ControlRequest;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.example.smartirrigation.util.Poller;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * 控制页（常驻 Fragment）：pump_on / pump_off / emergency_stop / auto / manual + 命令回执轮询。
 * 切走 tab 时停止轮询，切回若命令未到终态自动恢复轮询。
 */
public class ControlFragment extends BaseFragment {

    /** 状态查询最长持续时间：超过后停止轮询，避免无限刷接口 */
    private static final long ACK_TIMEOUT_MS = 90 * 1000L;
    /** 下发后 15 秒恢复按钮，设备迟迟不确认时界面不会像“卡死” */
    private static final long BUTTON_UNLOCK_MS = 15 * 1000L;
    private static final long POLL_INTERVAL_MS = 2000L;
    /** 按住出水时先按服务端允许的上限下发，松手再发 pump_off，避免超时自动断水前一直是“不确定时长” */
    private static final int HOLD_MAX_SEC = 60;

    private Chip chip5;
    private Chip chip10;
    private Chip chip15;
    private MaterialButton btnPumpOn;
    private MaterialButton btnPumpOff;
    private MaterialButton btnEmergency;
    private MaterialButton btnAuto;
    private MaterialButton btnManual;
    private TextView tvCmdState;
    private TextView tvCmdDetail;
    private TextView tvRecent;
    private ProgressBar progress;
    private MaterialButton btnHoldWater;
    private View waterProgressBox;
    private TextView tvWaterCountdown;
    private ProgressBar pbWater;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    /** 倒计时/按住状态 */
    private Runnable countdownTick;
    private int countdownTotalSec;
    private long countdownEndAt;
    private boolean holdPressed;
    private boolean holdPumpOnPending;
    private boolean holdStopPending;

    private final Poller poller = new Poller();
    private long cmdId;
    private long cmdStartAt;
    private boolean cmdFinished = true;
    private int selectedDuration = 5;

    /** 同一条命令的状态查询不允许并发，避免弱网下请求堆积导致界面卡顿 */
    private boolean queryInFlight;
    private boolean buttonsUnlocked;
    private String lastCmdState = "";
    private String lastCmdDetail = "";

    @Override
    protected int layoutId() {
        return R.layout.fragment_control;
    }

    @Override
    protected String pageTitle() {
        return "远程控制";
    }

    @Override
    protected String pageSubtitle() {
        return "命令回执 2 秒轮询 · 服务器安全校验";
    }

    @Override
    protected void onScreenReady(@NonNull View view) {
        chip5 = find(R.id.chip5);
        chip10 = find(R.id.chip10);
        chip15 = find(R.id.chip15);
        btnPumpOn = find(R.id.btnPumpOn);
        btnPumpOff = find(R.id.btnPumpOff);
        btnEmergency = find(R.id.btnEmergency);
        btnAuto = find(R.id.btnAuto);
        btnManual = find(R.id.btnManual);
        tvCmdState = find(R.id.tvCmdState);
        tvCmdDetail = find(R.id.tvCmdDetail);
        tvRecent = find(R.id.tvRecent);
        progress = find(R.id.progress);
        btnHoldWater = find(R.id.btnHoldWater);
        waterProgressBox = find(R.id.waterProgressBox);
        tvWaterCountdown = find(R.id.tvWaterCountdown);
        pbWater = find(R.id.pbWater);

        View.OnClickListener durationClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == chip5) {
                    selectedDuration = 5;
                } else if (v == chip10) {
                    selectedDuration = 10;
                } else {
                    selectedDuration = 15;
                }
            }
        };
        chip5.setOnClickListener(durationClick);
        chip10.setOnClickListener(durationClick);
        chip15.setOnClickListener(durationClick);

        btnPumpOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendControl("pump_on", selectedDuration);
            }
        });
        btnPumpOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendControl("pump_off", null);
            }
        });
        btnEmergency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendControl("emergency_stop", null);
            }
        });
        btnAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendControl("auto", null);
            }
        });
        btnManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendControl("manual", null);
            }
        });

        // 按住出水：按下即开泵，松手立即停泵
        btnHoldWater.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        beginHold();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        endHold();
                        v.performClick();
                        return true;
                    default:
                        return false;
                }
            }
        });

        renderRecent();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden() && cmdId > 0 && !cmdFinished) {
            setButtonsEnabled(false);
            startPolling();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        poller.stop();
        // 离开页面时不能把水泵撂在“按住”状态
        if (holdPressed) {
            endHold();
        }
        hideWaterProgress();
    }

    @Override
    public void onDestroyView() {
        poller.stop();
        stopCountdownTick();
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            poller.stop();
            if (holdPressed) {
                endHold();
            }
            hideWaterProgress();
        } else if (cmdId > 0 && !cmdFinished) {
            setButtonsEnabled(false);
            startPolling();
        }
    }

    private void sendControl(String action, Integer durationSec) {
        if (deviceId() <= 0) {
            toast("请先绑定设备");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);
        tvCmdState.setText("命令下发中…");
        tvCmdState.setTextColor(ContextCompat.getColor(ctx(), R.color.text_secondary));
        tvCmdDetail.setText("");

        ControlRequest request = new ControlRequest(deviceId(), action, durationSec);
        NetworkModule.enqueue(NetworkModule.api().control(request), new ApiCallback<ControlData>() {
            @Override
            public void onSuccess(ControlData data) {
                if (root == null) {
                    return;
                }
                progress.setVisibility(View.GONE);
                if (data == null) {
                    onError("服务器未返回命令信息");
                    return;
                }
                cmdId = data.cmd_id;
                cmdStartAt = SystemClock.elapsedRealtime();
                cmdFinished = false;
                queryInFlight = false;
                buttonsUnlocked = false;
                lastCmdState = "";
                lastCmdDetail = "";
                tvCmdState.setText("命令已下发（cmd_id=" + data.cmd_id + "），等待设备取走…");
                tvCmdState.setTextColor(ContextCompat.getColor(ctx(), R.color.warning));
                tvCmdDetail.setText("动作：" + actionText(data.action) + "\n过期时间：" + safe(data.expire_at));
                // 命令已被服务端受理：按本次时长走进度条
                if ("pump_on".equals(action)) {
                    startWaterCountdown(durationSec == null ? selectedDuration : durationSec);
                } else if ("pump_off".equals(action) || "emergency_stop".equals(action)) {
                    stopWaterCountdown("已停止浇水");
                }
                toast("命令已下发，正在等待设备确认");
                startPolling();
            }

            @Override
            public void onError(String message) {
                if (root == null) {
                    return;
                }
                progress.setVisibility(View.GONE);
                setButtonsEnabled(true);
                tvCmdState.setText("下发失败");
                tvCmdState.setTextColor(ContextCompat.getColor(ctx(), R.color.offline));
                tvCmdDetail.setText(message);
            }
        });
    }

    // ---------------- 按住出水 / 浇水倒计时 ----------------

    /** 按下：按允许的上限开泵，松手再发停止 */
    private void beginHold() {
        if (root == null || holdPressed) {
            return;
        }
        if (deviceId() <= 0) {
            toast("请先绑定设备");
            return;
        }
        holdPressed = true;
        holdStopPending = false;
        holdPumpOnPending = true;
        btnHoldWater.setText("正在浇水…松手停止");
        showWaterProgress(true, "正在浇水 · 松手停止");
        NetworkModule.enqueue(NetworkModule.api().control(
                        new ControlRequest(deviceId(), "pump_on", HOLD_MAX_SEC)),
                new ApiCallback<ControlData>() {
                    @Override
                    public void onSuccess(ControlData data) {
                        if (root == null) {
                            return;
                        }
                        holdPumpOnPending = false;
                        if (data != null) {
                            tvCmdState.setText("按住浇水中（cmd_id=" + data.cmd_id + "）");
                            tvCmdState.setTextColor(ContextCompat.getColor(ctx(), R.color.warning));
                            tvCmdDetail.setText("动作：启动水泵\n松手后立即发送停止指令");
                        }
                        if (holdStopPending) {
                            holdStopPending = false;
                            sendHoldOff();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (root == null) {
                            return;
                        }
                        holdPumpOnPending = false;
                        holdStopPending = false;
                        holdPressed = false;
                        btnHoldWater.setText("按住浇水 · 松手停止");
                        hideWaterProgress();
                        toast("按住浇水失败：" + message);
                    }
                });
    }

    /** 松手：开泵请求还没回来时挂起，等它成功后再补发停止，避免水一直流 */
    private void endHold() {
        if (!holdPressed) {
            return;
        }
        holdPressed = false;
        if (btnHoldWater != null) {
            btnHoldWater.setText("按住浇水 · 松手停止");
        }
        if (holdPumpOnPending) {
            holdStopPending = true;
            return;
        }
        sendHoldOff();
    }

    private void sendHoldOff() {
        if (deviceId() <= 0) {
            return;
        }
        NetworkModule.enqueue(NetworkModule.api().control(
                        new ControlRequest(deviceId(), "pump_off", null)),
                new ApiCallback<ControlData>() {
                    @Override
                    public void onSuccess(ControlData data) {
                        if (root == null) {
                            return;
                        }
                        tvCmdState.setText("已松手，水泵已停止");
                        tvCmdState.setTextColor(ContextCompat.getColor(ctx(), R.color.info));
                        stopWaterCountdown("已松手，水泵已停止");
                    }

                    @Override
                    public void onError(String message) {
                        if (root == null) {
                            return;
                        }
                        tvCmdState.setText("停止指令发送失败");
                        tvCmdState.setTextColor(ContextCompat.getColor(ctx(), R.color.offline));
                        tvCmdDetail.setText(message);
                        stopWaterCountdown("停止指令发送失败，请点“停止水泵”重试");
                    }
                });
    }

    /** 点击启动水泵后：按总时长走进度条 */
    private void startWaterCountdown(int seconds) {
        stopCountdownTick();
        countdownTotalSec = Math.max(1, seconds);
        countdownEndAt = SystemClock.elapsedRealtime() + countdownTotalSec * 1000L;
        showWaterProgress(false, "正在浇水 · 剩余 " + countdownTotalSec + " 秒");
        final long totalMs = countdownTotalSec * 1000L;
        countdownTick = new Runnable() {
            @Override
            public void run() {
                if (waterProgressBox == null) {
                    return;
                }
                long remain = countdownEndAt - SystemClock.elapsedRealtime();
                if (remain <= 0) {
                    pbWater.setProgress(100);
                    tvWaterCountdown.setText("本次浇水已结束（" + countdownTotalSec + " 秒）");
                    countdownTick = null;
                    return;
                }
                pbWater.setProgress((int) ((totalMs - remain) * 100 / totalMs));
                tvWaterCountdown.setText("正在浇水 · 剩余 " + ((remain + 999) / 1000) + " 秒");
                uiHandler.postDelayed(this, 200);
            }
        };
        uiHandler.post(countdownTick);
    }

    private void stopWaterCountdown(String text) {
        stopCountdownTick();
        if (waterProgressBox == null) {
            return;
        }
        if (text == null || text.isEmpty()) {
            hideWaterProgress();
            return;
        }
        waterProgressBox.setVisibility(View.VISIBLE);
        pbWater.setIndeterminate(false);
        pbWater.setProgress(100);
        tvWaterCountdown.setText(text);
    }

    private void showWaterProgress(boolean indeterminate, String text) {
        if (waterProgressBox == null) {
            return;
        }
        waterProgressBox.setVisibility(View.VISIBLE);
        pbWater.setIndeterminate(indeterminate);
        pbWater.setProgress(0);
        tvWaterCountdown.setText(text);
    }

    private void hideWaterProgress() {
        stopCountdownTick();
        if (waterProgressBox != null) {
            waterProgressBox.setVisibility(View.GONE);
        }
    }

    private void stopCountdownTick() {
        if (countdownTick != null) {
            uiHandler.removeCallbacks(countdownTick);
            countdownTick = null;
        }
    }

    private void startPolling() {
        queryInFlight = false;
        poller.start(POLL_INTERVAL_MS, new Runnable() {
            @Override
            public void run() {
                pollCommand();
            }
        });
    }

    private void pollCommand() {
        if (cmdId <= 0 || cmdFinished || root == null) {
            poller.stop();
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - cmdStartAt;
        if (elapsed > ACK_TIMEOUT_MS) {
            cmdFinished = true;
            poller.stop();
            setButtonsEnabled(true);
            tvCmdState.setText("状态查询已停止（90 秒未到终态）");
            tvCmdState.setTextColor(ContextCompat.getColor(ctx(), R.color.warning));
            tvCmdDetail.setText("请到首页查看设备实时状态，或重新下发命令。");
            return;
        }
        // 设备长时间不确认时先恢复按钮，避免用户觉得界面“卡住”
        if (!buttonsUnlocked && elapsed > BUTTON_UNLOCK_MS) {
            buttonsUnlocked = true;
            setButtonsEnabled(true);
        }
        // 上一次查询还没回来就跳过本轮，防止弱网下请求堆积
        if (queryInFlight) {
            return;
        }
        queryInFlight = true;
        NetworkModule.enqueue(NetworkModule.api().commandStatus(deviceId(), cmdId),
                new ApiCallback<CommandStatusData>() {
                    @Override
                    public void onSuccess(CommandStatusData data) {
                        queryInFlight = false;
                        if (data == null || root == null) {
                            return;
                        }
                        renderCommand(data);
                        if (data.isTerminal()) {
                            cmdFinished = true;
                            poller.stop();
                            setButtonsEnabled(true);
                            appendLog(data);
                            toast("命令终态：" + data.statusText());
                        }
                    }

                    @Override
                    public void onError(String message) {
                        queryInFlight = false;
                        if (root != null) {
                            String text = "状态查询失败：" + message + "（自动重试中）";
                            if (!text.equals(lastCmdDetail)) {
                                lastCmdDetail = text;
                                tvCmdDetail.setText(text);
                            }
                        }
                    }
                });
    }

    /** 只在文本真的变化时才 setText，减少 2 秒轮询引发的重排 */
    private void renderCommand(CommandStatusData c) {
        String state = c.statusText();
        if (!state.equals(lastCmdState)) {
            lastCmdState = state;
            tvCmdState.setText(state);
            tvCmdState.setTextColor(ContextCompat.getColor(ctx(), stateColor(c.status)));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("cmd_id：").append(c.cmd_id).append("\n");
        sb.append("动作：").append(actionText(c.action)).append("\n");
        sb.append("下发时间：").append(safe(c.sent_at)).append("\n");
        sb.append("确认时间：").append(safe(c.ack_at)).append("\n");
        sb.append("执行结果：").append(safe(c.result));
        String detail = sb.toString();
        if (!detail.equals(lastCmdDetail)) {
            lastCmdDetail = detail;
            tvCmdDetail.setText(detail);
        }
    }

    private void appendLog(CommandStatusData c) {
        List<CommandStatusData> log = SessionManager.get().loadCommandLog();
        log.add(0, c);
        while (log.size() > 20) {
            log.remove(log.size() - 1);
        }
        SessionManager.get().saveCommandLog(log);
        renderRecent();
    }

    private void renderRecent() {
        if (tvRecent == null) {
            return;
        }
        List<CommandStatusData> log = SessionManager.get().loadCommandLog();
        if (log.isEmpty()) {
            tvRecent.setText("暂无命令记录");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (CommandStatusData c : log) {
            sb.append("· #").append(c.cmd_id).append(" ").append(actionText(c.action))
                    .append(" → ").append(c.statusText()).append("\n");
        }
        tvRecent.setText(sb.toString().trim());
    }

    private int stateColor(String status) {
        if (status == null) {
            return R.color.text_secondary;
        }
        switch (status) {
            case "pending":
            case "sent":
                return R.color.warning;
            case "ack":
                return R.color.info;
            case "done":
                return R.color.online;
            default:
                return R.color.offline;
        }
    }

    private String actionText(String action) {
        if (action == null) {
            return "--";
        }
        switch (action) {
            case "pump_on":
                return "启动水泵";
            case "pump_off":
                return "关闭水泵";
            case "emergency_stop":
                return "急停";
            case "auto":
                return "切换自动模式";
            case "manual":
                return "切换手动模式";
            default:
                return action;
        }
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "--" : value;
    }

    private void setButtonsEnabled(boolean enabled) {
        btnPumpOn.setEnabled(enabled);
        btnPumpOff.setEnabled(enabled);
        btnAuto.setEnabled(enabled);
        btnManual.setEnabled(enabled);
        // 急停任何时候都必须可点，不能被命令回执锁住
        btnEmergency.setEnabled(true);
    }
}

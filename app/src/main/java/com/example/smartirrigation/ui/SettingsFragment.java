package com.example.smartirrigation.ui;

import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.smartirrigation.BuildConfig;
import com.example.smartirrigation.R;
import com.example.smartirrigation.model.SettingsData;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 设置页分两块：
 * 1) 灌溉：自动灌溉开关（保存到服务器）；
 * 2) 应用设置 + 关于：应用锁、方向锁定、版本与构建日期，全部只影响本机。
 */
public class SettingsFragment extends BaseFragment {

    private MaterialSwitch swAuto;
    private MaterialSwitch swAppLock;
    private MaterialSwitch swOrientationLock;
    private TextView tvError;
    private MaterialButton btnSave;
    private ProgressBar progress;

    private SettingsData current;
    /** 代码里回填开关状态时不触发监听 */
    private boolean binding;

    @Override
    protected int layoutId() {
        return R.layout.fragment_settings;
    }

    @Override
    protected String pageTitle() {
        return "设置";
    }

    @Override
    protected String pageSubtitle() {
        return "灌溉 · 应用锁 · 关于";
    }

    @Override
    protected void onScreenReady(@NonNull View view) {
        swAuto = find(R.id.swAuto);
        swAppLock = find(R.id.swAppLock);
        swOrientationLock = find(R.id.swOrientationLock);
        tvError = find(R.id.tvError);
        btnSave = find(R.id.btnSave);
        progress = find(R.id.progress);

        fillAbout();
        bindSwitches();

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAutoIrrigation();
            }
        });

        loadInitial();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadInitial();
        }
    }

    /** 版本与构建日期由构建配置注入 */
    private void fillAbout() {
        TextView tvAppVersion = find(R.id.tvAppVersion);
        TextView tvVersionName = find(R.id.tvVersionName);
        TextView tvBuildDate = find(R.id.tvBuildDate);
        if (tvAppVersion != null) {
            tvAppVersion.setText("v" + BuildConfig.VERSION_NAME);
        }
        if (tvVersionName != null) {
            tvVersionName.setText(BuildConfig.VERSION_NAME);
        }
        if (tvBuildDate != null) {
            tvBuildDate.setText(BuildConfig.BUILD_DATE);
        }
    }

    private void bindSwitches() {
        SessionManager sm = SessionManager.get();
        binding = true;
        swAppLock.setChecked(sm.isAppLockEnabled());
        swOrientationLock.setChecked(sm.isOrientationLocked());
        binding = false;

        swAppLock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (binding) {
                    return;
                }
                if (isChecked) {
                    if (SessionManager.get().getAppLockPin().isEmpty()) {
                        promptSetPin();
                    } else {
                        SessionManager.get().setAppLockEnabled(true);
                        toast("应用锁已开启");
                    }
                } else {
                    SessionManager.get().setAppLockEnabled(false);
                    SessionManager.get().setAppLockPin("");
                    toast("应用锁已关闭");
                }
            }
        });

        swOrientationLock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (binding) {
                    return;
                }
                SessionManager.get().setOrientationLocked(isChecked);
                if (getActivity() != null) {
                    getActivity().setRequestedOrientation(isChecked
                            ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
                toast(isChecked ? "已锁定竖屏" : "已恢复跟随系统旋转");
            }
        });
    }

    /** 开启应用锁时设置 4 位密码 */
    private void promptSetPin() {
        final EditText input = new EditText(ctx());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("4 位数字密码");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        FrameLayout container = new FrameLayout(ctx());
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(density * 20);
        container.setPadding(pad, Math.round(density * 6), pad, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(ctx())
                .setTitle("设置应用锁密码")
                .setView(container)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pin = input.getText().toString().trim();
                        if (pin.length() != 4) {
                            toast("请输入 4 位数字密码");
                            resetAppLockSwitch(false);
                            return;
                        }
                        SessionManager.get().setAppLockPin(pin);
                        SessionManager.get().setAppLockEnabled(true);
                        toast("应用锁已开启，下次启动需解锁");
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resetAppLockSwitch(false);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        resetAppLockSwitch(false);
                    }
                })
                .show();
    }

    private void resetAppLockSwitch(boolean checked) {
        binding = true;
        swAppLock.setChecked(checked);
        binding = false;
    }

    private void loadInitial() {
        if (root == null) {
            return;
        }
        SettingsData cached = SessionManager.get().loadSettings();
        if (cached == null) {
            cached = defaults();
        }
        cached.device_id = deviceId();
        current = cached;
        binding = true;
        swAuto.setChecked(cached.auto_enabled == 1);
        binding = false;
    }

    private SettingsData defaults() {
        SettingsData s = new SettingsData();
        s.auto_enabled = 1;
        s.soil_low = 35;
        s.soil_high = 70;
        s.low_water_pct = 20;
        s.report_interval = 60;
        s.pump_max_duration = 60;
        return s;
    }

    /** 只改 auto_enabled，其它阈值沿用服务器上次下发的值 */
    private void saveAutoIrrigation() {
        if (deviceId() <= 0) {
            toast("请先绑定设备");
            return;
        }
        SettingsData s = current == null ? defaults() : current;
        s.device_id = deviceId();
        s.auto_enabled = swAuto.isChecked() ? 1 : 0;

        show(tvError, false);
        progress.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        NetworkModule.enqueue(NetworkModule.api().saveSettings(s), new ApiCallback<SettingsData>() {
            @Override
            public void onSuccess(SettingsData data) {
                if (root == null) {
                    return;
                }
                progress.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                if (data != null) {
                    current = data;
                    SessionManager.get().saveSettings(data);
                    binding = true;
                    swAuto.setChecked(data.auto_enabled == 1);
                    binding = false;
                }
                toast("灌溉设置已保存到服务器");
            }

            @Override
            public void onError(String message) {
                if (root == null) {
                    return;
                }
                progress.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                showError("保存失败：" + message);
            }
        });
    }

    private void showError(String message) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(message);
    }
}

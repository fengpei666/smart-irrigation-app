package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.DeviceItem;
import com.example.smartirrigation.model.DevicesData;
import com.example.smartirrigation.model.LoginData;
import com.example.smartirrigation.model.LoginRequest;
import com.example.smartirrigation.model.RegisterRequest;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.example.smartirrigation.util.DemoData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * 登录 / 注册 / 演示入口。
 * 同步规则：登录成功先存 devices 快照，紧接着调 GET /api/app/devices 覆盖（唯一可信来源）。
 */
public class LoginActivity extends BaseActivity implements ApiCallback<LoginData> {

    private TextInputEditText etAccount;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnDemo;
    private MaterialButton btnRegister;
    private ProgressBar progress;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etAccount = findViewById(R.id.etAccount);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnDemo = findViewById(R.id.btnDemo);
        btnRegister = findViewById(R.id.btnRegister);
        progress = findViewById(R.id.progress);
        tvError = findViewById(R.id.tvError);
        ((TextView) findViewById(R.id.tvServer)).setText("服务器：" + NetworkModule.BASE_URL);

        if (SessionManager.get().isLoggedIn()) {
            // 演示账号的 token 是假的，不能拿去请求服务端（会 401 并清掉登录态）
            if (SessionManager.get().isDemoMode()) {
                route();
            } else {
                syncDevicesThenRoute();
            }
            return;
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });

        btnDemo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DemoData.seed();
                toast("已进入离线演示（缓存数据）");
                go(MainActivity.class);
                finish();
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRegisterDialog();
            }
        });
    }

    private void attemptLogin() {
        String account = text(etAccount);
        String password = text(etPassword);
        if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
            showError("请输入账号和密码");
            return;
        }
        show(progress, true);
        btnLogin.setEnabled(false);
        NetworkModule.enqueue(NetworkModule.api().login(new LoginRequest(account, password, "android")), this);
    }

    private void showRegisterDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_register, null, false);
        final TextInputEditText etUsername = view.findViewById(R.id.etRegUsername);
        final TextInputEditText etRegPassword = view.findViewById(R.id.etRegPassword);
        final TextInputEditText etRegNickname = view.findViewById(R.id.etRegNickname);

        new AlertDialog.Builder(this)
                .setTitle("注册账号")
                .setView(view)
                .setNegativeButton("取消", null)
                .setPositiveButton("注册", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String username = etUsername.getText() == null ? "" : etUsername.getText().toString().trim();
                        String password = etRegPassword.getText() == null ? "" : etRegPassword.getText().toString();
                        String nickname = etRegNickname.getText() == null ? "" : etRegNickname.getText().toString().trim();
                        if (!username.matches("^[a-z0-9_]{3,32}$")) {
                            showError("用户名需为 3~32 位小写字母/数字/下划线");
                            return;
                        }
                        if (password.length() < 6) {
                            showError("密码至少 6 位");
                            return;
                        }
                        show(progress, true);
                        NetworkModule.enqueue(NetworkModule.api().register(
                                new RegisterRequest(username, password, nickname)), LoginActivity.this);
                    }
                })
                .show();
    }

    @Override
    public void onSuccess(LoginData data) {
        show(progress, false);
        btnLogin.setEnabled(true);
        if (data == null || TextUtils.isEmpty(data.user_token)) {
            onError("服务器未返回 user_token");
            return;
        }
        SessionManager sm = SessionManager.get();
        sm.saveLogin(data.user_token, data.user == null ? "用户" : data.user.nickname, data.expires_at);
        // 快照先落地，随后用 /api/app/devices 覆盖
        sm.saveDevices(data.devices);
        toast("登录成功");
        syncDevicesThenRoute();
    }

    @Override
    public void onError(String message) {
        show(progress, false);
        btnLogin.setEnabled(true);
        showError(message);
    }

    /** 登录后必做：拉设备列表（唯一可信来源），失败时才退回快照 */
    private void syncDevicesThenRoute() {
        NetworkModule.enqueue(NetworkModule.api().devices(), new ApiCallback<DevicesData>() {
            @Override
            public void onSuccess(DevicesData data) {
                List<DeviceItem> devices = data == null ? null : data.devices;
                SessionManager.get().saveDevices(devices);
                route();
            }

            @Override
            public void onError(String message) {
                route();
            }
        });
    }

    private void route() {
        List<DeviceItem> devices = SessionManager.get().loadDevices();
        if (devices.isEmpty()) {
            go(BindActivity.class);
            finish();
            return;
        }
        SessionManager sm = SessionManager.get();
        if (!sm.hasDevice(sm.getDeviceId())) {
            sm.setDeviceId(devices.get(0).device_id);
        }
        go(MainActivity.class);
        finish();
    }

    private void showError(String message) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(message);
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

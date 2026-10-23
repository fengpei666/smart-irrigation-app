package com.example.smartirrigation.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.smartirrigation.R;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

/**
 * 应用主壳：底部 5 个 tab 全部是常驻 Fragment（首页 / 控制 / AI / 告警 / 设置）。
 * 切 tab 只做 show/hide，不重建页面、不播放左右转场，滚动位置与输入状态都保留。
 * 二级页面（AI 建议/计划/历史/分区/标定/报表/绑定/AI 对话）仍是独立页面，带返回箭头。
 */
public class MainActivity extends BaseActivity {

    public static final String EXTRA_TAB = "extra_tab";
    public static final String EXTRA_RELOAD = "extra_reload";

    private static final String TAG_DASHBOARD = "tab_dashboard";
    private static final String TAG_CONTROL = "tab_control";
    private static final String TAG_AI = "tab_ai";
    private static final String TAG_ALARM = "tab_alarm";
    private static final String TAG_SETTINGS = "tab_settings";

    private BottomNavigationView bottomNav;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
                selectTab(item.getItemId());
                return true;
            }
        });

        int tab = getIntent().getIntExtra(EXTRA_TAB, R.id.nav_home);
        bottomNav.setSelectedItemId(tab);

        // 非首页按返回先回首页 tab，符合主流 APP 的导航习惯
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomNav.getSelectedItemId() != R.id.nav_home) {
                    bottomNav.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState == null) {
            requireUnlockIfNeeded();
        }

        // 在线更新：6 小时内只静默查一次，有新版本才提示；
        // 开了应用锁时不自动弹，避免两层弹窗叠在一起（设置页仍可手动检查）
        if (!SessionManager.get().isAppLockEnabled()) {
            UpdatePrompt.checkQuietly(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 用户从「安装未知应用」授权页返回后，自动继续安装
        UpdatePrompt.resumePendingInstall(this);
    }

    // ---------------- 应用锁 ----------------

    /** 设置里开启应用锁后，冷启动先要求输入 4 位密码 */
    private void requireUnlockIfNeeded() {
        SessionManager sm = SessionManager.get();
        if (!sm.isAppLockEnabled()) {
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("4 位密码");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        FrameLayout container = new FrameLayout(this);
        int pad = dp(20);
        container.setPadding(pad, dp(6), pad, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("应用已锁定")
                .setMessage("请输入 4 位密码解锁")
                .setView(container)
                .setCancelable(false)
                .setPositiveButton("解锁", null)
                .setNegativeButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        finish();
                    }
                })
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pin = input.getText().toString().trim();
                if (pin.equals(SessionManager.get().getAppLockPin())) {
                    dialog.dismiss();
                } else {
                    input.setText("");
                    input.setError("密码不正确");
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_RELOAD, false)) {
            showHomeAndReload();
            return;
        }
        int tab = intent.getIntExtra(EXTRA_TAB, 0);
        if (tab != 0) {
            bottomNav.setSelectedItemId(tab);
        }
    }

    /** 供 Fragment 内部跳转到其它 tab（不新开页面） */
    public void openTab(int itemId) {
        bottomNav.setSelectedItemId(itemId);
    }

    /** 切到首页并重新拉设备列表（用于 2004 / 解绑后） */
    public void showHomeAndReload() {
        bottomNav.setSelectedItemId(R.id.nav_home);
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_DASHBOARD);
        if (fragment instanceof DashboardFragment) {
            ((DashboardFragment) fragment).refreshDevicesFromServer();
        }
    }

    private void selectTab(int itemId) {
        String tag;
        if (itemId == R.id.nav_control) {
            tag = TAG_CONTROL;
        } else if (itemId == R.id.nav_ai) {
            tag = TAG_AI;
        } else if (itemId == R.id.nav_alerts) {
            tag = TAG_ALARM;
        } else if (itemId == R.id.nav_settings) {
            tag = TAG_SETTINGS;
        } else {
            tag = TAG_DASHBOARD;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            target = createFragment(tag);
        }
        if (target == currentFragment) {
            return;
        }
        FragmentTransaction transaction = fm.beginTransaction().setReorderingAllowed(true);
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }
        if (target.isAdded()) {
            transaction.show(target);
        } else {
            transaction.add(R.id.fragmentContainer, target, tag);
        }
        transaction.commit();
        currentFragment = target;
    }

    private Fragment createFragment(String tag) {
        switch (tag) {
            case TAG_CONTROL:
                return new ControlFragment();
            case TAG_AI:
                return new AiFragment();
            case TAG_ALARM:
                return new AlarmFragment();
            case TAG_SETTINGS:
                return new SettingsFragment();
            default:
                return new DashboardFragment();
        }
    }
}

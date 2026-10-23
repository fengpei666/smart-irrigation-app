package com.example.smartirrigation.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartirrigation.R;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 页面基类：
 * - 401 → 清 token，回登录页（任务栈清空）；
 * - 2004 → 清本地 device_id，回首页并重新拉 /api/app/devices；
 * - 提供 deviceId()/currentSn()/toast()/go()/show()。
 */
public abstract class BaseActivity extends AppCompatActivity
        implements NetworkModule.AuthListener, NetworkModule.DeviceListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NetworkModule.addAuthListener(this);
        NetworkModule.addDeviceListener(this);
        applyOrientationLock();
    }

    /** 跟随「设置 → 方向锁定」：开启时全部页面固定竖屏 */
    protected void applyOrientationLock() {
        setRequestedOrientation(SessionManager.get().isOrientationLocked()
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetworkModule.removeAuthListener(this);
        NetworkModule.removeDeviceListener(this);
    }

    @Override
    public void onAuthExpired() {
        if (SessionManager.get().isDemoMode()) {
            // 离线演示不涉及登录态失效，避免误弹提示
            return;
        }
        Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDeviceUnbound() {
        SessionManager.get().clearDeviceId();
        Toast.makeText(this, "设备未绑定到当前账号，正在刷新列表", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_RELOAD, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    /**
     * 统一页面外壳：可折叠标题栏 + 底部导航。
     *
     * @param title     页面标题（折叠后显示在工具栏）
     * @param subtitle  展开态副标题（设备、统计等概要信息）
     * @param navItemId 底部导航选中项；传 0 表示二级页（显示返回箭头、不显示底部导航选中）
     */
    protected void setupPage(String title, String subtitle, int navItemId) {
        TextView heroTitle = findViewById(R.id.tvHeroTitle);
        if (heroTitle != null) {
            heroTitle.setText(title);
        }
        // 二级页左侧有返回箭头，标题相应右移；一级页从 20dp 开始
        View hero = findViewById(R.id.heroContent);
        if (hero != null) {
            hero.setPaddingRelative(navItemId > 0 ? dp(20) : dp(68), 0, dp(20), 0);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            if (navItemId > 0) {
                // 一级页面：不显示返回箭头
                toolbar.setNavigationIcon(null);
            } else {
                toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
        }

    }

    protected int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    /** 离线演示模式：页面只消费本地缓存，不弹网络错误提示 */
    protected boolean isDemo() {
        return SessionManager.get().isDemoMode();
    }

    /** 业务接口一律显式传 device_id */
    protected long deviceId() {
        return SessionManager.get().getDeviceId();
    }

    @NonNull
    protected String currentSn() {
        return SessionManager.get().getCurrentSn();
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void go(Class<?> target) {
        startActivity(new Intent(this, target));
        // 二级页面统一右滑进入，避免各页转场不一致
        overridePendingTransition(R.anim.slide_in_right, R.anim.hold);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.hold, R.anim.slide_out_right);
    }

    protected void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

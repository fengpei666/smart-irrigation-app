package com.example.smartirrigation.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartirrigation.R;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 底部导航一级页面的基类（Fragment 版）。
 * - 页面常驻（show/hide），切 tab 不重建、不重播转场；
 * - 每个 Fragment 自带折叠标题栏，隐藏返回箭头；
 * - 401 / 2004 与 Activity 版行为一致。
 */
public abstract class BaseFragment extends Fragment
        implements NetworkModule.AuthListener, NetworkModule.DeviceListener {

    protected View root;

    protected abstract int layoutId();

    protected abstract String pageTitle();

    protected abstract String pageSubtitle();

    /** 视图已就绪，做绑定与首次加载 */
    protected abstract void onScreenReady(@NonNull View view);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(layoutId(), container, false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyHeader(pageTitle(), pageSubtitle());
        NetworkModule.addAuthListener(this);
        NetworkModule.addDeviceListener(this);
        onScreenReady(view);
    }

    @Override
    public void onDestroyView() {
        NetworkModule.removeAuthListener(this);
        NetworkModule.removeDeviceListener(this);
        super.onDestroyView();
        root = null;
    }

    private void applyHeader(String title, String subtitle) {
        // 头部只有一行标题，且不再使用 CollapsingToolbarLayout 自带标题，避免出现两条标题
        TextView heroTitle = find(R.id.tvHeroTitle);
        if (heroTitle != null) {
            heroTitle.setText(title);
        }
        MaterialToolbar toolbar = find(R.id.toolbar);
        if (toolbar != null) {
            // 一级页面：无返回箭头
            toolbar.setNavigationIcon(null);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends View> T find(int id) {
        return root == null ? null : (T) root.findViewById(id);
    }

    protected Context ctx() {
        return requireContext();
    }

    protected boolean isDemo() {
        return SessionManager.get().isDemoMode();
    }

    protected long deviceId() {
        return SessionManager.get().getDeviceId();
    }

    @NonNull
    protected String currentSn() {
        return SessionManager.get().getCurrentSn();
    }

    protected void toast(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    protected void go(Class<?> target) {
        startActivity(new Intent(requireContext(), target));
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.hold);
        }
    }

    protected void show(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onAuthExpired() {
        if (SessionManager.get().isDemoMode() || getActivity() == null) {
            return;
        }
        Toast.makeText(requireContext(), "登录已过期，请重新登录", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDeviceUnbound() {
        SessionManager.get().clearDeviceId();
        toast("设备未绑定到当前账号，正在刷新列表");
        if (this instanceof DashboardFragment) {
            ((DashboardFragment) this).refreshDevicesFromServer();
        } else if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showHomeAndReload();
        }
    }
}

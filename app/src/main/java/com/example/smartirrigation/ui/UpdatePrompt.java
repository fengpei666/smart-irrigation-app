package com.example.smartirrigation.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.smartirrigation.BuildConfig;
import com.example.smartirrigation.update.UpdateManager;
import com.example.smartirrigation.update.UpdateRelease;

import java.io.File;
import java.util.Locale;

/**
 * 在线更新的全部弹窗交互：
 * - 启动时静默检查（6 小时内只查一次，无新版本/失败都不打扰）；
 * - 设置页「检查更新」手动检查（无论结果都给反馈）；
 * - 发现新版本 → 显示版本说明 → 下载进度 → 拉起系统安装器。
 */
public final class UpdatePrompt {

    private static final String PREF = "update_state";
    private static final String KEY_LAST_CHECK = "last_check";
    /** 启动时自动检查的最小间隔，避免每次冷启动都打一次接口 */
    private static final long AUTO_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    private UpdatePrompt() {
    }

    // ---------------- 入口 ----------------

    /** 启动时静默检查：有新版本才弹窗，其余情况完全静默 */
    public static void checkQuietly(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        SharedPreferences sp = prefs(activity);
        long last = sp.getLong(KEY_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) {
            return;
        }
        sp.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

        UpdateManager.check(new UpdateManager.CheckCallback() {
            @Override
            public void onChecked(UpdateRelease release) {
                if (release != null && alive(activity)) {
                    showAvailable(activity, release);
                }
            }

            @Override
            public void onError(String message) {
                // 静默：网络问题不该在打开 App 时弹提示
            }
        });
    }

    /** 设置页手动检查：无论结果都回写到状态文字上 */
    public static void checkManually(final Activity activity, final TextView status) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (status != null) {
            status.setText("正在检查…");
        }
        UpdateManager.check(new UpdateManager.CheckCallback() {
            @Override
            public void onChecked(UpdateRelease release) {
                if (!alive(activity)) {
                    return;
                }
                if (status != null) {
                    status.setText(release == null
                            ? "已是最新版 v" + BuildConfig.VERSION_NAME
                            : "发现新版本 " + release.tag + "，点击更新");
                }
                if (release == null) {
                    toast(activity, "当前已是最新版本 v" + BuildConfig.VERSION_NAME);
                } else {
                    showAvailable(activity, release);
                }
            }

            @Override
            public void onError(String message) {
                if (!alive(activity)) {
                    return;
                }
                if (status != null) {
                    status.setText("检查失败：" + message);
                }
                toast(activity, "检查更新失败：" + message);
            }
        });
    }

    /** 用户从「安装未知应用」页面授权后回到 App，自动接着装 */
    public static void resumePendingInstall(Activity activity) {
        File apk = UpdateManager.takePendingApk();
        if (apk == null || !alive(activity)) {
            return;
        }
        if (apk.exists() && UpdateManager.canInstall(activity)) {
            UpdateManager.install(activity, apk);
        }
    }

    // ---------------- 弹窗 ----------------

    private static void showAvailable(final Activity activity, final UpdateRelease release) {
        String notes = release.notes;
        if (notes.length() > 600) {
            notes = notes.substring(0, 600) + "…";
        }
        String message = "当前版本：v" + BuildConfig.VERSION_NAME
                + "\n最新版本：" + release.tag
                + (release.apkSize > 0
                ? "\n安装包大小：" + String.format(Locale.CHINA, "%.1f MB", release.apkSize / 1048576f)
                : "")
                + (notes.isEmpty() ? "" : "\n\n" + notes);

        new AlertDialog.Builder(activity)
                .setTitle("发现新版本 " + release.tag)
                .setMessage(message)
                .setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDownload(activity, release);
                    }
                })
                .setNeutralButton("浏览器打开", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UpdateManager.openReleasePage(activity, release.pageUrl);
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private static void startDownload(final Activity activity, final UpdateRelease release) {
        final ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        final TextView label = new TextView(activity);
        label.setText("正在下载 " + release.tag + "…");

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(activity.getResources().getDisplayMetrics().density * 22);
        box.setPadding(pad, pad, pad, 0);
        box.addView(label);
        box.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("下载更新")
                .setView(box)
                .setCancelable(true)
                .setNegativeButton("后台下载", null)
                .create();
        // 关闭弹窗不中断下载，装完会直接拉起安装器
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        UpdateManager.download(activity, release,
                new UpdateManager.ProgressCallback() {
                    @Override
                    public void onProgress(int percent, long downloaded, long total) {
                        bar.setProgress(percent);
                        label.setText(String.format(Locale.CHINA, "正在下载 %s… %d%%（%.1f/%.1f MB）",
                                release.tag, percent,
                                downloaded / 1048576f, total / 1048576f));
                    }
                },
                new UpdateManager.DownloadCallback() {
                    @Override
                    public void onSuccess(File apk) {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        if (!alive(activity)) {
                            return;
                        }
                        if (!UpdateManager.canInstall(activity)) {
                            promptInstallPermission(activity, apk);
                        } else {
                            UpdateManager.install(activity, apk);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        if (alive(activity)) {
                            toast(activity, "下载失败：" + message);
                        }
                    }
                });
    }

    private static void promptInstallPermission(final Activity activity, final File apk) {
        new AlertDialog.Builder(activity)
                .setTitle("需要安装权限")
                .setMessage("系统要求先允许本应用安装其他应用，授权后会自动继续安装。")
                .setPositiveButton("去授权", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UpdateManager.openInstallPermission(activity, apk);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 工具 ----------------

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static boolean alive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    /** 供设置页顶部「检查更新」行复用：只在检查中禁用点击 */
    public static void bindRow(final Activity activity, View row, final TextView status) {
        if (row == null) {
            return;
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkManually(activity, status);
            }
        });
    }
}

package com.example.smartirrigation.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.example.smartirrigation.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 在线更新：以 GitHub Releases 作为版本源，任何装了这个 App 的人都能自己更新。
 *
 * 发版流程（无需改 App）：打 tag + 上传 APK 到 Releases，App 侧查询
 * /repos/{owner}/{repo}/releases/latest 就能拿到新版本（见 tools/publish-release.ps1）。
 *
 * 网络说明：国内直连 api.github.com / 下载 CDN 时可能被重置，因此每个请求都按
 * 直连 → gh-proxy.com 镜像的顺序重试，任一成功即返回，失败才向上抛错。
 *
 * 注意：这里用的是独立 OkHttpClient，绝不能复用 NetworkModule 的客户端——那会把
 * 业务接口的 Bearer token 一起发到 GitHub。
 */
public final class UpdateManager {

    /** 版本源仓库（公开仓库，无需鉴权；匿名每小时 60 次查询，个人使用足够） */
    public static final String REPO = "fengpei666/smart-irrigation-app";

    private static final String API_LATEST =
            "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String RELEASE_PAGE =
            "https://github.com/" + REPO + "/releases/latest";

    /** 查询接口的兜底前缀（gh-proxy 能转发 api.github.com） */
    private static final String[] API_PREFIXES = {"", "https://gh-proxy.com/"};
    /** APK 下载的兜底前缀（ghproxy.net 只适合下载，不转发 API） */
    private static final String[] DOWNLOAD_PREFIXES = {
            "", "https://gh-proxy.com/", "https://ghproxy.net/"
    };

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    /** 已下载但还没装成的 APK，等用户授完「安装未知应用」权限后接着装 */
    private static File pendingApk;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private static final OkHttpClient DOWNLOAD_CLIENT = CLIENT.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public interface CheckCallback {
        /** release 为 null 表示已经是最新版 */
        void onChecked(UpdateRelease release);

        void onError(String message);
    }

    public interface ProgressCallback {
        void onProgress(int percent, long downloaded, long total);
    }

    public interface DownloadCallback {
        void onSuccess(File apk);

        void onError(String message);
    }

    private UpdateManager() {
    }

    // ---------------- 查询 ----------------

    /** 后台查询最新 Release，结果回主线程 */
    public static void check(final CheckCallback callback) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final UpdateRelease release = fetchLatest();
                    final boolean newer = release != null
                            && release.isNewerThan(BuildConfig.VERSION_NAME);
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onChecked(newer ? release : null);
                        }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(friendlyError(e));
                        }
                    });
                }
            }
        });
    }

    private static UpdateRelease fetchLatest() throws IOException {
        IOException last = null;
        for (String prefix : API_PREFIXES) {
            try {
                return fetchLatestFrom(prefix + API_LATEST);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("无法连接更新服务器") : last;
    }

    private static UpdateRelease fetchLatestFrom(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "smart-irrigation-app")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() == 404) {
                // 还没发过 Release：视为已是最新，不打扰用户
                return null;
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return parse(response.body().string());
        }
    }

    /**
     * 只取需要的字段；assets 里挑 .apk（没有 apk 时返回 null，视为无可用更新）。
     * 用 Gson.fromJson 而不是 JsonParser.parseString：Retrofit 2.9 带的是 Gson 2.8.5，
     * parseString 要 2.8.6+ 才有。
     */
    private static UpdateRelease parse(String json) {
        JsonObject root = new Gson().fromJson(json, JsonObject.class);
        if (root == null) {
            return null;
        }
        String tag = optString(root, "tag_name");
        String notes = optString(root, "body");
        String publishedAt = optString(root, "published_at");
        String pageUrl = optString(root, "html_url");
        if (pageUrl.isEmpty()) {
            pageUrl = RELEASE_PAGE;
        }
        if (root.has("draft") && root.get("draft").getAsBoolean()) {
            return null;
        }

        String apkUrl = "";
        long apkSize = 0;
        if (root.has("assets") && root.get("assets").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("assets")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject asset = element.getAsJsonObject();
                String name = optString(asset, "name").toLowerCase();
                String downloadUrl = optString(asset, "browser_download_url");
                if (name.endsWith(".apk") && !downloadUrl.isEmpty()) {
                    apkUrl = downloadUrl;
                    if (asset.has("size")) {
                        try {
                            apkSize = asset.get("size").getAsLong();
                        } catch (Exception ignored) {
                            apkSize = 0;
                        }
                    }
                    break;
                }
            }
        }
        if (apkUrl.isEmpty()) {
            return null;
        }
        return new UpdateRelease(tag, notes, apkUrl, apkSize, pageUrl, publishedAt);
    }

    private static String optString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------- 下载 ----------------

    /** 下载 APK 到应用私有目录，进度回主线程；已下载过同版本则直接复用 */
    public static void download(final Context context, final UpdateRelease release,
                                final ProgressCallback progress,
                                final DownloadCallback callback) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                File target = apkFile(context, release);
                if (target.exists() && release.apkSize > 0
                        && target.length() == release.apkSize) {
                    postSuccess(callback, target);
                    return;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    postError(callback, "无法创建下载目录");
                    return;
                }
                // 先删掉上次中断留下的半成品
                if (target.exists() && !target.delete()) {
                    postError(callback, "无法清理旧安装包");
                    return;
                }

                IOException last = null;
                for (String prefix : DOWNLOAD_PREFIXES) {
                    try {
                        downloadFrom(prefix + release.apkUrl, target, release.apkSize, progress);
                        postSuccess(callback, target);
                        return;
                    } catch (IOException e) {
                        last = e;
                        if (target.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            target.delete();
                        }
                    }
                }
                postError(callback, last == null ? "下载失败" : friendlyError(last));
            }
        });
    }

    private static void downloadFrom(String url, File target, long expectedSize,
                                     final ProgressCallback progress) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "smart-irrigation-app")
                .build();
        try (Response response = DOWNLOAD_CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            long total = body.contentLength() > 0 ? body.contentLength() : expectedSize;
            InputStream in = body.byteStream();
            FileOutputStream out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            long done = 0;
            int lastPercent = -1;
            try {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        final int percent = (int) (done * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            postProgress(progress, percent, done, total);
                        }
                    }
                }
                out.flush();
            } finally {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (total > 0 && done != total) {
                throw new IOException("安装包不完整（" + done + "/" + total + " 字节）");
            }
        }
    }

    private static File apkFile(Context context, UpdateRelease release) {
        File dir = context.getExternalFilesDir("update");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "update");
        }
        return new File(dir, release.apkFileName());
    }

    private static void postSuccess(final DownloadCallback callback, final File apk) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(apk);
            }
        });
    }

    private static void postError(final DownloadCallback callback, final String message) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(message);
            }
        });
    }

    private static void postProgress(final ProgressCallback progress, final int percent,
                                     final long done, final long total) {
        if (progress == null) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                progress.onProgress(percent, done, total);
            }
        });
    }

    private static String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        if (msg.contains("Unable to resolve host") || msg.contains("Failed to connect")
                || msg.contains("timeout") || msg.contains("Connection reset")
                || msg.contains("Canceled")) {
            return "网络不可用或 GitHub 连接被拦截（" + msg + "）";
        }
        return msg;
    }

    // ---------------- 安装 ----------------

    /** Android 8+ 需要用户单独授予「安装未知应用」权限 */
    public static boolean canInstall(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    /** 跳到「安装未知应用」授权页；授权后回到 App 会自动续装 */
    public static void openInstallPermission(Context context, File apk) {
        pendingApk = apk;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /** 取出授权前等待安装的 APK（取一次即清空） */
    public static File takePendingApk() {
        File apk = pendingApk;
        pendingApk = null;
        return apk;
    }

    /** 拉起系统安装器；FileProvider 只暴露 update/ 目录 */
    public static void install(Context context, File apk) {
        Uri uri = FileProvider.getUriForFile(context,
                BuildConfig.APPLICATION_ID + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** 浏览器打开 Release 页（下载被拦或想手动装时的兜底） */
    public static void openReleasePage(Context context, String url) {
        String target = url == null || url.isEmpty() ? RELEASE_PAGE : url;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}

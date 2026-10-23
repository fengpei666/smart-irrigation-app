package com.example.smartirrigation.update;

/**
 * GitHub Releases 里的一条发布记录（只保留更新需要的字段）。
 * 版本比较以 versionName 为准（tag 去掉前缀 v 后与 BuildConfig.VERSION_NAME 比较）。
 */
public final class UpdateRelease {

    /** 原始 tag，例如 v1.1 */
    public final String tag;
    /** 去掉 v 前缀的版本号，例如 1.1 */
    public final String version;
    /** Release 说明，直接展示给用户 */
    public final String notes;
    /** APK 下载地址（GitHub assets 直链） */
    public final String apkUrl;
    /** APK 字节数，服务端没给时为 0 */
    public final long apkSize;
    /** 发布页地址，下载不动时给浏览器兜底 */
    public final String pageUrl;
    /** 发布时间（ISO 8601 原样） */
    public final String publishedAt;

    public UpdateRelease(String tag, String notes, String apkUrl, long apkSize,
                         String pageUrl, String publishedAt) {
        this.tag = tag == null || tag.isEmpty() ? "未知版本" : tag;
        this.version = normalize(this.tag);
        this.notes = notes == null ? "" : notes.trim();
        this.apkUrl = apkUrl == null ? "" : apkUrl;
        this.apkSize = apkSize;
        this.pageUrl = pageUrl == null ? "" : pageUrl;
        this.publishedAt = publishedAt == null ? "" : publishedAt;
    }

    /** 是否比当前安装的版本更新 */
    public boolean isNewerThan(String currentVersion) {
        return compareVersion(version, normalize(currentVersion)) > 0;
    }

    public String apkFileName() {
        return "smart-irrigation-" + version + ".apk";
    }

    /** 去掉 v/V 前缀与空白，只留下数字和点 */
    static String normalize(String raw) {
        if (raw == null) {
            return "0";
        }
        String s = raw.trim();
        while (!s.isEmpty() && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) {
            s = s.substring(1);
        }
        return s.isEmpty() ? "0" : s;
    }

    /**
     * 逐段按数字比较：1.2 > 1.1.9，1.10 > 1.9，非数字段按 0 处理。
     *
     * @return a 比 b 新返回正数，相同返回 0，旧返回负数
     */
    static int compareVersion(String a, String b) {
        String[] pa = normalize(a).split("[^0-9]+");
        String[] pb = normalize(b).split("[^0-9]+");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? toInt(pa[i]) : 0;
            int vb = i < pb.length ? toInt(pb[i]) : 0;
            if (va != vb) {
                return va > vb ? 1 : -1;
            }
        }
        return 0;
    }

    private static int toInt(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

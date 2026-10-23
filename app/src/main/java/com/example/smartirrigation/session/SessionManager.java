package com.example.smartirrigation.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.smartirrigation.model.AdviceData;
import com.example.smartirrigation.model.AlertsData;
import com.example.smartirrigation.model.ChatSession;
import com.example.smartirrigation.model.ChatTurn;
import com.example.smartirrigation.model.CommandStatusData;
import com.example.smartirrigation.model.DeviceItem;
import com.example.smartirrigation.model.HistoryData;
import com.example.smartirrigation.model.SchedulesData;
import com.example.smartirrigation.model.SettingsData;
import com.example.smartirrigation.model.StatusData;
import com.example.smartirrigation.model.WaterReportData;
import com.example.smartirrigation.model.ZonesData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地会话 + 离线缓存。
 * 原则：绑定关系以服务端 /api/app/devices 为准，本地只保存“当前选中 device_id”；
 * 各页面 cache_* 仅用于断网时回退展示，不参与业务判断。
 */
public class SessionManager {

    private static final String SP_NAME = "smart_irrigation";
    private static SessionManager instance;

    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    public static void init(@NonNull Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
    }

    @Nullable
    public static SessionManager peek() {
        return instance;
    }

    @NonNull
    public static SessionManager get() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager 未初始化（应在 Application.onCreate 中 init）");
        }
        return instance;
    }

    private SessionManager(Context context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // ---------------- 登录态 ----------------

    public void saveLogin(String token, String nickname, String expiresAt) {
        sp.edit()
                .putString("user_token", token)
                .putString("nickname", nickname)
                .putString("expires_at", expiresAt)
                // 真实登录成功即退出演示模式
                .putBoolean("demo_mode", false)
                .apply();
    }

    public String getToken() {
        return sp.getString("user_token", "");
    }

    public String getNickname() {
        return sp.getString("nickname", "");
    }

    public boolean isLoggedIn() {
        return !getToken().isEmpty();
    }

    public void clearToken() {
        sp.edit().remove("user_token").remove("nickname").remove("expires_at").apply();
    }

    /** 当前选中设备（业务接口一律显式传 device_id） */
    public long getDeviceId() {
        return sp.getLong("device_id", 0L);
    }

    public void setDeviceId(long deviceId) {
        sp.edit().putLong("device_id", deviceId).apply();
    }

    /** 解绑 / 2004 时清空 */
    public void clearDeviceId() {
        sp.edit().remove("device_id").apply();
    }

    public void setDemoMode(boolean demo) {
        sp.edit().putBoolean("demo_mode", demo).apply();
    }

    public boolean isDemoMode() {
        return sp.getBoolean("demo_mode", false);
    }

    // ---------------- App 级设置（只影响本机，与设备无关） ----------------

    /** 应用锁：开启后冷启动需要输入 4 位密码 */
    public boolean isAppLockEnabled() {
        return sp.getBoolean("app_lock_enabled", false) && !getAppLockPin().isEmpty();
    }

    public void setAppLockEnabled(boolean enabled) {
        sp.edit().putBoolean("app_lock_enabled", enabled).apply();
    }

    public String getAppLockPin() {
        return sp.getString("app_lock_pin", "");
    }

    public void setAppLockPin(String pin) {
        sp.edit().putString("app_lock_pin", pin == null ? "" : pin).apply();
    }

    /** 方向锁定：开启后所有页面固定竖屏 */
    public boolean isOrientationLocked() {
        return sp.getBoolean("orientation_locked", false);
    }

    public void setOrientationLocked(boolean locked) {
        sp.edit().putBoolean("orientation_locked", locked).apply();
    }

    // ---------------- 设备列表（服务端为准，本地仅作展示缓存） ----------------

    public void saveDevices(List<DeviceItem> devices) {
        sp.edit().putString("cache_devices", gson.toJson(devices)).apply();
    }

    public List<DeviceItem> loadDevices() {
        String json = sp.getString("cache_devices", null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<DeviceItem> list = gson.fromJson(json,
                    new TypeToken<List<DeviceItem>>() {
                    }.getType());
            return list == null ? new ArrayList<DeviceItem>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 当前 device_id 是否仍在服务端列表内 */
    public boolean hasDevice(long deviceId) {
        for (DeviceItem item : loadDevices()) {
            if (item.device_id == deviceId) {
                return true;
            }
        }
        return false;
    }

    public String getCurrentSn() {
        for (DeviceItem item : loadDevices()) {
            if (item.device_id == getDeviceId()) {
                return item.sn == null ? "" : item.sn;
            }
        }
        return "";
    }

    // ---------------- 各页面缓存（离线回退） ----------------

    public void saveStatus(StatusData data) {
        sp.edit().putString("cache_status", gson.toJson(data)).apply();
    }

    public StatusData loadStatus() {
        return loadObject("cache_status", StatusData.class);
    }

    /** 旧调用入口：自动补上当前设备与状态快照，保证刷新策略元数据始终一致 */
    public void saveAdvice(AdviceData data) {
        StatusData status = loadStatus();
        saveAdvice(getDeviceId(), data, status == null ? null : status.metrics);
    }

    public AdviceData loadAdvice() {
        return loadObject("cache_advice", AdviceData.class);
    }

    // ---------------- AI 建议：缓存 + 刷新策略（控制模型调用成本） ----------------

    /** 自动刷新最短间隔：这段时间内进页面直接用缓存 */
    private static final long ADVICE_MIN_AUTO_GAP_MS = 10 * 60 * 1000L;
    /** 超过该时长视为过期，允许自动重新生成 */
    private static final long ADVICE_MAX_AGE_MS = 30 * 60 * 1000L;
    /** 用户手动“重新生成”的节流 */
    private static final long ADVICE_FORCE_GAP_MS = 60 * 1000L;

    /** 保存建议时同时记录设备、生成时间与依据指标，供后续判断是否需要重新生成 */
    public void saveAdvice(long deviceId, AdviceData data, StatusData.Metrics basis) {
        sp.edit()
                .putString("cache_advice", gson.toJson(data))
                .putLong("cache_advice_device", deviceId)
                .putLong("cache_advice_at", System.currentTimeMillis())
                .putString("cache_advice_basis", gson.toJson(basis))
                .apply();
    }

    public long getAdviceDeviceId() {
        return sp.getLong("cache_advice_device", -1L);
    }

    public long getAdviceAgeMs() {
        long at = sp.getLong("cache_advice_at", 0L);
        return at <= 0L ? Long.MAX_VALUE : System.currentTimeMillis() - at;
    }

    /**
     * 进入页面时是否值得重新生成建议：
     * 无缓存 / 换了设备 / 超过 30 分钟 / 关键指标明显变化 才请求，避免每次进页都烧 AI 额度。
     */
    public boolean shouldAutoRefreshAdvice(long deviceId) {
        if (loadAdvice() == null || getAdviceDeviceId() != deviceId) {
            return true;
        }
        long age = getAdviceAgeMs();
        if (age < ADVICE_MIN_AUTO_GAP_MS) {
            return false;
        }
        if (age > ADVICE_MAX_AGE_MS) {
            return true;
        }
        return metricsChangedSinceAdvice();
    }

    /** 用户主动点“重新生成”：60 秒内只允许一次 */
    public boolean canForceRefreshAdvice() {
        return getAdviceAgeMs() >= ADVICE_FORCE_GAP_MS;
    }

    private boolean metricsChangedSinceAdvice() {
        StatusData status = loadStatus();
        StatusData.Metrics now = status == null ? null : status.metrics;
        StatusData.Metrics old = loadObject("cache_advice_basis", StatusData.Metrics.class);
        if (now == null || old == null) {
            return false;
        }
        if (diff(now.soil_pct, old.soil_pct) >= 8d) {
            return true;
        }
        if (diff(now.temp, old.temp) >= 3d) {
            return true;
        }
        if (diff(now.humi, old.humi) >= 10d) {
            return true;
        }
        if (diff(now.light_pct, old.light_pct) >= 15d) {
            return true;
        }
        if (diff(now.water_pct, old.water_pct) >= 10d) {
            return true;
        }
        // 水泵“运行 → 停止”说明刚浇过水，此时才需要重新评估；浇水过程中不刷新，省一次调用
        return old.pump != null && old.pump == 1 && now.pump != null && now.pump == 0;
    }

    private static double diff(Number a, Number b) {
        if (a == null || b == null) {
            return 0d;
        }
        return Math.abs(a.doubleValue() - b.doubleValue());
    }

    public void saveAlerts(AlertsData data) {
        sp.edit().putString("cache_alerts", gson.toJson(data)).apply();
    }

    public AlertsData loadAlerts() {
        return loadObject("cache_alerts", AlertsData.class);
    }

    public void saveSchedules(SchedulesData data) {
        sp.edit().putString("cache_schedules", gson.toJson(data)).apply();
    }

    public SchedulesData loadSchedules() {
        return loadObject("cache_schedules", SchedulesData.class);
    }

    public void saveZones(ZonesData data) {
        sp.edit().putString("cache_zones", gson.toJson(data)).apply();
    }

    public ZonesData loadZones() {
        return loadObject("cache_zones", ZonesData.class);
    }

    public void saveSettings(SettingsData data) {
        sp.edit().putString("cache_settings", gson.toJson(data)).apply();
    }

    public SettingsData loadSettings() {
        return loadObject("cache_settings", SettingsData.class);
    }

    public void saveHistory(HistoryData data) {
        sp.edit().putString("cache_history", gson.toJson(data)).apply();
    }

    public HistoryData loadHistory() {
        return loadObject("cache_history", HistoryData.class);
    }

    public void saveWaterReport(WaterReportData data) {
        sp.edit().putString("cache_water_report", gson.toJson(data)).apply();
    }

    public WaterReportData loadWaterReport() {
        return loadObject("cache_water_report", WaterReportData.class);
    }

    public void saveCommandLog(List<CommandStatusData> list) {
        sp.edit().putString("cache_commands", gson.toJson(list)).apply();
    }

    public List<CommandStatusData> loadCommandLog() {
        String json = sp.getString("cache_commands", null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<CommandStatusData> list = gson.fromJson(json,
                    new TypeToken<List<CommandStatusData>>() {
                    }.getType());
            return list == null ? new ArrayList<CommandStatusData>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ---------------- AI 对话 ----------------

    /** 本地保存最近的对话轮次（服务端不存会话，历史由 App 维护） */
    public void saveChatHistory(List<ChatTurn> turns) {
        sp.edit().putString("cache_chat", gson.toJson(turns)).apply();
    }

    public List<ChatTurn> loadChatHistory() {
        String json = sp.getString("cache_chat", null);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<ChatTurn> list = gson.fromJson(json,
                    new TypeToken<List<ChatTurn>>() {
                    }.getType());
            return list == null ? new ArrayList<ChatTurn>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void clearChatHistory() {
        sp.edit().remove("cache_chat").apply();
    }

    // ---------------- 历史对话（本地多会话） ----------------

    /** 最多保留的会话条数，超出丢弃最旧的 */
    private static final int MAX_CHAT_SESSIONS = 20;

    public List<ChatSession> loadChatSessions() {
        List<ChatSession> list = new ArrayList<>();
        String json = sp.getString("cache_chat_sessions", null);
        if (json != null) {
            try {
                List<ChatSession> parsed = gson.fromJson(json,
                        new TypeToken<List<ChatSession>>() {
                        }.getType());
                if (parsed != null) {
                    list = parsed;
                }
            } catch (Exception ignored) {
                // 解析失败按空列表处理
            }
        }
        if (list.isEmpty()) {
            // 旧版本只有单条会话（cache_chat），迁移成「历史对话」里的一条
            List<ChatTurn> legacy = loadChatHistory();
            if (!legacy.isEmpty()) {
                ChatSession migrated = new ChatSession();
                migrated.id = "legacy";
                migrated.turns = legacy;
                migrated.updated_at = System.currentTimeMillis();
                migrated.title = buildSessionTitle(legacy);
                list.add(migrated);
                saveChatSessions(list);
            }
        }
        return list;
    }

    public void saveChatSessions(List<ChatSession> sessions) {
        sp.edit().putString("cache_chat_sessions", gson.toJson(sessions)).apply();
    }

    @Nullable
    public ChatSession findChatSession(String id) {
        if (id == null) {
            return null;
        }
        for (ChatSession session : loadChatSessions()) {
            if (id.equals(session.id)) {
                return session;
            }
        }
        return null;
    }

    /** 新增或更新一个会话：置顶、刷新时间与标题、只留最近 N 条 */
    public void upsertChatSession(String id, List<ChatTurn> turns) {
        if (id == null || turns == null || turns.isEmpty()) {
            return;
        }
        List<ChatSession> all = loadChatSessions();
        ChatSession target = null;
        for (ChatSession session : all) {
            if (id.equals(session.id)) {
                target = session;
                break;
            }
        }
        if (target != null) {
            all.remove(target);
        } else {
            target = new ChatSession();
            target.id = id;
        }
        target.updated_at = System.currentTimeMillis();
        target.turns = new ArrayList<>(turns);
        target.title = buildSessionTitle(turns);
        all.add(0, target);
        while (all.size() > MAX_CHAT_SESSIONS) {
            all.remove(all.size() - 1);
        }
        saveChatSessions(all);
    }

    public void removeChatSession(String id) {
        if (id == null) {
            return;
        }
        List<ChatSession> all = loadChatSessions();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (id.equals(all.get(i).id)) {
                all.remove(i);
            }
        }
        saveChatSessions(all);
    }

    /** 标题取第一条用户提问，压成一行并截断 */
    private static String buildSessionTitle(List<ChatTurn> turns) {
        for (ChatTurn turn : turns) {
            if ("user".equalsIgnoreCase(turn.role) && turn.content != null) {
                String text = turn.content.replaceAll("\\s+", " ").trim();
                if (!text.isEmpty()) {
                    return text.length() > 18 ? text.substring(0, 18) + "…" : text;
                }
            }
        }
        return "未命名对话";
    }

    /** 用问答返回的 metrics 快照回填首页缓存，省一次 status 请求 */
    public void applyChatMetrics(StatusData.Metrics metrics) {
        if (metrics == null) {
            return;
        }
        StatusData cached = loadStatus();
        if (cached == null) {
            cached = new StatusData();
        }
        cached.metrics = metrics;
        saveStatus(cached);
    }

    @Nullable
    private <T> T loadObject(String key, Class<T> clazz) {
        String json = sp.getString(key, null);
        if (json == null) {
            return null;
        }
        try {
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }
}

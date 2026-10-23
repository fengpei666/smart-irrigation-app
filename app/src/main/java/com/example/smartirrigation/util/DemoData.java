package com.example.smartirrigation.util;

import com.example.smartirrigation.model.AdviceData;
import com.example.smartirrigation.model.AlertItem;
import com.example.smartirrigation.model.AlertsData;
import com.example.smartirrigation.model.CommandStatusData;
import com.example.smartirrigation.model.DeviceItem;
import com.example.smartirrigation.model.HistoryData;
import com.example.smartirrigation.model.Schedule;
import com.example.smartirrigation.model.SchedulesData;
import com.example.smartirrigation.model.SettingsData;
import com.example.smartirrigation.model.StatusData;
import com.example.smartirrigation.model.WaterReportData;
import com.example.smartirrigation.model.Zone;
import com.example.smartirrigation.model.ZonesData;
import com.example.smartirrigation.session.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 离线演示数据（仅比赛现场/断网时使用）。
 * 正常流程以 GET /api/app/devices 为唯一可信来源，本类不参与真实业务判断。
 */
public final class DemoData {

    public static final long DEMO_DEVICE_ID = 9001L;
    public static final String DEMO_SN = "HP-DEMO-0001";

    private DemoData() {
    }

    public static void seed() {
        SessionManager sm = SessionManager.get();
        sm.saveLogin("offline-demo-token", "演示账号（离线）", now());
        sm.setDemoMode(true);
        sm.setDeviceId(DEMO_DEVICE_ID);

        List<DeviceItem> devices = new ArrayList<>();
        DeviceItem item = new DeviceItem();
        item.device_id = DEMO_DEVICE_ID;
        item.sn = DEMO_SN;
        item.model = "UNO+OBLOQ";
        item.bound = true;
        item.online = 1;
        item.status = "active";
        item.last_seen_at = now();
        item.bound_at = now();
        item.soil_pct = 42;
        item.water_pct = 92;
        item.temp = 23.6;
        item.reported_at = now();
        item.pending_count = 0;
        devices.add(item);
        sm.saveDevices(devices);

        sm.saveStatus(mockStatus());
        sm.saveAdvice(mockAdvice());
        sm.saveAlerts(mockAlerts());
        sm.saveSchedules(mockSchedules());
        sm.saveZones(mockZones());
        sm.saveSettings(mockSettings());
        sm.saveHistory(mockHistory());
        sm.saveWaterReport(mockReport());
        sm.saveCommandLog(mockCommands());
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static String date(int daysAgo) {
        long t = System.currentTimeMillis() - daysAgo * 24L * 3600L * 1000L;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(t));
    }

    private static StatusData mockStatus() {
        StatusData s = new StatusData();
        s.device = new StatusData.DeviceInfo();
        s.device.device_id = DEMO_DEVICE_ID;
        s.device.sn = DEMO_SN;
        s.device.model = "UNO+OBLOQ";
        s.device.fw = "1.0.0";
        s.device.online = 1;
        s.device.last_seen_at = now();
        s.device.mode = "auto";
        s.device.status = "active";
        s.device.bound = true;

        s.metrics = new StatusData.Metrics();
        s.metrics.soil_pct = 42;
        s.metrics.soil_raw = 626;
        s.metrics.temp = 23.6;
        s.metrics.humi = 58.0;
        s.metrics.light_pct = 70;
        s.metrics.light_raw = 420;
        s.metrics.water_pct = 92;
        s.metrics.water_raw = 360;
        s.metrics.pump = 0;
        s.metrics.reported_at = now();

        s.settings = new StatusData.Settings();
        s.settings.auto_enabled = 1;
        s.settings.soil_low = 35;
        s.settings.soil_high = 70;
        s.settings.low_water_pct = 20;
        s.settings.report_interval = 60;
        s.settings.pump_max_duration = 60;
        s.settings.ai_enabled = 1;

        s.safety = new StatusData.Safety();
        s.safety.low_water = false;
        s.safety.low_water_pct = 20;
        s.safety.pump_max_duration = 60;
        s.safety.auto_paused = false;
        s.safety.pause_reason = "";

        s.last_command = new StatusData.LastCommand();
        s.last_command.cmd_id = 128;
        s.last_command.action = "pump_on";
        s.last_command.status = "done";
        s.last_command.created_at = now();
        return s;
    }

    private static AdviceData mockAdvice() {
        AdviceData a = new AdviceData();
        a.water = 0;
        a.duration_sec = 0;
        a.reason = "土壤含水 42%，不低于阈值 35%，无需浇水。";
        a.source = "rule";
        a.latency_ms = 12;
        a.executable = false;
        a.soil_pct = 42;
        return a;
    }

    private static AlertsData mockAlerts() {
        AlertsData data = new AlertsData();
        List<AlertItem> list = new ArrayList<>();
        list.add(alert(1, "low_water", "warning", "水位偏低", "水箱剩余 18%", 0, "open", 1));
        list.add(alert(2, "offline", "warning", "设备离线", "HP-DEMO-0001 超过 180 秒未上报", 1, "handled", 2));
        list.add(alert(3, "sensor", "info", "传感器波动", "土壤湿度读数波动异常", 1, "open", 1));
        data.list = list;
        data.total = list.size();
        data.unread_count = 1;
        data.page = 1;
        data.page_size = 20;
        return data;
    }

    private static AlertItem alert(long id, String type, String level, String title,
                                   String msg, int read, String status, int occurrences) {
        AlertItem a = new AlertItem();
        a.id = id;
        a.type = type;
        a.level = level;
        a.title = title;
        a.message = msg;
        a.is_read = read;
        a.status = status;
        a.occurrences = occurrences;
        a.created_at = now();
        return a;
    }

    private static SchedulesData mockSchedules() {
        SchedulesData data = new SchedulesData();
        List<Schedule> list = new ArrayList<>();

        Schedule time = new Schedule();
        time.id = 1L;
        time.device_id = DEMO_DEVICE_ID;
        time.name = "每日早晨浇水";
        time.type = "time";
        time.time_of_day = "07:30:00";
        time.weekdays = "1111111";
        time.duration_sec = 15;
        time.enabled = true;
        time.next_run_at = date(0) + " 07:30:00";
        time.last_run_at = date(1) + " 07:30:05";
        list.add(time);

        Schedule condition = new Schedule();
        condition.id = 2L;
        condition.device_id = DEMO_DEVICE_ID;
        condition.name = "土壤偏干补灌";
        condition.type = "condition";
        condition.condition_type = "soil_below";
        condition.condition_value = 30.0;
        condition.duration_sec = 10;
        condition.enabled = false;
        condition.last_run_at = "--";
        list.add(condition);

        data.schedules = list;
        return data;
    }

    private static ZonesData mockZones() {
        ZonesData data = new ZonesData();
        List<Zone> list = new ArrayList<>();
        Zone a = new Zone();
        a.id = 1L;
        a.device_id = DEMO_DEVICE_ID;
        a.name = "A区 番茄";
        a.area_m2 = 12.5;
        a.soil_type = "壤土";
        a.plant = "番茄";
        a.note = "滴灌带 2 条";
        a.sort_order = 1;
        a.enabled = true;
        list.add(a);

        Zone b = new Zone();
        b.id = 2L;
        b.device_id = DEMO_DEVICE_ID;
        b.name = "B区 生菜";
        b.area_m2 = 8.0;
        b.soil_type = "沙壤土";
        b.plant = "生菜";
        b.note = "";
        b.sort_order = 2;
        b.enabled = true;
        list.add(b);
        data.zones = list;
        return data;
    }

    private static SettingsData mockSettings() {
        SettingsData s = new SettingsData();
        s.device_id = DEMO_DEVICE_ID;
        s.auto_enabled = 1;
        s.soil_low = 35;
        s.soil_high = 70;
        s.low_water_pct = 20;
        s.report_interval = 60;
        s.pump_max_duration = 60;
        s.ai_enabled = 1;
        return s;
    }

    private static HistoryData mockHistory() {
        HistoryData data = new HistoryData();
        data.interval = "raw";
        List<HistoryData.Point> points = new ArrayList<>();
        int[] soil = {58, 56, 54, 52, 50, 48, 46, 44, 42, 40, 38, 36, 35, 34, 33, 32,
                34, 40, 48, 56, 62, 64, 63, 62};
        for (int i = 0; i < soil.length; i++) {
            HistoryData.Point p = new HistoryData.Point();
            p.t = String.format(Locale.CHINA, "%02d:00", i);
            p.soil_pct = soil[i];
            p.temp = 23.6;
            p.humi = 58.0;
            p.light_pct = 70;
            p.water_pct = 92;
            p.pump = (i == 13 || i == 17) ? 1 : 0;
            points.add(p);
        }
        data.points = points;
        return data;
    }

    private static WaterReportData mockReport() {
        WaterReportData r = new WaterReportData();
        r.total_pump_seconds = 90;
        r.total_times = 6;
        r.est_liters = 1.5;
        List<WaterReportData.Day> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            WaterReportData.Day d = new WaterReportData.Day();
            d.date = date(6 - i);
            d.pump_seconds = (i % 3) * 15;
            d.times = i % 3;
            d.est_liters = d.pump_seconds * 0.0166;
            days.add(d);
        }
        r.days = days;
        return r;
    }

    private static List<CommandStatusData> mockCommands() {
        List<CommandStatusData> list = new ArrayList<>();
        CommandStatusData c = new CommandStatusData();
        c.cmd_id = 128;
        c.action = "pump_on";
        c.duration_sec = 15;
        c.status = "done";
        c.sent_at = now();
        c.ack_at = now();
        c.result = "ok";
        list.add(c);
        return list;
    }
}

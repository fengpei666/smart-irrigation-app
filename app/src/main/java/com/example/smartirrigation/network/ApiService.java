package com.example.smartirrigation.network;

import com.example.smartirrigation.model.AdviceData;
import com.example.smartirrigation.model.AiConfirmData;
import com.example.smartirrigation.model.AiConfirmRequest;
import com.example.smartirrigation.model.AlertsData;
import com.example.smartirrigation.model.BindData;
import com.example.smartirrigation.model.BindRequest;
import com.example.smartirrigation.model.CalibrationRequest;
import com.example.smartirrigation.model.ChatData;
import com.example.smartirrigation.model.ChatRequest;
import com.example.smartirrigation.model.CommandStatusData;
import com.example.smartirrigation.model.ControlData;
import com.example.smartirrigation.model.ControlRequest;
import com.example.smartirrigation.model.DevicesData;
import com.example.smartirrigation.model.HistoryData;
import com.example.smartirrigation.model.LoginData;
import com.example.smartirrigation.model.LoginRequest;
import com.example.smartirrigation.model.RegisterRequest;
import com.example.smartirrigation.model.Schedule;
import com.example.smartirrigation.model.ScheduleDeleteRequest;
import com.example.smartirrigation.model.SchedulesData;
import com.example.smartirrigation.model.SettingsData;
import com.example.smartirrigation.model.StatusData;
import com.example.smartirrigation.model.UnbindRequest;
import com.example.smartirrigation.model.WaterReportData;
import com.example.smartirrigation.model.Zone;
import com.example.smartirrigation.model.ZonesData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 契约 v1.0 接口（App 只做配置与展示，策略/调度/报警全在服务端）。
 * - 除 login/register 外，业务接口必须显式带 device_id；
 * - 设备列表 /api/app/devices 是唯一可信来源。
 */
public interface ApiService {

    @POST("api/app/login")
    Call<ApiResult<LoginData>> login(@Body LoginRequest request);

    @POST("api/app/register")
    Call<ApiResult<LoginData>> register(@Body RegisterRequest request);

    /** 设备列表（唯一可信来源，进页面/下拉刷新都调） */
    @GET("api/app/devices")
    Call<ApiResult<DevicesData>> devices();

    @POST("api/app/bind")
    Call<ApiResult<BindData>> bind(@Body BindRequest request);

    @POST("api/app/unbind")
    Call<ApiResult<Object>> unbind(@Body UnbindRequest request);

    @GET("api/app/device/status")
    Call<ApiResult<StatusData>> status(@Query("device_id") long deviceId);

    @GET("api/app/device/history")
    Call<ApiResult<HistoryData>> history(@Query("device_id") long deviceId,
                                         @Query("from") String from,
                                         @Query("to") String to,
                                         @Query("interval") String interval);

    @POST("api/app/device/control")
    Call<ApiResult<ControlData>> control(@Body ControlRequest request);

    @POST("api/app/device/settings")
    Call<ApiResult<SettingsData>> saveSettings(@Body SettingsData settings);

    @GET("api/app/zones")
    Call<ApiResult<ZonesData>> zones(@Query("device_id") long deviceId);

    @POST("api/app/zones")
    Call<ApiResult<Zone>> saveZone(@Body Zone zone);

    @GET("api/app/schedules")
    Call<ApiResult<SchedulesData>> schedules(@Query("device_id") long deviceId);

    @POST("api/app/schedules")
    Call<ApiResult<Schedule>> saveSchedule(@Body Schedule schedule);

    /** 删除计划：{"id","device_id","delete":true} */
    @POST("api/app/schedules")
    Call<ApiResult<Object>> deleteSchedule(@Body ScheduleDeleteRequest request);

    @POST("api/app/calibration")
    Call<ApiResult<Object>> calibration(@Body CalibrationRequest request);

    @GET("api/app/ai/advice")
    Call<ApiResult<AdviceData>> advice(@Query("device_id") long deviceId);

    /** AI 对话问答；可能返回待确认操作提案（action），确认前不会执行 */
    @POST("api/app/ai/chat")
    Call<ApiResult<ChatData>> chat(@Body ChatRequest request);

    /** 用户确认/取消 AI 提案：{"id","decision"}，服务端做白名单与安全校验 */
    @POST("api/app/ai/confirm")
    Call<ApiResult<AiConfirmData>> aiConfirm(@Body AiConfirmRequest request);

    @GET("api/app/alerts")
    Call<ApiResult<AlertsData>> alerts(@Query("device_id") long deviceId,
                                       @Query("status") String status,
                                       @Query("page") int page,
                                       @Query("page_size") int pageSize);

    @GET("api/app/water-report")
    Call<ApiResult<WaterReportData>> waterReport(@Query("device_id") long deviceId,
                                                 @Query("from") String from,
                                                 @Query("to") String to);

    @GET("api/app/command/status")
    Call<ApiResult<CommandStatusData>> commandStatus(@Query("device_id") long deviceId,
                                                     @Query("cmd_id") long cmdId);
}

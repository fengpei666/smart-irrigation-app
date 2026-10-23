# 智慧灌溉 App（Java 原生 Android）

按 `下注.cc` 线上接口契约实现的 App 端（阶段三）：只做配置入口、展示窗口、远程管控；
不承担调度、不调用大模型推理，所有配置与执行状态都以 PHP 返回为准。

## Base URL
`https://xn--ghq556e.cc/`（中文域名 下注.cc 的 punycode，集中配置在
`app/src/main/java/com/example/smartirrigation/network/NetworkModule.java` 的 `BASE_URL`；
换域名只改 host，`/api/app/...` 路径不变。必须 https，Manifest 已关闭明文流量。）

## 已对接的 13 组接口
| 接口 | 页面 |
|---|---|
| `POST /api/app/login` | 登录（返回 user_token、user、devices[]） |
| `POST /api/app/bind` / `unbind` | 设备绑定（sn + 6 位码 / device_id 解绑） |
| `GET /api/app/device/status` | 首页仪表盘（device/metrics/safety，5 秒轮询） |
| `GET /api/app/device/history` | 历史曲线（raw / 5m / 1h，pump=1 标浇水线） |
| `POST /api/app/device/control` | 控制页（pump_on / pump_off / emergency_stop / auto / manual） |
| `POST /api/app/device/settings` | 参数设置（auto_enabled、土壤上下限、低水位线、上报间隔、最大运行时长） |
| `GET/POST /api/app/zones` | 分区管理 |
| `GET/POST /api/app/schedules` | 灌溉计划（time / condition / rotation） |
| `POST /api/app/calibration` | 传感器标定（soil / light / water） |
| `GET /api/app/ai/advice` | AI 建议卡片（ai / rule / 本地安全规则降级） |
| `GET /api/app/alerts` | 报警页（status=open/all、分页、未读数） |
| `GET /api/app/water-report` | 用水报表（总秒数 / 次数 / 估算升数 + 每日明细） |
| `GET /api/app/command/status` | 命令回执（2 秒轮询，pending→sent→ack→done/failed，5 分钟未 ack 视为失败） |

## 关键实现
- 鉴权：`AuthInterceptor` 统一注入 `Authorization: Bearer <user_token>`；HTTP 401 或 `body.code=401` 清 token 并跳登录页。
- device_id：登录返回的 `devices[]` 或绑定接口获得，业务接口一律用 `device_id`，不用 sn。
- 错误码：`ApiResult.codeText()` 内置 400/401/403/429/500/1001/2001/2002/2003/2005/3001~3004/4001 的中文文案，服务端 `msg` 优先展示。
- 离线：`SessionManager` 缓存各页面最后一次成功响应；断网时回退展示 + “离线/最后上报值”横幅，不白屏。
- 轮询：首页 5 秒、命令状态 2 秒，`Poller` 在 `onPause()` 取消，避免泄漏。

## 构建 / 安装
- Android Studio 直接 Open（Gradle 8.13 wrapper 已内置，JDK 17+，本机 JBR 21 已验证）。
- 命令行：`gradlew.bat assembleDebug`
- 产物：`app/build/outputs/apk/debug/app-debug.apk`（已生成，可直接安装）
- 演示账号：`demo / demo123`；也可点“一键演示账号”看离线缓存数据。

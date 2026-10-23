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

## 在线更新（GitHub Releases）
版本源就是本仓库的 Releases，**发一个 Release，所有装了 App 的人都能自助升级**，不需要重新扫码发安装包。

用户侧：
- 启动 App 时静默检查（6 小时内最多一次），有新版本才弹窗；设置 → 关于 →「检查更新」可手动查。
- 弹窗展示版本号、安装包大小与更新说明，点「立即更新」下载 → 自动拉起系统安装器。
- Android 8+ 首次需允许「安装未知应用」，授权返回后自动续装（`UpdateManager.takePendingApk`）。
- 下载顺序：GitHub 直链 → `gh-proxy.com` → `ghproxy.net`，任一条通即可，避免单一线路被拦。

发版三步（维护者）：
1. 改 `app/build.gradle`：`versionCode` +1、`versionName` 改新版本号（两者必须同步）。
2. 提交并推送：`git add -A && git commit -m "release: v1.1" && git push`。
3. 发版：`powershell -ExecutionPolicy Bypass -File tools\publish-release.ps1 -Version 1.1 -Notes "本次更新说明"`，
   脚本会校验版本号 → `assembleRelease` → 创建 `v1.1` Release → 上传 `smart-irrigation-v1.1.apk`。
   Token 可用 `-Token`、环境变量 `GITHUB_TOKEN`，或运行后交互粘贴（需 `repo` 权限）。

注意：
- 在线更新要求**新旧 APK 签名一致**。默认 release 用 debug 签名（免配置）；要换正式签名就在根目录放
  `keystore.properties`（`storeFile/storePassword/keyAlias/keyPassword`，已 gitignore），换签名后老用户需先卸载。
- 判断新旧只看 `versionName`（`UpdateRelease.compareVersion`），所以漏改版本号会导致“永远提示已是最新”。

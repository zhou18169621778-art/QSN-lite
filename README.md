# QSN-lite

QSN-lite 是一款轻量级 Android 地震监测与地震预警应用。应用聚合多个公开数据源，提供全球及区域地震列表、实时地震预警（EEW）推送、天气预警查询、数据源状态监控等功能，安装包体积小、无多余依赖，适合日常使用。

- 包名：`com.example.eew.testlite`
- 最低系统版本：Android 7.0（API 24）
- 目标系统版本：Android 15（API 35）
- 开发语言：Kotlin（原生 View + XML 布局）
- 当前版本：1.0（versionCode 1）

## 功能说明

### 地震列表

聚合以下数据源最近 24 小时 M2.5 以上的地震记录，支持下拉刷新、前后台不同刷新间隔、点击查看详情：

- USGS（美国地质调查局）
- CENC（Wolfx 中国地震台网）
- JMA（Wolfx 日本气象厅）
- EMSC（欧洲地中海地震中心）
- HKO（香港天文台）
- BMKG（印度尼西亚气象气候地球物理局）
- JMA XML（日本气象厅 XML 速报）

地震详情页展示震级、深度、发震时间、经纬度、上报机构，并附带各数据源同一地震记录的横向对比，以及跳转原机构页面查看的入口。

### 地震预警（EEW）

以前台服务方式运行，常驻一条静音状态通知，在后台持续监听以下预警源：

- JMA 紧急地震速报（WebSocket 实时推送 + HTTP 轮询）
- CENC 地震速报（WebSocket 实时推送 + HTTP 轮询）
- 四川省预警（HTTP 轮询）
- 福建省预警（HTTP 轮询）
- 重庆市预警（HTTP 轮询）

收到预警后按用户设置弹出通知。预警接收设置支持按源独立开关（JMA / CENC / CEA / CEA-PR）、仅接收警报（速报不打扰）、地震情报通知及震级阈值（M3 / M4 / M5 / M6 可选）。

JMA 预警详情页展示震中、震级、最大震度、发震时间、发布时间、深度、报数、测定方法（PLUM 法 / Level 法 / IPF 单点 / 深层等自动判定），并可查看该地震在列表数据源中的对应记录。

### 天气预警

查询中央气象台发布的天气预警，支持按地区（全国 / 分省）筛选、无限滚动分页加载、点击查看预警详情页。

### 数据源控制面板

集中展示全部数据源的连接状态与延迟，提供一键测试连接、预警开关管理、刷新间隔配置入口，便于排查某一数据源异常。

### 地图

主界面内置基于 osmdroid 的地图视图（高德瓦片），在列表旁直观展示震中位置。

## 数据来源与致谢

地震预警与部分地震列表数据由 [Wolfx 开放 API](https://wolfx.jp/zh/docs/open-api) 提供，地震列表另聚合 USGS、EMSC、HKO、BMKG、JMA 等机构公开数据，天气预警来自中央气象台，地图瓦片来自高德地图。感谢上述机构与项目提供公开数据服务。

## 构建方法

### 环境要求

- OpenJDK 17（Android Gradle Plugin 8.7.3 要求 Java 17）
- Android SDK（含 `platforms;android-35` 与 `build-tools;34.0.0`）
- Gradle 8.10.2（通过 Wrapper 使用，`gradle/wrapper/gradle-wrapper.properties` 已配置）

### 标准构建

```bash
./gradlew :app:assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`，使用 debug 密钥签名，可直接安装。

### Release 构建说明

`app/build.gradle.kts` 中已配置 `release` 构建类型（默认不启用混淆）。正式对外分发前，请自行配置签名信息（建议通过 `local.properties` 或环境变量注入 keystore 路径与密码，不要将签名文件提交到版本库），然后执行：

```bash
./gradlew :app:assembleRelease
```

## 项目结构

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/example/eew/testlite/
│   ├── MainActivity.kt            # 主界面：地震列表、地图、设置入口
│   ├── data/
│   │   ├── Earthquake.kt          # 地震记录数据模型与展示格式化
│   │   ├── EarthquakeCache.kt     # 地震列表内存缓存
│   │   ├── EarthquakeRepository.kt# 多源地震列表拉取与解析
│   │   ├── Eew.kt                 # 预警数据模型
│   │   ├── EewRepository.kt       # 多源 EEW 拉取与解析
│   │   ├── Settings.kt            # 用户设置读写
│   │   ├── SourceStatus.kt        # 数据源状态模型
│   │   ├── WeatherAlert.kt        # 天气预警数据模型
│   │   └── WeatherAlertRepository.kt
│   ├── service/
│   │   ├── EewService.kt          # 前台预警服务：监听、通知、阈值过滤
│   │   └── EewWebSocketClient.kt  # Wolfx WebSocket 实时推送客户端
│   └── ui/
│       ├── DetailActivity.kt      # 地震详情与多源对比
│       ├── EewDetailActivity.kt   # 预警详情
│       ├── SourcePanelActivity.kt  # 数据源控制面板
│       ├── WeatherAlertActivity.kt# 天气预警列表
│       ├── EarthquakeAdapter.kt   # 地震列表适配器
│       ├── WeatherAlertAdapter.kt # 天气预警适配器
│       ├── AmapTileSource.kt      # 高德地图瓦片源
│       └── MapScrollView.kt       # 地图滚动容器
└── res/                           # 布局、字符串、主题、图标资源
```

## 更新日志

### 1.0（2026-10-05）

- 跟随 Wolfx 开放 API 变更，将 JMA / 四川 / 福建 / 重庆 EEW 解析中的震级字段由已废弃的 `Magunitude` 迁移至 `Magnitude`，旧字段保留为兼容回退。
- CENC 速报原本即使用 `Magnitude` 字段，无需改动。
- 首个公开发行版本。

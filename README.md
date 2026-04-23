# Travel Planner

前后端分离的旅行计划规划网站示例。当前版本使用代码内置的目的地和景点数据，不依赖 MySQL。

## 技术栈

- 后端：Spring Boot、Java 17、REST API
- 前端：Vue 3、Vite、Element Plus、Leaflet、OpenStreetMap

## 功能

- 查询目的地城市
- 按城市和兴趣筛选旅游景点
- 自动生成 1-7 天旅行计划
- 使用内存保存本次启动期间生成过的行程
- 在前端查看并重新打开已保存行程
- 在地图上显示景点标记和行程路线
- 点击每日行程中的景点后，地图自动聚焦到对应位置

## 后端接口

- `GET /api/destinations`：目的地列表
- `GET /api/attractions?city=Shanghai&interests=culture&interests=walk`：景点列表
- `POST /api/plans`：生成旅行计划
- `POST /api/ai/plans`：使用 AI 生成结构化旅行计划
- `GET /api/plans`：最近保存的旅行计划
- `GET /api/plans/{id}`：读取某个已保存旅行计划

请求示例：

```json
{
  "city": "Shanghai",
  "startDate": "2026-05-01",
  "days": 3,
  "interests": ["culture", "walk"]
}
```

## 本地运行

后端需要 Java 17 或更高版本：

Windows PowerShell 如果默认使用的是 Java 8/JRE，可以先临时切换到 JDK 17：

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
javac -version
```

```bash
./mvnw spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。

## AI 结构化规划接入

后端已预留 OpenAI 兼容接口的接入方式，新增接口：

- `POST /api/ai/plans`

需要提供以下配置：

```properties
TRAVEL_AI_ENABLED=true
TRAVEL_AI_BASE_URL=https://api.openai.com/v1
TRAVEL_AI_API_KEY=你的密钥
TRAVEL_AI_MODEL=gpt-4.1-mini
```

说明：

- 后端会要求模型返回固定 JSON 结构
- 模型只负责生成每日景点建议
- 后端会按本地景点库校验并补齐景点，避免返回候选集合之外的景点
- 如果你接的是兼容 OpenAI Chat Completions 的服务，通常只需要提供 `base-url + api-key + model`

例如接通义千问兼容接口时：

```properties
TRAVEL_AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
TRAVEL_AI_MODEL=qwen-plus
```

## 后续扩展

- 接入 MySQL/PostgreSQL 保存用户、景点和行程
- 接入高德、百度或 Google Maps API 获取实时 POI、路线和交通信息
- 增加登录、收藏、多人协作、预算估算和行程导出

# Travel Planner

前后端分离的旅行计划规划网站示例。

## 技术栈

- 后端：Spring Boot、Java 17、REST API
- 前端：Vue 3、Vite、Element Plus、Leaflet、OpenStreetMap

## 功能

- 查询目的地城市
- 按城市和兴趣筛选旅游景点
- 自动生成 1-7 天旅行计划
- 在地图上显示景点标记和行程路线
- 点击每日行程中的景点后，地图自动聚焦到对应位置

## 后端接口

- `GET /api/destinations`：目的地列表
- `GET /api/attractions?city=Shanghai&interests=culture&interests=walk`：景点列表
- `POST /api/plans`：生成旅行计划

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

## 后续扩展

- 接入 MySQL/PostgreSQL 保存用户、景点和行程
- 接入高德、百度或 Google Maps API 获取实时 POI、路线和交通信息
- 增加登录、收藏、多人协作、预算估算和行程导出

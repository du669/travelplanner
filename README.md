# Travel Planner

前后端分离的旅行计划规划网站示例。

## 技术栈

- 后端：Spring Boot、Java 17、REST API
- 前端：Vue 3、Vite、Element Plus、Leaflet、OpenStreetMap

## 功能

- 查询目的地城市
- 按城市和兴趣筛选旅游景点
- 自动生成 1-7 天旅行计划
- 使用 MySQL 保存城市、景点和生成过的行程
- 在前端查看并重新打开已保存行程
- 在地图上显示景点标记和行程路线
- 点击每日行程中的景点后，地图自动聚焦到对应位置

## 后端接口

- `GET /api/destinations`：目的地列表
- `GET /api/attractions?city=Shanghai&interests=culture&interests=walk`：景点列表
- `POST /api/plans`：生成旅行计划
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

后端默认连接本机 MySQL：

```properties
数据库：travelplanner
用户名：root
密码：空
```

如果你的 MySQL 密码不是空，可以在 PowerShell 启动前设置：

```powershell
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="你的MySQL密码"
```

也可以完整覆盖连接地址：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/travelplanner?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
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

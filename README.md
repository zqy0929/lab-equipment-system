# 实验室设备预约与管理系统

基于 **Spring Boot 3 + Vue 3 + MyBatis-Plus + MySQL** 的前后端分离实验室设备管理系统。

## 功能清单

| 模块 | 说明 |
|---|---|
| 📊 数据看板 | 设备总数/可用/维修中、待审批数、本月预约、待处理损耗、分类分布饼图、近 6 个月预约趋势柱状图 |
| 🧪 设备名录 | 设备搜索（名称/型号/位置）、分类/状态筛选、分页；管理员新增/编辑/删除 |
| 📅 预约日历 | 月视图日历展示全部有效预约，点日期查看当日预约，一键预约；**后端时间段冲突检测**（同设备重叠时段自动拒绝） |
| 📋 我的预约 | 查看本人预约、取消预约、归还设备 |
| ✅ 借用审批 | 管理员通过/驳回（驳回需填原因）、标记归还；完整状态流转 PENDING → APPROVED → RETURNED / REJECTED |
| 🔧 损耗记录 | 用户上报损耗（严重损耗自动将设备置为"维修中"）；管理员处理（维修中/已修复，记录费用与说明），修复后设备自动恢复可预约 |
| 📢 通知公告 | 公告时间线，管理员发布/删除 |
| 👥 用户管理 | 管理员查看用户、授权角色（ADMIN/USER）、禁用/启用、重置密码、删除 |

角色权限：`ADMIN`（管理员）与 `USER`（普通用户），基于 JWT 认证 + 拦截器路径鉴权（`/api/admin/**` 仅管理员）。

## 技术栈

- **后端**：Spring Boot 3.2、MyBatis-Plus 3.5.5、MySQL 8、JWT（jjwt 0.12）、BCrypt 密码加密
- **前端**：Vue 3、Vite 5、Element Plus、Pinia、Vue Router、Axios、ECharts、dayjs

## 目录结构

```
lab-equipment-system/
├── sql/lab_equipment.sql          # 建库脚本 + 示例设备/公告数据
├── backend/                       # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/lab/equipment/
│       ├── common/                # Result / JWT / 拦截器 / 全局异常
│       ├── config/                # MyBatis-Plus 分页、CORS、拦截器注册
│       ├── entity / mapper / dto
│       ├── service/               # 业务逻辑（含预约冲突检测）
│       └── controller/            # REST 接口（含 /api/admin 管理接口）
└── frontend/                      # Vue3 前端
    ├── vite.config.js             # 开发代理 /api → localhost:8081
    └── src/{api,store,router,views}
```

## 快速开始

### 1. 准备环境

- JDK 17+、Maven 3.8+
- Node.js 16+（建议 18+）
- MySQL 8.0+

### 2. 初始化数据库

执行 SQL 脚本（会自动创建 `lab_equipment` 库、5 张表和示例数据）：

```sql
source sql/lab_equipment.sql
-- 或命令行：mysql -uroot -p < sql/lab_equipment.sql
```

### 3. 配置并启动后端

数据库密码**不再写在代码里**，通过环境变量注入（避免提交到 Git 仓库）：

```bat
:: Windows：复制 env.local.bat.example 为 env.local.bat，填写本机密码
copy env.local.bat.example env.local.bat
:: 编辑 env.local.bat：
::   set "DB_USERNAME=root"
::   set "DB_PASSWORD=你的MySQL密码"
```

`env.local.bat` 已被 `.gitignore` 忽略，不会上传到 GitHub。也可直接用环境变量启动：

```bash
DB_PASSWORD=你的密码 mvn spring-boot:run
```

启动：

```bash
cd backend
run.bat            # 一键启动（自动加载 ../env.local.bat）
# 或：mvn clean package -DskipTests 后运行 target/lab-equipment-1.0.0.jar
```

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | 空（必填） | MySQL 密码，仅本地 `env.local.bat` 保存 |
| `JWT_SECRET` | 内置开发默认值 | 生产环境务必替换为随机长字符串 |
| `SERVER_PORT` | `8081` | 后端端口 |

后端运行在 `http://localhost:8081`。首次启动会自动创建管理员账号（users 表无 admin 时）。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。

### 5. 默认账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | admin | admin123 |

普通用户通过登录页「注册」自行创建。

## 接口概览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login、/api/auth/register | 登录 / 注册 |
| GET | /api/stats/dashboard | 看板统计 |
| GET | /api/equipment/page?keyword=&category=&status=&page=&size= | 设备分页查询 |
| POST/PUT/DELETE | /api/admin/equipment | 设备增删改（管理员） |
| POST | /api/reservations | 创建预约（含冲突检测） |
| GET | /api/reservations/calendar?month=2026-10 | 日历数据 |
| PUT | /api/reservations/{id}/cancel、/{id}/return | 取消 / 归还 |
| GET/PUT | /api/admin/reservations/page、/approve | 审批列表 / 审批（管理员） |
| POST/GET | /api/damages | 损耗上报 / 列表 |
| PUT | /api/admin/damages/handle | 损耗处理（管理员） |
| GET/POST/DELETE | /api/notices、/api/admin/notices | 公告查看 / 管理 |
| GET/PUT/DELETE | /api/admin/users/** | 用户管理（管理员） |

登录后请求头携带：`Authorization: Bearer <token>`。

## 常见问题

- **启动报数据库连接失败**：确认 MySQL 已启动、账号密码正确、`lab_equipment` 库已创建。
- **前端请求 401**：token 过期（默认 24 小时），重新登录即可；过期时间在 `application.yml` 的 `jwt.expire-hours` 配置。
- **接口 403**：普通用户访问了管理员接口，请用管理员账号操作。

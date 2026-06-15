# DBSyncer Web UI 前端重构设计

日期：2026-06-15

## 目标

将 dbsyncer-web-ui（Vue3 SPA）补全至与 ui_bak（Thymeleaf 多页应用）功能等价，同时统一视觉风格。

## 范围

8 个菜单项，不包含授权许可。API 联调延后到前端修改完成后手工进行。

## 全局架构

### 布局结构

- 左侧浅色侧边栏（240px）：Logo + 菜单 + 版权信息
- 右侧：顶部 Header（用户名 + 下拉菜单）+ 灰色背景主内容区（#fafafa）

### 路由表

| 路径 | 视图 | 状态 |
|------|------|------|
| `/login` | LoginView | 已有 |
| `/` | MainLayout | 已有（需改风格） |
| `/` (子) | DashboardView | 补全 |
| `/connectors` | ConnectorList | 已有 |
| `/connectors/add` | ConnectorAdd | 新建 |
| `/connectors/:id/edit` | ConnectorEdit | 新建 |
| `/mappings` | MappingList | 已有 |
| `/mappings/:id` | MappingEdit | 已有 |
| `/monitor` | MonitorView | 补全 |
| `/plugins` | PluginList | 新建 |
| `/config` | ConfigList | 新建 |
| `/users` | UserList | 已有 |
| `/users/add` | UserAdd | 新建 |
| `/users/:username/edit` | UserEdit | 新建 |
| `/system` | SystemConfig | 新建 |

### Element Plus 主题覆盖

对齐 ui_bak 设计系统变量：

- `--el-color-primary: #165DFF`
- 侧边栏：白色背景，文字 #595959，选中态浅蓝背景 + 蓝色文字
- 成功 #52c41a / 警告 #fa8c16 / 错误 #f5222d / 信息 #1890ff

### 新增依赖

- `echarts` + `vue-echarts`：替代旧 UI 的 Chart.js，用于仪表盘和监控图表

## 各页面设计

### 1. 仪表盘 DashboardView

数据来源：`GET /monitor/dashboard`

**顶部 4 个统计卡片：**
- 总任务数（较上周趋势）、运行中任务（占比）、失败任务、同步总数（较昨日趋势）

**中间区域：**
- 左 2/3：同步趋势折线图（ECharts，成功/失败双线，一周/半个月/一个月切换）
- 右 1/3：迷你统计卡片组（成功/失败/新增/修改/删除/DDL）+ 任务状态横向柱状图（ECharts）

**底部区域：**
- 左：连接器卡片列表（图标+名称+URL+状态，每页 3 条）
- 右 2/3：驱动列表表格（名称/类型/结果/状态/最后同步时间）

### 2. 连接管理 ConnectorList/Add/Edit

**列表页（已有，微调）：** 搜索 + 表格 + 分页，操作：测试/编辑/复制/删除

**Add 页（新建）：** 选择连接器类型 → 动态配置表单 → 保存

**Edit 页（新建）：** 加载已有配置 → 编辑表单 → 保存

### 3. 驱动管理 MappingList/Edit

已有页面，功能相对完整。子页面（editTable/editConvert/editFilter 等）不在本次范围。

### 4. 性能监控 MonitorView

数据来源：`POST /monitor/queryData`、`POST /monitor/queryLog`、`GET /monitor/metric`

**图表区（ECharts）：**
- TPS 折线图、堆积数据饼图/环图、持久化图表
- CPU 折线图、内存折线图

**系统状态区：**
- CPU/内存/磁盘进度条
- 应用状态指标表格

**数据管理区：**
- 执行器任务列表（驱动下拉筛选 + 表名搜索）
- 日志列表（关键字搜索 + 清空）
- 同步数据列表（驱动/状态筛选 + 关键字搜索 + 清空）

### 5. 插件管理 PluginList

数据来源：`GET /plugin`、`POST /plugin/upload`

- 插件列表表格（名称/驱动/类名/版本/文件）
- JAR 文件上传区域
- 开发文档（简化展示）

### 6. 配置管理 ConfigList

数据来源：`GET /config`、`POST /config/upload`

- 左右两栏：配置列表表格 + JSON 文件上传区
- 下载配置按钮

### 7. 用户管理 UserList/Add/Edit

**列表页（已有，微调）：** 表格 + 添加按钮，操作：编辑/删除

**Add 页（新建）：** 用户名/密码/昵称/邮箱/手机号/角色 表单

**Edit 页（新建）：** 同 Add，预填数据

### 8. 系统配置 SystemConfig

数据来源：`GET /system`、`POST /system/edit`、`POST /system/generateRSA`

表单页，包含：
- 6 个开关项（记录同步成功/全量/失败数据、打印trace、水印、开放API）
- 4 个数字输入（日志长度、执行器上限、数据过期天数、日志过期天数）
- 水印内容输入（条件显示）
- RSA 密钥管理区（条件显示）：密钥长度 + 生成/复制公钥/复制私钥
- 保存按钮

## 实现顺序

1. 全局布局 + 主题变量覆盖
2. 路由配置补全
3. DashboardView 重建（ECharts）
4. 4 个新建列表页（PluginList / ConfigList / SystemConfig / MonitorView 补全）
5. 4 个新建表单页（ConnectorAdd/Edit / UserAdd/Edit）
6. 已有页面微调（ConnectorList / UserList / MappingList）

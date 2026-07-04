# Task Scheduler（任务调度器）

[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Oracle](https://img.shields.io/badge/Oracle--MySQL--Ready-brightgreen.svg)](#)
[![Deploy](https://img.shields.io/badge/Deploy-Fat--JAR--only-important.svg)]()

> **零中间件、Oracle/MySQL 原生企业级工作流编排引擎**
> 以单一 JAR 包运行——无需消息代理、注册中心、ZooKeeper 或 Docker。
> 专为**端到端计划系统编排**构建（数据接入 → 处理 → 引擎计算 → 报表 → 外部系统回写）。

---

## 目录

- [为什么选择 Task Scheduler？](#-为什么选择-task-scheduler)
- [核心特性](#-核心特性)
- [技术栈](#-技术栈)
- [快速开始](#-快速开始)
- [架构概览](#-架构概览)
- [内置任务类型（20+）](#-内置任务类型20)
- [5 种工作流触发模式](#-5-种工作流触发模式)
- [竞品对比](#-竞品对比)
- [选型指南](#-选型指南)
- [API 概览](#-api-概览)
- [配置与部署](#-配置与部署)
- [文档](#-文档)
- [贡献指南](#-贡献指南)
- [许可证](#-许可证)

---

## 🎯 为什么选择 Task Scheduler？

### ✅ 核心差异化优势（竞品中独有）

| 差异化优势 | 说明 |
|------------|------|
| **零中间件轻量部署** | `java -jar` 即可启动——仅需 JVM + Oracle/MySQL。无需 Redis、ZooKeeper、消息队列。支持 Windows/Linux。 |
| **Oracle/MySQL 原生支持** | 通过 `DatabaseMetaData` 自动检测数据库类型。完整支持 Oracle 存储过程（IN/OUT/INOUT）、`DBMS_OUTPUT` 捕获，以及 6 种 SQL 任务变体（含流式 Excel/CSV 导出）。 |
| **EVENT 零轮询等待** | `EventTask` + `CountDownLatch`——线程挂起，零 CPU 消耗。子工作流完成时触发 HTTP 回调，实时唤醒父工作流（无需像 Airflow 的 `ExternalTaskSensor` 那样轮询）。 |
| **OR-门 DAG 语义** | 内置 `OrTask`——当*任一*上游任务完成时触发下游任务。Airflow/DolphinScheduler 仅支持 AND 依赖。 |
| **级联子工作流取消** | 取消父工作流会递归取消所有子工作流及其正在运行的任务。Airflow 不会传播取消操作。 |
| **TIMEOUT 作为独立状态** | `TIMEOUT` 是一个独立的终态（区别于 `ERROR`/`CANCELED`），带有专属的 UI 颜色和邮件模板。所有竞品都将超时归入通用失败状态。 |
| **内置分布式锁任务** | `LockTask` 通过 `TS_LOCK` 表实现分布式互斥锁——5 秒重试循环、`taskTimeout` 过期、取消时自动释放。没有任何竞品提供此类内置任务类型。 |
| **单任务粒度操作** | 可在单个任务级别重试/跳过/取消——无需重启整个工作流。UI 按钮具有状态感知能力，配有确认对话框和 Toast 反馈。 |
| **基于 Excel 的环境同步** | 将工作流定义导出为 Excel，然后导入到另一环境（upsert 语义——存在则更新，不存在则创建）。实现开发→测试→生产环境的快速同步。 |
| **3 级参数继承 + 输出持久化** | 全局 → 工作流 → 任务。`saveParam()` 将输出持久化到数据库，下游任务自动消费。Airflow 的 XCom 单条限制 48KB。 |
| **D3 甘特图 + SVG 导出** | 双层可视化（工作流 + 任务），支持可折叠分段、TIMEOUT 特定颜色和 SVG 导出功能。 |
| **双模式 DAG 可视化** | ① jsPlumb DAG 编辑器（面向业务人员的免代码拖拽）② D3 运行时 DAG 视图（实时状态/耗时/错误显示）。 |

### ⚠️ 已知局限性（坦诚说明）

| 局限性 | 说明 | 缓解措施 |
|--------|------|----------|
| **单 JVM，无 HA** | 进程崩溃需要手动调用 `recoverWorkflow()`。 | 生产环境使用进程监控（systemd）或 K8s 健康检查 + 自动重启。 |
| **无水平扩展** | 适用于中小规模（每日数万任务）。大规模场景请考虑 Airflow/DolphinScheduler。 |
| **连接器生态有限** | 20 种内置类型——新增连接器需要开发和部署（尽管扩展 `TaskType` 极为简单）。 | Airflow 有 200+ 个 Provider。大数据管道请选择 DolphinScheduler。 |
| **无 LDAP/SSO/OAuth2** | 仅 JWT 本地认证，两个角色（ADMIN/USER）。 | 使用反向代理（Nginx `auth_request`）实现 SSO，或扩展认证模块。 |
| **基础监控/告警** | 邮件 + Webhook（飞书/企业微信）——无 SLA 错过追踪或告警升级。 | 通过 `/api/stats` 暴露指标到 Prometheus + AlertManager。 |
| **暂无公共社区** | 当前以 Apache 2.0 协议开源。提供完善的文档（技术设计文档/用户手册/API 文档/TaskType 扩展规范）。 | 开源后将逐步建设社区。 |

---

## ✨ 核心特性

| 领域 | 能力 |
|------|------|
| **工作流 DAG** | AND + OR 语义、DFS 环路检测、JSON 序列化（定义 + 运行时双视图） |
| **子工作流嵌套** | 多层嵌套、完整的父子回调、取消传播、崩溃恢复 |
| **事件驱动协调** | `EventTask` + `CountDownLatch`——零轮询等待、DB 持久化、提前触发竞态保护 |
| **任务控制** | 重试/跳过/取消（单任务粒度）、恢复（断点续跑）、级联取消 |
| **参数系统** | 3 级继承（全局 → 工作流 → 任务）、`{variable}` 占位符、`saveParam()` 输出持久化 |
| **超时控制** | `TIMEOUT` 作为独立状态、守护线程中断、专用异常类型和邮件模板 |
| **通知** | 邮件（SMTP）+ Webhook（飞书/企业微信）——每个工作流/任务可独立开关 |
| **监控与可视化** | D3 甘特图、运行时 DAG、实时日志 + 参数诊断弹窗、每小时监控报表 |
| **安全与审计** | JWT + RBAC（ADMIN/USER）、IP 白名单、完整 API 审计日志 |
| **环境同步** | 工作流定义 Excel 导入/导出——跨环境快速部署 |

---

## 📦 技术栈

| 技术 | 版本/规格 | 用途 |
|------|-----------|------|
| Java | 11（LTS） | 主要语言——利用 `var`、NIO2、模块系统 |
| JVM | HotSpot | 运行时——依赖 `Thread.interrupt()` 实现取消 |
| 构建工具 | Maven / Gradle | 依赖管理和 Fat-JAR 打包 |
| 日志 | Log4j 2 | 结构化日志——`monitor.log` 用于专用报表 |
| 数据库 | Oracle / MySQL | 通过 `DatabaseMetaData` 自动检测——无需手动配置 |
| ORM | MyBatis 3 | SQL 映射——XML Mapper + 注解，无 Spring 依赖 |
| 连接池 | HikariCP | JDBC 连接池 |
| 时间轮 | Netty HashedWheelTimer 4.x | Cron 调度的高性能延迟执行 |
| JSON | Gson 2.x | 参数和 HTTP 响应的序列化/反序列化 |
| Excel | Apache POI（SXSSF）5.x | 工作流定义导入/导出、数据导出 |
| 邮件 | JavaMail 1.x | SMTP 邮件发送 |
| JWT | JJWT | Token 签发和验证（无需 Spring Security） |
| HTTP 服务器 | `com.sun.net.httpserver` | JDK 内置轻量嵌入式 HTTP 服务器 |
| 密码哈希 | BCrypt | 加盐哈希存储（`salt:hash` 格式） |

> **架构风格**：刻意采用**"无 Spring"（零框架）**设计。所有依赖通过构造函数手动注入，服务在 `Main.java` 中线性初始化。这带来了：极速启动（无 Bean 扫描、无 AOP 代理）、完全的依赖透明性、简化的部署。

---

## 🚀 快速开始

### 前置条件
- Java 11（LTS）
- Oracle 12c+ 或 MySQL 8+（兼容 MySQL 协议的数据库亦可）
- Maven 3.6+ 

### 构建
```bash
git clone https://github.com/your-username/task-scheduler.git
cd task-scheduler
mvn clean package
```

### 准备数据库
执行 `db/` 目录下的 DDL 脚本（分别提供 Oracle 和 MySQL 版本）。
数据库连接在 `mybatis-config.xml` 中配置。启动时，系统通过 `DatabaseMetaData` 自动检测数据库类型。

### 启动
```bash
java -jar target/task-scheduler-1.0.jar
```

嵌入式 HTTP 服务器默认监听 `8888` 端口。
在浏览器中访问 `http://localhost:8888/static/index.html` 打开 Web UI（需在全局参数中设置 `enableStaticResource=true`）。

### 默认账号
- 用户名：`admin`
- 密码：`admin123`（**请立即修改！**）

---

## 🧱 架构概览

系统采用**六层架构**，零框架依赖（无 Spring）：

```
┌─────────────────────────────────────────────────────────────┐
│  接入层（Access Layer）  – 嵌入式 HTTP 服务器（com.sun.net.httpserver）    │
│                          REST API 路由，BaseHandler 统一认证              │
├─────────────────────────────────────────────────────────────┤
│  服务层（Service Layer）  – ExecutionService / DefinitionService /         │
│                           ParamsService / SchedulerService 等              │
├─────────────────────────────────────────────────────────────┤
│  引擎层（Engine Layer）   – WorkflowEngine（中央协调器）                    │
│                           WorkflowInstance（运行时状态容器）                │
│                           DAG 构建与验证（AND/OR）                         │
├─────────────────────────────────────────────────────────────┤
│  调度层（Scheduler Layer） – CronScheduler + Netty HashedWheelTimer        │
│                           6 字段 Cron，100ms 精度，热加载                  │
├─────────────────────────────────────────────────────────────┤
│  任务层（Task Layer）     – Task 接口 + 20+ 种实现                         │
│                           AbstractTask → TimeoutTask → Script/Sql         │
├─────────────────────────────────────────────────────────────┤
│  持久层（Persistence）    – MyBatis Mapper + DAO + Service                 │
│                           HikariCP 连接池，ThreadLocal 事务管理             │
└─────────────────────────────────────────────────────────────┘
```

**启动流程**：`Main.java` 执行 16 步线性初始化（MyBatis → SeqIdGenerator → DAOs → Services → EventManager → TaskFactory → WorkflowEngine → NettyTimeWheel → CronScheduler → WorkflowMonitor → HttpServer → ShutdownHook → 主线程阻塞）。

---

## 🛠 内置任务类型（20+）

| 分类 | 任务类型 | 说明 |
|------|----------|------|
| **脚本** | ShellTask | `/bin/bash` 脚本执行，实时日志捕获 |
| | CmdTask | `cmd.exe` 批处理（Windows） |
| | PythonTask | Python3/Python 脚本执行 |
| | DataXTask | DataX ETL 作业（内联 JSON 或文件路径模式） |
| **SQL** | DMLSqlTask | INSERT/UPDATE/DELETE——返回影响行数 |
| | SqlStatementTask | SELECT 查询——结果序列化为 JSON |
| | ExportCsvTask | 流式 SELECT 结果导出为 CSV（UTF-8 BOM） |
| | ExportExcelTask | 流式 SELECT 结果导出为 Excel（SXSSF，低内存占用） |
| | ProcedureTask | 通用存储过程（IN/OUT/INOUT 参数绑定） |
| | OracleProcedureTask | Oracle 专用——`DBMS_OUTPUT` 捕获、`RETURN_CODE` 自动评估 |
| **HTTP** | HttpTask | 5 种认证模式：NONE / BASIC / BEARER / API_KEY / HYBRID（自动登录 + token 刷新） |
| | BYSRETask | SRE 平台作业调用 + 状态轮询（专有） |
| **文件传输** | FileXTask | 本地 / FTP / SFTP / 压缩文件——支持复制、移动、同步 |
| **编排** | WaitTask | 等待到指定时间——分段睡眠，可中断检查 |
| | LockTask | 分布式互斥锁（基于 `TS_LOCK` 表）——5 秒重试循环，取消时自动释放 |
| | EventTask | 基于 `CountDownLatch` 的事件等待——零轮询，DB 持久化 |
| | WorkflowTask | 同步启动子工作流并等待完成回调——支持多层嵌套 |
| | OrTask | OR-门汇聚——当*任一*上游完成时触发 |
| **Java 扩展** | JavaTask | 反射调用 `com.ts.custom` 包中的任意 Java 方法——无需重启 |

> **扩展机制**：① **JavaTask（推荐，无需重启）**——在 `com.ts.custom` 中实现 `public Object execute(Map<String,Object> params)`；② **注册新的 TaskType**——通过 `POST /api/tasktype`（需要重启）。

---

## ⏱ 5 种工作流触发模式

| 触发模式 | 使用场景 | 机制 | 等待子流程？ |
|----------|----------|------|:-----------:|
| **Cron（定时调度）** | 批处理、定时报表、数据同步 | `CronScheduler` + `NettyTimeWheel`——6 字段 Cron，100ms 精度，热加载 | 否（自动续期） |
| **同步（WORKFLOW 任务）** | 需要子流程完成的串行编排 | `WorkflowTask` + 引擎回调——父任务保持 `RUNNING`，子任务完成后触发回调 | 是（同步等待） |
| **异步 HTTP 触发** | 外部系统调用、解耦调用 | HTTP 任务 `POST /api/workflow/start`——父任务触发后立即完成 | 否（即发即忘） |
| **异步 HTTP + EVENT 回调** | 跨实例/跨服务器的父子工作流，需要感知完成状态 | `EventManager` + `EventTask`——`CountDownLatch` 零轮询等待，DB 持久化 | 是（零轮询事件等待） |
| **UI / API 手动触发** | 临时调试、紧急运行、外部集成 | 监控 UI 中的"触发"按钮或 `POST /api/workflow/start` | 否 |

---

## 📊 竞品对比

| 维度 | Task Scheduler | Apache Airflow | DolphinScheduler | XXL-Job | PowerJob |
|------|:-------------:|:--------------:|:----------------:|:-------:|:--------:|
| 语言 | Java 11 | Python 3 | Java 8+ | Java 8+ | Java 8+ |
| 零中间件部署 | ✅ 仅 JVM+DB | ❌ Redis/RabbitMQ+PG | ❌ ZooKeeper+多服务 | ❌ Admin+Executor+DB | ❌ Server+Worker+MongoDB |
| Oracle 存储过程 + DBMS_OUTPUT | ✅ 原生 | ❌ | ❌ | ❌ | ❌ |
| OR-门 DAG 语义 | ✅ 内置 `OrTask` | ❌ 仅 AND | ❌ 仅 AND | ❌ 无 DAG | ❌ 仅 AND |
| EVENT 零轮询等待 | ✅ `CountDownLatch` | ❌ 轮询 Sensor | ❌ 轮询 | ❌ | ❌ |
| 内置分布式锁 | ✅ `LockTask` | ❌ | ❌ | ❌ | ❌ |
| TIMEOUT 作为独立状态 | ✅ 终态 | ❌ 归入 FAILED | ❌ 归入 FAILED | ❌ 归入 FAILED | ❌ 归入 FAILED |
| 级联子工作流取消 | ✅ 递归 | ❌ 不传播 | ⚠️ 尽力而为 | ❌ | ⚠️ 有限 |
| 单任务重试/跳过/取消 | ✅ 任务级粒度 | ⚠️ 粗粒度 | ⚠️ 有限 | ❌ | ⚠️ 有限 |
| 3 级参数继承 + 输出持久化 | ✅ 完整支持 | ⚠️ XCom 48KB 限制 | ⚠️ 有限 | ❌ | ⚠️ 有限 |
| Excel 环境部署 | ✅ 导入/导出 | ❌ | ⚠️ 有限 | ❌ | ❌ |
| HA / 水平扩展 | ❌ 单 JVM | ✅ 成熟 HA | ✅ 成熟 HA | ✅ 无状态 Admin | ✅ 主主模式 |
| 大数据/ML 生态 | ⚠️ 可扩展 | ✅ 200+ Provider | ✅ 原生 Spark/Flink | ❌ | ❌ |
| 企业 SSO / 多租户 | ❌ 基础 RBAC | ✅ LDAP/OAuth2 | ✅ 项目级隔离 | ⚠️ 有限 | ⚠️ 有限 |

---

## 🎯 选型指南

### 何时选择 Task Scheduler

- ✅ **Oracle/MySQL 重度的环境**——带有 OUT 参数的存储过程、`DBMS_OUTPUT` 捕获、多格式 SQL 导出
- ✅ **严格的轻量部署要求**——受限企业网络不允许 Python/Redis/ZooKeeper/Docker，支持 Windows/Linux
- ✅ **端到端计划系统编排**——数据接入 → 处理 → 计划引擎 → 报表 → 外部系统回写
- ✅ **复杂的嵌套工作流编排**——完整的父子回调、级联取消、崩溃恢复
- ✅ **事件驱动零轮询协调**——`EventTask` + `CountDownLatch` 实现跨实例/跨服务器协作
- ✅ **细粒度任务控制**——单任务重试/跳过/取消、独立 `TIMEOUT` 检测
- ✅ **AND/OR 并行 + 分布式锁**——复杂的资源争用场景
- ✅ **业务人员无代码 DAG 编排**——开发人员扩展 `TaskType`，业务人员拖拽配置

### 其他场景推荐

| 场景 | 推荐工具 |
|------|----------|
| Python 数据管道，需要 200+ Provider 生态 | **Apache Airflow** |
| 云原生大数据（原生 Spark/Flink/Hadoop 支持） | **DolphinScheduler** |
| 轻量 Java 微服务任务调度（无需 DAG） | **XXL-Job** |
| MapReduce 式大型任务拆分到多个 Worker | **PowerJob** |

---

## 📡 API 概览

所有 API 需要 JWT Bearer Token 认证，带有 RBAC（ADMIN/USER）。

| 前缀 | 功能 | 写入权限 |
|------|------|----------|
| `/api/auth` | 登录、Token 管理 | 无需认证（登录） |
| `/api/workflow` | 启动/取消/恢复/重试/跳过，查询实例和日志 | 所有认证用户 |
| `/api/definition` | 工作流/任务定义 CRUD、DAG 导入/导出（Excel） | 仅 ADMIN（写入） |
| `/api/scheduler` | Cron 调度 CRUD（热加载） | 所有认证用户 |
| `/api/params` | 全局/工作流/任务参数管理 | 仅 ADMIN（写入） |
| `/api/tasktype` | 任务类型注册管理 | 仅 ADMIN（写入） |
| `/api/lock` | 分布式锁查询和释放 | 所有认证用户 |
| `/api/event` | 事件记录查询和触发 | 所有认证用户 |
| `/api/history` | 历史运行分页查询 + CSV 导出 | 所有认证用户 |
| `/api/stats` | 实时监控快照（线程池/调度器/时间轮） | 所有认证用户 |
| `/api/users` | 用户账户管理 | 仅 ADMIN（写入） |

详细 API 规范请参考 [REST API 文档](docs/API.md)。

---

## ⚙️ 配置与部署

### 全局参数配置

所有配置以 JSON 格式存储在 `GLOBAL_PARAMS` 表中，支持热加载：

| 参数键 | 配置类 | 关键字段 |
|--------|--------|----------|
| `ThreadPoolConfig` | 线程池 | `corePoolSize=15`、`maxPoolSize=75`、`queueCapacity=150` |
| `EmailConfig` | SMTP 邮件 | `host`、`port`、`ssl`、`username`、`password`、`from` |
| `EmailTemplateConfig` | 邮件模板 | 每种通知类型的主题和正文（支持 `{{variable}}`） |
| `HttpServerConfig` | HTTP 服务器 | `port=8888`、`staticPath`、`enableStaticResource` |
| `GlobalConfig` | 飞书/企业微信 | `enabled`、`url`、`secret`、`mentionList` |

### 部署方式

**JAR 部署**（推荐）：
```bash
java -jar -Dconfig.file=/path/to/application.properties task-scheduler.jar
```

**Systemd 服务（Linux）**：请参考 `deploy/` 目录中的示例 unit 文件。

**反向代理（Nginx）**：生产环境推荐用于处理 SSL 和访问控制。嵌入式服务器支持 IP 白名单。

---

## 📚 文档

| 文档 | 说明 |
|------|------|
| [技术设计文档](docs/TaskScheduler_Technical_Design.md) | 完整的系统架构、引擎内部原理、任务类型、API 设计 |
| [用户手册](docs/UserManual.md) | 前端 UI 操作指南（DAG 编辑器、监控、调度管理） |
| [REST API 文档](docs/API.md) | 所有 API 的请求/响应格式和示例 |
| [TaskType 扩展规范](docs/TaskTypeExtension.md) | 创建自定义任务类型的开发者指南 |
| [数据库 DDL](db/) | Oracle 和 MySQL 的 DDL 脚本 |

---

## 🤝 贡献指南

Task Scheduler 采用 Apache 2.0 协议开源——欢迎社区贡献！

1. Fork 本仓库
2. 创建功能分支（`git checkout -b feature/amazing`）
3. 提交您的更改（`git commit -m 'Add some amazing feature'`）
4. 推送到分支（`git push origin feature/amazing`）
5. 创建 Pull Request

详细指南请参考 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 📄 许可证

版权所有 © 2024-2025 Rock Rao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

**商标声明**："Task Scheduler" 名称及其徽标是 Rock Rao 的商标。
根据 Apache 2.0 许可证，您可以自由使用、修改和分发代码，
但未经明确书面许可，不得使用本项目名称或徽标来认可或推广您的衍生产品。

---

## 📬 联系方式

- Issues：[GitHub Issues](https://github.com/your-username/task-scheduler/issues)
- 邮箱：raohuacai@126.com

---

**用 ❤️ 构建，致力于企业级工作流自动化。**

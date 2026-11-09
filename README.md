# Task Scheduler

[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Oracle](https://img.shields.io/badge/Oracle--MySQL--Ready-brightgreen.svg)](#)
[![Deploy](https://img.shields.io/badge/Deploy-Fat--JAR--only-important.svg)]()

> **Zero-middleware, Oracle/MySQL-native enterprise workflow orchestration engine**  
> Runs as a single JAR – no message broker, no registry, no ZooKeeper, no Docker required.  
> Purpose-built for **end-to-end planning system orchestration** (data ingestion → processing → engine computation → reporting → external system write-back).

---

## Table of Contents

- [Why Task Scheduler?](#-why-task-scheduler)
- [Core Features](#-core-features)
- [Technology Stack](#-technology-stack)
- [Quick Start](#-quick-start)
- [Architecture Overview](#-architecture-overview)
- [Built-in Task Types (20+)](#-built-in-task-types-20)
- [5 Workflow Trigger Modes](#-5-workflow-trigger-modes)
- [Competitive Comparison](#-competitive-comparison)
- [Selection Guide](#-selection-guide)
- [API Overview](#-api-overview)
- [Configuration & Deployment](#-configuration--deployment)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🎯 Why Task Scheduler?

### ✅ Core Differentiators (Unique among competitors)

| Differentiator | Description |
|----------------|-------------|
| **Zero-middleware lightweight deployment** | `java -jar` to start – only JVM + Oracle/MySQL required. No Redis, no ZooKeeper, no message queue. Supports Windows/Linux. |
| **Oracle/MySQL native support** | Auto-detects database type via `DatabaseMetaData`. Full support for Oracle stored procedures (IN/OUT/INOUT), `DBMS_OUTPUT` capture, and 6 SQL task variants (including streaming Excel/CSV export). |
| **EVENT zero-polling wait** | `EventTask` + `CountDownLatch` – threads suspend with zero CPU consumption. Child workflow completion triggers an HTTP callback to wake the parent in real-time (no polling, unlike Airflow's `ExternalTaskSensor`). |
| **OR-gate DAG semantics** | Built-in `OrTask` – downstream tasks trigger when *any* upstream completes. Airflow/DolphinScheduler only support AND dependencies. |
| **Cascaded sub-workflow cancellation** | Cancelling a parent workflow recursively cancels all child workflows and their running tasks. Airflow does not propagate cancellation. |
| **TIMEOUT as an independent state** | `TIMEOUT` is a distinct terminal state (separate from `ERROR`/`CANCELED`), with dedicated UI colors and email templates. All competitors fold timeouts into generic failures. |
| **Built-in distributed lock task** | `LockTask` implements a distributed mutex via the `TS_LOCK` table – 5-second retry loop, `taskTimeout` expiration, and auto-release on cancellation. No competitor offers this as a built-in task type. |
| **Per-task granular operations** | Retry/Skip/Cancel at the individual task level – no need to restart the entire workflow. State-aware UI buttons with confirmation dialogs and Toast feedback. |
| **Excel-based environment synchronization** | Export workflow definitions as Excel, then import into another environment (upsert semantics – update if exists, create if not). Enables rapid dev→test→prod synchronization. |
| **3-level parameter inheritance + output persistence** | Global → Workflow → Task. `saveParam()` persists outputs to the DB, and downstream tasks automatically consume them. Airflow's XCom is limited to 48KB per entry. |
| **D3 Gantt chart + SVG export** | Dual-layer visualization (workflow + tasks) with collapsible sections, TIMEOUT-specific colors, and SVG export capability. |
| **Dual-mode DAG visualization** | ① jsPlumb DAG editor (no-code drag-and-drop for business users) ② D3 runtime DAG view (real-time status/duration/error display). |

### ⚠️ Known Limitations (Honest Disclosure)

| Limitation | Description | Mitigation |
|------------|-------------|------------|
| **Single JVM, no HA** | Process crash requires manual `recoverWorkflow()`. | Use process supervision (systemd) or K8s health checks + auto-restart in production. |
| **No horizontal scaling** | Max 100 concurrent workflows / 75 threads (configurable). | Suitable for small-to-medium scale (tens of thousands of daily tasks). For massive scale, consider Airflow/DolphinScheduler. |
| **Limited connector ecosystem** | 20 built-in types – new connectors require development and deployment (though extending `TaskType` is extremely simple). | Airflow has 200+ Providers. For big data pipelines, choose DolphinScheduler. |
| **No LDAP/SSO/OAuth2** | JWT local auth only, with two roles (ADMIN/USER). | Use a reverse proxy (Nginx `auth_request`) for SSO, or extend the auth module. |
| **Basic monitoring/alerting** | Email + Webhook (Feishu/WeChat Work) – no SLA miss tracking or alert escalation. | Expose metrics via `/api/stats` to Prometheus + AlertManager. |
| **No public community yet** | Currently open-sourcing under Apache 2.0. Comprehensive documentation available (Technical Design / User Manual / API Docs / TaskType Extension Spec). | Community will be built post-open-source. |

---

## ✨ Core Features

| Area | Capabilities |
|------|--------------|
| **Workflow DAG** | AND + OR semantics, DFS cycle detection, JSON serialization (definition + runtime views) |
| **Sub-workflow nesting** | Multi-level nesting, full parent-child callbacks, cancel propagation, crash recovery |
| **Event-driven coordination** | `EventTask` + `CountDownLatch` – zero-polling wait, DB-persisted, early-trigger race protection |
| **Task control** | Retry/Skip/Cancel (per-task granularity), Recover (checkpoint resume), Cascaded Cancel |
| **Parameter system** | 3-level inheritance (Global → Workflow → Task), `{variable}` placeholders, `saveParam()` output persistence |
| **Timeout control** | `TIMEOUT` as an independent state, Daemon thread interruption, dedicated exception type and email template |
| **Notifications** | Email (SMTP) + Webhook (Feishu/WeChat Work) – independent per-workflow/per-task switches |
| **Monitoring & Visualization** | D3 Gantt chart, runtime DAG (task type shown in the node tooltip), real-time log + parameter diagnostic popup (passwords masked), hourly monitoring reports |
| **Security & Audit** | JWT + RBAC (ADMIN/USER), ADMIN-only access to Global Parameters, password masking in Task Details, IP whitelist, full API audit logging |
| **Environment sync** | Excel import/export for workflow definitions – rapid cross-environment deployment |

---

## 📦 Technology Stack

| Technology | Version/Spec | Purpose |
|------------|--------------|---------|
| Java | 11 (LTS) | Primary language – leverages `var`, NIO2, module system |
| JVM | HotSpot | Runtime – relies on `Thread.interrupt()` for cancellation |
| Build Tool | Maven / Gradle | Dependency management and Fat-JAR packaging |
| Logging | Log4j 2 | Structured logging – `monitor.log` for dedicated reporting |
| Database | Oracle / MySQL | Auto-detects via `DatabaseMetaData` – no manual configuration |
| ORM | MyBatis 3 | SQL mapping – XML Mapper + annotations, no Spring dependency |
| Connection Pool | HikariCP | JDBC connection pooling |
| Time Wheel | Netty HashedWheelTimer 4.x | High-performance delayed execution for Cron scheduling |
| JSON | Gson 2.x | Serialization/deserialization for parameters and HTTP responses |
| Excel | Apache POI (SXSSF) 5.x | Workflow definition import/export, data export |
| Email | JavaMail 1.x | SMTP email delivery |
| JWT | JJWT | Token issuance and verification (no Spring Security) |
| HTTP Server | `com.sun.net.httpserver` | JDK-built-in lightweight embedded HTTP server |
| Password Hashing | BCrypt | Salted hash storage (`salt:hash` format) |

> **Architectural Style**: Deliberate **"No-Spring" (zero-framework)** design. All dependencies are manually injected via constructors, with services initialized linearly in `Main.java`. This yields: ultra-fast startup (no Bean scanning, no AOP proxies), full dependency transparency, and simplified deployment.

---

## 🚀 Quick Start

### Prerequisites
- Java 11 (LTS)
- Oracle 12c+ or MySQL 8+ (databases compatible with MySQL protocol also work)
- Maven 3.6+ or Gradle

> **Extra JDBC drivers**: only Oracle (`ojdbc11`) and MySQL (`mysql-connector-j`) ship in `lib/` and `libx/`. Task types that connect to another database need its driver jar dropped into `libx/` – e.g. `RowCountCompareTask` against a PostgreSQL target requires `postgresql-42.x.jar`, otherwise the task fails with "JDBC driver not found on classpath".

### Build
```bash
git clone https://github.com/your-username/task-scheduler.git
cd task-scheduler
mvn clean package
# or ./gradlew build
```

### Prepare Database
Execute the DDL scripts from the `db/` directory (Oracle and MySQL versions are provided separately).  
Database connection is configured in `mybatis-config.xml`. On startup, the system automatically detects the database type via `DatabaseMetaData`.

### Start
```bash
java -jar target/task-scheduler-1.0.jar
```

The embedded HTTP server listens on port `8888` by default.  
Access the Web UI at `http://localhost:8888/static/index.html` (requires `enableStaticResource=true` in global parameters).

### Default Account
- Username: `admin`
- Password: `admin123` (**Please change immediately!**)

---

## 🧱 Architecture Overview

The system follows a **six-layer architecture** with zero framework dependencies (No-Spring):

```
┌─────────────────────────────────────────────────────────────┐
│  Access Layer   – Embedded HTTP Server (com.sun.net.httpserver) │
│                   REST API routing, BaseHandler unified auth    │
├─────────────────────────────────────────────────────────────┤
│  Service Layer  – ExecutionService / DefinitionService /      │
│                   ParamsService / SchedulerService etc.       │
├─────────────────────────────────────────────────────────────┤
│  Engine Layer   – WorkflowEngine (central coordinator)        │
│                   WorkflowInstance (runtime state container)  │
│                   DAG construction & validation (AND/OR)      │
├─────────────────────────────────────────────────────────────┤
│  Scheduler Layer – CronScheduler + Netty HashedWheelTimer     │
│                   6-field Cron, 100ms precision, hot-reload   │
├─────────────────────────────────────────────────────────────┤
│  Task Layer     – Task interface + 20+ implementations        │
│                   AbstractTask → TimeoutTask → Script/Sql     │
├─────────────────────────────────────────────────────────────┤
│  Persistence    – MyBatis Mapper + DAO + Service              │
│                   HikariCP connection pool, ThreadLocal TX     │
└─────────────────────────────────────────────────────────────┘
```

**Startup Flow**: `Main.java` performs 16 linear initialization steps (MyBatis → SeqIdGenerator → DAOs → Services → EventManager → TaskFactory → WorkflowEngine → NettyTimeWheel → CronScheduler → WorkflowMonitor → HttpServer → ShutdownHook → main thread blocks).

---

## 🛠 Built-in Task Types (20+)

| Category | Task Type | Description |
|----------|-----------|-------------|
| **Script** | ShellTask | `/bin/bash` script execution with real-time log capture |
| | CmdTask | `cmd.exe` batch processing (Windows) |
| | PythonTask | Python3/Python script execution |
| | DataXTask | DataX ETL jobs (inline JSON or file-path mode) |
| **SQL** | DMLSqlTask | INSERT/UPDATE/DELETE – returns affected row count |
| | RowCountCompareTask | Source/target row-count comparison across two databases (Oracle → Oracle/PostgreSQL) – fails the task on mismatch |
| | SqlStatementTask | SELECT query – results serialized to JSON |
| | ExportCsvTask | Streaming SELECT results to CSV (UTF-8 BOM) |
| | ExportExcelTask | Streaming SELECT results to Excel (SXSSF, low memory footprint) |
| | ProcedureTask | Generic stored procedure (IN/OUT/INOUT parameter binding) |
| | OracleProcedureTask | Oracle-specific – `DBMS_OUTPUT` capture, `RETURN_CODE` auto-evaluation |
| **HTTP** | HttpTask | 5 auth modes: NONE / BASIC / BEARER / API_KEY / HYBRID (auto-login + token refresh) |
| | BYSRETask | SRE platform job invocation + status polling (proprietary) |
| **File Transfer** | FileXTask | Local / FTP / SFTP / compressed files – supports copy, move, sync |
| **Orchestration** | WaitTask | Wait until a specified time – segmented sleep with interruptible checks |
| | LockTask | Distributed mutex lock (via `TS_LOCK` table) – 5-second retry loop, auto-release on cancel |
| | EventTask | `CountDownLatch`-based event wait – zero polling, DB-persisted |
| | WorkflowTask | Synchronously start a sub-workflow and wait for completion callback – supports multi-level nesting |
| | OrTask | OR-gate convergence – triggers when *any* upstream completes |
| **Java Extension** | JavaTask | Reflection-invokes any Java method in the `com.ts.custom` package – no restart required |

> **Extension Mechanisms**: ① **JavaTask (recommended, no restart)** – implement `public Object execute(Map<String,Object> params)` in `com.ts.custom`; ② **Register new TaskType** – via `POST /api/tasktype` (requires restart).

---

## ⏱ 5 Workflow Trigger Modes

| Trigger Mode | Use Case | Mechanism | Waits for Sub-flow? |
|--------------|----------|-----------|:-------------------:|
| **Cron (Scheduled)** | Batch processing, periodic reports, data sync | `CronScheduler` + `NettyTimeWheel` – 6-field Cron (incl. `L` month-end), 100ms precision, hot-reload | No (auto-renews) |
| **Synchronous (WORKFLOW task)** | Serial orchestration requiring sub-flow completion | `WorkflowTask` + engine callback – parent task stays `RUNNING`, child completion triggers callback | Yes (sync wait) |
| **Async HTTP trigger** | External system invocation, decoupled calls | HTTP task `POST /api/workflow/start` – parent task completes immediately after trigger | No (fire-and-forget) |
| **Async HTTP + EVENT callback** | Cross-instance/cross-server parent-child workflows needing completion awareness | `EventManager` + `EventTask` – `CountDownLatch` zero-polling wait, DB-persisted | Yes (zero-polling event wait) |
| **UI / API manual trigger** | Ad-hoc debugging, emergency runs, external integration | "Trigger" button in monitoring UI or `POST /api/workflow/start` | No |

### Cron Expression Syntax

Parsed by `com.ts.core.scheduler.CronExpression`. Fields are
`second(0-59) minute(0-59) hour(0-23) day-of-month(1-31) month(1-12) day-of-week(0-7)`.

| Syntax | Meaning | Example |
|--------|---------|---------|
| `*` | Every value | `0 0 * * * ?` – hourly |
| `5` | Single value | `0 0 12 ? * 1` – Mondays at 12:00 |
| `1-5` | Range | `0 0 12 ? * 1-5` – weekdays at 12:00 |
| `1,3,5` | List | `0 0 12 1,15 * ?` – 1st and 15th at 12:00 |
| `*/5`, `10/5` | Step | `0 */10 * * * ?` – every 10 minutes |
| `?` | Unspecified (day-of-month / day-of-week only) | `0 0 12 1 * ?` |
| `L` | Last day of month (day-of-month only) | `0 0 2 L * ?` – last day at 02:00 |
| `L-N` | `N` days before the last day | `0 0 2 L-4 * ?` – fifth-to-last day |

Rules and caveats:

- **Exactly one** of day-of-month / day-of-week must be `?`. Expressions such as
  `0 0 12 * * *` or `0 0 12 1 * 3` are rejected at parse time.
- Day-of-week accepts `0-7`; both `0` and `7` mean Sunday.
- **`L-N` resolves against the month being matched**, not at parse time, so the
  same offset lands on a different date each month: `L` is 31 in January but
  28/29 in February, and `L-4` is the 27th in January but the 24th in February.
  Equivalently, `L-N` is the `N+1`-th day counting back from month end.
- If an offset reaches past the first day of a short month, that month simply
  does not match — `L-29` fires only in months long enough to contain it. This
  is not an error; use `L` when you want every month to fire.
- `L` may be combined in a list (`0 0 2 L-4,15 * ?`) but **not** with ranges or
  steps. The offset must be `0-30`; larger values can never match any month.
- **Not supported**: alphabetic aliases (`MON`, `JAN`), `W` (nearest weekday),
  `#` (nth weekday), `LW`, and `6L` (last weekday of month). These are rejected
  at parse time rather than silently misinterpreted.

---

## 📊 Competitive Comparison

| Dimension | Task Scheduler | Apache Airflow | DolphinScheduler | XXL-Job | PowerJob |
|-----------|:-------------:|:--------------:|:----------------:|:-------:|:--------:|
| Language | Java 11 | Python 3 | Java 8+ | Java 8+ | Java 8+ |
| Zero-middleware deployment | ✅ JVM+DB only | ❌ Redis/RabbitMQ+PG | ❌ ZooKeeper+multi-service | ❌ Admin+Executor+DB | ❌ Server+Worker+MongoDB |
| Oracle proc + DBMS_OUTPUT | ✅ Native | ❌ | ❌ | ❌ | ❌ |
| OR-gate DAG semantics | ✅ Built-in `OrTask` | ❌ AND only | ❌ AND only | ❌ No DAG | ❌ AND only |
| EVENT zero-polling wait | ✅ `CountDownLatch` | ❌ Polling Sensor | ❌ Polling | ❌ | ❌ |
| Built-in distributed lock | ✅ `LockTask` | ❌ | ❌ | ❌ | ❌ |
| TIMEOUT as independent state | ✅ Terminal state | ❌ Folded into FAILED | ❌ Folded into FAILED | ❌ Folded into FAILED | ❌ Folded into FAILED |
| Cascaded sub-workflow cancel | ✅ Recursive | ❌ Does not propagate | ⚠️ Best-effort | ❌ | ⚠️ Limited |
| Per-task Retry/Skip/Cancel | ✅ Task-level granularity | ⚠️ Coarse-grained | ⚠️ Limited | ❌ | ⚠️ Limited |
| 3-level param inheritance + output persistence | ✅ Full support | ⚠️ XCom 48KB limit | ⚠️ Limited | ❌ | ⚠️ Limited |
| Excel environment deployment | ✅ Import/Export | ❌ | ⚠️ Limited | ❌ | ❌ |
| HA / Horizontal scaling | ❌ Single JVM | ✅ Mature HA | ✅ Mature HA | ✅ Stateless Admin | ✅ Active-Active |
| Big Data / ML ecosystem | ⚠️ Extensible | ✅ 200+ Providers | ✅ Native Spark/Flink | ❌ | ❌ |
| Enterprise SSO / Multi-tenancy | ❌ Basic RBAC | ✅ LDAP/OAuth2 | ✅ Project-level isolation | ⚠️ Limited | ⚠️ Limited |

---

## 🎯 Selection Guide

### When to Choose Task Scheduler

- ✅ **Oracle/MySQL-heavy environments** – stored procedures with OUT params, `DBMS_OUTPUT` capture, multi-format SQL exports
- ✅ **Strict lightweight deployment requirements** – no Python/Redis/ZooKeeper/Docker allowed in restricted enterprise networks, supports Windows/Linux
- ✅ **End-to-end planning system orchestration** – data ingestion → processing → planning engine → reporting → external system write-back
- ✅ **Complex nested workflow orchestration** – full parent-child callbacks, cascaded cancellation, crash recovery
- ✅ **Event-driven zero-polling coordination** – `EventTask` + `CountDownLatch` for cross-instance/cross-server collaboration
- ✅ **Fine-grained task control** – per-task Retry/Skip/Cancel, independent `TIMEOUT` detection
- ✅ **AND/OR parallelism + distributed locking** – complex resource contention scenarios
- ✅ **No-code DAG orchestration for business users** – developers extend `TaskType`, business users drag-and-drop to configure

### Recommendations for Other Scenarios

| Scenario | Recommended Tool |
|----------|------------------|
| Python data pipelines requiring 200+ Provider ecosystem | **Apache Airflow** |
| Cloud-native big data (native Spark/Flink/Hadoop support) | **DolphinScheduler** |
| Lightweight Java microservice task scheduling (no DAG required) | **XXL-Job** |
| MapReduce-style large task splitting across multiple workers | **PowerJob** |

---

## 📡 API Overview

All APIs require JWT Bearer Token authentication with RBAC (ADMIN/USER).

| Prefix | Function | Permission |
|--------|----------|------------|
| `/api/auth` | Login, token management | No auth required (login) |
| `/api/workflow` | Start/cancel/recover/retry/skip, query instances and logs | All authenticated users |
| `/api/definition` | Workflow/task definition CRUD, DAG import/export (Excel) | ADMIN only (write) |
| `/api/scheduler` | Cron schedule CRUD (hot-reload) | All authenticated users |
| `/api/params` | Global/workflow/task parameter management | ADMIN only (write); `GET /api/params/global` is also ADMIN only |
| `/api/tasktype` | Task type registry management | ADMIN only (write) |
| `/api/lock` | Distributed lock query and release | All authenticated users |
| `/api/event` | Event record query and trigger | All authenticated users |
| `/api/history` | Historical run paginated query + CSV export | All authenticated users |
| `/api/stats` | Real-time monitoring snapshot (thread pool/scheduler/time wheel) | All authenticated users |
| `/api/users` | User account management | ADMIN only (write) |

For detailed API specifications, refer to the [REST API Documentation](docs/API.md).

---

## ⚙️ Configuration & Deployment

### Global Parameter Configuration

All configurations are stored as JSON in the `GLOBAL_PARAMS` table and support hot-reload:

| Parameter Key | Config Class | Key Fields |
|---------------|--------------|------------|
| `ThreadPoolConfig` | Thread pool | `corePoolSize=15`, `maxPoolSize=75`, `queueCapacity=150` |
| `EmailConfig` | SMTP mail | `host`, `port`, `ssl`, `username`, `password`, `from` |
| `EmailTemplateConfig` | Email templates | Subject and body for each notification type (supports `{{variable}}`) |
| `HttpServerConfig` | HTTP server | `port=8888`, `staticPath`, `enableStaticResource` |
| `WebhookConfig` | Feishu/WeChat Work | `enabled`, `url`, `secret`, `mentionList` |

> **Access and masking.** `GLOBAL_PARAMS` holds environment credentials (SMTP password, webhook secret), so reading `GET /api/params/global` as well as updating it is restricted to the **ADMIN** role; the Global Parameters page is hidden from USER accounts. Separately, in **Monitoring → Task Details → Parameters**, any parameter whose key contains `password` (for example `password`, `sourcePassword`, `targetPassword`, `ssh.password`, `httpAuthPassword`) is returned masked as `*` characters matching the original value length, for all roles.

### Deployment Options

**JAR deployment** (recommended):
```bash
java -jar -Dconfig.file=/path/to/application.properties task-scheduler.jar
```

**Systemd service (Linux)**: Refer to the sample unit file in the `deploy/` directory.

**Reverse proxy (Nginx)**: Recommended for production to handle SSL and access control. The embedded server supports IP whitelisting.

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [Technical Design Document](docs/TaskScheduler_Technical_Design.md) | Complete system architecture, engine internals, task types, API design |
| [User Manual](docs/UserManual.md) | Frontend UI operations guide (DAG editor, monitoring, schedule management) |
| [REST API Documentation](docs/API.md) | Request/response formats and examples for all APIs |
| [TaskType Extension Specification](docs/TaskTypeExtension.md) | Developer guide for creating custom task types |
| [Database DDL](db/) | DDL scripts for Oracle and MySQL |

---

## 🤝 Contributing

Task Scheduler is open-sourced under the Apache 2.0 license – community contributions are welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing`)
5. Open a Pull Request

Please refer to [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## 📄 License

Copyright © 2024-2025 Rock Rao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

**Trademark Notice**: The name "Task Scheduler" and its logo are trademarks of Rock Rao.
Under the Apache 2.0 license, you are free to use, modify, and distribute the code,
but you may not use the project's name or logo to endorse or promote your derived
products without explicit written permission.

---

## 📬 Contact

- Issues: [GitHub Issues](https://github.com/your-username/task-scheduler/issues)
- Email: raohuacai@126.com

---

**Built with ❤️ for enterprise workflow automation.**
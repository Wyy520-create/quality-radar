# Quality Radar · 持续质量决策平台

> 把“测试报告”变成可解释的发布决策：安全导入 JUnit XML → 聚合质量趋势与失败指纹 → 解析 Git diff 计算风险 → 推荐最小回归集 → 执行质量门禁。

![Java](https://img.shields.io/badge/Java-21-ff6b35) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6db33f) ![React](https://img.shields.io/badge/React-TypeScript-61dafb) ![Docker](https://img.shields.io/badge/Docker-Compose-2496ed)

## 这是什么

Quality Radar 是一个面向研发与测试协作的本地质量平台。它不执行测试，也不伪装成 AI 预测工具；它解决的是交付前真实、可解释的问题：**这次变更风险多高、应该跑哪些回归、已有测试结果是否允许发布。**

项目内置 `shop-demo` 演示项目和静态 JUnit XML 样例，clone 后无需账号、Token 或外部 SaaS。

## 核心能力

- **JUnit XML 安全导入**：支持 `testsuites / testsuite / testcase / failure / error / skipped`，拒绝超 10MB 文件，禁用 DTD 和外部实体，原始报告不入库，只持久化结构化结果与 SHA-256。
- **质量洞察**：显示最近一次执行、通过率趋势、失败指纹聚合；失败信息中的数字会归一化后生成 fingerprint，便于发现重复故障。
- **可解释风险评分**：只解析用户提供的 unified diff，不在服务端运行 `git`，不需要 GitHub Token。生产代码、敏感模块、配置/迁移、改动规模均有可见评分项。
- **最小回归推荐**：根据固定的“代码路径 → 组件 → 测试目录”映射给出推荐集和预计时长；未覆盖组件会明确暴露，绝不伪造覆盖率。
- **质量门禁**：执行错误、失败率超过 5%、空执行等情况会阻断发布，并返回具体违规原因。
- **完整交付**：React + TypeScript 前端，Spring Boot 后端，PostgreSQL 存业务事实，Redis 预留作可替换缓存，Docker Compose 一键启动。

## 架构

```text
浏览器 http://localhost:4170
        │
        ▼
 React + TypeScript / Nginx
        │ /api 同源反向代理
        ▼
 Spring Boot 3.3 / Java 21
  ├── JUnit XML 安全解析、报告和失败指纹
  ├── Diff 规则评分、最小回归推荐、质量门禁
  ├── PostgreSQL 16：项目、执行、用例结果、风险、门禁事实
  └── Redis 7：可替换的短期缓存边界（不承载正确性）
```

## 页面与演示路径

1. **质量概览**：查看最近执行、趋势和失败指纹。
2. **报告导入**：上传 [`demo/sample-junit.xml`](demo/sample-junit.xml)，平台展示 4 条用例中 3 条通过、1 条失败。
3. **变更风险**：粘贴已有示例 diff，查看评分因素和推荐的支付契约回归。
4. **质量门禁**：将最近一次执行与当前风险评估合并为 `PASSED / FAILED` 结论，并展示具体违规项。

## Docker 一键运行（推荐）

### 前置条件

- 安装 Docker Desktop（Windows/macOS）或 Docker Engine + Compose plugin（Linux）。
- 确认终端能执行：

```bash
docker --version
docker compose version
```

### Linux / macOS

```bash
git clone git@github.com:Wyy520-create/quality-radar.git
cd quality-radar
docker compose up -d --build
```

浏览器打开：`http://localhost:4170`

首次构建需要下载 Java、Node、PostgreSQL、Redis 镜像和依赖，网络正常时可能需要数分钟；后续构建会命中 Docker 缓存。

### Windows PowerShell

```powershell
git clone git@github.com:Wyy520-create/quality-radar.git
cd quality-radar
docker compose up -d --build
```

浏览器同样访问：`http://localhost:4170`。

> 没有 Git 也可以在仓库页面点击 **Code → Download ZIP**，解压后在该目录打开 PowerShell，再运行最后一条命令。

### 确认服务状态

```bash
docker compose ps
```

预期 `postgres`、`redis`、`api`、`web` 都是 `running` 或 `healthy`。查看日志：

```bash
docker compose logs --tail=80 api
```

停止服务（保留数据库数据）：

```bash
docker compose down
```

如需连同演示数据一起清除：

```bash
docker compose down -v
```

## 本地开发（不使用 Docker）

本地开发要求 Java 21、Node.js 22、PostgreSQL 16、Redis 7；仅想体验功能时，请优先使用 Docker。

```bash
# 终端 1：后端
cd backend
mvn spring-boot:run

# 终端 2：前端
cd frontend
npm ci
npm run dev
```

默认前端开发地址由 Vite 输出；本地后端默认是 `http://localhost:8080`。本地开发时请自行在 Vite 开发服务器配置代理或直接通过 Docker 运行。

## API 快速验证

Docker 服务启动后，下面命令通过前端 Nginx 访问后端 API：

```bash
# 查询内置演示项目
curl http://localhost:4170/api/v1/projects

# 导入示例报告
curl -F report=@demo/sample-junit.xml -F branch=main \
  http://localhost:4170/api/v1/projects/00000000-0000-0000-0000-000000000001/test-runs:import

# 计算变更风险
curl -X POST http://localhost:4170/api/v1/projects/00000000-0000-0000-0000-000000000001/risk-assessments \
  -H 'Content-Type: application/json' \
  -d '{"diff":"+++ b/src/payment/PaymentService.java\n+public void capture() {}\n"}'
```

Windows PowerShell 中可使用 `curl.exe` 替代 `curl`；JSON 内双引号需要使用反引号转义，例如 `\"diff\"`。

## 评分规则

| 因素 | 分值 |
|---|---:|
| 每个生产代码文件变更 | +8，最多 +32 |
| `auth`、`security`、`payment`、`order`、`migration` 敏感路径 | 每文件 +15，最多 +30 |
| `pom.xml`、`package.json`、应用配置、迁移文件 | 每文件 +12，最多 +24 |
| 改动 100–299 / ≥300 行 | +8 / +16 |
| 有受影响组件却缺少测试映射 | +15 |

`0–29` 为 LOW，`30–59` 为 MEDIUM，`60–100` 为 HIGH。相同 diff、相同规则与测试映射，输出必定一致。

## 工程约束与测试策略

CI 只做确定性检查：Java 单元测试、TypeScript 类型检查/构建、Docker Compose 健康检查和静态演示数据 API 冒烟。不会调用任何付费模型、外部 Git 平台、第三方线上服务；不通过失败重跑掩盖不稳定性。

浏览器级 E2E 和视觉回归不进入基础 CI，避免共享 runner 的浏览器时序噪声；这类检查应放在本机或专用浏览器网格。

## 目录结构

```text
quality-radar/
├── backend/                 # Spring Boot API、Flyway 迁移、JUnit 解析与风险服务
├── frontend/                # React + TypeScript + Vite 可视化前端
├── demo/sample-junit.xml    # 可直接导入的演示报告
├── compose.yaml             # PostgreSQL / Redis / API / Web 四服务编排
└── README.md                # Linux / Windows 从零操作说明
```

## 面试说明

本项目的重点不是“用规则冒充智能”，而是把质量决策变成可复核资产：每一分风险均能定位到变更事实，每条推荐均能追溯到组件映射，每次门禁结论都有具体违规项。实际生产环境可进一步接入 GitHub/GitLab Webhook、真实测试目录、对象存储与 RBAC；这些能力刻意未在 MVP 中伪造。

## License

MIT License. Copyright (c) 2026 余阳辉。

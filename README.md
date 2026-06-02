# AI Data ETL Platform

基于 Spring Cloud Alibaba 微服务架构的 AI 数据解析与智能分析平台。

## 项目架构

```
ai-data-etl-platform (父工程，管理依赖版本)
├── ai-data-common           (公共工具包，不单独启动)
├── ai-data-model            (共享实体/DTO，不单独启动)
├── ai-data-service-client   (Feign 接口，不单独启动)
├── ai-data-gateway          (网关服务，端口 8000)
├── ai-data-user             (用户与系统管理服务，端口 8010)
├── ai-data-processor        (数据解析与 ETL 服务，端口 8020)
├── ai-data-intelligence     (AI 智能分析服务，端口 8030)
└── ai-data-monitor          (监控与 Token 审计服务，端口 8040)
```

## 模块依赖链

```
ai-data-service-client (Feign 接口)
        ↓
ai-data-model (共享 DTO/实体)
        ↓
ai-data-common (工具类、统一返回体、异常)
```

业务服务模块根据需求依赖 `ai-data-service-client`（调用其他服务）和/或 `ai-data-model`（共享实体）。

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.2.4 |
| Spring Cloud | 2023.0.1 |
| Spring Cloud Alibaba | 2023.0.1.0 |
| Nacos (注册中心/配置中心) | 2.x |
| Sentinel (流量控制/熔断降级) | 配合 Spring Cloud Alibaba |
| RabbitMQ (消息队列) | 最新稳定版 |
| MyBatis-Plus | 3.5.5 |
| MySQL | 8.0+ |
| Redis (分布式锁) | 最新稳定版 |
| EasyExcel (流式读写) | 最新稳定版 |
| Java | 17 |

## 模块职责

### ai-data-common (通用公共模块)

纯 Java 依赖包，不对外提供网络服务。

- 封装全局统一返回对象 `Result<T>`、全局异常处理类
- 全局异常处理器 `GlobalExceptionHandler`
- 工具类封装（EasyExcel 监听器基类、RSA 签名工具等）

### ai-data-model (共享实体/DTO 模型)

纯依赖包，存放跨服务公用的实体和 DTO。

- 公用的实体类（如 `UserDTO`）
- RabbitMQ / Token 计费等消息体
- 依赖 `ai-data-common`（继承 `BaseEntity`、引用 `Result` 等）

### ai-data-service-client (Feign 接口模块)

纯依赖包，只放接口不放实现。

- 定义跨服务调用的 Feign 接口（如 `UserFeignClient`）
- 引用 `ai-data-model` 的 DTO 作为接口参数/返回值

### ai-data-gateway (微服务统一网关)

系统的唯一入口，路由分发（端口 `8000`）。

- 集成 Nacos 动态实现路由转发到后端各个微服务
- 集成 Sentinel，对高频 Excel 上传请求进行限流，保护 Processor 服务
- 基于 WebFlux 响应式编程模型

### ai-data-user (用户与系统管理微服务)

基础业务服务（端口 `8010`）。

- 用户注册、登录、权限管理（JWT）
- 文件元数据历史记录管理（文件名、上传时间、解析状态、AI 分析状态、下载链接）
- 文件状态机维护：上传中 -> 解析中 -> AI 分析中 -> 分析完成 -> 失败
- 利用 Redis 分布式锁防止状态并发乱序

### ai-data-processor (数据解析与 ETL 微服务)

数据密集型核心服务（端口 `8020`）。

- Excel 上传接口，基于 EasyExcel `PageReadListener` 流式读取超大表格
- 数据清洗（ETL），批量写入数据源
- 每读满一批数据（如 2000 条），打包投递到 RabbitMQ 数据队列
- JVM 内存控制与批处理性能优化

### ai-data-intelligence (AI 智能分析微服务)

AI 计算密集型核心服务（端口 `8030`）。

- RabbitMQ 消费者，按 QoS（prefetch=1）匀速消费数据
- 集成 LangChain4j / Spring AI，组合 Prompt 调用大模型 API
- 利用大模型 Structured Outputs 强制返回结构化的业务指标与分析洞察
- 调用 EasyExcel 动态生成"AI 批注版 Excel 报表"并存储到对象存储（OSS/MinIO）
- Sentinel 熔断降级：大模型网络抖动时实施线程隔离和本地算法 Fallback

### ai-data-monitor (监控与 Token 审计微服务)

可观测性中台服务（端口 `8040`）。

- 监听 RabbitMQ Token-Billing-Queue，异步记录每次 AI 调用的流量成本
- 提供前端看板所需的多维度 Token 消耗趋势数据（天/小时/模型/用户）
- 集成 Spring Boot Actuator 监控集群健康状态
- MQ 异步解耦 + 定时任务聚合分析

## 快速启动

### 环境准备

1. JDK 17+
2. Nacos Server 2.x（默认 `127.0.0.1:8848`）
3. MySQL 8.0+（各业务模块需创建对应数据库）
4. RabbitMQ
5. Redis

### 数据库初始化

```sql
-- ai-data-user 模块
CREATE DATABASE IF NOT EXISTS ai_data_user DEFAULT CHARACTER SET utf8mb4;
```

### 构建与运行

```bash
# 编译全部模块
mvn clean compile

# 按顺序启动各服务
mvn spring-boot:run -pl ai-data-gateway
mvn spring-boot:run -pl ai-data-user
mvn spring-boot:run -pl ai-data-processor
mvn spring-boot:run -pl ai-data-intelligence
mvn spring-boot:run -pl ai-data-monitor
```

## 端口规划

| 服务 | 端口 |
|------|------|
| ai-data-gateway | 8000 |
| ai-data-user | 8010 |
| ai-data-processor | 8020 |
| ai-data-intelligence | 8030 |
| ai-data-monitor | 8040 |

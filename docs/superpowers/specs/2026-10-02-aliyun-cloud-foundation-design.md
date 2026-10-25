# 月笺阿里云低成本云端基础设计

## 目标

为中国大陆两台手机提供一个可真实使用、成本可控、可迁移的云端运行方案：阿里云学生 ECS 承载 Spring Boot 与第一阶段自托管 PostgreSQL，阿里云 OSS 保存图片/音乐/视频和数据库备份；后续可把 PostgreSQL 无感迁移到阿里云 RDS PostgreSQL。

## 已确认的产品约束

- 用户范围仍然只有一对情侣，不做公开社交、统计榜单或多租户运营后台。
- 云端数据默认私有，只有同一情侣空间的成员可以读取记录、评论和媒体。
- Android APK 不包含数据库密码、OSS AccessKey、SecretKey 或任何云厂商长期密钥。
- 真实媒体必须经过上传、校验、完成确认后才进入可同步状态；不能用“上传成功”的假状态替代对象存储中的真实文件。
- 先按学生优惠和低固定成本设计，不自动购买、开通或操作用户云账号。
- 生产环境必须使用 HTTPS 域名；当前开发包的局域网地址只保留为 debug 配置。

## 成本优先的目标架构

```text
Android APK
    │ HTTPS + Bearer session
    ▼
阿里云 ECS 学生机
    ├── Nginx / TLS 终止
    ├── Spring Boot API
    └── PostgreSQL（第一阶段 Docker 自托管）
          │ nightly pg_dump
          ▼
       阿里云 OSS 私有 Bucket
       ├── media/{coupleId}/{assetId}
       └── backups/postgres/{date}.dump.gz
```

第一阶段不采购 RDS，原因是当前只有两位用户，学生 ECS 的优惠对计算资源最有价值，而 RDS 的持续费用可能成为主要成本。数据库不因此裸奔：每天备份到 OSS，保留轮换副本，并提供恢复演练命令。真实内容增多、备份恢复要求提升或 ECS 运维成本超过接受范围时，将 `JDBC_DATABASE_URL` 切换到同地域 RDS PostgreSQL；Android 与服务端业务协议不变。

## 服务端边界

### 配置

保留当前环境变量协议，并补充明确的生产校验：

- `JDBC_DATABASE_URL`
- `JDBC_DATABASE_USERNAME`
- `JDBC_DATABASE_PASSWORD`
- `BOOTSTRAP_SECRET`
- `MOON_LETTER_STORAGE_MODE=s3`
- `S3_ENDPOINT`
- `S3_REGION`
- `S3_BUCKET`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`

OSS 凭据只出现在 ECS 的环境变量或受保护的部署文件中，使用一个只允许访问指定 Bucket/前缀的 RAM 身份。Bucket 默认私有，媒体对象不生成公开链接。

### 对象存储适配器

`ObjectStorage` 是业务层唯一依赖。`LocalObjectStorage` 继续服务本地开发和测试；`S3ObjectStorage` 使用 AWS SDK for Java 2.x 的 S3 客户端和预签名器，兼容阿里云 OSS 的 S3 API。适配器必须支持：

1. 生成带有效期和 MIME 类型约束的 PUT 预签名地址；
2. 查询对象大小和 SHA-256 元数据；
3. 服务端读取私有对象，供当前 Android 图片读取链路使用；
4. 防止 Bucket、Endpoint、对象 key 未配置时应用以“本地模式”误启动。

### 媒体状态闭环

1. Android 向 API 请求上传票据；服务端写入 `UPLOADING` 资产记录并返回短时 PUT 地址。
2. Android 将文件直接 PUT 到 OSS，不把长期 OSS 密钥放进 APK。
3. Android 调用 `complete`；服务端从 OSS `head` 校验字节数和 SHA-256，成功后标记 `READY`。
4. 只有 `READY` 资产才会解除记录 outbox 的媒体门禁；失败对象标记 `FAILED`，可再次获取票据。
5. 读取媒体时仍由 API 验证情侣空间权限，再从私有 OSS 读取；未来可把读取改为短时 GET 地址，业务模型不变。

第一阶段保留本地模式的 `/data` 兼容路径，避免开发机测试回归；S3 模式禁止把上传/下载降级成假成功。

## 备份与恢复

- 使用 `pg_dump` 的自定义格式，压缩后上传到 `backups/postgres/`。
- 保留最近 7 天日备份和最近 4 个周备份；清理动作只允许针对明确的备份前缀。
- 恢复到临时 PostgreSQL 后先执行 Flyway 校验和核心表计数，再切换应用连接串。
- 备份脚本不输出数据库密码；密码通过环境变量或受保护的部署环境注入。
- 备份成功只代表文件上传成功，恢复演练另有明确命令和日志。

## 不在本阶段解决

- 不购买云资源、不替用户完成实名认证、学生认证或域名备案。
- 不接 FCM、CDN、转码、消息队列和高可用多 ECS。
- 不把 OSS 直接暴露为公开图床。
- 不把云厂商 SDK 引入 Android；所有云存储凭据和签名逻辑留在服务端。

## 验收标准

- 本地模式现有服务端测试仍然通过。
- S3 模式配置缺失时快速失败，配置完整时可以创建票据。
- 使用 MinIO 的集成测试覆盖：申请票据、上传、完成校验、权限读取和校验失败。
- Android 媒体上传使用票据 URL，上传完成后记录才解除媒体门禁。
- `docker compose` 能以不含真实密钥的模板启动本地依赖；生产部署文档明确哪些变量必须由用户填写。
- 备份脚本提供 dry-run/恢复说明，不宣称未执行过的云端恢复已经通过。

<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# OSS 可读目录：V20 更新

本地代码、APK、后端更新包已准备并测试。ECS 尚未更新：免密 SSH 检查被服务器拒绝，未索要或读取密码/OSS 密钥，未搬动云端文件。必须完成以下部署后，新目录规则才在 ECS 生效。

## 分类规则

```text
couples/<空间 UUID>/
├── avatars/<上传年月>/<文件 UUID>.png
├── albums/photos/<上传年月>/<文件 UUID>.jpg
├── records/images/<上传年月>/<文件 UUID>.jpg
├── records/audio/<上传年月>/<文件 UUID>.m4a
└── records/video/<上传年月>/<文件 UUID>.mp4
```

扩展名按服务端白名单 MIME 生成，以上只是例子；支持 PNG/JPEG/HEIC 等原图、MP3/M4A 等音频及 MP4/MOV 等视频。年月使用上传票据创建时间、Asia/Shanghai 时区，不是照片拍摄时间。路径不含姓名、相册标题或正文。

Android 相册上传传 `purpose=ALBUM`，头像传 `AVATAR`，记录默认 `RECORD`。服务端只允许三种用途，头像/相册仅接受 IMAGE。旧客户端未传用途时默认 RECORD，因此要用 V20 上传才能区分头像和相册。记录按 IMAGE/AUDIO/VIDEO 再分类。

旧数据库 `object_key` 保持不变，下载继续读取该字段；重试同一 operationId 返回原有 assetId 和路径，不会移动文件或重复生成分类路径。没有新增数据库迁移。不更换 Bucket，不开启公共访问。若 RAM 权限仅允许旧 `couples/*/assets/*`，需调整为覆盖本 Bucket 内 `couples/*` 的必要读写操作；不建议授予全部 OSS 管理权限。

## 部署到 ECS

在 **Windows PowerShell** 执行（不是 ECS 终端）：

```powershell
scp "E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-oss-v20-update-20261004.tar" root@192.0.2.10:/root/
```

密码仅在自己的终端输入，不发送到聊天。

然后在 **ECS SSH 终端** 执行：

```sh
tar -xf /root/moonletter-oss-v20-update-20261004.tar -C /opt/moonletter
sh /opt/moonletter/update-private-app-v20.sh
```

脚本验证 JAR SHA-256、保留旧 JAR、只重建 app 容器，等待本机 8080 健康检查。失败会尝试恢复旧 JAR，需继续查看 health/logs；不承诺故障时一定恢复成功。不会替换 .env、清数据库/卷、重配客户端或新增公网端口。成功应输出 `V20 backend healthy`。

下一步用 V20 新上传一张相册照片并更换一次自己的头像，在 OSS 查看新前缀；同一张旧照片仍应在另一台设备正常查看。此步骤尚未在 ECS 实测，不应把本地测试视为真实 OSS 上传验收。

## 验证与产物

- 后端完整测试：94 项，0 失败/错误；包含新分类票据、默认记录、Asia/Shanghai 跨月、旧路径上传重试及伴侣下载。
- Android 网络：11 项，0 失败；请求序列化保留明确用途。
- Android 应用：69 项，0 失败；指定新 JAR 重跑，真实 HTTP 相册测试确认 ALBUM、`albums/photos/`、原图双端一致。
- 华为 DBY-W09 相册原生测试：12 项通过，22.011 秒；已确认手机和平板均覆盖安装 V20，原 debug 签名 v2/v3 验证通过。
- 部署脚本通过本地隔离容器 `sh -n` 语法检查；未在服务器执行部署、失败回滚演练。
- 独立只读代码审查未发现本轮范围内的阻断问题；审查结论不替代 ECS 上的真实验收。
- 不改已验收的自然比例照片拼图、相册集封面及 V19 加载状态。

APK：`android/app/build/outputs/apk/debug/MoonLetter-oss-folders-v20-20261004.apk`

APK SHA-256：`3AFD3980C42A39953835AE1CFC071197CD713587E5DB172B15CF89F13DADAE7C`

更新包：`infra/local-runtime/cloud-transfer/moonletter-oss-v20-update-20261004.tar`，仅包含 JAR、校验文件和脚本，不含 .env 或密钥。

更新包 SHA-256：`D1DAB6A7EB75A0A9F11DF66BDFCC8DCA455FDE87C1BB68BE8E37C7F73D3B3FED`

JAR SHA-256：`2208964DAA13CCB4749EA8A63B285F168922EF3143F1B3A16706E2D0DC8C9B4B`

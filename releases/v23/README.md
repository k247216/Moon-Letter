<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# V23：记录与回忆手书

版本：`0.1.0+recording-keepsakes-v23-20261004`，versionCode 23。

## 验收入口

- 时间轴：`记下今天` 每次新建，不覆盖旧草稿；`草稿箱` 查看、继续和确认删除未完成内容。
- 对方已发布且本机没打开的记录标记 `新回忆`，可 `继续看新回忆`。打开后消失，补写旧日期也能提示。不是对方可见的已读回执，换设备不共用阅读进度。
- 草稿自动保存正文、发生时间、原照片与语音/视频引用；按服务、情侣空间和账号隔离。删除草稿不删除原媒体。暂不分享本轮采用本机草稿，尚未提供云端私密记录开关。
- `我们 → 导出 → 离线回忆手书`：生成 ZIP，解压后打开 `index.html`；含手账字体、日期、正文、自然比例照片、回应以及原始语音/视频。保留 media、fonts 文件夹，可离线观看。音乐外链仍需联网。
- 导出仅包含本机可读取的已发布记录；缺失原媒体会提示失败，不生成伪完整手书。JSON/Markdown 仍作为备份选项。专门的 PDF 排版另行推进。
- 移除地图累计统计摘要，保留城市记录数量和日期等查找信息；自然比例照片拼图和相册封面未改。

直接覆盖安装 APK，不卸载、不清应用数据、不重新配对。APK 使用原测试签名；不是应用商店正式发布包。

## 服务器更新

后端包含此前的轻量图片缩略图支持；同时限制私密个人草稿被空修改、导出或已知媒体 ID 绕过读取。已发布记录、独立相册和已设置头像继续可双端访问。没有清理数据库、迁移或删除 OSS 原文件，也没有开放公网端口。

在**Windows PowerShell**执行（不要在服务器中使用 E 盘路径）：

```powershell
scp "E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-recording-v23-update-20261004.tar" root@192.0.2.10:/root/
```

在**服务器 SSH**执行：

```sh
tar -xf /root/moonletter-recording-v23-update-20261004.tar -C /opt/moonletter
sh /opt/moonletter/update-private-app-v23.sh
```

脚本验证 JAR 校验值、备份旧 JAR、仅重建 app 容器；启动失败会恢复旧 JAR。等待最后的 `V23 backend healthy`。服务器部署尚需用户执行以上步骤，本地验证通过不代表 ECS 已更新。

域名备案、公网 HTTPS 与跨省连接条件未因本轮改变；不要为了测试公开数据库或 OSS Bucket。

## 验证

- 新功能审查问题都有对应回归测试：账号范围草稿、异步发布时切换草稿与继续编辑、导出过滤音乐 URL 凭据、恢复待保存文件名。
- 隔离模拟器手机尺寸：真实草稿导航和离线手书字体/自然比例图片渲染，4 项通过。
- 隔离模拟器平板尺寸：记录、地图、相册墙，19 项通过。没有清除真实平板数据。
- 后端实际 PostgreSQL 隔离回归：100 项通过。
- 最终客户端单元/集成回归：app 80、sync 19、editor 20、timeline 23，共142项通过；使用最新 V23 后端 JAR。
- 截图 `book-v23-cover.png`、`book-v23-page.png` 来自实际 Android 离线手书页面，不是设计稿。

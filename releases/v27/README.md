<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# V27：公网双端测试

2026-10-09 更新：用户已确认备案完成。服务器实际输出确认 V27 JAR、Flyway13、健康 UP，以及当日自动备份和独立恢复指纹匹配。公网可信 HTTPS、HTTP308跳转、管理接口404及无凭据拒绝已实测通过。客户端覆盖包已构建，全部九模块262项测试通过；真实双端矩阵尚未执行。保留现有初版视觉和配对，不以重新初始化替代地址迁移。

APK：`MoonLetter-public-cloud-v27-20261009.apk`，versionCode27，约36.1MiB。

SHA256：`6e3917f9f41439ada44ede35fbc73c494c8028bc930d76f9004e8d238d0c55ee`。

与V25/V26实际APK核对签名相同：证书SHA256 `74001d60036e79f897f761523c7cdbd87580b0a04256125973ee4bf66fd8a015`。

## 两台现有设备的切换步骤

1. 使用本次交付的同一 V27 APK **直接覆盖安装**，不要卸载或清数据；版本名称应含 `public-cloud-v27-20261009`。继续原测试签名，不换备案用的正式签名。
2. 在每台设备打开「我们」→「云端连接与服务管理」→「保留数据，切换正式地址」→「验证并切换」。不输入初始化密钥、不生成新空间、不重新配对，不使用重新绑定表单代替迁移。
3. 确认当前地址变为 `https://api.example.invalid`；相册、旧记录、草稿及原身份仍在。若验证失败，保留原连接与数据，反馈页面提示，不自行卸载或重建空间。
4. 不接 USB、不启用隧道，A使用移动数据、B使用其他网络。先双向新文字/回应，再测试独立相册照片和真实录音，最后验证离线重连和重启；不用退出重进才能看到新增内容。记录实际等待时间。

这是两人公网测试包，不是正式签名发行包；受信任 HTTPS 不等于媒体双端验收已完成。

本轮重点：令牌不明文降级、不因读取失败丢身份；可核验找回原身份；敏感入口限流；原件鉴权/流式校验；封堵OSS覆盖票据；文字与媒体独立调度；列表预览及全屏原图；私有巡检/回退说明。

云端仍是私有OSS；`local://`票据仅提示通过鉴权API上传，后端校验后写OSS，不代表只保存本机。新视频32MiB、音频16MiB、图片64MiB；旧原件不删除。旧APK超限上传会失败，请不要认为已同步。

## 私有后端包（历史准备步骤，本服务器已更新，不要重复执行）

电脑PowerShell上传，不能在Linux里执行Windows路径：

```powershell
scp "E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-private-hardening-v27-20261005.tar" root@192.0.2.10:/root/
```

包的SHA256见同目录 `.tar.sha256`；上传后先在服务器核对。SSH中执行：

```sh
tar -xf /root/moonletter-private-hardening-v27-20261005.tar -C /opt/moonletter
sh /opt/moonletter/update-private-app-v27.sh
sh /opt/moonletter/check-private-release.sh
sh /opt/moonletter/check-private-health.sh
```

更新前检查现有数据库为V13，做一致快照备份并恢复到独立probe库核对；保留旧JAR，启动失败回退应用包。不覆盖.env/compose，不清库、不更换签名、不增加公网端口。候选无新增数据库迁移；回退不回滚数据库。

旧OSS签名PUT链接有15分钟有效期，更新后等待过期再验收不可覆盖约束。后端转发媒体占用ECS带宽/临时磁盘，不承诺直传性能；服务健康不代表OSS真实读写通过。

## 仍需真实证据

自动备份首跑及异机恢复、OSS原件独立保护、正式私钥异机恢复、原签名覆盖安装/地址迁移，以及备案后的HTTPS/不同网络双机矩阵。本地测试不能代替这些。巡检仅本地可见结果，不是手机推送告警。

具体记录见 `docs/testing/v27-private-hardening.md` 与 `docs/testing/public-cross-province-acceptance.md`。本服务器已由用户执行公网激活；不要把历史准备说明当成当前仍须等待备案。

<!-- Deployment/person/device identifiers in this public document are anonymized. -->
# V21 相册按需预览

## 用户确认的范围

照片墙与封面优先轻量预览，只请求可见单元；打开详情或保存才获取原图。不改自然比例拼图、裁切、边框、照片数据或配对。旧照片无需重新上传。

## 实现

- 鉴权接口 `/api/v1/media/{assetId}/thumbnail` 使用现有空间成员检查，兼容数据库中旧 object_key；原图 `/data` 保持字节不变。
- 服务端 ImageIO 先降采样再缩至最长边 600 像素，JPEG 质量 .72，不放大、不裁切；JPEG EXIF 方向转正。透明部分合成暖纸底色。服务端仅缓存最多 128 张/8MiB 预览，重启后按需重建；不移动 OSS 文件，不更改权限或开放端口。
- JPEG、PNG 等内置解码器支持的格式可以生成预览。HEIC/WebP 等服务端不支持的格式返回 415，照片墙提示“点开查看原图”，不偷偷下载原图。可在 Android 详情查看/保存原图，依赖设备格式支持。其他错误允许重试；旧后端返回 404 提示需更新。
- 相册元数据同步不再批量下载原图，不把未打开照片当作待同步失败。列表根据裁剪后的窗口坐标判断可见性，离屏请求取消；预览最多同时 2 个，原图独立 1 个通道。网络取消传递到 OkHttp，避免离屏图片继续占用槽位。
- 预览只进入独立的私有 cacheDir/album_previews（包含服务地址和空间摘要）。原图仍位于 filesDir/cloud_album_cache；原图/预览不混用。已有本机原图直接解码显示，不清缓存。新下载采用临时文件加重命名，下载失败不发布半文件。
- 保存到手机/系统文档前明确确保原图存在，继续复制原始字节。系统文档选择器重建时保留 assetId，避免云端原图尚未下载便丢失标识。

## 云端部署（待用户执行）

Windows PowerShell 上传：

```powershell
scp "E:\MoonLetter\Moon-Letter\infra\local-runtime\cloud-transfer\moonletter-previews-v21-update-20261004.tar" root@192.0.2.10:/root/
```

ECS 终端：

```sh
tar -xf /root/moonletter-previews-v21-update-20261004.tar -C /opt/moonletter
sh /opt/moonletter/update-private-app-v21.sh
```

脚本仅更新 app JAR，保留 .env、数据库、OSS 原图和旧 JAR。失败尝试回滚；无真实服务器回滚演练。仅更新 APK 而不更新 ECS，旧照片列表无法调用新预览接口。

## 验证记录

- 后端红灯：新接口 404；实现后完整后端 97 项通过。
- 客户端红灯：元数据同步提前下载伴侣原图。指定新后端 JAR 后确认此行为失败，再改为按需下载。
- 双端隔离测试：先仅收到元数据，预览单独缓存，详情原图字节一致，缓存丢失可再次显式下载；上传失败不阻止合并对方相册。
- 最终应用完整回归 70 项、网络 13 项通过；补验系统保存器重建后获取原图，以及慢响应下载可及时取消。
- 隔离模拟器真实 Android 相册 UI 与保存原图测试 26 项通过；现有自然比例拼图保持不变。
- APK 签名 v2/v3 验证通过。平板覆盖安装请求已发出，但系统安装确认尚未完成，实际仍为 V20；手机当前未连接。未卸载、清数据或改配对。
- 更新脚本通过离线隔离 Linux 容器 sh -n。ECS 尚未部署 V21，真实照片下载耗时尚待部署验收；不能把本地回归当成跨网性能实测。

APK（稳定交付目录，避免被后续 Gradle 构建清理）：`releases/v21/MoonLetter-album-previews-v21-20261004.apk`

APK SHA-256：`CDFC494B86D9E686CBE29774FD49AE9110ED8D219AD8360F48F25FC221384C44`

后端更新包 SHA-256：`3C09CFEE1F80946775EB017EA0AF864D2F8A6002A06E345AE42DE131B869590F`

JAR SHA-256：`FADEBC757294A32D2E1D73BA4A64FA4F414FA947F5368635C0C7CA7221F88227`

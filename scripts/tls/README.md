# 各区域免费TLS证书迁移

适用Java直接监听443、Alibaba Cloud Linux/systemd节点。域名、证书目录和服务名均由每台服务器配置；不按地区写死逻辑。新加坡示例为globalkejiapi.asia，美国替换为globalkejiapi.top，其他节点同理。

## 交付边界

这些是待部署工具，不会自动修改线上服务器。首次切换和每次自动续期部署都会重启Java，存在短暂中断。多个节点逐台迁移。不要直接部署包含其他未发布业务变更的本地整包。

保留现有classpath:JKS配置兼容未迁移节点。本次未删除旧密钥库：所有节点迁移后，再单独清理构建资源和历史密钥，避免旧节点启动失败。不要将私钥、密钥库、密码或真实app.env提交Git。

## 迁移前置检查

1. 保存线上原JAR、启动脚本、工作目录和完整环境配置作为回滚备份。秘密仅在服务器保存，目录700、文件600，不能发到聊天或日志。
2. 保留所有业务环境变量，不能只保存SSL变量。systemd EnvironmentFile不是shell脚本，不能直接粘贴export命令或未经转义的/proc环境输出。审核引号、换行和特殊字符，并保留Java相关选项及外部配置。
3. 确认新包范围、Java版本、443入口和内存。先隔离验证，再约维护窗口。无需改App包。
4. Certbot standalone要求各域名A/AAAA解析正确，公网80可达且空闲。只添加真正使用且能验证的域名。若业务需要www，须纳入证书，不能用仅根域名证书替换www服务。

## 每台节点配置

在服务器创建root所有的/etc/quittr和/etc/quittr/tls目录（700）。

- 将deploy-certificate.sh安装为/usr/local/sbin/quittr-deploy-certificate（root:700）。
- 将node.conf.example复制为/etc/quittr/tls/<域名>.conf（root:600），填写实际LINEAGE、KEYSTORE、PASSWORD_FILE、KEY_ALIAS和SERVICE。
- 用openssl rand -hex 32在服务器生成独立密码，写入PASSWORD_FILE（root:600）。不要打印或传给命令行参数。
- 在/etc/quittr/app.env（root:600）保留全部原业务配置，只替换下列SSL项。密码与PASSWORD_FILE一致，在服务器受保护编辑；不能使用占位符作为实际密码。

```ini
SERVER_SSL_ENABLED=true
SERVER_PORT=443
SSL_KEY_STORE=file:/etc/quittr/tls/globalkejiapi.asia.p12
SSL_KEY_STORE_TYPE=PKCS12
SSL_KEY_ALIAS=server
SSL_KEY_STORE_PASSWORD=<在服务器填写对应密码>
```

如果存在SERVER_SSL_KEY_PASSWORD等单独私钥密码配置，须同步更新，不能继续使用旧密码。

## 首次准备与切换

获取生产证书后执行：

```bash
/usr/local/sbin/quittr-deploy-certificate --prepare globalkejiapi.asia
```

prepare检查域名、有效期、转换并验证PKCS12，仅生成外部文件，不重启、不覆盖已存在密钥库，不改JAR。

将quittr.service.example按实际路径审查后安装为/etc/systemd/system/quittr.service。暂保持现有root运行方式，后续再单独迁移专用用户。

维护窗口内：确保原JAR与原环境可回滚，停止准确识别的原Java进程，等待443释放，再换上已验证新JAR并执行systemctl daemon-reload、systemctl start quittr.service。不要用fuser -k批量杀进程。失败则停止新服务，恢复原JAR和原环境启动。成功后才enable服务。

验收systemd状态、应用日志、登录和只读业务接口，以及带SNI的公网证书域名/到期日/信任链。不能仅凭active就认定成功。检查其他服务域名和8080依赖。

## 自动续期部署

首次切换验收后，才安装certbot-deploy-hook.sh到/etc/letsencrypt/renewal-hooks/deploy/quittr（root:700），只处理存在同名配置的证书。

1. 检查已有Certbot timer/cron已启用且有下次执行时间，不重复创建调度任务。
2. 执行certbot renew --cert-name globalkejiapi.asia --dry-run。旧版dry-run通常不执行deploy hook，这不是完整部署测试。
3. 维护窗口可用已有生产证书执行--deploy验证转换、重启。不要反复真实续签或部署测试CA证书。
4. 确认脚本退出码0，再检查公网。脚本保留.previous备份，重启后检查本机443可信证书及指纹；失败则恢复旧密钥库并尝试重启，返回非零。回滚也可能失败，须人工检查。
5. 接入现有告警平台：公网证书剩余时间（14/7/3天）、HTTPS接口可用性、Certbot任务失败和journal中的quittr-tls错误。脚本只写日志，不自动发邮件/短信；告警未接通不能宣称闭环完成。

旧版Certbot应另行安排升级验证，不替换系统Python。续期不会延长域名注册或服务器订阅。

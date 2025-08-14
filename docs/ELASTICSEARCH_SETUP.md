# Elasticsearch 配置指南

## 概述

本项目已集成阿里云Elasticsearch服务，提供基础的搜索功能支持。

## 配置说明

### 1. 环境变量配置

在 `application.yml` 中配置以下参数：

```yaml
elasticsearch:
  aliyun:
    region-id: ${ALIYUN_ES_REGION_ID:cn-hangzhou}
    access-key-id: ${ALIYUN_ES_ACCESS_KEY_ID:your-access-key-id}
    access-key-secret: ${ALIYUN_ES_ACCESS_KEY_SECRET:your-access-key-secret}
    username: ${ALIYUN_ES_USERNAME:quittr-m73}
    password: ${ALIYUN_ES_PASSWORD:Zzjjzbg1314!}
    instance-id: ${ALIYUN_ES_INSTANCE_ID:es-cn-xxx}
    endpoint: ${ALIYUN_ES_ENDPOINT:http://quittr-m73.public.cn-hangzhou.es-serverless.aliyuncs.com}
    protocol: http
    port: 9200
    connection-timeout: 5000
    socket-timeout: 10000
```

### 2. 配置文件

在 `application.yml` 中已配置以下参数：

### 3. 依赖说明

项目已添加以下Elasticsearch相关依赖：

- `spring-boot-starter-data-elasticsearch`: Spring Data Elasticsearch支持
- `elasticsearch-java`: 官方Elasticsearch Java客户端
- `elasticsearch-rest-high-level-client`: REST高级客户端

## 功能特性

### 1. 基础配置

- 自动配置Elasticsearch客户端连接
- 支持用户名密码认证
- 可配置连接超时和套接字超时
- 支持HTTP协议连接

### 2. 健康检查

提供以下API端点进行连接测试：

- `GET /api/elasticsearch/health`: 检查连接状态
- `GET /api/elasticsearch/info`: 获取基本信息

### 3. 扩展性

- 预留了实体类和仓库接口的扩展空间
- 可根据业务需求添加具体的搜索功能
- 支持自定义索引和映射配置

## 使用说明

### 1. 启动应用

直接启动Spring Boot应用即可，配置已在 `application.yml` 中设置。

### 2. 测试连接

访问 `http://localhost:8080/api/elasticsearch/health` 检查连接状态。

### 3. 添加业务功能

如需添加具体的搜索功能，可以：

1. 创建实体类并添加 `@Document` 注解
2. 创建对应的Repository接口
3. 实现具体的搜索服务

## 注意事项

1. **安全性**: 请妥善保管AccessKey和密码信息
2. **网络**: 确保应用服务器能够访问阿里云Elasticsearch服务
3. **版本兼容**: 当前配置适用于Elasticsearch 7.x版本
4. **性能**: 生产环境建议调整连接池配置参数

## 故障排除

### 常见问题

1. **连接超时**: 检查网络连接和防火墙设置
2. **认证失败**: 验证用户名密码和AccessKey配置（在application.yml中修改）
3. **索引不存在**: 首次使用需要创建索引

### 日志查看

查看应用日志中的Elasticsearch相关信息：

```bash
tail -f logs/application.log | grep elasticsearch
```

## 后续扩展

当前配置为基础集成，可根据业务需求进一步扩展：

- 添加具体的业务实体索引
- 实现复杂的搜索查询
- 配置索引映射和分词器
- 添加数据同步机制

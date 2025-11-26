# Cloudflare D1 数据库集成文档

## 概述

本项目已完全集成 Cloudflare D1 数据库支持。D1 是基于 SQLite 的云数据库服务，通过 HTTP API 访问。所有数据库操作都通过 D1 API 完成。

## 配置方式

### 必需配置

要使用 Cloudflare D1，必须设置以下环境变量：

```bash
export CLOUDFLARE_D1_ENABLED=true
export CLOUDFLARE_D1_API_TOKEN=your_api_token
export CLOUDFLARE_D1_ACCOUNT_ID=your_account_id
export CLOUDFLARE_D1_DATABASE_ID=your_database_id
```

或在 `application.yml` 中配置：

```yaml
cloudflare:
  d1:
    api-token: your_api_token
    account-id: your_account_id
    database-id: your_database_id
```

## 获取 Cloudflare D1 凭证

1. **API Token**：
   - 登录 Cloudflare Dashboard
   - 进入 "My Profile" > "API Tokens"
   - 创建新的 API Token，需要以下权限：
     - `Account.Cloudflare D1:Edit`
     - `Account.Cloudflare D1:Read`

2. **Account ID**：
   - 在 Cloudflare Dashboard 右侧边栏可以看到 Account ID

3. **Database ID**：
   - 使用 Wrangler CLI 创建 D1 数据库：
     ```bash
     npx wrangler d1 create cursor-quitter-db
     ```
   - 创建后会返回 Database ID

## 使用 D1 客户端

在代码中可以直接注入 `CloudflareD1Client` 来执行 SQL：

```java
@Autowired
private CloudflareD1Client d1Client;

// 执行查询
D1QueryResult result = d1Client.query("SELECT * FROM users WHERE id = ?", userId);
List<Map<String, Object>> rows = result.getRows();

// 执行更新
D1ExecuteResult executeResult = d1Client.execute(
    "UPDATE users SET nickname = ? WHERE id = ?", 
    newNickname, userId
);
int changes = executeResult.getChanges();
```

## JPA/Hibernate 集成

项目已实现完整的 JDBC 适配层，将 JPA/Hibernate 操作转换为 D1 API 调用：

- **D1 DataSource**：实现了 `javax.sql.DataSource` 接口
- **D1 Connection**：实现了 `java.sql.Connection` 接口
- **D1 Statement/PreparedStatement**：实现了 SQL 执行接口
- **D1 ResultSet**：实现了结果集接口
- **D1 DatabaseMetaData**：实现了数据库元数据接口

所有 JPA/Hibernate 操作都会自动通过 D1 HTTP API 执行，无需修改现有的 Repository 和 Entity 代码。

## 迁移数据到 D1

1. **从 PostgreSQL/MySQL 导出数据**：
   ```bash
   # PostgreSQL
   pg_dump -d your_database > dump.sql
   
   # MySQL
   mysqldump -u user -p your_database > dump.sql
   ```

2. **转换 SQL 语法**（D1 基于 SQLite，需要调整语法）：
   - 移除 PostgreSQL/MySQL 特定语法
   - 调整数据类型（UUID -> TEXT, JSONB -> TEXT 等）
   - 移除 schema 引用

3. **导入到 D1**：
   ```bash
   npx wrangler d1 execute cursor-quitter-db --file=./dump.sql
   ```

## 注意事项

1. **SQL 兼容性**：D1 基于 SQLite，确保所有 SQL 语句兼容 SQLite 语法
2. **事务支持**：D1 支持事务，但需要通过批量 API 执行
3. **性能考虑**：D1 API 调用有延迟，建议批量操作使用 `batch()` 方法
4. **错误处理**：D1 API 调用失败时会抛出异常，需要适当的错误处理

## 参考文档

- [Cloudflare D1 官方文档](https://developers.cloudflare.com/d1/)
- [D1 HTTP API 文档](https://developers.cloudflare.com/api/operations/cloudflare-d1-query-d1-database)


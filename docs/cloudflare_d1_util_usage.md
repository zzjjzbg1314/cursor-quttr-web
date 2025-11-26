# CloudflareD1Util 工具类使用指南

## 概述

`CloudflareD1Util` 是一个便捷的工具类，封装了常用的 D1 数据库操作，简化了与 Cloudflare D1 的交互。

## 基本用法

### 1. 注入工具类

```java
@Service
public class UserService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    // ...
}
```

### 2. 查询操作

#### 查询单条记录
```java
// 查询单行
Map<String, Object> user = d1Util.queryOne(
    "SELECT * FROM users WHERE id = ?", 
    userId
);

// 查询单个值
String nickname = d1Util.queryString(
    "SELECT nickname FROM users WHERE id = ?", 
    userId
);

// 查询整数值
int count = d1Util.queryInt(
    "SELECT COUNT(*) FROM users"
);

// 查询 UUID
UUID userId = d1Util.queryUUID(
    "SELECT id FROM users WHERE phone_number = ?", 
    phoneNumber
);
```

#### 查询多条记录
```java
// 查询所有记录
List<Map<String, Object>> users = d1Util.queryList(
    "SELECT * FROM users ORDER BY created_at DESC"
);

// 分页查询
List<Map<String, Object>> users = d1Util.queryPage(
    "SELECT * FROM users WHERE is_deleted = ? ORDER BY created_at DESC",
    1,  // 页码（从1开始）
    10, // 每页大小
    false
);
```

### 3. 更新操作

#### 插入记录
```java
// 方式1：使用 Map
Map<String, Object> data = new HashMap<>();
data.put("nickname", "新用户");
data.put("phone_number", "13800138000");
data.put("registration_time", OffsetDateTime.now());
long newId = d1Util.insert("users", data);

// 方式2：使用 SQL
long newId = d1Util.executeInsert(
    "INSERT INTO users (nickname, phone_number, registration_time) VALUES (?, ?, ?)",
    "新用户", "13800138000", OffsetDateTime.now()
);
```

#### 更新记录
```java
// 方式1：使用 Map 和 WHERE 条件
Map<String, Object> data = new HashMap<>();
data.put("nickname", "更新后的昵称");
data.put("updated_at", OffsetDateTime.now());
int affected = d1Util.update(
    "users", 
    data, 
    "id = ?", 
    userId
);

// 方式2：根据 ID 更新
Map<String, Object> data = new HashMap<>();
data.put("nickname", "更新后的昵称");
int affected = d1Util.updateById("users", data, "id", userId);

// 方式3：使用 SQL
int affected = d1Util.execute(
    "UPDATE users SET nickname = ?, updated_at = ? WHERE id = ?",
    "更新后的昵称", OffsetDateTime.now(), userId
);
```

#### 删除记录
```java
// 方式1：根据条件删除
int affected = d1Util.delete("users", "id = ?", userId);

// 方式2：根据 ID 删除
int affected = d1Util.deleteById("users", "id", userId);

// 方式3：使用 SQL
int affected = d1Util.execute("DELETE FROM users WHERE id = ?", userId);
```

### 4. 便捷方法

#### 检查表是否存在
```java
boolean exists = d1Util.tableExists("users");
```

#### 获取表的行数
```java
long count = d1Util.countTable("users");
```

#### 检查记录是否存在
```java
boolean exists = d1Util.exists("users", "phone_number = ?", phoneNumber);
```

#### 根据 ID 查询
```java
Map<String, Object> user = d1Util.findById("users", "id", userId);
```

### 5. 批量操作

#### 批量执行 SQL
```java
List<String> sqls = Arrays.asList(
    "INSERT INTO users (nickname) VALUES ('用户1')",
    "INSERT INTO users (nickname) VALUES ('用户2')",
    "UPDATE users SET updated_at = ? WHERE id = ?"
);
CloudflareD1Client.D1BatchResult result = d1Util.batchExecute(sqls);
```

#### 执行事务
```java
List<String> sqls = Arrays.asList(
    "UPDATE account SET balance = balance - 100 WHERE id = ?",
    "UPDATE account SET balance = balance + 100 WHERE id = ?"
);
boolean success = d1Util.executeTransaction(sqls);
```

## 完整示例

```java
@Service
public class UserService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 创建用户
     */
    public UUID createUser(String nickname, String phoneNumber) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", UUID.randomUUID());
        data.put("nickname", nickname);
        data.put("phone_number", phoneNumber);
        data.put("registration_time", OffsetDateTime.now());
        data.put("challenge_reset_time", OffsetDateTime.now());
        data.put("best_record", 1);
        data.put("created_at", OffsetDateTime.now());
        data.put("updated_at", OffsetDateTime.now());
        
        d1Util.insert("users", data);
        return (UUID) data.get("id");
    }
    
    /**
     * 根据手机号查找用户
     */
    public Map<String, Object> findByPhoneNumber(String phoneNumber) {
        return d1Util.queryOne(
            "SELECT * FROM users WHERE phone_number = ? LIMIT 1",
            phoneNumber
        );
    }
    
    /**
     * 更新用户昵称
     */
    public boolean updateNickname(UUID userId, String nickname) {
        Map<String, Object> data = new HashMap<>();
        data.put("nickname", nickname);
        data.put("updated_at", OffsetDateTime.now());
        int affected = d1Util.updateById("users", data, "id", userId);
        return affected > 0;
    }
    
    /**
     * 获取用户列表（分页）
     */
    public List<Map<String, Object>> getUserList(int page, int pageSize) {
        return d1Util.queryPage(
            "SELECT * FROM users WHERE is_deleted = ? ORDER BY created_at DESC",
            page,
            pageSize,
            false
        );
    }
    
    /**
     * 统计用户总数
     */
    public long getUserCount() {
        return d1Util.countTable("users");
    }
}
```

## 注意事项

1. **SQL 注入防护**：工具类使用参数化查询，但直接拼接 SQL 的方法（如 `countTable`）需要注意安全性
2. **事务支持**：D1 的事务通过批量操作实现，`executeTransaction` 方法会执行批量 SQL
3. **错误处理**：工具类会记录错误日志，但不会抛出异常，调用方需要检查返回值
4. **性能考虑**：对于大量数据的操作，建议使用批量方法而不是循环调用单个方法


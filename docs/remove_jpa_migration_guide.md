# 移除 JPA 迁移指南

## 概述

项目已禁用 JPA 自动配置，现在只使用 Cloudflare D1 和 `CloudflareD1Util` 工具类进行数据库操作。

## 已完成的更改

1. ✅ 禁用了 JPA 自动配置（`DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `JpaRepositoriesAutoConfiguration`）
2. ✅ 移除了 `application.yml` 中的 JPA 配置
3. ✅ 删除了 `DatabaseConfig.java`（JPA 配置类）
4. ✅ 创建了 `CloudflareD1Util` 工具类

## 需要重构的部分

### 1. Service 层重构

所有 Service 实现类需要从使用 JPA Repository 改为使用 `CloudflareD1Util`。

#### 示例：UserServiceImpl

**之前（使用 JPA Repository）：**
```java
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }
    
    @Override
    public User save(User user) {
        return userRepository.save(user);
    }
}
```

**之后（使用 CloudflareD1Util）：**
```java
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<User> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("users", "id", id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(mapToUser(row));
    }
    
    @Override
    public User save(User user) {
        if (user.getId() == null) {
            // 插入新记录
            Map<String, Object> data = userToMap(user);
            long newId = d1Util.insert("users", data);
            user.setId(UUID.fromString(String.valueOf(newId)));
            return user;
        } else {
            // 更新记录
            Map<String, Object> data = userToMap(user);
            d1Util.updateById("users", data, "id", user.getId());
            return user;
        }
    }
    
    private User mapToUser(Map<String, Object> row) {
        User user = new User();
        user.setId(UUID.fromString(row.get("id").toString()));
        user.setNickname((String) row.get("nickname"));
        // ... 其他字段映射
        return user;
    }
    
    private Map<String, Object> userToMap(User user) {
        Map<String, Object> data = new HashMap<>();
        if (user.getId() != null) {
            data.put("id", user.getId().toString());
        }
        data.put("nickname", user.getNickname());
        // ... 其他字段
        return data;
    }
}
```

### 2. 需要重构的 Service 类

以下 Service 实现类需要重构：

- `UserServiceImpl`
- `ArticleServiceImpl`
- `BookService`
- `BreatheService`
- `MusicService`
- `VideoService`
- `PostServiceImpl`
- `CommentServiceImpl`
- `RecoverJourneyServiceImpl`
- `ChangeReasonServiceImpl`
- `PostLikeServiceImpl`
- `PostReportServiceImpl`
- `UserReportServiceImpl`
- `UserIdentityServiceImpl`
- `VideoScenarioServiceImpl`
- `MeditateVideoService`
- `ArticleSectionService`

### 3. Repository 接口

所有 Repository 接口可以保留作为参考，但不会被使用。建议：
- 保留 Repository 接口作为文档参考
- 或者删除 Repository 接口（如果确定不再需要）

### 4. Entity 类

Entity 类可以保留作为数据模型，但需要：
- 移除 JPA 注解（`@Entity`, `@Table`, `@Id`, `@Column` 等）
- 保留为普通 POJO 类
- 用于在 Service 层和 Controller 层之间传递数据

### 5. 事务管理

由于移除了 JPA，`@Transactional` 注解将不再生效。如果需要事务支持：
- 使用 `CloudflareD1Util.executeTransaction()` 方法
- 或者实现自定义的事务管理器

## 迁移步骤

1. **选择一个 Service 类开始重构**
   - 建议从简单的 Service 开始（如 `BookService`）
   - 重构完成后测试功能是否正常

2. **创建 Entity 映射方法**
   - `mapToEntity()`: 将 `Map<String, Object>` 转换为 Entity
   - `entityToMap()`: 将 Entity 转换为 `Map<String, Object>`

3. **替换 Repository 调用**
   - `repository.findById()` → `d1Util.findById()`
   - `repository.save()` → `d1Util.insert()` 或 `d1Util.updateById()`
   - `repository.deleteById()` → `d1Util.deleteById()`
   - `repository.findAll()` → `d1Util.queryList()`

4. **测试**
   - 确保所有功能正常工作
   - 检查数据映射是否正确

5. **逐步迁移其他 Service**
   - 一个接一个地迁移
   - 每次迁移后测试

## 注意事项

1. **UUID 处理**：D1 中 UUID 存储为字符串，需要转换
2. **时间类型**：`OffsetDateTime` 需要转换为字符串存储
3. **空值处理**：注意处理数据库中的 NULL 值
4. **分页查询**：使用 `d1Util.queryPage()` 方法
5. **复杂查询**：使用 `d1Util.queryList()` 或 `d1Util.queryOne()` 执行自定义 SQL

## 示例：完整的 Service 重构

```java
@Service
public class BookServiceImpl implements BookService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<Book> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("book", "id", id);
        return row != null ? Optional.of(mapToBook(row)) : Optional.empty();
    }
    
    @Override
    public Book save(Book book) {
        Map<String, Object> data = bookToMap(book);
        if (book.getId() == null) {
            long newId = d1Util.insert("book", data);
            book.setId(UUID.fromString(String.valueOf(newId)));
        } else {
            d1Util.updateById("book", data, "id", book.getId());
        }
        return book;
    }
    
    @Override
    public List<Book> searchByTitle(String title) {
        String sql = "SELECT * FROM book WHERE title LIKE ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream()
            .map(this::mapToBook)
            .collect(Collectors.toList());
    }
    
    private Book mapToBook(Map<String, Object> row) {
        Book book = new Book();
        book.setId(UUID.fromString(row.get("id").toString()));
        book.setTitle((String) row.get("title"));
        book.setPostUrl((String) row.get("post_url"));
        book.setPdfUrl((String) row.get("pdf_url"));
        if (row.get("create_at") != null) {
            book.setCreateAt(OffsetDateTime.parse(row.get("create_at").toString()));
        }
        return book;
    }
    
    private Map<String, Object> bookToMap(Book book) {
        Map<String, Object> data = new HashMap<>();
        if (book.getId() != null) {
            data.put("id", book.getId().toString());
        }
        data.put("title", book.getTitle());
        data.put("post_url", book.getPostUrl());
        data.put("pdf_url", book.getPdfUrl());
        if (book.getCreateAt() != null) {
            data.put("create_at", book.getCreateAt().toString());
        }
        return data;
    }
}
```

## 总结

移除 JPA 后，项目将：
- ✅ 更轻量级（不需要 Hibernate）
- ✅ 更直接（直接使用 SQL）
- ✅ 更灵活（可以执行任意 SQL）
- ⚠️ 需要手动编写数据映射代码
- ⚠️ 需要手动管理事务

建议逐步迁移，确保每个 Service 重构后都经过充分测试。


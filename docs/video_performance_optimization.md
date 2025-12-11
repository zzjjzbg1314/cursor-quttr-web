# getAllVideos接口性能优化分析

## 问题分析

### 当前性能
- **getAllVideos接口耗时：1026ms**
- **默认每页大小：100条**

### 表结构问题

```sql
CREATE TABLE video (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    title TEXT NOT NULL,
    playurl TEXT NOT NULL,
    posturl TEXT NOT NULL,
    create_at TEXT DEFAULT (datetime('now')),  -- ⚠️ 问题1：TEXT类型
    update_at TEXT DEFAULT (datetime('now'))   -- ⚠️ 问题2：TEXT类型
)
```

### 发现的问题

#### 1. **2次数据库查询**

```java
// 第一次查询：获取总数（全表扫描）
long totalElements = videoService.count();

// 第二次查询：获取分页数据（需要排序）
List<Video> videos = videoService.getAllVideos(page, size);
```

**查询语句：**
```sql
-- 查询1：COUNT查询（全表扫描）
SELECT COUNT(*) FROM video;

-- 查询2：分页查询（需要排序）
SELECT * FROM video ORDER BY create_at ASC LIMIT 100 OFFSET 0;
```

**影响：**
- ❌ 2次网络往返
- ❌ 2次数据库查询
- ❌ COUNT查询需要全表扫描

#### 2. **没有索引支持排序**

```sql
SELECT * FROM video ORDER BY create_at ASC LIMIT 100 OFFSET 0;
```

**问题：**
- ❌ 没有`create_at`索引
- ❌ 需要对所有数据进行排序
- ❌ 排序操作在内存中进行，效率低

#### 3. **TEXT类型存储时间**

```sql
create_at TEXT DEFAULT (datetime('now'))
```

**问题：**
- ⚠️ TEXT类型排序需要字符串比较
- ⚠️ 字符串排序效率低于DATETIME类型
- ⚠️ 如果时间格式不一致，排序可能不准确

**示例：**
```sql
-- TEXT类型排序（字符串比较）
'2025-12-11 11:34:33' < '2025-12-11 11:34:34'  -- ✅ 正确
'2025-12-11 11:34:33' < '2025-12-11 11:34:3'   -- ❌ 可能错误（字符串比较）

-- DATETIME类型排序（数值比较）
2025-12-11 11:34:33 < 2025-12-11 11:34:34      -- ✅ 正确且高效
```

#### 4. **默认分页大小过大**

```java
@RequestParam(defaultValue = "100") int size
```

**问题：**
- ⚠️ 默认返回100条数据
- ⚠️ 如果数据量大，排序和传输都会变慢

## 性能瓶颈分析

### 瓶颈1：COUNT查询（全表扫描）

```sql
SELECT COUNT(*) FROM video;
```

- 需要扫描所有行
- 没有索引优化
- 如果表有数千条记录，会很慢

### 瓶颈2：排序查询（无索引）

```sql
SELECT * FROM video ORDER BY create_at ASC LIMIT 100 OFFSET 0;
```

- 需要扫描所有行
- 在内存中排序
- 没有索引支持

### 瓶颈3：TEXT类型排序效率低

- TEXT类型排序需要字符串比较
- 效率低于DATETIME类型

## 优化方案

### 方案1：添加索引（立即执行，最简单有效）

```sql
-- 为create_at字段创建索引，优化排序查询
CREATE INDEX IF NOT EXISTS idx_video_create_at 
ON video(create_at ASC);
```

**预期效果：**
- 排序查询：从数百毫秒降低到几十毫秒
- COUNT查询：仍然需要全表扫描（需要方案2优化）

### 方案2：使用窗口函数优化（推荐）

参考`getAllPostsWithUpvotesAndCount`的优化方式，使用窗口函数一次性获取数据和总数：

```java
public VideoPageResult getAllVideosWithCount(int page, int size) {
    String sql = String.format(
        "SELECT *, COUNT(*) OVER() as total_count " +
        "FROM video " +
        "ORDER BY create_at ASC " +
        "LIMIT ? OFFSET ?",
        size, page * size
    );
    
    List<Map<String, Object>> rows = d1Util.queryList(sql, size, page * size);
    
    long totalElements = 0;
    List<Video> videos = new ArrayList<>();
    
    for (Map<String, Object> row : rows) {
        if (totalElements == 0 && row.get("total_count") != null) {
            totalElements = ((Number) row.get("total_count")).longValue();
        }
        Map<String, Object> videoRow = new HashMap<>(row);
        videoRow.remove("total_count");
        videos.add(mapToVideo(videoRow));
    }
    
    return new VideoPageResult(videos, totalElements);
}
```

**预期效果：**
- 从2次查询减少到1次查询
- 从1026ms降低到300-500ms

### 方案3：优化表结构（长期优化）

**建议的表结构：**

```sql
CREATE TABLE video (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    title TEXT NOT NULL,
    playurl TEXT NOT NULL,
    posturl TEXT NOT NULL,
    create_at DATETIME DEFAULT (datetime('now')),  -- ✅ 改为DATETIME类型
    update_at DATETIME DEFAULT (datetime('now'))   -- ✅ 改为DATETIME类型
);

-- 创建索引
CREATE INDEX idx_video_create_at ON video(create_at ASC);
CREATE INDEX idx_video_update_at ON video(update_at ASC);
```

**迁移步骤：**

```sql
-- 1. 添加新列（DATETIME类型）
ALTER TABLE video ADD COLUMN create_at_new DATETIME;
ALTER TABLE video ADD COLUMN update_at_new DATETIME;

-- 2. 迁移数据（将TEXT转换为DATETIME）
UPDATE video 
SET create_at_new = datetime(create_at),
    update_at_new = datetime(update_at);

-- 3. 删除旧列
ALTER TABLE video DROP COLUMN create_at;
ALTER TABLE video DROP COLUMN update_at;

-- 4. 重命名新列
ALTER TABLE video RENAME COLUMN create_at_new TO create_at;
ALTER TABLE video RENAME COLUMN update_at_new TO update_at;

-- 5. 创建索引
CREATE INDEX idx_video_create_at ON video(create_at ASC);
CREATE INDEX idx_video_update_at ON video(update_at ASC);
```

**预期效果：**
- 排序效率提升约20-30%
- 数据一致性更好

### 方案4：减少默认分页大小

```java
@RequestParam(defaultValue = "20") int size  // 从100改为20
```

**预期效果：**
- 减少数据传输量
- 提升响应速度

## 推荐执行顺序

### 高优先级（立即执行）

1. **创建索引**
   ```sql
   CREATE INDEX IF NOT EXISTS idx_video_create_at ON video(create_at ASC);
   ```
   - 预期：排序查询从数百毫秒降低到几十毫秒
   - 风险：低

2. **使用窗口函数优化**
   - 参考`getAllPostsWithUpvotesAndCount`的实现
   - 预期：从1026ms降低到300-500ms
   - 风险：低

### 中优先级（建议执行）

3. **减少默认分页大小**
   - 从100改为20
   - 预期：减少数据传输量
   - 风险：低

### 低优先级（长期优化）

4. **优化表结构**
   - 将TEXT改为DATETIME类型
   - 预期：排序效率提升20-30%
   - 风险：中（需要数据迁移）

## 对比分析

| 方案 | 实施难度 | 性能提升 | 推荐度 |
|------|---------|---------|--------|
| 添加索引 | ⭐ 低 | ⭐⭐⭐ 高 | ⭐⭐⭐⭐⭐ 强烈推荐 |
| 窗口函数优化 | ⭐⭐ 中 | ⭐⭐⭐⭐⭐ 极高 | ⭐⭐⭐⭐⭐ 强烈推荐 |
| 减少分页大小 | ⭐ 低 | ⭐⭐ 中 | ⭐⭐⭐ 推荐 |
| 优化表结构 | ⭐⭐⭐ 高 | ⭐⭐ 中 | ⭐⭐ 可选 |

## 总结

### 关键问题

1. ❌ **2次数据库查询**：COUNT查询 + 分页查询
2. ❌ **没有索引支持排序**：`ORDER BY create_at`需要全表扫描+排序
3. ⚠️ **TEXT类型存储时间**：排序效率低于DATETIME类型
4. ⚠️ **默认分页大小过大**：100条数据

### 立即行动

1. **创建索引**：`CREATE INDEX idx_video_create_at ON video(create_at ASC);`
2. **使用窗口函数**：参考posts接口的优化方式，减少查询次数
3. **测试性能**：验证优化效果

### 预期效果

- **创建索引后**：排序查询从数百毫秒降低到几十毫秒
- **窗口函数优化后**：总体响应时间从1026ms降低到300-500ms（约50-70%提升）


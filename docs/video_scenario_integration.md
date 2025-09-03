# 视频场景功能集成说明

## 概述

本次集成添加了完整的视频场景管理功能，包括数据库表、实体类、数据访问层、业务逻辑层和REST API接口。

## 数据库表结构

### video_scenario 表

```sql
CREATE TABLE "public"."video_scenario" (
    "videoId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "type" varchar,
    "title" varchar,
    "subtitle" varchar,
    "image" varchar,
    "audiourl" varchar,
    "videourl" varchar,
    "color" varchar,
    "quotes" varchar,
    "author" varchar,
    "createAt" timestamptz DEFAULT now(),
    "updateAt" timestamptz DEFAULT now()
);
```

### 字段说明

- `videoId`: 视频唯一标识，UUID类型，自动生成
- `type`: 视频类型，如冥想、激励、助眠等
- `title`: 视频主标题
- `subtitle`: 视频副标题
- `image`: 封面图片URL
- `audiourl`: 音频资源链接
- `videourl`: 视频资源链接
- `color`: 背景颜色，用于界面展示
- `quotes`: 励志语录文本
- `author`: 语录作者
- `createAt`: 创建时间（带时区）
- `updateAt`: 更新时间（带时区）

## 新增文件列表

### 实体类
- `src/main/java/com/example/cursorquitterweb/entity/VideoScenario.java`

### 数据访问层
- `src/main/java/com/example/cursorquitterweb/repository/VideoScenarioRepository.java`

### 业务逻辑层
- `src/main/java/com/example/cursorquitterweb/service/VideoScenarioService.java`
- `src/main/java/com/example/cursorquitterweb/service/impl/VideoScenarioServiceImpl.java`

### DTO类
- `src/main/java/com/example/cursorquitterweb/dto/VideoScenarioDto.java`
- `src/main/java/com/example/cursorquitterweb/dto/CreateVideoScenarioRequest.java`
- `src/main/java/com/example/cursorquitterweb/dto/UpdateVideoScenarioRequest.java`

### 控制器
- `src/main/java/com/example/cursorquitterweb/controller/VideoScenarioController.java`

### 数据库迁移
- `src/main/resources/db/migration/V1__Create_video_scenario_table.sql`

### 测试类
- `src/test/java/com/example/cursorquitterweb/VideoScenarioIntegrationTest.java`

## API接口说明

### 基础CRUD操作

#### 1. 创建视频场景
```
POST /api/video-scenarios
Content-Type: application/json

{
    "type": "冥想",
    "title": "晨间冥想",
    "subtitle": "开始美好的一天",
    "image": "https://example.com/image.jpg",
    "audiourl": "https://example.com/audio.mp3",
    "videourl": "https://example.com/video.mp4",
    "color": "#4A90E2",
    "quotes": "每一天都是新的开始",
    "author": "禅修大师"
}
```

#### 2. 获取视频场景
```
GET /api/video-scenarios/{videoId}
```

#### 3. 更新视频场景
```
PUT /api/video-scenarios/{videoId}
Content-Type: application/json

{
    "type": "冥想",
    "title": "更新后的标题",
    "subtitle": "更新后的副标题",
    "image": "https://example.com/new-image.jpg",
    "audiourl": "https://example.com/new-audio.mp3",
    "videourl": "https://example.com/new-video.mp4",
    "color": "#4A90E2",
    "quotes": "更新后的语录",
    "author": "更新后的作者"
}
```

#### 4. 删除视频场景
```
DELETE /api/video-scenarios/{videoId}
```

### 查询操作

#### 1. 获取所有视频场景（分页）
```
GET /api/video-scenarios?page=0&size=20&sortBy=createAt&sortDir=desc
```

#### 2. 根据类型获取视频场景（按创建时间正序排列）
```
GET /api/video-scenarios/type/{type}
```

#### 3. 根据标题搜索视频场景
```
GET /api/video-scenarios/search/title?title=冥想
```

#### 4. 根据副标题搜索视频场景
```
GET /api/video-scenarios/search/subtitle?subtitle=开始
```

#### 5. 根据颜色获取视频场景
```
GET /api/video-scenarios/color/{color}
```

#### 6. 根据作者获取视频场景
```
GET /api/video-scenarios/author/{author}
```

### 特殊查询

#### 1. 获取有音频URL的视频场景
```
GET /api/video-scenarios/with-audio
```

#### 2. 获取有视频URL的视频场景
```
GET /api/video-scenarios/with-video
```

#### 3. 获取有封面图片的视频场景
```
GET /api/video-scenarios/with-image
```

#### 4. 统计指定类型的视频场景数量
```
GET /api/video-scenarios/count/type/{type}
```

## 使用示例

### 创建视频场景
```java
@Autowired
private VideoScenarioService videoScenarioService;

// 创建视频场景
VideoScenario videoScenario = videoScenarioService.createVideoScenario(
    "冥想",
    "晨间冥想",
    "开始美好的一天",
    "https://example.com/image.jpg",
    "https://example.com/audio.mp3",
    "https://example.com/video.mp4",
    "#4A90E2",
    "每一天都是新的开始",
    "禅修大师"
);
```

### 查询视频场景
```java
// 根据类型查找（按创建时间正序排列）
List<VideoScenario> meditationVideos = videoScenarioService.findByType("冥想");

// 根据标题搜索
List<VideoScenario> searchResults = videoScenarioService.searchByTitle("冥想");

// 获取所有视频场景
List<VideoScenario> allVideos = videoScenarioService.getAllVideoScenarios();
```

### 更新视频场景
```java
VideoScenario updated = videoScenarioService.updateVideoScenario(
    videoId,
    "冥想",
    "更新后的标题",
    "更新后的副标题",
    "https://example.com/new-image.jpg",
    "https://example.com/new-audio.mp3",
    "https://example.com/new-video.mp4",
    "#4A90E2",
    "更新后的语录",
    "更新后的作者"
);
```

## 数据库索引

为了提高查询性能，创建了以下索引：

1. `idx_video_scenario_type` - 类型字段索引
2. `idx_video_scenario_create_at` - 创建时间索引
3. `idx_video_scenario_update_at` - 更新时间索引
4. `idx_video_scenario_title` - 标题全文搜索索引
5. `idx_video_scenario_subtitle` - 副标题全文搜索索引
6. `idx_video_scenario_quotes` - 语录全文搜索索引

## 测试

运行集成测试：
```bash
mvn test -Dtest=VideoScenarioIntegrationTest
```

## 注意事项

1. 所有API接口都返回统一的`ApiResponse`格式
2. 创建和更新操作都包含参数验证
3. 删除操作是物理删除，请谨慎使用
4. 时间字段使用`OffsetDateTime`类型，支持时区
5. 更新操作会自动更新`updateAt`字段
6. 所有查询操作都按创建时间倒序排列

## 与现有功能的集成

这个新的视频场景功能与现有的视频功能（VideoController）是独立的：

- `VideoController`: 处理阿里云OSS中的视频文件访问
- `VideoScenarioController`: 处理视频场景的元数据管理

两者可以配合使用，VideoScenario中的`videourl`字段可以指向VideoController提供的视频文件URL。

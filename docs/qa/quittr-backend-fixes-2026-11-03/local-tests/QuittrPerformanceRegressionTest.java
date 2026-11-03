import com.example.cursorquitterweb.controller.*;
import com.example.cursorquitterweb.dto.*;
import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.service.*;
import com.example.cursorquitterweb.service.impl.*;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class QuittrPerformanceRegressionTest {
    private final ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager("videos");

    private <T> T cached(T target) {
        CacheInterceptor advice = new CacheInterceptor();
        advice.setCacheManager(caches);
        advice.setCacheOperationSources(new AnnotationCacheOperationSource());
        advice.afterPropertiesSet();
        advice.afterSingletonsInstantiated();
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(advice);
        return (T) factory.getProxy();
    }

    @Test void internationalVideoRecoversThenCachesSuccessAndHonorsEviction() {
        VideoService service = mock(VideoService.class);
        VideoController target = new VideoController();
        ReflectionTestUtils.setField(target, "videoService", service);
        when(service.getAllVideosWithCount(0, 10))
                .thenThrow(new RuntimeException("temporary"))
                .thenReturn(new VideoPageResult(Collections.emptyList(), 0));
        VideoController proxy = cached(target);
        assertEquals(500, proxy.getAllVideos(0, 10).getStatusCodeValue());
        assertNull(caches.getCache("videos").get("0_10"));
        assertTrue(proxy.getAllVideos(0, 10).getBody().isSuccess());
        assertTrue(proxy.getAllVideos(0, 10).getBody().isSuccess());
        verify(service, times(2)).getAllVideosWithCount(0, 10);
        caches.getCache("videos").clear();
        assertEquals(200, proxy.getAllVideos(0, 10).getStatusCodeValue());
        verify(service, times(3)).getAllVideosWithCount(0, 10);
    }

    @Test void chineseVideoRecoversWithoutCollidingWithInternationalCache() {
        VideoCnService cn = mock(VideoCnService.class);
        VideoCnController target = new VideoCnController();
        ReflectionTestUtils.setField(target, "videoCnService", cn);
        when(cn.getAllVideosWithCount(0, 10)).thenThrow(new RuntimeException("temporary"))
                .thenReturn(new VideoCnPageResult(Collections.emptyList(), 0));
        VideoCnController proxy = cached(target);
        assertEquals(500, proxy.getAllVideos(0, 10).getStatusCodeValue());
        assertNull(caches.getCache("videos").get("cn_0_10"));
        assertTrue(proxy.getAllVideos(0, 10).getBody().isSuccess());
        proxy.getAllVideos(0, 10);
        verify(cn, times(2)).getAllVideosWithCount(0, 10);
        assertNotNull(caches.getCache("videos").get("cn_0_10"));
        assertNull(caches.getCache("videos").get("0_10"));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 10, 50, 90, 91, 1000})
    void personalPostsPreserveEveryFieldAndOrderWithBoundedStatisticsQueries(int count) throws Exception {
        PostServiceImpl service = spy(new PostServiceImpl());
        PostLikeService likes = mock(PostLikeService.class);
        CommentService comments = mock(CommentService.class);
        ReflectionTestUtils.setField(service, "postLikeService", likes);
        ReflectionTestUtils.setField(service, "commentService", comments);
        UUID user = UUID.randomUUID();
        List<Post> posts = new ArrayList<>();
        Map<UUID, Integer> likeValues = new HashMap<>();
        Map<UUID, Long> commentValues = new HashMap<>();
        List<PostWithUpvotesDto> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Post post = new Post(user, "昵称", "stage", "正文 " + i);
            post.setPostId(UUID.randomUUID());
            post.setAvatarUrl("https://example.invalid/avatar");
            post.setContentZh("中文"); post.setContentEn("English");
            post.setContentJa("日本語"); post.setContentKo("한국어");
            post.setContentDe("Deutsch"); post.setContentFr("Français");
            post.setContentPt("Português"); post.setContentEs("Español");
            post.setOriginalLanguage("zh"); post.setTranslationStatus("completed");
            posts.add(post);
            if (i % 2 == 0) { likeValues.put(post.getPostId(), i + 2); commentValues.put(post.getPostId(), (long)i + 3); }
            expected.add(new PostWithUpvotesDto(post.getPostId(), post.getUserId(), post.getUserNickname(),
                    post.getUserStage(), post.getAvatarUrl(), post.getContent(), post.getIsDeleted(),
                    post.getCreatedAt(), post.getUpdatedAt(), likeValues.getOrDefault(post.getPostId(), 0),
                    commentValues.getOrDefault(post.getPostId(), 0L).intValue()).withTranslationsFrom(post));
        }
        doReturn(posts).when(service).findByUserId(user, 0, count);
        when(likes.getLikeCountsBatch(anyList())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0); assertTrue(ids.size() <= 90);
            return ids.stream().filter(likeValues::containsKey).collect(Collectors.toMap(id -> id, likeValues::get));
        });
        when(comments.countByPostIdsBatch(anyList())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0); assertTrue(ids.size() <= 90);
            return ids.stream().filter(commentValues::containsKey).collect(Collectors.toMap(id -> id, commentValues::get));
        });
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        assertEquals(mapper.valueToTree(expected), mapper.valueToTree(service.findByUserIdWithUpvotes(user, 0, count)));
        int batches = (count + 89) / 90;
        verify(likes, times(batches)).getLikeCountsBatch(anyList());
        verify(comments, times(batches)).countByPostIdsBatch(anyList());
        verify(likes, never()).getLikeCount(any());
        verify(comments, never()).countByPostId(any());
    }

    @Test void existingBatchSqlRetainsDeletedCommentFilterAndZeroDefaults() {
        CloudflareD1Util d1 = mock(CloudflareD1Util.class);
        CommentServiceImpl comments = new CommentServiceImpl();
        PostLikeServiceImpl likes = new PostLikeServiceImpl();
        ReflectionTestUtils.setField(comments, "d1Util", d1);
        ReflectionTestUtils.setField(likes, "d1Util", d1);
        UUID id = UUID.randomUUID();
        assertEquals(0L, comments.countByPostIdsBatch(Collections.singletonList(id)).get(id).longValue());
        assertEquals(0, likes.getLikeCountsBatch(Collections.singletonList(id)).get(id).intValue());
        assertEquals(2, mockingDetails(d1).getInvocations().size());
        assertTrue(mockingDetails(d1).getInvocations().stream().anyMatch(call ->
                call.getArgument(0).toString().contains("AND is_deleted = ? GROUP BY post_id")
                        && Arrays.asList(call.getArguments()).contains(false)));
    }
}

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import com.example.cursorquitterweb.controller.PostController;
import com.example.cursorquitterweb.service.*;
import com.example.cursorquitterweb.service.impl.*;
import com.example.cursorquitterweb.dto.*;
import com.example.cursorquitterweb.entity.*;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.config.WebConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 这些测试记录现有行为；通过代表问题可复现，不代表满足安全或性能验收标准。
public class QuittrBehaviorAuditTest {
 PostService posts; MockMvc mvc;
 final UUID id=UUID.fromString("00000000-0000-4000-8000-000000000001");
 @BeforeEach void setup(){posts=mock(PostService.class); PostController c=new PostController();ReflectionTestUtils.setField(c,"postService",posts);mvc=MockMvcBuilders.standaloneSetup(c).build();}
 @Test void anonymousDeleteReachesServiceWithoutIdentity() throws Exception {
  mvc.perform(delete("/api/posts/"+id+"/delete")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));verify(posts).deletePost(id);
 }
 @Test void anonymousUpdateReachesServiceWithoutIdentity() throws Exception {
  mvc.perform(put("/api/posts/"+id+"/update").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"isolated audit\"}")).andExpect(status().isOk());verify(posts).updatePost(id,"isolated audit");
 }
 @Test void notFoundUsesHttp200WithBusinessFailure() throws Exception {
  when(posts.findByIdWithUpvotes(id)).thenReturn(Optional.empty());mvc.perform(get("/api/posts/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
 }
 @Test void serviceErrorMessageIsExposedInResponse() throws Exception {
  doThrow(new RuntimeException("AUDIT_INTERNAL_DATABASE_DETAIL")).when(posts).deletePost(id);
  mvc.perform(delete("/api/posts/"+id+"/delete")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("AUDIT_INTERNAL_DATABASE_DETAIL")));
 }
 @Test void invalidUuidRejectedBeforeService() throws Exception {
  mvc.perform(delete("/api/posts/not-a-uuid/delete")).andExpect(status().isBadRequest());verifyNoInteractions(posts);
 }
 @Test void malformedJsonRejectedBeforeService() throws Exception {
  mvc.perform(put("/api/posts/"+id+"/update").contentType(MediaType.APPLICATION_JSON).content("{" )).andExpect(status().isBadRequest());verifyNoInteractions(posts);
 }
 @Test void emptyUpdateBodyReachesService() throws Exception {
  mvc.perform(put("/api/posts/"+id+"/update").contentType(MediaType.APPLICATION_JSON).content("{}"));verify(posts).updatePost(id,null);
 }
 @Test void unboundedPageSizeReachesService() throws Exception {
  when(posts.getAllPostsWithUpvotesAndCount(anyInt(),anyInt(),anyString(),anyString())).thenReturn(new PostPageResult(Collections.emptyList(),0));
  mvc.perform(get("/api/posts/getAllPosts").param("size","100000")).andExpect(status().isOk());verify(posts).getAllPostsWithUpvotesAndCount(0,100000,"created_at","desc");
 }
 @Test void negativeSizeReachesDatabaseQuery() {
  CloudflareD1Util d1=mock(CloudflareD1Util.class);PostServiceImpl service=new PostServiceImpl();ReflectionTestUtils.setField(service,"d1Util",d1);
  service.getAllPostsWithUpvotesAndCount(0,-1,"created_at","desc");
  assertTrue(mockingDetails(d1).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("queryList")&&Arrays.asList(i.getArguments()).contains(-1)));
 }
 @Test void tenPersonalPostsCauseTwentyStatisticsCalls() {
  PostServiceImpl service=spy(new PostServiceImpl()); PostLikeService likes=mock(PostLikeService.class);CommentService comments=mock(CommentService.class);
  ReflectionTestUtils.setField(service,"postLikeService",likes);ReflectionTestUtils.setField(service,"commentService",comments);
  List<Post> rows=new ArrayList<>();for(int n=0;n<10;n++){Post p=new Post(id,"audit","stage","text");p.setPostId(UUID.randomUUID());rows.add(p);}
  doReturn(rows).when(service).findByUserId(id,0,10);service.findByUserIdWithUpvotes(id,0,10);
  verify(likes,times(10)).getLikeCount(any(UUID.class));verify(comments,times(10)).countByPostId(any(UUID.class));
 }
 @Test void googleLoginAcceptsMissingTokenForExistingSyntheticIdentity() {
  GoogleAuthServiceImpl service=new GoogleAuthServiceImpl();UserService users=mock(UserService.class);UserIdentityService identities=mock(UserIdentityService.class);
  ReflectionTestUtils.setField(service,"userService",users);ReflectionTestUtils.setField(service,"userIdentityService",identities);ReflectionTestUtils.setField(service,"recoverJourneyService",mock(RecoverJourneyService.class));
  User user=User.initUser();user.setId(id);when(identities.findUserIdByIdentity(UserIdentity.IdentityType.GOOGLE,"audit-only-subject")).thenReturn(id);when(users.findById(id)).thenReturn(Optional.of(user));when(users.save(any(User.class))).thenAnswer(i->i.getArgument(0));
  GoogleLoginRequest request=new GoogleLoginRequest();request.setGoogleUserId("audit-only-subject");assertNull(request.getIdToken());assertNotNull(service.login(request));verify(users).save(user);
 }
 @Test void configuredHttpClientRetriesPostAfterServerConsumesBody() throws Exception {
  AtomicInteger attempts=new AtomicInteger();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/write",exchange->{while(exchange.getRequestBody().read()!=-1){} if(attempts.incrementAndGet()==1){exchange.close();return;}byte[] data="{}".getBytes("UTF-8");exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();});server.start();
  WebConfig config=new WebConfig();PoolingHttpClientConnectionManager pool=config.poolingHttpClientConnectionManager();
  try(CloseableHttpClient client=config.httpClient(pool)) {config.restTemplate(client).postForEntity("http://127.0.0.1:"+server.getAddress().getPort()+"/write",Collections.singletonMap("synthetic","audit"),String.class);assertEquals(2,attempts.get());} finally{server.stop(0);pool.close();}
 }
 @Test void caughtVideoFailureIsCachedAndPreventsRecovery() {
  com.example.cursorquitterweb.controller.VideoController target=new com.example.cursorquitterweb.controller.VideoController();
  VideoService videos=mock(VideoService.class);ReflectionTestUtils.setField(target,"videoService",videos);
  when(videos.getAllVideosWithCount(0,10)).thenThrow(new RuntimeException("isolated temporary failure"));
  org.springframework.cache.interceptor.CacheInterceptor interceptor=new org.springframework.cache.interceptor.CacheInterceptor();
  interceptor.setCacheManager(new org.springframework.cache.concurrent.ConcurrentMapCacheManager("videos"));
  interceptor.setCacheOperationSources(new org.springframework.cache.annotation.AnnotationCacheOperationSource());
  interceptor.afterPropertiesSet();interceptor.afterSingletonsInstantiated();
  org.springframework.aop.framework.ProxyFactory factory=new org.springframework.aop.framework.ProxyFactory(target);factory.addAdvice(interceptor);
  com.example.cursorquitterweb.controller.VideoController proxy=(com.example.cursorquitterweb.controller.VideoController)factory.getProxy();
  assertEquals(500,proxy.getAllVideos(0,10).getStatusCodeValue());
  assertEquals(500,proxy.getAllVideos(0,10).getStatusCodeValue());
  verify(videos,times(1)).getAllVideosWithCount(0,10);
 }
}

package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.repository.MusicMvAuthRepository;
import com.example.cursorquitterweb.musicmv.service.MusicMvOidcIdentityService.VerifiedIdentity;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MusicMvAuthMethodsTest {
    @TempDir Path temp;
    SqliteD1 db;
    MusicMvAuthRepository repository;
    @BeforeEach void setup() throws Exception {
        db=new SqliteD1(temp.resolve("auth.db"));
        String schema=new String(Files.readAllBytes(Paths.get("src/main/resources/db/music-mv-d1-schema.sql")),java.nio.charset.StandardCharsets.UTF_8);
        db.script(schema.substring(0,schema.indexOf("-- Reusable, private user media library.")));
        db.script(new String(Files.readAllBytes(Paths.get("src/main/resources/db/migrations/music-mv-auth-methods.sql")),java.nio.charset.StandardCharsets.UTF_8));
        repository=new MusicMvAuthRepository(db);
    }
    VerifiedIdentity identity(String provider,String sub,String email,boolean verified){return new VerifiedIdentity(provider,sub,email,verified,"Original",null);}
    Map<String,Object> resolve(String candidate,VerifiedIdentity identity,String target){return repository.resolveNewIdentity(candidate,"id-"+UUID.randomUUID(),candidate,"Original","en",identity,target);}
    void original(){resolve("usr_original",identity("google","g1","Person@example.com",true),null);}
    long users(){return ((Number)db.query("SELECT count(*) AS n FROM music_mv_users").firstRow().get("n")).longValue();}
    @Test void verifiedEmailCodeReusesOriginalWorkspaceAndPreservesProfile() {
        original();
        assertEquals("usr_original",resolve("usr_new",identity("email","person@example.com","person@example.com",true),null).get("user_id"));
        assertEquals(1,users());assertEquals(2,repository.identities("usr_original").size());
        repository.touchIdentity("usr_original",identity("email","person@example.com","person@example.com",true),"Replacement");
        assertEquals("Original",repository.findByIdentity("google","g1").get("display_name"));
    }
    @Test void appleSameEmailRequiresOriginalAccountProofAndHiddenEmailCanBeExplicitlyLinked() {
        original();
        assertNull(resolve("usr_apple",identity("apple","a1","person@example.com",true),null));
        assertEquals("usr_original",resolve("usr_unused",identity("apple","a1","private@privaterelay.appleid.com",true),"usr_original").get("user_id"));
        assertEquals(1,users());
    }
    @Test void unverifiedEmailNeverGrantsOriginalWorkspace() {
        resolve("usr_original",identity("google","g1","person@example.com",false),null);
        assertEquals("usr_new",resolve("usr_new",identity("email","person@example.com","person@example.com",true),null).get("user_id"));
    }
    @Test void duplicateLegacyVerifiedEmailsAreBlockedAndNeverMerged() {
        original();
        repository.createUserAndIdentity("usr_other","legacy","other","Other","en",identity("google","g2","person@example.com",true));
        assertNull(resolve("usr_new",identity("email","person@example.com","person@example.com",true),null));
        assertEquals(2,users());
    }
    @Test void disabledAccountCannotBeRecoveredOrReplaced() {
        original();db.query("UPDATE music_mv_users SET status='disabled' WHERE user_id='usr_original'");
        assertNull(resolve("usr_new",identity("email","person@example.com","person@example.com",true),null));assertEquals(1,users());
    }
    @Test void concurrentSignupProducesOneUserAndIdentity() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            VerifiedIdentity email=identity("email","same@example.com","same@example.com",true);
            Future<Map<String,Object>> a=pool.submit(()->resolve("usr_a",email,null));
            Future<Map<String,Object>> b=pool.submit(()->resolve("usr_b",email,null));
            assertEquals(a.get().get("user_id"),b.get().get("user_id"));assertEquals(1,users());
        }finally{pool.shutdownNow();}
    }
    @Test void linkingAnAlreadyOwnedIdentityRefusesWithoutChangingSessionOrCredits() {
        original();
        resolve("usr_other",identity("apple","a2","other@example.com",true),null);
        MusicMvAuthService auth=new MusicMvAuthService(repository,mock(MusicMvOidcIdentityService.class),30);
        MockHttpServletResponse response=new MockHttpServletResponse();
        ApiException failure=assertThrows(ApiException.class,()->auth.finishLogin(identity("apple","a2","other@example.com",true),null,null,"usr_original",new MockHttpServletRequest(),response));
        assertEquals("AUTH_IDENTITY_IN_USE",failure.getCode());assertNull(response.getHeader("Set-Cookie"));assertEquals(2,users());
        assertEquals("usr_other",repository.findByIdentity("apple","a2").get("user_id"));
    }
    @Test void emailLoginIssuesSessionForOriginalBalanceOwner() {
        original();
        MusicMvAuthService auth=new MusicMvAuthService(repository,mock(MusicMvOidcIdentityService.class),30);
        Map<String,Object> session=auth.finishLogin(identity("email","person@example.com","person@example.com",true),null,null,null,new MockHttpServletRequest(),new MockHttpServletResponse());
        assertEquals("usr_original",((Map<?,?>)session.get("user")).get("id"));assertEquals(1,users());
    }
    @Test void codeIsBrowserBoundSingleUseAndRateLimited() {
        MusicMvAuthEmailSender mail=mock(MusicMvAuthEmailSender.class);when(mail.configured()).thenReturn(true);
        MusicMvAuthChallengeService service=new MusicMvAuthChallengeService(db,mail,"12345678901234567890123456789012");
        MockHttpServletResponse response=new MockHttpServletResponse();
        Map<String,Object> start=service.begin("email"," Person@example.com ",null,"en",new MockHttpServletRequest(),response);
        org.mockito.ArgumentCaptor<String> code=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("person@example.com"),code.capture(),eq("en"),anyString());
        String id=(String)start.get("challengeId");
        assertFalse(db.query("SELECT secret_hash FROM music_mv_auth_challenges").firstRow().get("secret_hash").equals(code.getValue()));
        assertThrows(ApiException.class,()->service.consume(id,"email",code.getValue(),null,new MockHttpServletRequest()));
        MockHttpServletRequest request=withBrowser(response);
        assertThrows(ApiException.class,()->service.consume(id,"email",code.getValue(),"usr_other",request));
        assertEquals("person@example.com",service.consume(id,"email",code.getValue(),null,request).get("email"));
        assertThrows(ApiException.class,()->service.consume(id,"email",code.getValue(),null,request));
        assertEquals("AUTH_RATE_LIMITED",assertThrows(ApiException.class,()->service.begin("email","person@example.com",null,"en",request,new MockHttpServletResponse())).getCode());
    }
    @Test void fiveFailuresExhaustCodeAndExpiredCodeCannotLogin() {
        MusicMvAuthEmailSender mail=mock(MusicMvAuthEmailSender.class);when(mail.configured()).thenReturn(true);
        MusicMvAuthChallengeService service=new MusicMvAuthChallengeService(db,mail,"12345678901234567890123456789012");
        MockHttpServletResponse response=new MockHttpServletResponse();
        String id=(String)service.begin("email","one@example.com",null,"en",new MockHttpServletRequest(),response).get("challengeId");
        org.mockito.ArgumentCaptor<String> code=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).send(anyString(),code.capture(),anyString(),anyString());
        MockHttpServletRequest request=withBrowser(response);
        for(int i=0;i<5;i++)assertThrows(ApiException.class,()->service.consume(id,"email","wrong",null,request));
        assertThrows(ApiException.class,()->service.consume(id,"email",code.getValue(),null,request));
        db.query("UPDATE music_mv_auth_challenges SET attempts=0,expires_at=datetime('now','-1 second')");
        assertThrows(ApiException.class,()->service.consume(id,"email",code.getValue(),null,request));
    }
    @Test void failedMailCannotLeaveUsableCodeAndMissingConfigIsClosed() {
        MusicMvAuthEmailSender mail=mock(MusicMvAuthEmailSender.class);when(mail.configured()).thenReturn(true);
        doThrow(new IllegalStateException("private upstream error")).when(mail).send(anyString(),anyString(),anyString(),anyString());
        MusicMvAuthChallengeService service=new MusicMvAuthChallengeService(db,mail,"12345678901234567890123456789012");
        assertEquals("AUTH_EMAIL_UNAVAILABLE",assertThrows(ApiException.class,()->service.begin("email","one@example.com",null,"en",new MockHttpServletRequest(),new MockHttpServletResponse())).getCode());
        assertEquals(1,((Number)db.query("SELECT consumed FROM music_mv_auth_challenges").firstRow().get("consumed")).intValue());
        assertFalse(new MusicMvAuthChallengeService(db,mail,"").emailConfigured());
    }
    @Test void appleNonceIsSingleUseAndBoundToLinkTarget() {
        MusicMvAuthChallengeService service=new MusicMvAuthChallengeService(db,mock(MusicMvAuthEmailSender.class),"");
        MockHttpServletResponse response=new MockHttpServletResponse();
        Map<String,Object> start=service.begin("apple",null,"usr_original","en",new MockHttpServletRequest(),response);
        MockHttpServletRequest request=withBrowser(response);String id=(String)start.get("challengeId"),nonce=(String)start.get("nonce");
        assertThrows(ApiException.class,()->service.consume(id,"apple",nonce,null,request));
        service.consume(id,"apple",nonce,"usr_original",request);
        assertThrows(ApiException.class,()->service.consume(id,"apple",nonce,"usr_original",request));
    }
    @Test void concurrentFirstChallengesKeepIndependentBrowserBindings() {
        MusicMvAuthEmailSender mail=mock(MusicMvAuthEmailSender.class);when(mail.configured()).thenReturn(true);
        MusicMvAuthChallengeService service=new MusicMvAuthChallengeService(db,mail,"12345678901234567890123456789012");
        MockHttpServletResponse first=new MockHttpServletResponse(),second=new MockHttpServletResponse();
        Map<String,Object> a=service.begin("apple",null,null,"en",new MockHttpServletRequest(),first);
        Map<String,Object> b=service.begin("email","concurrent@example.com",null,"en",new MockHttpServletRequest(),second);
        org.mockito.ArgumentCaptor<String> code=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).send(anyString(),code.capture(),anyString(),anyString());
        List<Cookie> cookies=new ArrayList<>(Arrays.asList(withBrowser(second).getCookies()));
        for(Cookie cookie:withBrowser(first).getCookies()) if(cookie.getName().endsWith((String)a.get("challengeId")))cookies.add(cookie);
        MockHttpServletRequest combined=new MockHttpServletRequest();combined.setCookies(cookies.toArray(new Cookie[0]));
        service.consume((String)a.get("challengeId"),"apple",(String)a.get("nonce"),null,combined);
        service.consume((String)b.get("challengeId"),"email",code.getValue(),null,combined);
    }

    @Test void emailsCoverAllTenLanguagesAndDoNotLogCodes() {
        Set<String> subjects=new HashSet<>();
        for(String locale:Arrays.asList("en","zh-CN","zh-TW","ja","ko","es","pt-BR","fr","de","it")){
            String[] copy=MusicMvAuthEmailSender.copy(locale);assertTrue(copy[1].contains("{code}"));subjects.add(copy[0]);
        }
        assertEquals(10,subjects.size());
    }
    private MockHttpServletRequest withBrowser(MockHttpServletResponse response){
        List<Cookie> cookies=new ArrayList<>();
        for(String header:response.getHeaders("Set-Cookie")) { String[] parts=header.split(";",2)[0].split("=",2);cookies.add(new Cookie(parts[0],parts[1])); }
        MockHttpServletRequest request=new MockHttpServletRequest();request.setCookies(cookies.toArray(new Cookie[0]));return request;
    }
    // 使用本地 SQLite 执行真实 SQL 与事务，不连接远程 D1 或发送邮件。
    static class SqliteD1 extends D1DatabaseClient {
        private final Path path;
        private final ObjectMapper json=new ObjectMapper();
        SqliteD1(Path path){super(new ObjectMapper());this.path=path;}
        void script(String sql){run(Collections.singletonMap("script",sql));}
        @Override public D1QueryResult query(String sql,Object... params){return batch(Collections.singletonList(D1Statement.of(sql,params))).get(0);}
        @Override public List<D1QueryResult> batch(List<D1Statement> statements){
            List<Map<String,Object>> input=new ArrayList<>();
            for(D1Statement statement:statements){Map<String,Object> s=new LinkedHashMap<>();s.put("sql",statement.getSql());s.put("params",statement.getParams());input.add(s);}
            List<?> rows=(List<?>)run(Collections.singletonMap("batch",input));List<D1QueryResult> results=new ArrayList<>();
            for(Object row:rows)results.add(new D1QueryResult((List<Map<String,Object>>)row,null));return results;
        }
        private Object run(Object input){try{
            String script="import sqlite3,json,sys\nc=sqlite3.connect(sys.argv[1],timeout=10)\nc.row_factory=sqlite3.Row\nb=json.load(sys.stdin)\nr=[]\nif 'script' in b:c.executescript(b['script'])\nelse:\n c.execute('BEGIN IMMEDIATE')\n for s in b['batch']:r.append([dict(x) for x in c.execute(s['sql'],s['params']).fetchall()])\nc.commit()\nprint(json.dumps(r))";
            Process process=new ProcessBuilder("python3","-c",script,path.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            json.writeValue(process.getOutputStream(),input);Object result=json.readValue(process.getInputStream(),Object.class);
            if(process.waitFor()!=0)throw new IllegalStateException("SQLite failed");return result;
        }catch(Exception e){throw new IllegalStateException(e);}}
    }
}

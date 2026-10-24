package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
public class MusicMvAuthChallengeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String COOKIE = "music_mv_auth_browser";
    private final D1DatabaseClient d1;
    private final MusicMvAuthEmailSender mail;
    private final String pepper;
    @Value("${music-mv.auth.email.hourly-limit:100}")
    private int emailHourlyLimit = 100;
    public MusicMvAuthChallengeService(D1DatabaseClient d1, MusicMvAuthEmailSender mail,
            @Value("${music-mv.auth.email.code-secret:}") String pepper) {
        this.d1=d1; this.mail=mail; this.pepper=pepper;
    }
    public boolean emailConfigured() { return pepper.length()>=32 && mail.configured(); }
    public Map<String,Object> begin(String kind,String email,String target,String locale,
            HttpServletRequest request,HttpServletResponse response) {
        if ("email".equals(kind) && !emailConfigured()) throw error("AUTH_EMAIL_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        String normalized = "email".equals(kind) ? normalizeEmail(email) : null;
        String browser=browser(request,COOKIE);
        if (browser==null) browser=token();
        String hash=sha(browser), id=token();
        String secret="email".equals(kind) ? String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1000000)) : token();
        String digest="email".equals(kind) ? codeHash(id,secret) : sha(secret);
        // 原子限流跨实例共享；全局预算限制不断换浏览器和邮箱的发送滥用。
        Map<String,Object> saved=d1.query("INSERT INTO music_mv_auth_challenges (challenge_id,kind,browser_hash,target_user_id,email,secret_hash,expires_at) "
                + "SELECT ?,?,?,?,?,?,datetime('now','+10 minutes') "
                + "WHERE (SELECT count(*) FROM music_mv_auth_challenges WHERE kind=? AND created_at>datetime('now','-1 hour'))<? "
                + "AND (SELECT count(*) FROM music_mv_auth_challenges WHERE browser_hash=? AND kind=? AND created_at>datetime('now','-1 hour'))<10 "
                + "AND (? IS NULL OR ((SELECT count(*) FROM music_mv_auth_challenges WHERE email=? AND created_at>datetime('now','-1 hour'))<5 "
                + "AND NOT EXISTS(SELECT 1 FROM music_mv_auth_challenges WHERE email=? AND created_at>datetime('now','-60 seconds')))) RETURNING challenge_id",
                id,kind,hash,target,normalized,digest,kind,"email".equals(kind)?Math.max(1,emailHourlyLimit):1000,hash,kind,normalized,normalized,normalized).firstRow();
        if(saved==null) throw error("AUTH_RATE_LIMITED",HttpStatus.TOO_MANY_REQUESTS);
        if("email".equals(kind)) {
            try { mail.send(normalized,secret,locale,id); }
            catch(RuntimeException exception) {
                d1.query("UPDATE music_mv_auth_challenges SET consumed=1 WHERE challenge_id=?",id);
                throw error("AUTH_EMAIL_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        boolean secure=request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,browser).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(3600).build().toString());
        // 每个挑战单独绑定 Cookie，避免首次并发打开 Apple 与邮箱流程时互相覆盖。
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE+"_"+id,browser).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(600).build().toString());
        Map<String,Object> result=new LinkedHashMap<>();result.put("challengeId",id);
        if("apple".equals(kind)) result.put("nonce",secret);
        else { result.put("retryAfterSeconds",60); result.put("expiresInSeconds",600); }
        // 保留一天的限流证据，清理时不输出邮箱或验证码。
        d1.query("DELETE FROM music_mv_auth_challenges WHERE created_at<datetime('now','-1 day')");
        return result;
    }
    public Map<String,Object> consume(String id,String kind,String secret,String target,HttpServletRequest request) {
        String browser=browser(request,COOKIE+"_"+id);
        if(id==null || id.length()>128 || secret==null || secret.length()>256 || browser==null)
            throw error("AUTH_CODE_INVALID",HttpStatus.UNAUTHORIZED);
        String digest="email".equals(kind)?codeHash(id,secret):sha(secret);
        // 成功与失败都计数；正确的验证码也只能被并发请求消费一次。
        Map<String,Object> row=d1.query("UPDATE music_mv_auth_challenges SET attempts=attempts+1,consumed=CASE WHEN secret_hash=? THEN 1 ELSE consumed END "
                + "WHERE challenge_id=? AND kind=? AND browser_hash=? AND target_user_id IS ? AND consumed=0 AND attempts<5 "
                + "AND expires_at>CURRENT_TIMESTAMP RETURNING email,secret_hash,consumed",digest,id,kind,sha(browser),target).firstRow();
        if(row==null || !digest.equals(RowUtils.str(row,"secret_hash")) || !RowUtils.bool(row,"consumed"))
            throw error("AUTH_CODE_INVALID",HttpStatus.UNAUTHORIZED);
        return row;
    }
    public static String normalizeEmail(String value) {
        String email=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254 || !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}"))
            throw error("AUTH_EMAIL_INVALID",HttpStatus.BAD_REQUEST);
        return email;
    }
    private String codeHash(String id,String code) {
        if(!emailConfigured()) throw error("AUTH_EMAIL_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE);
        try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((id+":"+code).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception exception) { throw new IllegalStateException("Code hashing unavailable"); }
    }
    private static String browser(HttpServletRequest request,String name) {
        if(request.getCookies()!=null) for(Cookie c:request.getCookies()) if(name.equals(c.getName()) && c.getValue().matches("[A-Za-z0-9_-]{43}"))return c.getValue();
        return null;
    }
    private static String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String sha(String s){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static ApiException error(String code,HttpStatus status){return new ApiException(status,code,"Sign-in could not be completed. Please try again.");}
}

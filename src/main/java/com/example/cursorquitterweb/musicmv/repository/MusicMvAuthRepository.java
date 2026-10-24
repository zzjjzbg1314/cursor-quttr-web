package com.example.cursorquitterweb.musicmv.repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1Statement;
import com.example.cursorquitterweb.musicmv.service.MusicMvOidcIdentityService.VerifiedIdentity;

/** Persistence for the isolated Music MV user system. */
@Repository
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvAuthRepository {
    private static final String USER_VIEW = "u.user_id,u.display_name,u.handle,u.avatar_url,u.email,"
            + "u.locale,u.status,u.last_login_at,u.created_at";

    private final D1DatabaseClient d1;

    public MusicMvAuthRepository(D1DatabaseClient d1) {
        this.d1 = d1;
    }

    public Map<String, Object> findByIdentity(String provider, String subject) {
        return d1.query("SELECT " + USER_VIEW + ",i.provider,i.provider_email,i.email_verified "
                        + "FROM music_mv_user_identities i JOIN music_mv_users u ON u.user_id=i.user_id "
                        + "WHERE i.provider=? AND i.provider_subject=? AND u.deleted_at IS NULL LIMIT 1",
                provider, subject).firstRow();
    }

    public Map<String, Object> findBySessionTokenHash(String tokenSha256) {
        return d1.query("SELECT " + USER_VIEW + ",s.session_id,s.expires_at "
                        + ",CASE WHEN s.last_seen_at IS NULL OR s.last_seen_at<datetime('now','-5 minutes') "
                        + "THEN 1 ELSE 0 END AS session_touch_due "
                        + "FROM music_mv_user_sessions s JOIN music_mv_users u ON u.user_id=s.user_id "
                        + "WHERE s.token_sha256=? AND s.revoked_at IS NULL "
                        + "AND s.expires_at>CURRENT_TIMESTAMP AND u.status='active' "
                        + "AND u.deleted_at IS NULL LIMIT 1", tokenSha256).firstRow();
    }

    public void createUserAndIdentity(String userId, String identityId, String handle,
                                      String displayName, String locale, VerifiedIdentity identity) {
        d1.batch(Arrays.asList(
                D1Statement.of("INSERT INTO music_mv_users "
                                + "(user_id,display_name,handle,avatar_url,email,locale,status,last_login_at,created_at,updated_at) "
                                + "VALUES (?,?,?,?,?,?,'active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                        userId, displayName, handle, identity.getAvatarUrl(), identity.getEmail(), locale),
                D1Statement.of("INSERT INTO music_mv_user_identities "
                                + "(identity_id,user_id,provider,provider_subject,provider_email,email_verified,"
                                + "provider_display_name,provider_avatar_url,last_login_at,created_at,updated_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                        identityId, userId, identity.getProvider(), identity.getSubject(), identity.getEmail(),
                        Integer.valueOf(identity.isEmailVerified() ? 1 : 0), identity.getDisplayName(),
                        identity.getAvatarUrl())
        ));
    }

    public void touchIdentity(String userId, VerifiedIdentity identity, String displayName) {
        d1.batch(Arrays.asList(
                D1Statement.of("UPDATE music_mv_users SET last_login_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId),
                D1Statement.of("UPDATE music_mv_user_identities SET provider_email=COALESCE(?,provider_email),"
                                + "email_verified=?,provider_display_name=COALESCE(?,provider_display_name),"
                                + "provider_avatar_url=COALESCE(?,provider_avatar_url),last_login_at=CURRENT_TIMESTAMP,"
                                + "updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND provider=? AND provider_subject=?",
                        identity.getEmail(), Integer.valueOf(identity.isEmailVerified() ? 1 : 0),
                        identity.getDisplayName(), identity.getAvatarUrl(), userId,
                        identity.getProvider(), identity.getSubject())
        ));
    }

    public List<Map<String, Object>> identities(String userId) {
        return d1.query("SELECT provider,provider_email AS email FROM music_mv_user_identities WHERE user_id=? ORDER BY created_at", userId).getRows();
    }

    // 在同一 D1 事务内判定邮箱归属和创建身份，避免并发登录创建两个工作区。
    public Map<String, Object> resolveNewIdentity(String candidate, String identityId, String handle,
            String name, String locale, VerifiedIdentity identity, String targetUserId) {
        String email = identity.isEmailVerified() && identity.getEmail() != null
                ? identity.getEmail().trim().toLowerCase(java.util.Locale.ROOT) : null;
        String matches = "SELECT DISTINCT user_id FROM music_mv_user_identities WHERE email_verified=1 AND lower(trim(provider_email))=?";
        String owner = targetUserId;
        List<D1Statement> statements = new java.util.ArrayList<>();
        statements.add(D1Statement.of("INSERT INTO music_mv_users "
                + "(user_id,display_name,handle,avatar_url,email,locale,status,last_login_at,created_at,updated_at) "
                + "SELECT ?,?,?,?,?,?,'active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP "
                + "WHERE ? IS NULL AND NOT EXISTS (SELECT 1 FROM music_mv_user_identities WHERE provider=? AND provider_subject=?) "
                + "AND NOT EXISTS (" + matches + ")", candidate,name,handle,identity.getAvatarUrl(),email,locale,
                owner,identity.getProvider(),identity.getSubject(),email));
        String resolved = "COALESCE(?, CASE WHEN ?='email' AND (SELECT count(*) FROM (" + matches + "))=1 "
                + "THEN (" + matches + ") ELSE ? END)";
        statements.add(D1Statement.of("INSERT INTO music_mv_user_identities "
                + "(identity_id,user_id,provider,provider_subject,provider_email,email_verified,provider_display_name,provider_avatar_url,last_login_at,created_at,updated_at) "
                + "SELECT ?,u.user_id,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM music_mv_users u "
                + "WHERE u.user_id=" + resolved + " AND u.status='active' AND u.deleted_at IS NULL "
                + "AND NOT EXISTS (SELECT 1 FROM music_mv_user_identities WHERE provider=? AND provider_subject=?) "
                + "AND (NOT EXISTS (SELECT 1 FROM music_mv_user_identities WHERE email_verified=1 "
                + "AND lower(trim(provider_email))=? AND user_id<>u.user_id))",
                identityId,identity.getProvider(),identity.getSubject(),email,identity.isEmailVerified()?1:0,identity.getDisplayName(),identity.getAvatarUrl(),
                owner,identity.getProvider(),email,email,candidate,identity.getProvider(),identity.getSubject(),email));
        d1.batch(statements);
        return findByIdentity(identity.getProvider(), identity.getSubject());
    }

    public void createSession(String sessionId, String userId, String tokenSha256, int sessionDays) {
        d1.query("INSERT INTO music_mv_user_sessions "
                        + "(session_id,user_id,token_sha256,expires_at,last_seen_at,created_at) "
                        + "VALUES (?,?,?,datetime('now',?),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                sessionId, userId, tokenSha256, "+" + sessionDays + " days");
    }

    public void touchSession(String sessionId) {
        d1.query("UPDATE music_mv_user_sessions SET last_seen_at=CURRENT_TIMESTAMP "
                + "WHERE session_id=? AND (last_seen_at IS NULL OR last_seen_at<datetime('now','-5 minutes'))", sessionId);
    }

    public void revokeSession(String tokenSha256) {
        d1.query("UPDATE music_mv_user_sessions SET revoked_at=CURRENT_TIMESTAMP "
                + "WHERE token_sha256=? AND revoked_at IS NULL", tokenSha256);
    }

    public void revokeExpiredSessions(String userId) {
        d1.query("UPDATE music_mv_user_sessions SET revoked_at=CURRENT_TIMESTAMP "
                + "WHERE user_id=? AND revoked_at IS NULL AND expires_at<=CURRENT_TIMESTAMP", userId);
    }

    public void claimAnonymousWork(String userId, String anonymousClientId) {
        if (anonymousClientId == null || anonymousClientId.equals(userId)) return;
        List<D1Statement> statements = Arrays.asList(
                D1Statement.of("UPDATE ai_music_jobs SET user_id=?,client_id=?,updated_at=CURRENT_TIMESTAMP "
                        + "WHERE client_id=?", userId, userId, anonymousClientId),
                D1Statement.of("UPDATE music_mv_render_jobs SET client_id=?,updated_at=CURRENT_TIMESTAMP "
                        + "WHERE client_id=?", userId, anonymousClientId)
        );
        d1.batch(statements);
    }
}

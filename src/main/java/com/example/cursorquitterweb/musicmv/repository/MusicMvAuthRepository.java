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
                D1Statement.of("UPDATE music_mv_users SET display_name=COALESCE(?,display_name),"
                                + "avatar_url=COALESCE(?,avatar_url),email=COALESCE(?,email),"
                                + "last_login_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
                        displayName, identity.getAvatarUrl(), identity.getEmail(), userId),
                D1Statement.of("UPDATE music_mv_user_identities SET provider_email=COALESCE(?,provider_email),"
                                + "email_verified=?,provider_display_name=COALESCE(?,provider_display_name),"
                                + "provider_avatar_url=COALESCE(?,provider_avatar_url),last_login_at=CURRENT_TIMESTAMP,"
                                + "updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND provider=? AND provider_subject=?",
                        identity.getEmail(), Integer.valueOf(identity.isEmailVerified() ? 1 : 0),
                        identity.getDisplayName(), identity.getAvatarUrl(), userId,
                        identity.getProvider(), identity.getSubject())
        ));
    }

    public void createSession(String sessionId, String userId, String tokenSha256, int sessionDays) {
        d1.query("INSERT INTO music_mv_user_sessions "
                        + "(session_id,user_id,token_sha256,expires_at,last_seen_at,created_at) "
                        + "VALUES (?,?,?,datetime('now',?),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                sessionId, userId, tokenSha256, "+" + sessionDays + " days");
    }

    public void touchSession(String sessionId) {
        d1.query("UPDATE music_mv_user_sessions SET last_seen_at=CURRENT_TIMESTAMP "
                + "WHERE session_id=? AND last_seen_at<datetime('now','-5 minutes')", sessionId);
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

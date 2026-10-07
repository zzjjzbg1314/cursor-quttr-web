package com.example.cursorquitterweb.musicmv.billing;

import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.cursorquitterweb.musicmv.service.*;

@Repository
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
public class BillingRepository {
    private final D1DatabaseClient db;
    public BillingRepository(D1DatabaseClient db) { this.db=db; }
    public Map<String,Object> customer(String provider,String user) {
        return db.query("SELECT * FROM music_mv_billing_customers WHERE provider=? AND user_id=?",provider,user).firstRow();
    }
    public void saveCustomer(String provider,String user,String customer) {
        db.query("INSERT INTO music_mv_billing_customers(provider,user_id,customer_id) VALUES(?,?,?) ON CONFLICT(user_id,provider) DO NOTHING",provider,user,customer);
    }
    public String owner(String provider,String customer) {
        Map<String,Object> r=db.query("SELECT user_id FROM music_mv_billing_customers WHERE provider=? AND customer_id=?",provider,customer).firstRow();
        return r==null?null:String.valueOf(r.get("user_id"));
    }
    public Map<String,Object> subscription(String user) {
        return db.query("SELECT * FROM music_mv_billing_subscriptions WHERE user_id=?",user).firstRow();
    }
    public void syncSubscription(String provider,String user,String id,String status,boolean canceled) {
        // 仅使用当前渠道查证的状态，不能覆盖其他渠道的订阅或结账占用。
        db.query("INSERT INTO music_mv_billing_subscriptions(user_id,provider,subscription_id,status,cancel_at_period_end) VALUES(?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET subscription_id=excluded.subscription_id,status=excluded.status,cancel_at_period_end=excluded.cancel_at_period_end WHERE music_mv_billing_subscriptions.provider=excluded.provider",user,provider,id,status,canceled?1:0);
    }
    public boolean claimCheckout(String provider,String user,String token,String plan,String returnUrl,long now) {
        return db.query("INSERT INTO music_mv_billing_subscriptions(user_id,provider,checkout_token,checkout_plan,checkout_expires,checkout_return_url) VALUES(?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET provider=excluded.provider,checkout_token=excluded.checkout_token,checkout_plan=excluded.checkout_plan,checkout_expires=excluded.checkout_expires,checkout_return_url=excluded.checkout_return_url,checkout_id=NULL,checkout_url=NULL,status=NULL,cancel_at_period_end=0 WHERE music_mv_billing_subscriptions.subscription_id IS NULL AND (music_mv_billing_subscriptions.checkout_expires IS NULL OR music_mv_billing_subscriptions.checkout_expires<?) RETURNING user_id",user,provider,token,plan,now+3600,returnUrl,now).firstRow()!=null;
    }
    public void saveCheckout(String provider,String user,String token,String id,String url) {
        db.query("UPDATE music_mv_billing_subscriptions SET checkout_id=?,checkout_url=? WHERE provider=? AND user_id=? AND checkout_token=?",id,url,provider,user,token);
    }
    public boolean processed(String provider,String id) { return db.query("SELECT event_id FROM music_mv_billing_events WHERE provider=? AND event_id=?",provider,id).firstRow()!=null; }
    public void processed(String provider,String id,String type) { db.query("INSERT INTO music_mv_billing_events(provider,event_id,event_type) VALUES(?,?,?) ON CONFLICT DO NOTHING",provider,id,type); }
    public void grant(String provider,String payment,String user,String subscription,String plan,int allowance,long start,long end) {
        db.query("INSERT INTO music_mv_billing_grants(grant_id,provider,payment_id,user_id,subscription_id,plan_key,allowance,period_start,period_end,revoked) VALUES(?,?,?,?,?,?,?,?,?,EXISTS(SELECT 1 FROM music_mv_billing_revocations WHERE provider=? AND payment_id=?)) ON CONFLICT(provider,payment_id) DO NOTHING",UUID.randomUUID().toString(),provider,payment,user,subscription,plan,allowance,start,end,provider,payment);
    }
    public void ensureWelcomeCredits(String user) {
        D1Statement statement=welcomeStatement(user);
        db.query(statement.getSql(),statement.getParams().toArray());
    }
    private D1Statement welcomeStatement(String user) {
        // 固定账号唯一键保证首次赠送不重复。
        return D1Statement.of("INSERT INTO music_mv_billing_grants(grant_id,provider,payment_id,user_id,subscription_id,plan_key,allowance,period_start,period_end) VALUES(?,'internal',?,?,'welcome','welcome',10,0,1) ON CONFLICT(provider,payment_id) DO NOTHING",UUID.randomUUID().toString(),"welcome:"+user,user);
    }
    public List<Map<String,Object>> history(String user,int limit,int offset) { return history(user,limit,offset,"all"); }
    public List<Map<String,Object>> history(String user,int limit,int offset,String kind) {
        // 先按用户和类型过滤，再分页，避免仅筛选当前页造成遗漏。
        return db.query("SELECT * FROM (SELECT 'grant:'||grant_id AS id, CASE WHEN revoked=1 THEN 'revoked' WHEN provider='internal' AND plan_key='welcome' THEN 'welcome' ELSE 'subscription' END AS kind, CASE WHEN revoked=1 THEN 0 ELSE allowance END AS amount, created_at AS occurredAt FROM music_mv_billing_grants WHERE user_id=? UNION ALL SELECT 'job:'||job_id AS id,state AS kind,CASE WHEN state='released' THEN 0 ELSE -credits END AS amount,updated_at AS occurredAt FROM music_mv_billing_reservations WHERE user_id=?) WHERE (?='all' OR kind=?) ORDER BY occurredAt DESC,id DESC LIMIT ? OFFSET ?",user,user,kind,kind,limit,offset).getRows();
    }
    public Map<String,Object> usage(String user) {
        return db.query("SELECT (SELECT COALESCE(SUM(allowance),0) FROM music_mv_billing_grants WHERE user_id=?) AS earned,(SELECT COALESCE(SUM(credits),0) FROM music_mv_billing_reservations WHERE user_id=? AND state='consumed') AS spent",user,user).firstRow();
    }
    public List<Map<String,Object>> generationSpend(String user) {
        // 一次任务只显示一笔费用，不因返回多首候选歌曲重复计算。
        return db.query("SELECT r.job_id AS jobId,r.state,r.credits,CASE WHEN g.provider='internal' AND g.plan_key='welcome' THEN 'welcome' ELSE 'subscription' END AS source,r.created_at AS occurredAt,(SELECT c.title FROM ai_music_candidates c WHERE c.job_id=j.job_id AND c.title IS NOT NULL AND c.title<>'' ORDER BY c.selected DESC,c.created_at,c.candidate_id LIMIT 1) AS title FROM music_mv_billing_reservations r JOIN music_mv_billing_grants g ON g.grant_id=r.grant_id AND g.user_id=r.user_id JOIN ai_music_jobs j ON j.job_id=r.job_id AND j.user_id=r.user_id WHERE r.user_id=? AND r.state IN ('consumed','reserved') ORDER BY r.created_at DESC,r.job_id DESC LIMIT 8",user).getRows();
    }
    public boolean paymentGranted(String user,String invoice) {
        return db.query("SELECT grant_id FROM music_mv_billing_grants WHERE user_id=? AND provider='stripe' AND payment_id=? AND revoked=0",user,invoice).firstRow()!=null;
    }
    public Map<String,Object> balance(String user,long now) {
        D1Statement statement=balanceStatement(user,now);
        return db.query(statement.getSql(),statement.getParams().toArray()).firstRow();
    }
    private D1Statement balanceStatement(String user,long now) {
        return D1Statement.of("SELECT COALESCE(SUM(g.allowance-(SELECT COALESCE(SUM(r.credits),0) FROM music_mv_billing_reservations r WHERE r.grant_id=g.grant_id AND r.state<>'released')),0) AS remaining, COALESCE(SUM(CASE WHEN g.plan_key='welcome' AND g.provider='internal' THEN 0 ELSE g.allowance END),0) AS allowance, COALESCE(SUM((SELECT COALESCE(SUM(r.credits),0) FROM music_mv_billing_reservations r WHERE r.grant_id=g.grant_id AND r.state='reserved')),0) AS reserved, MAX(CASE WHEN g.provider='internal' AND g.plan_key='welcome' THEN NULL ELSE g.period_end END) AS period_end, COALESCE(SUM(CASE WHEN g.provider='internal' AND g.plan_key='welcome' THEN g.allowance-(SELECT COALESCE(SUM(r.credits),0) FROM music_mv_billing_reservations r WHERE r.grant_id=g.grant_id AND r.state<>'released') ELSE 0 END),0) AS welcome_remaining, MAX(CASE WHEN g.provider='internal' AND g.plan_key='welcome' AND NOT EXISTS(SELECT 1 FROM music_mv_billing_reservations r WHERE r.grant_id=g.grant_id AND r.state IN ('reserved','consumed')) AND NOT EXISTS(SELECT 1 FROM music_mv_billing_reservations r WHERE r.user_id=g.user_id AND r.state='reserved') THEN g.grant_id ELSE NULL END) AS unused_welcome_id FROM music_mv_billing_grants g WHERE g.user_id=? AND g.revoked=0 AND g.period_start<=? AND (g.period_end>? OR (g.provider='internal' AND g.plan_key='welcome'))",user,now,now);
    }
    public Map<String,Object> refreshBalance(String user,long now) {
        // 同一批次按顺序赠送、对账再读取余额，减少远程往返且不缓存账务状态。
        List<D1QueryResult> results=db.batch(Arrays.asList(welcomeStatement(user),reconcileStatement(user),balanceStatement(user,now)));
        if(results.size()!=3) throw new IllegalStateException("Incomplete billing balance batch");
        return results.get(2).firstRow();
    }
    public boolean reserve(String user,String request,String job,long now,int credits) {
        // 每笔任务保存扣分数；单条条件写入检查余额，避免并发超扣。
        if (credits <= 0) throw new IllegalArgumentException("Credits must be positive");
        return db.query("INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) SELECT ?,?,?,g.grant_id,'reserved',? FROM music_mv_billing_grants g WHERE g.user_id=? AND g.revoked=0 AND g.period_start<=? AND (g.period_end>? OR (g.provider='internal' AND g.plan_key='welcome')) AND g.allowance-? >=(SELECT COALESCE(SUM(r.credits),0) FROM music_mv_billing_reservations r WHERE r.grant_id=g.grant_id AND r.state<>'released') ORDER BY CASE WHEN g.provider='internal' AND g.plan_key='welcome' THEN 1 ELSE 0 END,g.period_end,g.grant_id LIMIT 1 ON CONFLICT DO NOTHING RETURNING job_id",job,user,request,credits,user,now,now,credits).firstRow()!=null;
    }
    public boolean hasReservation(String user,String request) {
        return db.query("SELECT job_id FROM music_mv_billing_reservations WHERE user_id=? AND request_id=?",user,request).firstRow()!=null;
    }
    public void settle(String job,String state) {
        db.query("UPDATE music_mv_billing_reservations SET state=?,updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND state='reserved'",state,job);
    }
    public void reconcile(String user) {
        D1Statement statement=reconcileStatement(user);
        db.query(statement.getSql(),statement.getParams().toArray());
    }
    private D1Statement reconcileStatement(String user) {
        return D1Statement.of("UPDATE music_mv_billing_reservations SET state=CASE WHEN (SELECT status FROM ai_music_jobs j WHERE j.job_id=music_mv_billing_reservations.job_id)='completed' THEN 'consumed' ELSE 'released' END,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND state='reserved' AND job_id IN (SELECT job_id FROM ai_music_jobs WHERE user_id=? AND status IN ('completed','failed'))", user, user);
    }
    public void reconcile() {
        // 根据已持久化的最终任务状态恢复回调中断，不释放结果未知的请求。
        db.query("UPDATE music_mv_billing_reservations SET state=CASE WHEN (SELECT status FROM ai_music_jobs j WHERE j.job_id=music_mv_billing_reservations.job_id)='completed' THEN 'consumed' ELSE 'released' END,updated_at=CURRENT_TIMESTAMP WHERE state='reserved' AND job_id IN (SELECT job_id FROM ai_music_jobs WHERE status IN ('completed','failed'))");
    }
    public void revoke(String provider,String payment) {
        db.batch(Arrays.asList(D1Statement.of("INSERT INTO music_mv_billing_revocations(provider,payment_id) VALUES(?,?) ON CONFLICT DO NOTHING",provider,payment),D1Statement.of("UPDATE music_mv_billing_grants SET revoked=1 WHERE provider=? AND payment_id=?",provider,payment)));
    }
}

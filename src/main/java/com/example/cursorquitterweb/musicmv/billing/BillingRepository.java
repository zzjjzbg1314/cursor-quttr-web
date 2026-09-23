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
    public Map<String,Object> customer(String user) {
        return db.query("SELECT * FROM music_mv_billing_customers WHERE user_id=?",user).firstRow();
    }
    public void saveCustomer(String user,String customer) {
        db.query("INSERT INTO music_mv_billing_customers(user_id,customer_id) VALUES(?,?) ON CONFLICT(user_id) DO NOTHING",user,customer);
    }
    public String owner(String customer) {
        Map<String,Object> r=db.query("SELECT user_id FROM music_mv_billing_customers WHERE customer_id=?",customer).firstRow();
        return r==null?null:String.valueOf(r.get("user_id"));
    }
    public boolean claimCheckout(String user,String token,String plan,String returnUrl,long now) {
        return db.query("UPDATE music_mv_billing_customers SET checkout_token=?,checkout_plan=?,checkout_expires=?,checkout_return_url=?,checkout_id=NULL,checkout_url=NULL WHERE user_id=? AND (checkout_expires IS NULL OR checkout_expires<?) RETURNING user_id",token,plan,now+3600,returnUrl,user,now).firstRow()!=null;
    }
    public void saveCheckout(String user,String token,String id,String url) {
        db.query("UPDATE music_mv_billing_customers SET checkout_id=?,checkout_url=? WHERE user_id=? AND checkout_token=?",id,url,user,token);
    }
    public boolean processed(String id) { return db.query("SELECT event_id FROM music_mv_billing_events WHERE event_id=?",id).firstRow()!=null; }
    public void processed(String id,String type) { db.query("INSERT INTO music_mv_billing_events(event_id,event_type) VALUES(?,?) ON CONFLICT DO NOTHING",id,type); }
    public void grant(String invoice,String user,String subscription,String plan,int allowance,long start,long end) {
        db.query("INSERT INTO music_mv_billing_grants(invoice_id,user_id,subscription_id,plan_key,allowance,period_start,period_end,revoked) VALUES(?,?,?,?,?,?,?,EXISTS(SELECT 1 FROM music_mv_billing_revocations WHERE invoice_id=?)) ON CONFLICT(invoice_id) DO NOTHING",invoice,user,subscription,plan,allowance,start,end,invoice);
    }
    public Map<String,Object> balance(String user,long now) {
        return db.query("SELECT COALESCE(SUM(g.allowance-(SELECT COUNT(*) FROM music_mv_billing_reservations r WHERE r.invoice_id=g.invoice_id AND r.state<>'released')),0) AS remaining, MAX(g.period_end) AS period_end FROM music_mv_billing_grants g WHERE g.user_id=? AND g.revoked=0 AND g.period_start<=? AND g.period_end>?",user,now,now).firstRow();
    }
    public boolean reserve(String user,String request,String job,long now) {
        // 单条条件写入同时检查余额和占用，避免并发请求超额。
        return db.query("INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,invoice_id,state) SELECT ?,?,?,g.invoice_id,'reserved' FROM music_mv_billing_grants g WHERE g.user_id=? AND g.revoked=0 AND g.period_start<=? AND g.period_end>? AND g.allowance>(SELECT COUNT(*) FROM music_mv_billing_reservations r WHERE r.invoice_id=g.invoice_id AND r.state<>'released') ORDER BY g.period_end,g.invoice_id LIMIT 1 ON CONFLICT DO NOTHING RETURNING job_id",job,user,request,user,now,now).firstRow()!=null;
    }
    public boolean hasReservation(String user,String request) {
        return db.query("SELECT job_id FROM music_mv_billing_reservations WHERE user_id=? AND request_id=?",user,request).firstRow()!=null;
    }
    public void settle(String job,String state) {
        db.query("UPDATE music_mv_billing_reservations SET state=?,updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND state='reserved'",state,job);
    }
    public void reconcile(String user) {
        // 用户请求只对账自己的任务，避免一次状态查询触发全站更新。
        db.query("UPDATE music_mv_billing_reservations SET state=CASE WHEN (SELECT status FROM ai_music_jobs j WHERE j.job_id=music_mv_billing_reservations.job_id)='completed' THEN 'consumed' ELSE 'released' END,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND state='reserved' AND job_id IN (SELECT job_id FROM ai_music_jobs WHERE user_id=? AND status IN ('completed','failed'))", user, user);
    }
    public void reconcile() {
        // 根据已持久化的最终任务状态恢复回调中断，不释放结果未知的请求。
        db.query("UPDATE music_mv_billing_reservations SET state=CASE WHEN (SELECT status FROM ai_music_jobs j WHERE j.job_id=music_mv_billing_reservations.job_id)='completed' THEN 'consumed' ELSE 'released' END,updated_at=CURRENT_TIMESTAMP WHERE state='reserved' AND job_id IN (SELECT job_id FROM ai_music_jobs WHERE status IN ('completed','failed'))");
    }
    public void revoke(String invoice) { db.batch(Arrays.asList(D1Statement.of("INSERT INTO music_mv_billing_revocations(invoice_id) VALUES(?) ON CONFLICT DO NOTHING",invoice),D1Statement.of("UPDATE music_mv_billing_grants SET revoked=1 WHERE invoice_id=?",invoice))); }
}

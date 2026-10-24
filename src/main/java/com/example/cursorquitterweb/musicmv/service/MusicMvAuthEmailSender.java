package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
public class MusicMvAuthEmailSender {
    private final String key,from;
    private final RestTemplate http;
    public MusicMvAuthEmailSender(@Value("${music-mv.auth.email.resend-api-key:}") String key,
            @Value("${music-mv.auth.email.from:}") String from) {
        this.key=key;this.from=from;
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(3000);factory.setReadTimeout(5000);
        http=new RestTemplate(factory);
    }
    public boolean configured(){return !key.trim().isEmpty() && !from.trim().isEmpty();}
    public void send(String email,String code,String locale,String id) {
        String[] copy=copy(locale);
        Map<String,Object> body=new LinkedHashMap<>();body.put("from",from);body.put("to",Collections.singletonList(email));
        body.put("subject",copy[0]);body.put("text",copy[1].replace("{code}",code));
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(key);headers.setContentType(MediaType.APPLICATION_JSON);headers.set("Idempotency-Key","auth/"+id);
        // 不记录邮件正文、邮箱、验证码或服务商原始错误。
        try { http.postForEntity("https://api.resend.com/emails",new HttpEntity<>(body,headers),String.class); }
        catch(RuntimeException exception){throw new IllegalStateException("Authentication email delivery failed");}
    }
    static String[] copy(String locale) {
        switch(locale==null?"en":locale) {
            case "zh-CN":return new String[]{"SongToVideo 登录验证码","你的验证码是 {code}，10 分钟内有效。请勿分享。若非你本人操作，请忽略此邮件。"};
            case "zh-TW":return new String[]{"SongToVideo 登入驗證碼","你的驗證碼是 {code}，10 分鐘內有效。請勿分享。若非你本人操作，請忽略此郵件。"};
            case "ja":return new String[]{"SongToVideo ログイン確認コード","確認コードは {code} です。有効期限は10分です。他の人に教えないでください。心当たりがない場合は、このメールを無視してください。"};
            case "ko":return new String[]{"SongToVideo 로그인 인증 코드","인증 코드는 {code}이며 10분간 유효합니다. 다른 사람에게 공유하지 마세요. 요청하지 않았다면 이 이메일을 무시하세요."};
            case "es":return new String[]{"Tu código de acceso a SongToVideo","Tu código es {code}. Caduca en 10 minutos. No lo compartas. Si no lo solicitaste, ignora este correo."};
            case "pt-BR":return new String[]{"Seu código de acesso ao SongToVideo","Seu código é {code}. Ele expira em 10 minutos. Não o compartilhe. Se você não o solicitou, ignore este e-mail."};
            case "fr":return new String[]{"Votre code de connexion SongToVideo","Votre code est {code}. Il expire dans 10 minutes. Ne le partagez pas. Si vous ne l’avez pas demandé, ignorez cet e-mail."};
            case "de":return new String[]{"Dein SongToVideo-Anmeldecode","Dein Code lautet {code}. Er ist 10 Minuten gültig. Gib ihn nicht weiter. Falls du ihn nicht angefordert hast, ignoriere diese E-Mail."};
            case "it":return new String[]{"Il tuo codice di accesso a SongToVideo","Il tuo codice è {code}. Scade tra 10 minuti. Non condividerlo. Se non l’hai richiesto, ignora questa email."};
            default:return new String[]{"Your SongToVideo sign-in code","Your code is {code}. It expires in 10 minutes. Do not share it. If you did not request this, ignore this email."};
        }
    }
}

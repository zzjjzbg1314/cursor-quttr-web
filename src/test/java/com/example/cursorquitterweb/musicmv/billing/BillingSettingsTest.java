package com.example.cursorquitterweb.musicmv.billing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BillingSettingsTest {
    @Test void returnUrlsPreserveAllSupportedLanguages() {
        BillingSettings settings=new BillingSettings();
        String[][] languages={{"en",""},{"zh-CN","/zh-cn"},{"zh-TW","/zh-tw"},{"ja","/ja"},{"ko","/ko"},
            {"es","/es"},{"pt-BR","/pt-br"},{"de","/de"},{"fr","/fr"},{"it","/it"}};
        String[][] sites={{"https://www.suno.film","https://www.suno.film"},{"https://www.suno.film/","https://www.suno.film"},
            {"https://www.suno.film///","https://www.suno.film"},{"http://localhost:3000/","http://localhost:3000"},
            {"http://localhost","http://localhost"},{"http://127.0.0.1:3000///","http://127.0.0.1:3000"}};
        for(String[] site:sites) {
            settings.siteUrl=site[0];
            for(String[] language:languages)assertThat(settings.returnUrl(language[0])).isEqualTo(site[1]+language[1]+"/pricing");
            for(String locale:new String[]{null,"unknown","//evil.example","../de","de?next=evil"})
                assertThat(settings.returnUrl(locale)).isEqualTo(site[1]+"/pricing");
        }
    }
}

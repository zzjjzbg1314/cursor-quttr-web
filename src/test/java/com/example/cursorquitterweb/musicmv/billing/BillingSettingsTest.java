package com.example.cursorquitterweb.musicmv.billing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BillingSettingsTest {
    @Test void returnUrlsPreserveAllSupportedLanguages() {
        BillingSettings settings = new BillingSettings();
        settings.siteUrl = "https://www.suno.film/";
        for (String locale : new String[]{"zh-CN", "zh-TW", "ja", "ko", "es", "pt-BR", "de", "fr"}) {
            assertThat(settings.returnUrl(locale)).isEqualTo("https://www.suno.film/"
                    + locale.toLowerCase(java.util.Locale.ROOT) + "/pricing");
        }
        for (String locale : new String[]{null, "en", "unknown", "//evil.example", "../de", "de?next=evil"}) {
            assertThat(settings.returnUrl(locale)).isEqualTo("https://www.suno.film/pricing");
        }
        settings.siteUrl = "http://localhost:3000";
        assertThat(settings.returnUrl("ja")).isEqualTo("http://localhost:3000/ja/pricing");
    }
}

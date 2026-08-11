package com.example.cursorquitterweb.musicmv.service;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

final class CloudflareRestTemplateFactory {
    private CloudflareRestTemplateFactory() {
    }

    static RestTemplate create() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15000)
                .setConnectionRequestTimeout(15000)
                .setSocketTimeout(120000)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}

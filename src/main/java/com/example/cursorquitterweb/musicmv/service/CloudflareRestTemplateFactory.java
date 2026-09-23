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
        return create(15000, 120000);
    }

    static RestTemplate create(int connectTimeoutMs, int readTimeoutMs) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(40)
                .setMaxConnPerRoute(20)
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}

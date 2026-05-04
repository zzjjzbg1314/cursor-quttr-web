package com.example.cursorquitterweb.dto;

import java.util.List;
import java.util.Map;

/**
 * 客户端启动路由信息
 */
public class BootstrapRoutingDto {

    private String clientIp;
    private String countryCode;
    private String countryName;
    private String countrySource;
    private String currentBaseUrl;
    private String currentNodeKey;
    private String recommendedBaseUrl;
    private String recommendedNodeKey;
    private Map<String, String> baseUrls;
    private List<RouteNodeDto> nodes;
    private long timestamp;

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCountrySource() {
        return countrySource;
    }

    public void setCountrySource(String countrySource) {
        this.countrySource = countrySource;
    }

    public String getCurrentBaseUrl() {
        return currentBaseUrl;
    }

    public void setCurrentBaseUrl(String currentBaseUrl) {
        this.currentBaseUrl = currentBaseUrl;
    }

    public String getCurrentNodeKey() {
        return currentNodeKey;
    }

    public void setCurrentNodeKey(String currentNodeKey) {
        this.currentNodeKey = currentNodeKey;
    }

    public String getRecommendedBaseUrl() {
        return recommendedBaseUrl;
    }

    public void setRecommendedBaseUrl(String recommendedBaseUrl) {
        this.recommendedBaseUrl = recommendedBaseUrl;
    }

    public String getRecommendedNodeKey() {
        return recommendedNodeKey;
    }

    public void setRecommendedNodeKey(String recommendedNodeKey) {
        this.recommendedNodeKey = recommendedNodeKey;
    }

    public Map<String, String> getBaseUrls() {
        return baseUrls;
    }

    public void setBaseUrls(Map<String, String> baseUrls) {
        this.baseUrls = baseUrls;
    }

    public List<RouteNodeDto> getNodes() {
        return nodes;
    }

    public void setNodes(List<RouteNodeDto> nodes) {
        this.nodes = nodes;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

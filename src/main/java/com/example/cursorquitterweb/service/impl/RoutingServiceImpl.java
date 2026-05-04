package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.BootstrapRoutingDto;
import com.example.cursorquitterweb.dto.RouteNodeDto;
import com.example.cursorquitterweb.service.RoutingService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/**
 * 节点路由服务实现
 */
@Service
public class RoutingServiceImpl implements RoutingService {

    private static final Logger logger = LogUtil.getLogger(RoutingServiceImpl.class);

    @Value("${app.routing.cn-base-url:https://www.kejiapi.cn}")
    private String cnBaseUrl;

    @Value("${app.routing.sg-base-url:https://www.globalkejiapi.asia}")
    private String sgBaseUrl;

    @Value("${app.routing.us-base-url:https://www.globalkejiapi.top}")
    private String usBaseUrl;

    @Value("${app.routing.default-base-url:https://www.globalkejiapi.top}")
    private String defaultBaseUrl;

    @Override
    public BootstrapRoutingDto buildBootstrapRouting(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        String countryCode = resolveCountryCode(request);
        String countrySource = resolveCountrySource(request);
        String currentBaseUrl = resolveCurrentBaseUrl(request);
        String currentNodeKey = resolveNodeKeyByBaseUrl(currentBaseUrl);
        String recommendedNodeKey = chooseRecommendedNodeKey(countryCode, currentBaseUrl);
        String recommendedBaseUrl = resolveBaseUrlByNodeKey(recommendedNodeKey, currentBaseUrl);

        BootstrapRoutingDto dto = new BootstrapRoutingDto();
        dto.setClientIp(clientIp);
        dto.setCountryCode(countryCode);
        dto.setCountryName(countryNameOf(countryCode));
        dto.setCountrySource(countrySource);
        dto.setCurrentBaseUrl(currentBaseUrl);
        dto.setCurrentNodeKey(currentNodeKey);
        dto.setRecommendedBaseUrl(recommendedBaseUrl);
        dto.setRecommendedNodeKey(recommendedNodeKey);
        dto.setBaseUrls(buildBaseUrls());
        dto.setNodes(buildNodes());
        dto.setTimestamp(System.currentTimeMillis());

        logger.info("构建节点引导信息，ip: {}, country: {}, source: {}, current: {}({}), recommended: {}({})",
                clientIp, countryCode, countrySource, currentBaseUrl, currentNodeKey, recommendedBaseUrl, recommendedNodeKey);
        return dto;
    }

    private Map<String, String> buildBaseUrls() {
        Map<String, String> baseUrls = new LinkedHashMap<>();
        baseUrls.put("cn", cnBaseUrl);
        baseUrls.put("sg", sgBaseUrl);
        baseUrls.put("us", usBaseUrl);
        if (defaultBaseUrl != null && !defaultBaseUrl.trim().isEmpty()) {
            baseUrls.put("default", defaultBaseUrl);
        }
        return baseUrls;
    }

    private List<RouteNodeDto> buildNodes() {
        List<RouteNodeDto> nodes = new ArrayList<>();

        nodes.add(buildNode("cn", "China", "中国节点", cnBaseUrl, "国内用户优先", 1));
        nodes.add(buildNode("sg", "Singapore", "新加坡节点", sgBaseUrl, "亚洲用户优先", 2));
        nodes.add(buildNode("us", "United States", "美国西部节点", usBaseUrl, "欧美用户优先", 3));

        return nodes;
    }

    private RouteNodeDto buildNode(String nodeKey, String region, String nodeName, String baseUrl, String description, int priority) {
        RouteNodeDto node = new RouteNodeDto();
        node.setNodeKey(nodeKey);
        node.setRegion(region);
        node.setNodeName(nodeName);
        node.setBaseUrl(baseUrl);
        node.setDescription(description);
        node.setPriority(priority);
        return node;
    }

    private String chooseRecommendedNodeKey(String countryCode, String currentBaseUrl) {
        if (countryCode == null || countryCode.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(countryCode)) {
            String currentNodeKey = resolveNodeKeyByBaseUrl(currentBaseUrl);
            return currentNodeKey != null ? currentNodeKey : "us";
        }

        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if ("CN".equals(normalized)) {
            return "cn";
        }

        if (isSingaporePreferredCountry(normalized)) {
            return "sg";
        }

        if (isUsPreferredCountry(normalized)) {
            return "us";
        }

        return resolveNodeKeyByBaseUrl(currentBaseUrl) != null ? resolveNodeKeyByBaseUrl(currentBaseUrl) : "us";
    }

    private String resolveBaseUrlByNodeKey(String nodeKey, String currentBaseUrl) {
        if (nodeKey == null) {
            return currentBaseUrl != null && !currentBaseUrl.trim().isEmpty() ? currentBaseUrl : defaultBaseUrl;
        }

        switch (nodeKey) {
            case "cn":
                return cnBaseUrl;
            case "sg":
                return sgBaseUrl;
            case "us":
                return usBaseUrl;
            default:
                return currentBaseUrl != null && !currentBaseUrl.trim().isEmpty() ? currentBaseUrl : defaultBaseUrl;
        }
    }

    private String resolveNodeKeyByBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized == null) {
            return null;
        }

        if (normalized.equals(normalizeBaseUrl(cnBaseUrl))) {
            return "cn";
        }
        if (normalized.equals(normalizeBaseUrl(sgBaseUrl))) {
            return "sg";
        }
        if (normalized.equals(normalizeBaseUrl(usBaseUrl))) {
            return "us";
        }
        return null;
    }

    private String resolveCountryCode(HttpServletRequest request) {
        String[] candidateHeaders = {"CF-IPCountry", "X-Country-Code", "X-Geo-Country"};
        for (String header : candidateHeaders) {
            String value = trimToNull(request.getHeader(header));
            if (value != null && !"XX".equalsIgnoreCase(value)) {
                return value.toUpperCase(Locale.ROOT);
            }
        }
        return "UNKNOWN";
    }

    private String resolveCountrySource(HttpServletRequest request) {
        String[] candidateHeaders = {"CF-IPCountry", "X-Country-Code", "X-Geo-Country"};
        for (String header : candidateHeaders) {
            String value = trimToNull(request.getHeader(header));
            if (value != null && !"XX".equalsIgnoreCase(value)) {
                return header;
            }
        }
        return "UNKNOWN";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String[] candidateHeaders = {
                "CF-Connecting-IP",
                "True-Client-IP",
                "X-Forwarded-For",
                "X-Real-IP",
                "X-Client-IP"
        };

        for (String header : candidateHeaders) {
            String value = trimToNull(request.getHeader(header));
            if (value != null) {
                int commaIndex = value.indexOf(',');
                return commaIndex >= 0 ? value.substring(0, commaIndex).trim() : value;
            }
        }

        return trimToNull(request.getRemoteAddr());
    }

    private String resolveCurrentBaseUrl(HttpServletRequest request) {
        StringBuffer requestUrl = request.getRequestURL();
        String requestUri = request.getRequestURI();
        if (requestUrl == null) {
            return defaultBaseUrl;
        }
        String url = requestUrl.toString();
        if (requestUri != null && !requestUri.isEmpty() && url.endsWith(requestUri)) {
            return url.substring(0, url.length() - requestUri.length());
        }
        return url;
    }

    private String countryNameOf(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(countryCode)) {
            return "Unknown";
        }

        switch (countryCode.trim().toUpperCase(Locale.ROOT)) {
            case "CN":
                return "China";
            case "SG":
                return "Singapore";
            case "US":
                return "United States";
            case "DE":
                return "Germany";
            case "HK":
                return "Hong Kong";
            case "TW":
                return "Taiwan";
            case "MO":
                return "Macao";
            default:
                return countryCode.toUpperCase(Locale.ROOT);
        }
    }

    private boolean isSingaporePreferredCountry(String countryCode) {
        switch (countryCode) {
            case "SG":
            case "MY":
            case "TH":
            case "ID":
            case "PH":
            case "VN":
            case "MM":
            case "KH":
            case "LA":
            case "BN":
            case "TW":
            case "HK":
            case "MO":
            case "JP":
            case "KR":
            case "IN":
            case "AU":
            case "NZ":
                return true;
            default:
                return false;
        }
    }

    private boolean isUsPreferredCountry(String countryCode) {
        switch (countryCode) {
            case "US":
            case "CA":
            case "MX":
            case "BR":
            case "AR":
            case "CL":
            case "PE":
            case "CO":
            case "UY":
            case "PY":
            case "EC":
            case "BO":
            case "VE":
            case "CR":
            case "PA":
            case "GT":
            case "HN":
            case "SV":
            case "NI":
            case "DO":
            case "PR":
            case "GB":
            case "IE":
            case "FR":
            case "DE":
            case "NL":
            case "BE":
            case "LU":
            case "CH":
            case "AT":
            case "ES":
            case "PT":
            case "IT":
            case "SE":
            case "NO":
            case "DK":
            case "FI":
            case "IS":
            case "PL":
            case "CZ":
            case "SK":
            case "HU":
            case "RO":
            case "BG":
            case "GR":
            case "TR":
            case "UA":
            case "LT":
            case "LV":
            case "EE":
            case "HR":
            case "SI":
            case "RS":
            case "BA":
            case "ME":
            case "AL":
            case "MK":
            case "MD":
            case "CY":
            case "MT":
            case "ZA":
            case "AE":
            case "SA":
            case "IL":
            case "QA":
            case "KW":
            case "BH":
            case "OM":
            case "EG":
            case "MA":
            case "DZ":
            case "TN":
            case "KE":
            case "NG":
            case "GH":
            case "TZ":
            case "UG":
            case "RW":
            case "SN":
            case "CI":
                return true;
            default:
                return false;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = trimToNull(baseUrl);
        if (value == null) {
            return null;
        }
        value = value.toLowerCase(Locale.ROOT);
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

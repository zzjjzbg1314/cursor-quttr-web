package com.example.cursorquitterweb.musicmv.aiphoto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/** 方舟同步生成单图；持久化交给任务服务，超时后不自动重复生成。 */
@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class ArkAiPhotoProvider {
    static final String MODEL="doubao-seedream-5-0-pro-260628";
    static final String ENDPOINT="https://ark.cn-beijing.volces.com/api/v3/images/generations";
    private final boolean enabled;
    private final String key;
    private final Set<String> hosts;
    private final RestTemplate http;

    @Autowired
    public ArkAiPhotoProvider(@Value("${music-mv.ai-photo.ark.enabled:false}") boolean enabled,
            @Value("${music-mv.ai-photo.ark.api-key:}") String key,
            @Value("${music-mv.ai-photo.ark.output-hosts:ark-content-generation-v2-cn-beijing.tos-cn-beijing.volces.com}") String hosts) {
        this(enabled,key,hosts,client());
    }
    ArkAiPhotoProvider(boolean enabled,String key,String hosts,RestTemplate http) {
        this.enabled=enabled;this.key=key;this.http=http;
        this.hosts=new HashSet<>();
        for (String host:hosts.split(",")) if (!host.trim().isEmpty()) this.hosts.add(host.trim().toLowerCase(Locale.ROOT));
    }
    private static RestTemplate client() {
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);factory.setReadTimeout(120000);
        return new RestTemplate(factory);
    }
    public boolean enabled() { return enabled; }
    public void requireConfigured(JsonNode recipe) {
        if (!enabled || key.trim().isEmpty() || hosts.isEmpty()) throw AiPhotoRecipe.error("AI_PHOTO_PROVIDER_UNAVAILABLE");
        requestBody(recipe,"https://input.invalid/photo");
    }
    static ObjectNode requestBody(JsonNode recipe,String input) {
        if (!"ark".equals(recipe.path("provider").asText()) || !MODEL.equals(recipe.path("model").asText()))
            throw AiPhotoRecipe.error("AI_PHOTO_MODEL_UNSUPPORTED");
        URI uri;
        try { uri=URI.create(input); } catch (IllegalArgumentException e) { throw AiPhotoRecipe.error("AI_PHOTO_INPUT_UNREACHABLE"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
            throw AiPhotoRecipe.error("AI_PHOTO_INPUT_UNREACHABLE");
        JsonNode params=recipe.path("parameters");
        if (!params.isObject()) throw AiPhotoRecipe.error("AI_PHOTO_PARAMETERS_UNSUPPORTED");
        params.fieldNames().forEachRemaining(k -> {
            if (!"size".equals(k)) throw AiPhotoRecipe.error("AI_PHOTO_PARAMETERS_UNSUPPORTED");
        });
        String size=params.path("size").asText("2K");
        if (!Arrays.asList("1K","1.5K","2K").contains(size)) throw AiPhotoRecipe.error("AI_PHOTO_PARAMETERS_UNSUPPORTED");
        String prompt=recipe.path("prompt").asText();
        if (prompt.trim().isEmpty() || prompt.length()>10000) throw AiPhotoRecipe.error("AI_PHOTO_RECIPE_INVALID");
        ObjectNode body=new ObjectMapper().createObjectNode();
        body.put("model",MODEL);body.put("prompt",prompt);body.put("image",input);body.put("size",size);
        // Pro 不支持组图与流式参数，显式关闭图层拆分以保持单张结果合同。
        body.put("layer_decomposition",false);
        body.put("response_format","url");body.put("watermark",true);
        return body;
    }
    public String generate(JsonNode recipe,String input) {
        requireConfigured(recipe);
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(key);headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode response=http.exchange(ENDPOINT,HttpMethod.POST,new HttpEntity<>(requestBody(recipe,input),headers),JsonNode.class).getBody();
        return output(response);
    }
    String output(JsonNode response) {
        if (response==null || response.hasNonNull("error")) throw AiPhotoRecipe.error("AI_PHOTO_GENERATION_FAILED");
        if (!MODEL.equals(response.path("model").asText())) throw AiPhotoRecipe.error("AI_PHOTO_RESPONSE_INVALID");
        JsonNode data=response.path("data");
        if (!data.isArray() || data.size()!=1 || data.get(0).hasNonNull("error") || !data.get(0).path("url").isTextual())
            throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_INVALID");
        String url=data.get(0).path("url").asText();
        validateOutput(url);
        return url;
    }
    private void validateOutput(String url) {
        URI uri;
        try { uri=URI.create(url); } catch (IllegalArgumentException e) { throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_HOST_INVALID"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getPort()!=-1 && uri.getPort()!=443 || !hosts.contains(uri.getHost().toLowerCase(Locale.ROOT)))
            throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_HOST_INVALID");
    }
    public KieAiPhotoProvider.Image download(String url) throws IOException {
        validateOutput(url);
        return KieAiPhotoProvider.download(url,hosts);
    }
}

package com.example.cursorquitterweb.musicmv.aiphoto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.net.*;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class KieAiPhotoProvider {
    private final boolean enabled;
    private final String key;
    private final Set<String> hosts;
    private final RestTemplate http;
    private final ObjectMapper json;
    public KieAiPhotoProvider(@Value("${music-mv.ai-photo.enabled:false}") boolean enabled,
            @Value("${music-mv.ai-photo.kie.api-key:}") String key,
            @Value("${music-mv.ai-photo.output-hosts:}") String hosts, ObjectMapper json) {
        this.enabled=enabled; this.key=key; this.json=json;
        this.hosts=new HashSet<>(Arrays.asList(hosts.toLowerCase(Locale.ROOT).split(",")));
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(25000);
        this.http=new RestTemplate(factory);
    }
    public boolean enabled() { return enabled; }
    public void requireConfigured(JsonNode recipe) {
        if (!enabled || key.trim().isEmpty() || hosts.size()==1 && hosts.contains(""))
            throw AiPhotoRecipe.error("AI_PHOTO_PROVIDER_UNAVAILABLE");
        requestBody(recipe,"https://input.invalid/photo");
    }
    static ObjectNode requestBody(JsonNode recipe,String inputUrl) {
        if (!"kie".equals(recipe.path("provider").asText())
                || !"bytedance/seedream-v4-edit".equals(recipe.path("model").asText()))
            throw AiPhotoRecipe.error("AI_PHOTO_MODEL_UNSUPPORTED");
        ObjectNode input=new ObjectMapper().createObjectNode();
        Set<String> allowed=new HashSet<>(Arrays.asList("image_size","image_resolution","seed"));
        recipe.path("parameters").fieldNames().forEachRemaining(k -> {
            if (!allowed.contains(k)) throw AiPhotoRecipe.error("AI_PHOTO_PARAMETERS_UNSUPPORTED");
            input.set(k,recipe.path("parameters").get(k));
        });
        input.put("prompt",recipe.path("prompt").asText());
        input.putArray("image_urls").add(inputUrl);
        input.put("max_images",1); input.put("nsfw_checker",true);
        ObjectNode body=new ObjectMapper().createObjectNode();
        body.put("model",recipe.path("model").asText()); body.set("input",input);
        return body;
    }
    public String submit(JsonNode recipe,String inputUrl) {
        requireConfigured(recipe);
        if (!inputUrl.startsWith("https://")) throw AiPhotoRecipe.error("AI_PHOTO_INPUT_UNREACHABLE");
        JsonNode data=exchange(HttpMethod.POST,"/api/v1/jobs/createTask",requestBody(recipe,inputUrl));
        String task=data.path("taskId").asText();
        if (!task.matches("[A-Za-z0-9_-]{1,200}")) throw AiPhotoRecipe.error("AI_PHOTO_RESPONSE_INVALID");
        return task;
    }
    public JsonNode query(String taskId,JsonNode recipe) {
        requireConfigured(recipe);
        if (!taskId.matches("[A-Za-z0-9_-]{1,200}")) throw AiPhotoRecipe.error("AI_PHOTO_RESPONSE_INVALID");
        JsonNode data=exchange(HttpMethod.GET,"/api/v1/jobs/recordInfo?taskId="+taskId,null);
        if (!taskId.equals(data.path("taskId").asText()) || !recipe.path("model").asText().equals(data.path("model").asText()))
            throw AiPhotoRecipe.error("AI_PHOTO_RESPONSE_INVALID");
        return data;
    }
    private JsonNode exchange(HttpMethod method,String path,Object body) {
        HttpHeaders headers=new HttpHeaders(); headers.setBearerAuth(key); headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode response=http.exchange("https://api.kie.ai"+path,method,new HttpEntity<>(body,headers),JsonNode.class).getBody();
        if (response==null || response.path("code").asInt()!=200) throw AiPhotoRecipe.error("AI_PHOTO_PROVIDER_ERROR");
        return response.path("data");
    }
    static String output(JsonNode data,ObjectMapper json) {
        try {
            JsonNode result=json.readTree(data.path("resultJson").asText());
            if (result==null) throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_INVALID");
            JsonNode urls=result.path("resultUrls");
            if (!urls.isArray() || urls.size()!=1 || !urls.get(0).isTextual() || urls.get(0).asText().isEmpty())
                throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_INVALID");
            return urls.get(0).asText();
        } catch (IOException e) { throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_INVALID"); }
    }
    public Image download(String value) throws IOException {
        return download(value, hosts);
    }
    static Image download(String value, Set<String> hosts) throws IOException {
        URI uri=URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getPort()!=-1 && uri.getPort()!=443 || !hosts.contains(uri.getHost().toLowerCase(Locale.ROOT)))
            throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_HOST_INVALID");
        for (InetAddress address:InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress()) throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_HOST_INVALID");
        }
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(20000);
        long deadline=System.nanoTime()+45000000000L;
        try {
            if (connection.getResponseCode()!=200) throw new IOException("AI photo download failed");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try (InputStream input=connection.getInputStream()) {
                byte[] buffer=new byte[16384]; int count;
                while ((count=input.read(buffer))!=-1) {
                    if (bytes.size()+count>25*1024*1024 || System.nanoTime()>deadline) throw new IOException("AI photo output limit exceeded");
                    bytes.write(buffer,0,count);
                }
            }
            return inspect(bytes.toByteArray());
        } finally { connection.disconnect(); }
    }
    static Image inspect(byte[] bytes) throws IOException {
        try (ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_INVALID");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input); String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Arrays.asList("jpeg","png").contains(format) || reader.getWidth(0)<1 || reader.getHeight(0)<1
                        || (long)reader.getWidth(0)*reader.getHeight(0)>32000000L || reader.read(0)==null)
                    throw AiPhotoRecipe.error("AI_PHOTO_OUTPUT_INVALID");
                return new Image(bytes,"image/"+format,"jpeg".equals(format)?"jpg":"png");
            } finally { reader.dispose(); }
        }
    }
    public static final class Image {
        public final byte[] bytes; public final String contentType,extension;
        Image(byte[] bytes,String contentType,String extension) { this.bytes=bytes;this.contentType=contentType;this.extension=extension; }
    }
}

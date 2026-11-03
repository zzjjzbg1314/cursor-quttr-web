import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.core.env.*;
import org.springframework.core.io.FileSystemResource;
import javax.servlet.http.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

public class QuittrJsonCompressionTest {
    private Compression configuration(boolean disabled) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        if (disabled) env.getPropertySources().addFirst(new MapPropertySource("override",
                Collections.singletonMap("SERVER_COMPRESSION_ENABLED", "false")));
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("repository",
                new FileSystemResource(System.getProperty("audit.repo") + "/src/main/resources/application.yml"))) {
            env.getPropertySources().addLast(source);
        }
        return Binder.get(env).bind("server.compression", Compression.class).get();
    }

    private byte[] read(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) >= 0) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private void check(boolean disabled) throws Exception {
        Compression config = configuration(disabled);
        assertEquals(!disabled, config.getEnabled());
        assertArrayEquals(new String[]{"application/json"}, config.getMimeTypes());
        assertEquals(2048, config.getMinResponseSize().toBytes());
        StringBuilder json = new StringBuilder("{\"text\":\"");
        for (int i = 0; i < 300; i++) json.append("中文 日本語 한국어 English Deutsch Français Português Español العربية हिन्दी ");
        json.append("\"}");
        byte[] large = json.toString().getBytes(StandardCharsets.UTF_8);
        byte[] small = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        factory.setAddress(InetAddress.getByName("127.0.0.1"));
        factory.setCompression(config);
        WebServer server = factory.getWebServer(context -> {
            javax.servlet.ServletRegistration.Dynamic registration = context.addServlet("fixture", new HttpServlet() {
                @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
                    String path = req.getRequestURI();
                    String type = path.equals("/events") ? "text/event-stream" : path.equals("/media") ? "video/mp4" : "application/json";
                    byte[] data = path.equals("/small") ? small : large;
                    res.setContentType(type);
                    res.setContentLength(data.length);
                    res.getOutputStream().write(data);
                }
            });
            registration.addMapping("/*");
        });
        try {
            server.start();
            for (String path : new String[]{"/json", "/small", "/events", "/media"}) {
                for (String encoding : new String[]{"gzip", "identity", "gzip;q=0"}) {
                    HttpURLConnection connection = (HttpURLConnection)new URL("http://127.0.0.1:" + server.getPort() + path).openConnection();
                    connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
                    connection.setRequestProperty("Accept-Encoding", encoding);
                    try {
                        assertEquals(200, connection.getResponseCode());
                        boolean compressed = "gzip".equals(connection.getHeaderField("Content-Encoding"));
                        boolean expected = !disabled && path.equals("/json") && encoding.equals("gzip");
                        assertEquals(expected, compressed, path + " " + encoding);
                        byte[] wire = read(connection.getInputStream());
                        byte[] decoded = compressed ? read(new GZIPInputStream(new ByteArrayInputStream(wire))) : wire;
                        assertArrayEquals(path.equals("/small") ? small : large, decoded);
                        if (compressed) {
                            assertTrue(wire.length < large.length);
                            assertTrue(connection.getHeaderField("Vary").toLowerCase(Locale.ROOT).contains("accept-encoding"));
                        }
                    } finally { connection.disconnect(); }
                }
            }
        } finally { server.stop(); }
    }
    @Test void jsonCompressionNegotiationPreservesBytesAndExcludesMediaAndEvents() throws Exception { check(false); }
    @Test void environmentSwitchDisablesCompression() throws Exception { check(true); }
}

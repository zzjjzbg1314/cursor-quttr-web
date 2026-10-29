package com.example.cursorquitterweb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

class ExternalSslConfigTest {
    @TempDir Path directory;

    private Path createStore(String type) throws Exception {
        Path store = directory.resolve("test." + type.toLowerCase());
        String keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "server",
                "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost", "-validity", "2",
                "-storetype", type, "-keystore", store.toString(), "-storepass", "test-password",
                "-keypass", "test-password", "-noprompt").redirectErrorStream(true)
                .redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertEquals(0, process.waitFor());
        return store;
    }

    private SslConfig config(String path, String type) {
        SslConfig config = new SslConfig();
        ReflectionTestUtils.setField(config, "keyStorePath", path);
        ReflectionTestUtils.setField(config, "keyStoreType", type);
        ReflectionTestUtils.setField(config, "keyStorePassword", "test-password");
        ReflectionTestUtils.setField(config, "keyAlias", "server");
        ReflectionTestUtils.setField(config, "sslEnabled", true);
        ReflectionTestUtils.setField(config, "httpsPort", 443);
        ReflectionTestUtils.setField(config, "httpPort", 8080);
        return config;
    }

    @Test void acceptsExternalPkcs12() throws Exception {
        assertNotNull(config(createStore("PKCS12").toUri().toString(), "PKCS12").servletContainer());
    }
    @Test void acceptsExternalJks() throws Exception {
        assertNotNull(config(createStore("JKS").toUri().toString(), "JKS").servletContainer());
    }
    @Test void preservesClasspathJks() throws Exception {
        Path store = createStore("JKS");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(loader);
            assertNotNull(config("classpath:" + store.getFileName(), "JKS").servletContainer());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
    @Test void rejectsWrongPasswordAndAlias() throws Exception {
        String path = createStore("PKCS12").toUri().toString();
        SslConfig wrongPassword = config(path, "PKCS12");
        ReflectionTestUtils.setField(wrongPassword, "keyStorePassword", "incorrect");
        assertThrows(IllegalStateException.class, wrongPassword::servletContainer);
        SslConfig wrongAlias = config(path, "PKCS12");
        ReflectionTestUtils.setField(wrongAlias, "keyAlias", "missing");
        assertThrows(IllegalStateException.class, wrongAlias::servletContainer);
    }
    @Test void missingCertificateFailsClosed() {
        assertThrows(IllegalStateException.class,
                config("file:/missing/certificate.p12", "PKCS12")::servletContainer);
    }
    @Test void disabledSslDoesNotRequireCertificate() {
        SslConfig config = config("file:/missing/certificate.p12", "PKCS12");
        ReflectionTestUtils.setField(config, "sslEnabled", false);
        assertNotNull(config.servletContainer());
    }
}

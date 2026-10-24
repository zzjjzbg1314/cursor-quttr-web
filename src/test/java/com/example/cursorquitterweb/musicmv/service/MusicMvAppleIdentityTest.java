package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicMvAppleIdentityTest {
    HttpServer server;
    KeyPair pair;
    MusicMvOidcIdentityService oidc;
    ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();
        RSAPublicKey key=(RSAPublicKey)pair.getPublic();
        Map<String,Object> jwk=new LinkedHashMap<>();jwk.put("kty","RSA");jwk.put("kid","test");
        jwk.put("n",encode(unsigned(key.getModulus().toByteArray())));jwk.put("e",encode(unsigned(key.getPublicExponent().toByteArray())));
        byte[] keys=json.writeValueAsBytes(Collections.singletonMap("keys",Collections.singletonList(jwk)));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/keys",exchange->{exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,keys.length);exchange.getResponseBody().write(keys);exchange.close();});server.start();
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/keys";
        oidc=new MusicMvOidcIdentityService(json,"google-client",url,"apple-client","https://example.test/auth/apple",url);
    }
    @AfterEach void close(){server.stop(0);}
    Map<String,Object> claims(){Map<String,Object> c=new LinkedHashMap<>();c.put("iss","https://appleid.apple.com");c.put("aud","apple-client");c.put("sub","apple-person");c.put("exp",Instant.now().getEpochSecond()+600);c.put("iat",Instant.now().getEpochSecond());c.put("email","private@privaterelay.appleid.com");c.put("email_verified","true");c.put("nonce","server-nonce");return c;}
    @Test void verifiesSignedAppleIdentityAndRequiresExactNonce() throws Exception {
        String token=token(claims());assertEquals("apple-person",oidc.verifyWithNonce("apple",token,null,"server-nonce").getSubject());
        assertTrue(oidc.verifyWithNonce("apple",token,null,"server-nonce").isEmailVerified());
        assertThrows(ApiException.class,()->oidc.verifyWithNonce("apple",token,null,"another-nonce"));
        assertThrows(ApiException.class,()->oidc.verifyWithNonce("apple",token,null,null));
        assertThrows(ApiException.class,()->oidc.verify("google",token,null));
    }
    @Test void rejectsWrongAudienceIssuerExpiryAndSignature() throws Exception {
        for(String field:Arrays.asList("aud","iss","exp")){
            Map<String,Object> c=claims();c.put(field,"exp".equals(field)?Instant.now().getEpochSecond()-600:"wrong");String token=token(c);
            assertThrows(ApiException.class,()->oidc.verifyWithNonce("apple",token,null,"server-nonce"));
        }
        String token=token(claims());String[] parts=token.split("\\.");
        byte[] signature=Base64.getUrlDecoder().decode(parts[2]);signature[0]^=1;
        String invalid=parts[0]+"."+parts[1]+"."+encode(signature);
        assertThrows(ApiException.class,()->oidc.verify("apple",invalid,null));
    }
    String token(Map<String,Object> claims)throws Exception{
        Map<String,Object> header=new LinkedHashMap<>();header.put("alg","RS256");header.put("kid","test");
        String body=encode(json.writeValueAsBytes(header))+"."+encode(json.writeValueAsBytes(claims));
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(body.getBytes(StandardCharsets.US_ASCII));return body+"."+encode(signature.sign());
    }
    static String encode(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    static byte[] unsigned(byte[] value){return value[0]==0?Arrays.copyOfRange(value,1,value.length):value;}
}

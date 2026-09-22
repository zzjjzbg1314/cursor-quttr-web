package com.example.cursorquitterweb.musicmv.billing;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
class StripeGatewayTest {
 @Test void officialSdkSendsSubscriptionContractWithStableIdempotency() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  AtomicReference<String> body=new AtomicReference<>(),idempotency=new AtomicReference<>(),version=new AtomicReference<>();
  server.createContext("/v1/checkout/sessions",exchange->{
   body.set(URLDecoder.decode(new String(org.springframework.util.StreamUtils.copyToByteArray(exchange.getRequestBody()),StandardCharsets.UTF_8),"UTF-8"));
   idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));version.set(exchange.getRequestHeaders().getFirst("Stripe-Version"));
   byte[] response="{\"id\":\"cs_test_fixture\",\"object\":\"checkout.session\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_fixture\"}".getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
  });server.start();String original=Stripe.getApiBase();
  try{
   Stripe.overrideApiBase("http://127.0.0.1:"+server.getAddress().getPort());
   BillingSettings s=new BillingSettings();s.enabled=true;s.secretKey="sk_test_fixture";s.webhookSecret="whsec_fixture";s.starterPrice="price_s";s.creatorPrice="price_c";s.portalConfiguration="bpc_fixture";
   assertThat(new StripeGateway(s,new ObjectMapper()).checkout("cus_1","u","price_s","token",2000000000L,"http://localhost:3000/pricing").path("id").asText()).isEqualTo("cs_test_fixture");
   assertThat(body.get()).contains("mode=subscription","line_items[0][price]=price_s","line_items[0][quantity]=1","subscription_data[billing_mode][type]=flexible","subscription_data[metadata][sunofilm_user_id]=u");
   assertThat(idempotency.get()).isEqualTo("sunofilm-checkout-token");assertThat(version.get()).isEqualTo(Stripe.API_VERSION);
  }finally{Stripe.overrideApiBase(original);server.stop(0);}
 }
}

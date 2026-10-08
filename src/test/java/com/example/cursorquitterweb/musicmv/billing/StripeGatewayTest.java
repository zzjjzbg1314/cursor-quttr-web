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
 @Test void onlyExplicitExpiryValidationAllowsCheckoutReconciliation() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  AtomicReference<String> param=new AtomicReference<>("expires_at");
  server.createContext("/v1/checkout/sessions",exchange->{
   byte[] response=("{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Invalid parameter\",\"param\":\""+param.get()+"\"}}").getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(400,response.length);exchange.getResponseBody().write(response);exchange.close();
  });server.start();String original=Stripe.getApiBase();
  try {
   Stripe.overrideApiBase("http://127.0.0.1:"+server.getAddress().getPort());
   BillingSettings s=new BillingSettings();s.enabled=true;s.secretKey="sk_test_fixture";s.webhookSecret="whsec_fixture";s.starterPrice="price_s";s.creatorPrice="price_c";s.portalConfiguration="bpc_fixture";
   StripeGateway gateway=new StripeGateway(s,new ObjectMapper());
   assertThatThrownBy(()->gateway.checkout("cus_1","u","price_s","token",100L,"http://localhost:3000/pricing")).isInstanceOf(StripeGateway.CheckoutExpiryRejectedException.class);
   param.set("line_items");
   assertThatThrownBy(()->gateway.checkout("cus_1","u","price_s","token",100L,"http://localhost:3000/pricing")).isInstanceOf(com.example.cursorquitterweb.musicmv.support.ApiException.class);
  } finally {Stripe.overrideApiBase(original);server.stop(0);}
 }

 @Test void recoveryListsCustomerSessionsAndExpiresVerifiedSessionThroughSdk() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  AtomicReference<String> query=new AtomicReference<>(),expiryMethod=new AtomicReference<>();
  server.createContext("/v1/checkout/sessions",exchange->{
   String path=exchange.getRequestURI().getPath(),response;
   if(path.endsWith("/expire")) {expiryMethod.set(exchange.getRequestMethod());response="{\"object\":\"checkout.session\",\"id\":\"cs_test_fixture\",\"status\":\"expired\"}";}
   else if(path.endsWith("cs_test_fixture")) response="{\"object\":\"checkout.session\",\"id\":\"cs_test_fixture\",\"status\":\"open\"}";
   else {query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(),"UTF-8"));response="{\"object\":\"list\",\"data\":[],\"has_more\":false}";}
   byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();String original=Stripe.getApiBase();
  try {
   Stripe.overrideApiBase("http://127.0.0.1:"+server.getAddress().getPort());
   BillingSettings s=new BillingSettings();s.enabled=true;s.secretKey="sk_test_fixture";s.webhookSecret="whsec_fixture";s.starterPrice="price_s";s.creatorPrice="price_c";s.portalConfiguration="bpc_fixture";
   StripeGateway gateway=new StripeGateway(s,new ObjectMapper());
   assertThat(gateway.checkoutSessions("cus_1",100L).path("data").size()).isZero();
   assertThat(query.get()).contains("customer=cus_1","created[gte]=100","limit=100");
   assertThat(gateway.expireCheckout("cs_test_fixture").path("status").asText()).isEqualTo("expired");
   assertThat(expiryMethod.get()).isEqualTo("POST");
  } finally {Stripe.overrideApiBase(original);server.stop(0);}
 }

 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"429","500","bad-json"})
 void channelFailureReturnsStableRetryableErrorThroughOfficialSdk(String scenario) throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
  server.createContext("/v1/invoices/in_fixture",exchange->{
   calls.incrementAndGet();
   byte[] response=(scenario.equals("bad-json")?"not JSON":"{\"error\":{\"type\":\"api_error\",\"message\":\"fixture-private-provider-detail\"}}").getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().add("Content-Type","application/json");
   exchange.getResponseHeaders().add("Stripe-Should-Retry","false");
   exchange.sendResponseHeaders(scenario.equals("bad-json")?200:Integer.parseInt(scenario),response.length);
   exchange.getResponseBody().write(response);exchange.close();
  });server.start();String original=Stripe.getApiBase();
  try {
   Stripe.overrideApiBase("http://127.0.0.1:"+server.getAddress().getPort());
   BillingSettings s=new BillingSettings();s.enabled=true;s.secretKey="sk_test_fixture";s.webhookSecret="whsec_fixture";s.starterPrice="price_s";s.creatorPrice="price_c";s.portalConfiguration="bpc_fixture";
   StripeGateway gateway=new StripeGateway(s,new ObjectMapper());
   assertThatThrownBy(()->gateway.invoice("in_fixture")).isInstanceOfSatisfying(com.example.cursorquitterweb.musicmv.support.ApiException.class,
    e->{assertThat(e.getStatus().value()).isEqualTo(502);assertThat(e.getCode()).isEqualTo("BILLING_PROVIDER_UNAVAILABLE");assertThat(e.isRetryable()).isTrue();assertThat(e.getMessage()).doesNotContain("fixture-private-provider-detail");});
   assertThat(calls.get()).isEqualTo(1);
   com.stripe.net.RequestOptions options=org.springframework.test.util.ReflectionTestUtils.invokeMethod(gateway,"options","fixture-idempotency");
   assertThat(options.getConnectTimeout()).isEqualTo(10000);assertThat(options.getReadTimeout()).isEqualTo(20000);assertThat(options.getMaxNetworkRetries()).isEqualTo(1);
  } finally {Stripe.overrideApiBase(original);server.stop(0);}
 }

}

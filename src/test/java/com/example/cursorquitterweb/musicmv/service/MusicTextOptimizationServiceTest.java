package com.example.cursorquitterweb.musicmv.service;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class MusicTextOptimizationServiceTest {
 @Test void missingConfigurationNeverReturnsFakeContent() {
  MusicTextOptimizationService service=new MusicTextOptimizationService(new RestTemplate(), "", "https://example.com", "test");
  assertFalse(service.available()); assertThrows(ResponseStatusException.class,()->service.optimize("lyrics","Original lyrics", ""));
 }
 @Test void rejectsUnsupportedKindAndEmptyText() {
  MusicTextOptimizationService service=new MusicTextOptimizationService(new RestTemplate(), "key", "https://example.com", "test");
  assertThrows(ResponseStatusException.class,()->service.optimize("audio","test", ""));
  assertThrows(ResponseStatusException.class,()->service.optimize("styles"," ", ""));
 }
 @Test void returnsProviderResultAndKeepsEditingInstructions() {
  RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
  server.expect(requestTo("https://example.com")).andExpect(jsonPath("$.messages[1].content").value("Requested edit: Warm\nSource text:\nAcoustic pop"))
   .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"Warm acoustic pop, gentle piano\"}}]}",MediaType.APPLICATION_JSON));
  assertEquals("Warm acoustic pop, gentle piano",new MusicTextOptimizationService(client,"key","https://example.com","test").optimize("styles","Acoustic pop","Warm")); server.verify();
 }
 @Test void rejectsMalformedProviderResponse() {
  RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
  server.expect(requestTo("https://example.com")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
  assertThrows(ResponseStatusException.class,()->new MusicTextOptimizationService(client,"key","https://example.com","test").optimize("lyrics","Original lyrics",""));
 }

 @Test void styleUsesLyricsAndInterfaceLanguageWithoutRewritingLyrics() {
  for (String locale : new String[]{"zh-CN", "en"}) {
   RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
   String lyrics="[Verse]\n关了灯还没睡";
   server.expect(requestTo("https://example.com"))
    .andExpect(jsonPath("$.messages[1].content").value("Requested edit: \nSource text:\nPop, 90 BPM, female vocal\nLyrics (emotional reference only; do not rewrite):\n"+lyrics))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("Output language: "+("zh-CN".equals(locale) ? "Simplified Chinese" : "English"))))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("Preserve every explicit musical direction")))
    .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"Gentle pop, 90 BPM, female vocal\"}}]}",MediaType.APPLICATION_JSON));
   assertEquals("Gentle pop, 90 BPM, female vocal",new MusicTextOptimizationService(client,"key","https://example.com","test").optimize("styles","Pop, 90 BPM, female vocal","",lyrics,locale));
   server.verify();
  }
 }
 @Test void rejectsOversizedContextAndUnknownLocaleBeforeProviderCall() {
  RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
  MusicTextOptimizationService service=new MusicTextOptimizationService(client,"key","https://example.com","test");
  assertThrows(ResponseStatusException.class,()->service.optimize("styles","Pop","",new String(new char[5001]).replace('\0', 'x'),"en"));
  assertThrows(ResponseStatusException.class,()->service.optimize("styles","Pop","","","invalid"));
  server.verify();
 }
}

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

 @Test void styleUsesLyricsAsContextAndInterfaceLanguageOnlyAsFallback() {
  for (String locale : new String[]{"zh-CN", "en"}) {
   RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
   String lyrics="[Verse]\n关了灯还没睡";
   server.expect(requestTo("https://example.com"))
    .andExpect(jsonPath("$.messages[1].content").value("Requested edit: \nSource text:\nPop, 90 BPM, female vocal\nLyrics (emotional reference only; do not rewrite):\n"+lyrics))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("fallback language: "+("zh-CN".equals(locale) ? "Simplified Chinese" : "English"))))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("Preserve every explicit musical direction")))
    .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"Gentle pop, 90 BPM, female vocal\"}}]}",MediaType.APPLICATION_JSON));
   assertEquals("Gentle pop, 90 BPM, female vocal",new MusicTextOptimizationService(client,"key","https://example.com","test").optimize("styles","Pop, 90 BPM, female vocal","",lyrics,locale));
   server.verify();
  }
 }
 @Test void styleLanguageContractCoversCrossLanguageMixedAndAmbiguousSources() {
  String[][] cases = {
   {"Acoustic pop, warm piano", "中文歌词", "zh-CN", "Simplified Chinese"},
   {"温暖流行，钢琴伴奏", "English lyrics", "en", "English"},
   {"温暖的 Pop，90 BPM，轻柔人声", "English lyrics", "en", "English"},
   {"90 BPM", "中文歌词", "zh-CN", "Simplified Chinese"},
   {"90 BPM", "中文歌词", "", "English"}
  };
  for (String[] scenario : cases) {
   RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
   server.expect(requestTo("https://example.com"))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("original music style) only")))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("follow the dominant descriptive language")))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("Do not infer the output language from the lyrics")))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("fallback language: "+scenario[3])))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("fallback must not override a recognizable source language")))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("regardless of the source text language"))))
    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Source text:\n"+scenario[0])))
    .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"Optimized style\"}}]}",MediaType.APPLICATION_JSON));
   new MusicTextOptimizationService(client,"key","https://example.com","test").optimize("styles",scenario[0],"",scenario[1],scenario[2]);
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

 @Test void selectedConstraintsOverrideConflictingStyleInProviderPrompt() {
  for (String gender : new String[]{"m", "f"}) {
   RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
   server.expect(requestTo("https://example.com"))
    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Selected vocal gender: "+("m".equals(gender) ? "male" : "female"))))
    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Excluded styles (constraints only):\n摇滚、鼓")))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("take precedence over conflicting source style")))
    .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("never add those traits or repeat the exclusion list")))
    .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"温暖的原声流行\"}}]}",MediaType.APPLICATION_JSON));
   assertEquals("温暖的原声流行",new MusicTextOptimizationService(client,"key","https://example.com","test")
    .optimize("styles","摇滚，鼓，男声","", "[Verse] 想念你","zh-CN",gender,"摇滚、鼓"));
   server.verify();
  }
 }
 @Test void rejectsInvalidConstraintsBeforeProviderCall() {
  RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
  MusicTextOptimizationService service=new MusicTextOptimizationService(client,"key","https://example.com","test");
  assertThrows(ResponseStatusException.class,()->service.optimize("styles","Pop","","","en","other",""));
  assertThrows(ResponseStatusException.class,()->service.optimize("styles","Pop","","","en","",new String(new char[1001])));
  server.verify();
 }
 @Test void lyricOptimizationDoesNotReceiveStyleConstraints() {
  RestTemplate client=new RestTemplate(); MockRestServiceServer server=MockRestServiceServer.createServer(client);
  server.expect(requestTo("https://example.com"))
   .andExpect(jsonPath("$.messages[1].content").value("Requested edit: \nSource text:\nOriginal lyrics"))
   .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"Revised lyrics\"}}]}",MediaType.APPLICATION_JSON));
  assertEquals("Revised lyrics",new MusicTextOptimizationService(client,"key","https://example.com","test")
   .optimize("lyrics","Original lyrics","","","en","f","drums"));
  server.verify();
 }
}

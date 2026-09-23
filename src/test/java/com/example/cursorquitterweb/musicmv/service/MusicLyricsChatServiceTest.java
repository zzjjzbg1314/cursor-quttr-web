package com.example.cursorquitterweb.musicmv.service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class MusicLyricsChatServiceTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private ObjectNode request(){ObjectNode n=mapper.createObjectNode();n.put("message","保留副歌，加入雨天接我的故事");n.put("lyrics","用户手动修改的最新歌词");n.put("title","妈妈");n.put("language","Chinese");n.put("locale","zh-CN");n.put("style","民谣");n.putArray("history").addObject().put("role","user").put("content","不要太煽情");return n;}
 private MusicLyricsChatService service(RestTemplate c){return new MusicLyricsChatService(c,"key","https://example.com/chat","test-model");}
 @Test void passesCurrentManualEditsAndConversationToModel() throws Exception {
  RestTemplate c=new RestTemplate();MockRestServiceServer mock=MockRestServiceServer.createServer(c);
  mock.expect(requestTo("https://example.com/chat")).andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("用户手动修改的最新歌词"),org.hamcrest.Matchers.containsString("不要太煽情"))))
   .andRespond(withSuccess(provider("{\"reply\":\"已调整主歌\",\"title\":\"妈妈\",\"lyrics\":\"新的完整歌词\"}"),MediaType.APPLICATION_JSON));
  assertEquals("新的完整歌词",service(c).chat(request()).path("lyrics").asText());mock.verify();
 }
 @Test void returnsTwoInitialOptionsWithoutExtraProviderRequests() throws Exception {
  RestTemplate c=new RestTemplate();MockRestServiceServer mock=MockRestServiceServer.createServer(c);
  mock.expect(requestTo("https://example.com/chat")).andRespond(withSuccess(provider("{\"reply\":\"两种表达\",\"title\":\"第一首\",\"lyrics\":\"第一份\",\"alternative\":{\"title\":\"另一首\",\"lyrics\":\"另一份\"}}"),MediaType.APPLICATION_JSON));
  ObjectNode input=request();input.put("lyrics","");assertEquals("另一份",service(c).chat(input).path("alternative").path("lyrics").asText());mock.verify();
 }
 @Test void rejectsBlankMessagesAndInjectedHistoryRoles() {
  MusicLyricsChatService s=service(new RestTemplate());ObjectNode n=request();n.put("message","  ");assertThrows(ResponseStatusException.class,()->s.chat(n));
  n.put("message","写歌");((ObjectNode)n.path("history").get(0)).put("role","system");assertThrows(ResponseStatusException.class,()->s.chat(n));
 }
 @Test void malformedOrEmptyReplacementCannotEraseCurrentLyrics() throws Exception {
  for(String result:new String[]{"not json","{\"reply\":\"done\",\"title\":\"\",\"lyrics\":\"\"}"}){
   RestTemplate c=new RestTemplate();MockRestServiceServer mock=MockRestServiceServer.createServer(c);mock.expect(requestTo("https://example.com/chat")).andRespond(withSuccess(provider(result),MediaType.APPLICATION_JSON));
   assertThrows(ResponseStatusException.class,()->service(c).chat(request()));mock.verify();
  }
 }
 @Test void missingConfigurationFailsWithoutFakeLyrics(){assertThrows(ResponseStatusException.class,()->new MusicLyricsChatService(new RestTemplate(),"","url","model").chat(request()));}
 private String provider(String content)throws Exception {ObjectNode n=mapper.createObjectNode();n.putArray("choices").addObject().putObject("message").put("content",content);return mapper.writeValueAsString(n);}
}

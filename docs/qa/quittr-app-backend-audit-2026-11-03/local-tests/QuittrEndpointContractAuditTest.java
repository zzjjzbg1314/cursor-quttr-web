import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 全部服务依赖使用替身，执行真实控制器的参数绑定及响应封装；不代表数据库端到端验收。
public class QuittrEndpointContractAuditTest {
 static final ObjectMapper JSON=new ObjectMapper();
 final List<Map<String,Object>> rows=new ArrayList<>();
 static String fixture(Class<?> type){
  if(type==UUID.class)return "00000000-0000-4000-8000-000000000001";
  if(type==int.class||type==Integer.class||type==long.class||type==Long.class||type==double.class||type==Double.class)return "1";
  if(type==boolean.class||type==Boolean.class)return "true";
  if(type.getName().contains("OffsetDateTime"))return "2026-11-02T00:00:00Z";
  if(type.getName().contains("LocalDate"))return "2026-11-02";
  if(type.isEnum())return type.getEnumConstants()[0].toString();
  return "audit";
 }
 @TestFactory Collection<DynamicTest> exerciseEveryEndpointInIsolation() throws Exception {
  List<Object> controllers=new ArrayList<>();
  Path source=Paths.get(System.getProperty("audit.repo"),"src/main/java/com/example/cursorquitterweb/controller");
  try(Stream<Path> files=Files.list(source)){
   for(Path file:files.filter(p->p.toString().endsWith(".java")).collect(Collectors.toList())){
    Class<?> type=Class.forName("com.example.cursorquitterweb.controller."+file.getFileName().toString().replace(".java",""));
    if(!org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(type,org.springframework.stereotype.Controller.class))continue;
    Object controller=mock(type,CALLS_REAL_METHODS);
    for(Field f:type.getDeclaredFields()){
     if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);
     if(f.getType()==String.class)f.set(controller,"");
     else if(!f.getType().isPrimitive()&&!f.getType().isEnum()) f.set(controller,mock(f.getType()));
    }
    controllers.add(controller);
   }
  }
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controllers.toArray()).build();
  RequestMappingHandlerMapping mapping=mvc.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerMapping.class);
  List<DynamicTest> tests=new ArrayList<>();
  for(Map.Entry<RequestMappingInfo,HandlerMethod> entry:mapping.getHandlerMethods().entrySet()){
   RequestMappingInfo info=entry.getKey();HandlerMethod expected=entry.getValue();
   for(String path:info.getPatternValues())for(RequestMethod verb:info.getMethodsCondition().getMethods()){
    tests.add(DynamicTest.dynamicTest(verb+" "+path,()->{
     Map<String,Object> row=new LinkedHashMap<>();row.put("method",verb.name());row.put("path",path);row.put("produces",info.getProducesCondition().toString());row.put("controller",expected.getMethod().getDeclaringClass().getSimpleName().split("\\$")[0]);row.put("handler",expected.getMethod().getName());
     String url=path;Map<String,String> params=new LinkedHashMap<>();List<String> uploads=new ArrayList<>();boolean body=false;
     for(Parameter p:expected.getMethod().getParameters()){
      PathVariable pv=p.getAnnotation(PathVariable.class);RequestParam qp=p.getAnnotation(RequestParam.class);RequestBody rb=p.getAnnotation(RequestBody.class);
      if(pv!=null){String name=!pv.value().isEmpty()?pv.value():!pv.name().isEmpty()?pv.name():p.getName();url=url.replace("{"+name+"}",fixture(p.getType()));}
      if(qp!=null){String name=!qp.value().isEmpty()?qp.value():!qp.name().isEmpty()?qp.name():p.getName();if(org.springframework.web.multipart.MultipartFile.class.isAssignableFrom(p.getType()))uploads.add(name);else if(qp.required()&&qp.defaultValue().equals(ValueConstants.DEFAULT_NONE))params.put(name,fixture(p.getType()));}
      body|=rb!=null;
     }
     url=url.replaceAll("\\{[^}]+\\}","audit");
     MockHttpServletRequestBuilder request;
     if(!uploads.isEmpty()){
      MockMultipartHttpServletRequestBuilder multipart=MockMvcRequestBuilders.multipart(url);for(String name:uploads)multipart.file(new MockMultipartFile(name,"audit.txt","text/plain",new byte[]{1}));request=multipart.with(r->{r.setMethod(verb.name());return r;});
     }else request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(verb.name()),url);
     if(body)request.contentType(MediaType.APPLICATION_JSON).content("{}");
     for(Map.Entry<String,String> p:params.entrySet())request.param(p.getKey(),p.getValue());
     if(!info.getProducesCondition().getProducibleMediaTypes().isEmpty())request.accept(info.getProducesCondition().getProducibleMediaTypes().iterator().next());
     try{
      MvcResult result=mvc.perform(request).andReturn();row.put("status",result.getResponse().getStatus());row.put("handler_matched",result.getHandler() instanceof HandlerMethod&&((HandlerMethod)result.getHandler()).getMethod().equals(expected.getMethod()));
      try{com.fasterxml.jackson.databind.JsonNode parsed=JSON.readTree(result.getResponse().getContentAsByteArray());if(parsed!=null&&parsed.has("success"))row.put("success",parsed.get("success").asBoolean());}catch(Exception ignored){}
      if(result.getResolvedException()!=null)row.put("exception",result.getResolvedException().getClass().getSimpleName());
     }catch(Exception e){row.put("exception",e.getClass().getSimpleName());row.put("status",500);row.put("handler_matched",null);}
     row.put("scope","real controller; mocked dependencies; empty JSON for body; no business acceptance claim");rows.add(row);
     assertNotNull(row.get("status"));
    }));
   }
  }
  return tests;
 }
 @AfterAll static void end(){}
 @AfterEach void save() throws Exception {JSON.writerWithDefaultPrettyPrinter().writeValue(Paths.get(System.getProperty("audit.output"),"endpoint-contracts.json").toFile(),rows);}
}

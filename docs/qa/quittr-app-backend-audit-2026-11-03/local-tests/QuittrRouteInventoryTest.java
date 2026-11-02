import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class QuittrRouteInventoryTest {
 @TestFactory Collection<DynamicTest> inventoryAndResolveEveryRoute() throws Exception {
  Path source=Paths.get(System.getProperty("audit.repo"),"src/main/java/com/example/cursorquitterweb/controller");
  List<Object> controllers=new ArrayList<>();
  try(Stream<Path> files=Files.list(source)) {
   for(Path file:files.filter(p->p.toString().endsWith(".java")).collect(Collectors.toList())) {
    String name=file.getFileName().toString().replace(".java","");
    Class<?> type=Class.forName("com.example.cursorquitterweb.controller."+name);
    if(org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(type,org.springframework.stereotype.Controller.class)) controllers.add(mock(type));
   }
  }
  MockMvc mvc=MockMvcBuilders.standaloneSetup(controllers.toArray()).build();
  RequestMappingHandlerMapping mapping=mvc.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerMapping.class);
  List<Map<String,Object>> inventory=new ArrayList<>();
  List<DynamicTest> tests=new ArrayList<>();
  for(Map.Entry<RequestMappingInfo,HandlerMethod> entry:mapping.getHandlerMethods().entrySet()) {
   RequestMappingInfo info=entry.getKey(); HandlerMethod expected=entry.getValue();
   Set<RequestMethod> methods=info.getMethodsCondition().getMethods();
   List<String> verbs=methods.isEmpty()?Collections.singletonList("ANY"):methods.stream().map(Enum::name).collect(Collectors.toList());
   for(String pattern:info.getPatternValues()) for(String verb:verbs) {
    Map<String,Object> row=new LinkedHashMap<>();
    row.put("caseIndex",inventory.size()+1);row.put("produces",info.getProducesCondition().toString());row.put("consumes",info.getConsumesCondition().toString());row.put("method",verb);row.put("path",pattern);row.put("controller",expected.getMethod().getDeclaringClass().getSimpleName().split("\\$")[0]);row.put("handler",expected.getMethod().getName());
    row.put("parameters",Arrays.stream(expected.getMethod().getParameters()).map(p->p.getType().getSimpleName()+" "+p.getName()).collect(Collectors.toList()));
    row.put("verification","local-routing-only; controller mocked, no business or database execution");inventory.add(row);
    tests.add(DynamicTest.dynamicTest(verb+" "+pattern,()->{
     String url=pattern.replaceAll("\\{[^}]+\\}","00000000-0000-4000-8000-000000000001").replace("**","audit").replace("*","audit");
     MockHttpServletRequest request=new MockHttpServletRequest(verb.equals("ANY")?"GET":verb,url);
     request.setServletPath(url); request.setContentType("application/json");
     if(!info.getProducesCondition().getProducibleMediaTypes().isEmpty()) request.addHeader("Accept",info.getProducesCondition().getProducibleMediaTypes().iterator().next().toString());
     for(org.springframework.web.servlet.mvc.condition.NameValueExpression<String> e:info.getParamsCondition().getExpressions()) if(!e.isNegated())request.addParameter(e.getName(),e.getValue()==null?"audit":e.getValue());
     HandlerExecutionChain chain=mapping.getHandler(request);
     assertNotNull(chain);assertTrue(chain.getHandler() instanceof HandlerMethod);
     HandlerMethod actual=(HandlerMethod)chain.getHandler();assertEquals(expected.getMethod(),actual.getMethod());
    }));
   }
  }
  inventory.sort(Comparator.comparing(x->x.get("path").toString()+x.get("method")));
  new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Paths.get(System.getProperty("audit.output"),"routes.json").toFile(),inventory);
  assertTrue(inventory.size()>400);
  return tests;
 }
}

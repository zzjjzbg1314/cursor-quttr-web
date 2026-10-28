import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.dto.TemplatePreviewMp4Request;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.net.*;
import java.util.*;
import java.io.*;
import org.springframework.boot.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;

@Configuration
@Import({D1DatabaseClient.class,MusicMvTemplateCatalogRepository.class,R2StorageService.class,
    TemplatePreviewStorage.class,CloudflareTemplateMediaProvider.class,TemplatePreviewMp4Service.class,
    TemplateSyncAuthenticationService.class})
public class PreviewLowHelper {
 @Bean ObjectMapper mapper(){return new ObjectMapper();}
 @Bean MusicMvTemplateCatalogService catalog(MusicMvTemplateCatalogRepository r,CloudflareTemplateMediaProvider p,D1DatabaseClient d,ObjectMapper m){
  return new MusicMvTemplateCatalogService(r,p,null,d,m);
 }
 public static void main(String[] args)throws Exception{
  SpringApplication app=new SpringApplication(PreviewLowHelper.class);
  app.setWebApplicationType(WebApplicationType.NONE);
  ConfigurableApplicationContext ctx=app.run(args);
  String token=ctx.getEnvironment().getProperty("music-mv.security.template-sync-token","");
  if(token.trim().isEmpty())throw new IllegalStateException("必须配置独立同步令牌");
  ObjectMapper mapper=ctx.getBean(ObjectMapper.class);
  TemplatePreviewMp4Service svc=ctx.getBean(TemplatePreviewMp4Service.class);
  TemplateSyncAuthenticationService auth=ctx.getBean(TemplateSyncAuthenticationService.class);
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",ctx.getEnvironment().getProperty("local-preview.port",Integer.class,41239)),0);
  server.createContext("/internal/music-mv/v1/templates/",exchange->{
   int status=200;Object result;
   try{
    auth.requireAuthorized(exchange.getRequestHeaders().getFirst("X-Music-Mv-Template-Sync-Token"));
    String[] parts=exchange.getRequestURI().getPath().split("/");
    if(parts.length!=10 || !"versions".equals(parts[6]) || !"preview-mp4-low".equals(parts[8]))
      throw new IllegalArgumentException("unsupported path");
    String t=parts[5],v=parts[7],action=parts[9];
    System.out.println("PREVIEW_LOW_REQUEST "+action+" "+t+" "+v);
    if("source".equals(action) && "GET".equals(exchange.getRequestMethod()))result=svc.source(t,v,true);
    else if("POST".equals(exchange.getRequestMethod())){
      TemplatePreviewMp4Request r=mapper.readValue(exchange.getRequestBody(),TemplatePreviewMp4Request.class);
      if("upload-session".equals(action))result=svc.upload(t,v,r,true);
      else if("complete".equals(action))result=svc.complete(t,v,r,true);
      else throw new IllegalArgumentException("unsupported action");
    }else throw new IllegalArgumentException("unsupported method");
   }catch(ApiException e){status=e.getStatus().value();result=Collections.singletonMap("code",e.getCode());}
    catch(Exception e){status=500;result=Collections.singletonMap("error",e.getClass().getSimpleName());System.err.println("Helper failure: "+e.getClass().getSimpleName());}
   byte[] bytes=mapper.writeValueAsBytes(result);
   exchange.getResponseHeaders().set("Content-Type","application/json");
   exchange.sendResponseHeaders(status,bytes.length);
   try(OutputStream output=exchange.getResponseBody()){output.write(bytes);}
  });
  server.start();
  Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(0);ctx.close();}));
  System.out.println("PREVIEW_LOW_HELPER_READY 127.0.0.1:41239");
 }
}

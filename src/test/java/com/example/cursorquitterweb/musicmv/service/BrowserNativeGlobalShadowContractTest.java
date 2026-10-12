package com.example.cursorquitterweb.musicmv.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BrowserNativeGlobalShadowContractTest {
 @Test void acceptsBoundSignedShadowAndRejectsUnboundController() throws Exception {
  BrowserNativeGlobalEffectContractTest fixture=new BrowserNativeGlobalEffectContractTest();
  Map<String,Object> effect=fixture.effect(),descriptor=fixture.descriptor();
  Map<String,Object> source=(Map<String,Object>)effect.get("nativeGlobalSource");source.put("type","adjustment");source.put("value",1);
  Map<String,Object> parameter=new HashMap<>();parameter.put("name","shadow");parameter.put("value",-.16);source.put("adjustParams",Collections.singletonList(parameter));
  for(Object binding:(List<?>)descriptor.get("bindings"))((Map<String,Object>)binding).put("kind","adjustment");
  Map<String,Object> scene=Collections.singletonMap("postEffects",Collections.singletonList(effect));
  assertEquals(Collections.singleton("s"),BrowserNativeGlobalEffectContract.validate(scene,descriptor));
  effect.put("resourceId","absent");assertThrows(RuntimeException.class,()->BrowserNativeGlobalEffectContract.validate(scene,descriptor));
 }
}

package com.example.cursorquitterweb.musicmv.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
class BrowserNativeHslContractTest {
 @Test @SuppressWarnings("unchecked") void hslPreservesControllerBindingAndRequiresDeliveredConfig(){
  Map<String,Object> value=BrowserNativeRuntimeContractTest.descriptor();
  Map<String,Object> binding=((List<Map<String,Object>>)value.get("bindings")).get(0);
  binding.put("kind","hsl");binding.put("hslEnableParameter",true);
  binding.put("adjustmentParameters",Collections.singletonMap("brightness","brightness_intensity"));
  assertEquals(value,BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
  assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.emptySet()));
  binding.put("hslEnableParameter","true");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
  binding.put("hslEnableParameter",true);binding.put("kind","adjustment");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
  binding.put("kind","hsl");value.put("files",Collections.singletonList("effect/other.json"));assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(value,Collections.singleton("effect")));
 }
 @Test @SuppressWarnings("unchecked") void capturedRuntimeRoundTripsWithoutLosingHsl() throws Exception {
  Map<String,Object> value;try(java.io.InputStream input=getClass().getResourceAsStream("/musicmv/hsl-native-engine.json")){assertNotNull(input);value=new com.fasterxml.jackson.databind.ObjectMapper().readValue(input,Map.class);}
  Set<String> ids=new HashSet<>();for(Map<String,Object> item:(List<Map<String,Object>>)value.get("bindings"))ids.add((String)item.get("resourceId"));
  for(Object file:(List<?>)value.get("files"))ids.add(file.toString().split("/")[0]);
  assertEquals(value,BrowserNativeRuntimeContract.validate(value,ids));
 }
}

package com.example.cursorquitterweb.musicmv.controller;
import com.example.cursorquitterweb.musicmv.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RendererTemplateRuntimePackageControllerTest {
 @Test void completedPackageInvalidatesOnlyItsTemplateAfterVerification() {
  TemplateSyncAuthenticationService auth=mock(TemplateSyncAuthenticationService.class);
  TemplateRuntimePackageService packages=mock(TemplateRuntimePackageService.class);
  MusicMvTemplateCatalogService catalog=mock(MusicMvTemplateCatalogService.class);
  RendererTemplateRuntimePackageController controller=new RendererTemplateRuntimePackageController(auth,packages,catalog);
  Map<String,Object> result=Collections.singletonMap("status","ready");
  when(packages.complete("tpl","ver")).thenReturn(result);
  assertSame(result,controller.complete("token","tpl","ver"));
  org.mockito.InOrder order=inOrder(auth,packages,catalog);
  order.verify(auth).requireAuthorized("token");order.verify(packages).complete("tpl","ver");order.verify(catalog).invalidateDetail("tpl");
 }
 @Test void failedVerificationDoesNotAdvertiseNewPackage() {
  TemplateSyncAuthenticationService auth=mock(TemplateSyncAuthenticationService.class);
  TemplateRuntimePackageService packages=mock(TemplateRuntimePackageService.class);
  MusicMvTemplateCatalogService catalog=mock(MusicMvTemplateCatalogService.class);
  when(packages.complete("tpl","ver")).thenThrow(new IllegalStateException("bad package"));
  assertThrows(IllegalStateException.class,()->new RendererTemplateRuntimePackageController(auth,packages,catalog).complete("token","tpl","ver"));
  verifyNoInteractions(catalog);
 }
}

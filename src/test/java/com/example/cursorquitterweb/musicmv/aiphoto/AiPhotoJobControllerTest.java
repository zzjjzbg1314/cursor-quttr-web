package com.example.cursorquitterweb.musicmv.aiphoto;
import com.example.cursorquitterweb.musicmv.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AiPhotoJobControllerTest {
 @Test void queryAndCancelUseAuthenticatedOwner() {
  MusicMvAuthService auth=mock(MusicMvAuthService.class);MusicMvRenderClientAuthenticationService gate=mock(MusicMvRenderClientAuthenticationService.class);AiPhotoJobService jobs=mock(AiPhotoJobService.class);
  AiPhotoJobController controller=new AiPhotoJobController(auth,gate,jobs);MockHttpServletRequest request=new MockHttpServletRequest();
  when(auth.requireUserId(request)).thenReturn("owner");controller.get("token","job",request);controller.cancel("token","job",request);
  verify(gate,times(2)).requireAuthorized("token");verify(jobs).get("owner","job");verify(jobs).cancel("owner","job");
 }
 @Test void unauthorizedCallerNeverReadsJob() {
  MusicMvAuthService auth=mock(MusicMvAuthService.class);MusicMvRenderClientAuthenticationService gate=mock(MusicMvRenderClientAuthenticationService.class);AiPhotoJobService jobs=mock(AiPhotoJobService.class);
  doThrow(new IllegalStateException()).when(gate).requireAuthorized(null);
  assertThrows(IllegalStateException.class,()->new AiPhotoJobController(auth,gate,jobs).get(null,"job",new MockHttpServletRequest()));verifyNoInteractions(jobs);
 }
}

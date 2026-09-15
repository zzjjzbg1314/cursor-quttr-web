package com.example.cursorquitterweb.musicmv.aimusic;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import com.example.cursorquitterweb.musicmv.controller.AiMusicSongController;
import com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AiMusicAudioOwnershipTest {
    @Test
    void rejectsUnownedAudioBeforeSubmittingGeneration() {
        MusicMvAuthService auth = mock(MusicMvAuthService.class);
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        AiMusicGenerationService service = mock(AiMusicGenerationService.class);
        MockHttpServletRequest servlet = new MockHttpServletRequest();
        when(auth.requireUserId(servlet)).thenReturn("owner");
        AiMusicSongCreateRequest request = new AiMusicSongCreateRequest();
        MusicMvRenderJobCreateRequest.Asset asset = new MusicMvRenderJobCreateRequest.Asset();
        request.setAudio(asset);
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "MV_INPUT_ASSET_NOT_FOUND", "Not found"))
                .when(storage).requireOwnedCloudAsset("owner", asset, "music");
        AiMusicSongController controller = new AiMusicSongController(
                mock(MusicMvRenderClientAuthenticationService.class), auth, service, storage);
        assertThatThrownBy(() -> controller.create(null, null, request, servlet)).isInstanceOf(ApiException.class);
        verifyNoInteractions(service);
    }
}

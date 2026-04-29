package com.example.cursorquitterweb.service;

import java.util.UUID;

public interface CommunityContentTranslationService {

    String normalizeOriginalLanguage(String originalLanguage, String content);

    void translatePostAsync(UUID postId, String content, String originalLanguage);

    void translateCommentAsync(UUID commentId, String content, String originalLanguage);
}

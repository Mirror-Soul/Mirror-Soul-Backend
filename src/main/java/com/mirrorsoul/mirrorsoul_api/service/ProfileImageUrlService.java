package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileImageUrlService {
    private final FileService fileService;

    public String resolve(User user) {
        if (user == null) {
            return null;
        }
        return fileService.createPresignedDownloadUrlOrFallback(
                user.getProfileImageObjectKey(),
                user.getProfileImageUrl()
        );
    }
}

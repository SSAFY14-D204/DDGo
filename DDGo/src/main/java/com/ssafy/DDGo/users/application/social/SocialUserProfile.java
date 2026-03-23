package com.ssafy.DDGo.users.application.social;

import com.ssafy.DDGo.users.domain.SocialProvider;

public record SocialUserProfile(
        SocialProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String nickname) {
}

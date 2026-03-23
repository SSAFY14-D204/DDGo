package com.ssafy.DDGo.users.application.social;

import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.domain.SocialProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SocialAuthProviderRegistry {

    private final Map<SocialProvider, SocialAuthProvider> providers;

    public SocialAuthProviderRegistry(List<SocialAuthProvider> providers) {
        this.providers = new EnumMap<>(SocialProvider.class);
        providers.forEach(provider -> this.providers.put(provider.provider(), provider));
    }

    public SocialAuthProvider get(SocialProvider provider) {
        SocialAuthProvider socialAuthProvider = providers.get(provider);
        if (socialAuthProvider == null) {
            throw new CustomException(ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED, "지원하지 않는 소셜 제공자입니다.");
        }
        return socialAuthProvider;
    }
}

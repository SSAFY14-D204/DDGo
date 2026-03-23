package com.ssafy.DDGo.users.application.social;

import com.ssafy.DDGo.users.domain.SocialProvider;
import com.ssafy.DDGo.users.dto.request.SocialLoginRequest;

public interface SocialAuthProvider {

    SocialProvider provider();

    SocialUserProfile getUserProfile(SocialLoginRequest request);
}

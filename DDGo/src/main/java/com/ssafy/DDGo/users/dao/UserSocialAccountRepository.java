package com.ssafy.DDGo.users.dao;

import com.ssafy.DDGo.users.domain.SocialProvider;
import com.ssafy.DDGo.users.domain.UserSocialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

    Optional<UserSocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    Optional<UserSocialAccount> findByUserIdAndProvider(Long userId, SocialProvider provider);

    List<UserSocialAccount> findAllByUserId(Long userId);

    @Query(value = "SELECT COUNT(*) FROM user_social_accounts WHERE provider = ?1 AND provider_user_id = ?2",
            nativeQuery = true)
    long countByProviderAndProviderUserIdIncludingDeleted(String provider, String providerUserId);
}

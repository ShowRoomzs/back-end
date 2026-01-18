package showroomz.domain.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.social.entity.SocialLoginPolicy;
import showroomz.api.app.auth.entity.ProviderType;

import java.util.Optional;

public interface SocialLoginPolicyRepository extends JpaRepository<SocialLoginPolicy, Long> {
    Optional<SocialLoginPolicy> findByProviderType(ProviderType providerType);
}

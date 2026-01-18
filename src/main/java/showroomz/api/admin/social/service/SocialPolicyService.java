package showroomz.api.admin.social.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.domain.social.entity.SocialLoginPolicy;
import showroomz.domain.social.repository.SocialLoginPolicyRepository;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SocialPolicyService {

    private final SocialLoginPolicyRepository policyRepository;

    /**
     * 소셜 로그인 가능 여부 확인 (로그인 시 호출)
     */
    @Transactional(readOnly = true)
    public void validateProviderActive(ProviderType providerType) {
        SocialLoginPolicy policy = policyRepository.findByProviderType(providerType)
                .orElseGet(() -> SocialLoginPolicy.builder()
                        .providerType(providerType)
                        .isActive(true) // 기본값: 활성
                        .build());

        if (!policy.isActive()) {
            throw new BusinessException(ErrorCode.SOCIAL_LOGIN_SUSPENDED);
        }
    }

    /**
     * 소셜 로그인 상태 변경 (관리자용)
     */
    @Transactional
    public void updateProviderStatus(ProviderType providerType, boolean isActive) {
        SocialLoginPolicy policy = policyRepository.findByProviderType(providerType)
                .orElseGet(() -> SocialLoginPolicy.builder()
                        .providerType(providerType)
                        .isActive(true)
                        .build());
        
        // 기존 데이터가 없으면 저장, 있으면 업데이트
        if (policy.getId() == null) {
            policy.updateStatus(isActive);
            policyRepository.save(policy);
        } else {
            policy.updateStatus(isActive);
        }
    }

    /**
     * 전체 소셜 로그인 상태 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> getAllProviderStatuses() {
        Map<String, Boolean> statusMap = new HashMap<>();
        
        // 지원하는 ProviderType 목록
        ProviderType[] supportedProviders = {
            ProviderType.GOOGLE,
            ProviderType.NAVER,
            ProviderType.KAKAO,
            ProviderType.APPLE
        };
        
        for (ProviderType providerType : supportedProviders) {
            SocialLoginPolicy policy = policyRepository.findByProviderType(providerType)
                    .orElseGet(() -> SocialLoginPolicy.builder()
                            .providerType(providerType)
                            .isActive(true) // 기본값: 활성
                            .build());
            
            statusMap.put(providerType.name(), policy.isActive());
        }
        
        return statusMap;
    }
}

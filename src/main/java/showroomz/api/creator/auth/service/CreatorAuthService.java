package showroomz.api.creator.auth.service;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.DTO.SocialLoginRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.service.AuthService;
import showroomz.api.app.auth.service.SocialLoginService;
import showroomz.api.app.auth.service.SocialLoginService.SocialLoginResult;
import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.ClientUtils;

import java.util.Date;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorAuthService {

    private static final long REGISTER_TOKEN_EXPIRY_MSEC = 5 * 60 * 1000;

    private final CreatorRepository creatorRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final UserRepository userRepository;
    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final AuthTokenProvider tokenProvider;

    @Transactional
    public TokenResponse socialLogin(HttpServletRequest request, SocialLoginRequest socialLoginRequest) {
        if (socialLoginRequest.getToken() == null || socialLoginRequest.getToken().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_TOKEN);
        }
        if (socialLoginRequest.getProviderType() == null || socialLoginRequest.getProviderType().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_PROVIDER_TYPE);
        }

        ProviderType providerType;
        try {
            providerType = ProviderType.valueOf(socialLoginRequest.getProviderType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_PROVIDER);
        }

        SocialLoginResult result;
        try {
            if (providerType == ProviderType.APPLE && socialLoginRequest.getName() != null) {
                result = socialLoginService.loginOrSignup(
                        providerType,
                        socialLoginRequest.getToken(),
                        socialLoginRequest.getName()
                );
            } else {
                result = socialLoginService.loginOrSignup(
                        providerType,
                        socialLoginRequest.getToken()
                );
            }
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message.contains("유효하지 않은") || message.contains("토큰") || message.contains("만료")) {
                throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
            }
            if (message.contains("이미 다른 계정에서 사용 중인 이메일")) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Users user = result.getUser();
        validateCreatorLoginEligibility(user.getId(), user.getRoleType());

        Creator creator = creatorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        authService.saveLoginHistory(
                user.getId(),
                ClientUtils.getRemoteIP(request),
                ClientUtils.getUserAgent(request)
        );

        if (Boolean.TRUE.equals(creator.getIsNewMember())) {
            return createRegisterTokenResponse(user);
        }

        return authService.generateTokens(
                user.getUsername(),
                user.getRoleType(),
                user.getId(),
                false
        );
    }

    @Transactional
    public TokenResponse completeRegistration(String registerTokenStr, CreatorCompleteRegistrationRequest request) {
        if (registerTokenStr == null || registerTokenStr.isEmpty()) {
            throw new BusinessException(ErrorCode.REGISTER_EXPIRED);
        }

        AuthToken registerToken = tokenProvider.convertAuthToken(registerTokenStr);
        if (!registerToken.validate()) {
            throw new BusinessException(ErrorCode.REGISTER_EXPIRED);
        }

        Claims claims = registerToken.getTokenClaims();
        if (claims == null) {
            throw new BusinessException(ErrorCode.REGISTER_EXPIRED);
        }

        String username = claims.getSubject();
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRoleType() != RoleType.CREATOR) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        Creator creator = creatorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        if (!Boolean.TRUE.equals(creator.getIsNewMember())) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        validateBusinessFields(request);
        validateShowroomNameAvailable(request.getShowroomName());

        boolean isBusiness = request.getBusinessType() == CreatorBusinessType.BUSINESS;
        creator.completeRegistration(
                request.getShowroomName(),
                request.getBusinessType(),
                isBusiness ? request.getBusinessRegistrationNumber() : null,
                isBusiness ? request.getBusinessLicenseImageUrl() : null,
                request.getBankName(),
                request.getAccountNumber(),
                request.getBankBookImageUrl()
        );

        return authService.generateTokens(
                user.getUsername(),
                user.getRoleType(),
                user.getId(),
                false
        );
    }

    private TokenResponse createRegisterTokenResponse(Users user) {
        Date now = new Date();
        AuthToken registerToken = tokenProvider.createAuthToken(
                user.getUsername(),
                new Date(now.getTime() + REGISTER_TOKEN_EXPIRY_MSEC)
        );
        return new TokenResponse(registerToken.getToken(), RoleType.CREATOR.toString());
    }

    private void validateCreatorLoginEligibility(Long userId, RoleType roleType) {
        creatorApplicationRepository.findTopByUser_IdOrderByCreatedAtDesc(userId)
                .ifPresent(this::validateApplicationStatus);

        if (roleType != RoleType.CREATOR) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }
    }

    private void validateApplicationStatus(CreatorApplication application) {
        if (application.getStatus() == CreatorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_APPROVED);
        }
        if (application.getStatus() == CreatorApplicationStatus.REJECTED) {
            String rejectReason = application.getRejectReason();
            if (rejectReason != null && !rejectReason.isBlank()) {
                throw new BusinessException(
                        ErrorCode.ACCOUNT_REJECTED_WITH_REASON,
                        String.format("가입 승인이 반려되었습니다. 반려 사유: %s", rejectReason)
                );
            }
            throw new BusinessException(ErrorCode.ACCOUNT_REJECTED);
        }
    }

    private void validateBusinessFields(CreatorCompleteRegistrationRequest request) {
        if (request.getBusinessType() != CreatorBusinessType.BUSINESS) {
            return;
        }

        if (request.getBusinessRegistrationNumber() == null || request.getBusinessRegistrationNumber().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.getBusinessLicenseImageUrl() == null || request.getBusinessLicenseImageUrl().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateShowroomNameAvailable(String showroomName) {
        if (creatorRepository.existsByShowroomName(showroomName)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SHOWROOM_NAME);
        }
    }
}

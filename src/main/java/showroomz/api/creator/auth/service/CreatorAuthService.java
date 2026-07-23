package showroomz.api.creator.auth.service;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.DTO.SocialLoginRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.service.AuthService;
import showroomz.api.app.auth.service.SocialLoginService.SocialLoginResult;
import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;
import showroomz.api.creator.auth.DTO.ShowroomNameCheckResponse;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorAuthService {

    private static final long REGISTER_TOKEN_EXPIRY_MSEC = 5 * 60 * 1000;
    private static final Pattern SHOWROOM_NAME_PATTERN =
            Pattern.compile("^([가-힣0-9]+|[a-zA-Z0-9]+)$");

    private final CreatorRepository creatorRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuthTokenProvider tokenProvider;

    @Transactional
    public TokenResponse socialLogin(HttpServletRequest request, SocialLoginRequest socialLoginRequest) {
        // 가입 없이 기존 계정만 조회 (계정 없으면 예외, GUEST 생성 없음)
        SocialLoginResult result = authService.authenticateSocial(socialLoginRequest, false);
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

    public ShowroomNameCheckResponse checkShowroomName(String showroomName) {
        if (showroomName == null || showroomName.isBlank()) {
            return new ShowroomNameCheckResponse(
                    false,
                    "INVALID_FORMAT",
                    "쇼룸명은 필수 입력값입니다."
            );
        }

        if (!SHOWROOM_NAME_PATTERN.matcher(showroomName).matches()) {
            return new ShowroomNameCheckResponse(
                    false,
                    "INVALID_FORMAT",
                    "쇼룸명은 공백과 특수문자를 사용할 수 없으며, 한글 또는 영문 중 하나만 사용해야 합니다."
            );
        }

        if (creatorRepository.existsByShowroomName(showroomName)) {
            return new ShowroomNameCheckResponse(
                    false,
                    "DUPLICATE",
                    "이미 사용 중인 쇼룸명입니다."
            );
        }

        return new ShowroomNameCheckResponse(
                true,
                "AVAILABLE",
                "사용 가능한 쇼룸명입니다."
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
                throw new BusinessException(ErrorCode.ACCOUNT_REJECTED_WITH_REASON, rejectReason);
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

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
import showroomz.api.creator.auth.DTO.CreatorRegistrationInfoResponse;
import showroomz.api.creator.auth.DTO.ShowroomNameCheckResponse;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.connection.service.OperatorChannelService;
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
import showroomz.global.utils.ConnectionCodeGenerator;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorAuthService {

    private static final long REGISTER_TOKEN_EXPIRY_MSEC = 5 * 60 * 1000;
    private static final Pattern SHOWROOM_NAME_PATTERN =
            Pattern.compile("^[가-힣a-zA-Z0-9 ]{2,20}$");
    private static final String SHOWROOM_NAME_FORMAT_MESSAGE =
            "쇼룸명은 2~20자, 한글·영문·숫자·공백만 사용할 수 있습니다.";

    private final CreatorRepository creatorRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final AuthService authService;
    private final AuthTokenProvider tokenProvider;
    private final OperatorChannelService operatorChannelService;

    @Transactional
    public TokenResponse socialLogin(HttpServletRequest request, SocialLoginRequest socialLoginRequest) {
        // 가입 없이 기존 계정만 조회 (계정 없으면 예외, GUEST 생성 없음)
        SocialLoginResult result = authService.authenticateSocial(socialLoginRequest, false);
        Users user = result.getUser();

        TokenResponse ineligibleResponse = resolveIneligibleCreatorLogin(user, request);
        if (ineligibleResponse != null) {
            return ineligibleResponse;
        }

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
        Creator creator = resolveNewCreatorFromRegisterToken(registerTokenStr);
        Users user = creator.getUser();

        validateBusinessFields(request);
        validateShowroomNameAvailable(request.getShowroomName());

        Bank bank = bankRepository.findById(request.getBankCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_NOT_FOUND));

        boolean isBusiness = request.getBusinessType() == CreatorBusinessType.BUSINESS;
        creator.completeRegistration(
                request.getShowroomName(),
                request.getBusinessType(),
                isBusiness ? request.getBusinessRegistrationNumber() : null,
                isBusiness ? request.getBusinessLicenseImageUrl() : null,
                bank.getName(),
                request.getAccountNumber(),
                request.getBankBookImageUrl()
        );
        // §13-6 — 연결코드는 등록 완료 시 쇼룸별로 고정 발급된다(재발급은 §14-1 연결·소통 영역에서 별도 처리).
        creator.reissueConnectionCode(ConnectionCodeGenerator.generateUnique(creatorRepository::existsByConnectionCode));

        // §14-6 운영자 고정 채널 — 안내 문구가 쇼룸명을 쓰므로 승인 시점이 아니라 쇼룸명이 확정되는 이 시점에 연다.
        operatorChannelService.ensureCreatorChannel(creator);

        return authService.generateTokens(
                user.getUsername(),
                user.getRoleType(),
                user.getId(),
                false
        );
    }

    public CreatorRegistrationInfoResponse getRegistrationInfo(String registerTokenStr) {
        return CreatorRegistrationInfoResponse.from(resolveNewCreatorFromRegisterToken(registerTokenStr));
    }

    private Creator resolveNewCreatorFromRegisterToken(String registerTokenStr) {
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

        return creator;
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
                    SHOWROOM_NAME_FORMAT_MESSAGE
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

    /**
     * 크리에이터 권한이 아닌 로그인 시도 처리.
     * - 신청 이력 없음 / 반려 후 재신청 가능일 경과: USER access·refresh 토큰 + 사유(code/message) 반환
     * - 반려(재신청 가능일 이전): 토큰 없이 code(ACCOUNT_REJECTED), rejectReasonType, rejectReasonDetail, reapplyAvailableAt 반환
     * - 승인 대기(PENDING): 기존과 동일하게 예외
     * - 승인된 크리에이터: null 반환 후 정상 크리에이터 로그인 진행
     */
    private TokenResponse resolveIneligibleCreatorLogin(Users user, HttpServletRequest request) {
        var latestApplication = creatorApplicationRepository
                .findTopByUser_IdOrderByCreatedAtDesc(user.getId());

        if (latestApplication.isPresent()) {
            CreatorApplication application = latestApplication.get();
            if (application.getStatus() == CreatorApplicationStatus.PENDING) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_APPROVED);
            }
            if (application.getStatus() == CreatorApplicationStatus.REJECTED) {
                if (!LocalDateTime.now().isBefore(application.resolveReapplyAvailableAt())) {
                    return issueNoApplicationHistoryResponse(user, request);
                }
                return createRejectedApplicationResponse(application);
            }
            // APPROVED
            if (user.getRoleType() != RoleType.CREATOR) {
                return issueUserTokenWithReason(
                        user, request, ErrorCode.ACCOUNT_ROLE_MISMATCH, ErrorCode.ACCOUNT_ROLE_MISMATCH.getMessage());
            }
            return null;
        }

        // 신청 이력 없음
        return issueNoApplicationHistoryResponse(user, request);
    }

    private TokenResponse issueNoApplicationHistoryResponse(Users user, HttpServletRequest request) {
        if (user.getRoleType() == RoleType.CREATOR) {
            return null;
        }
        return issueUserTokenWithReason(
                user,
                request,
                ErrorCode.ACCOUNT_ROLE_MISMATCH,
                "크리에이터 권한 신청 이력이 없습니다."
        );
    }

    private TokenResponse createRejectedApplicationResponse(CreatorApplication application) {
        TokenResponse response = new TokenResponse();
        response.setTokenType(null);
        response.setCode(ErrorCode.ACCOUNT_REJECTED.getCode());
        response.setRejectReasonType(application.getRejectReasonType());
        response.setRejectReasonDetail(application.getRejectReasonDetail());
        response.setReapplyAvailableAt(application.resolveReapplyAvailableAt());
        return response;
    }

    private TokenResponse issueUserTokenWithReason(
            Users user,
            HttpServletRequest request,
            ErrorCode errorCode,
            String message
    ) {
        if (user.getRoleType() != RoleType.USER) {
            throw new BusinessException(errorCode, message);
        }

        authService.saveLoginHistory(
                user.getId(),
                ClientUtils.getRemoteIP(request),
                ClientUtils.getUserAgent(request)
        );

        TokenResponse response = authService.generateTokens(
                user.getUsername(),
                RoleType.USER,
                user.getId(),
                false
        );
        response.setCode(errorCode.getCode());
        response.setMessage(message);
        return response;
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

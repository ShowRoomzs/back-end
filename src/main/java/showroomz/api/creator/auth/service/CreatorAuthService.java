package showroomz.api.creator.auth.service;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.DTO.SocialLoginRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.refreshToken.UserRefreshTokenRepository;
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
import showroomz.domain.member.creator.type.ResidentRegistrationNumber;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.ClientUtils;
import showroomz.global.utils.ConnectionCodeGenerator;
import showroomz.global.utils.PersonalDataCipher;
import showroomz.global.utils.ShowroomNamePolicy;
import showroomz.global.utils.ShowroomAddressGenerator;

import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorAuthService {

    private static final long REGISTER_TOKEN_EXPIRY_MSEC = 5 * 60 * 1000;

    private final CreatorRepository creatorRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final AuthService authService;
    private final AuthTokenProvider tokenProvider;
    private final PersonalDataCipher personalDataCipher;
    private final OperatorChannelService operatorChannelService;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

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
        // 1009 기획 7-a — 비사업자 주민등록번호(원천징수 신고용). 암호문과 마스킹 값만 저장한다.
        String residentNumber = isBusiness ? null
                : ResidentRegistrationNumber.normalize(request.getResidentRegistrationNumber());
        creator.registerResidentNumber(residentNumber == null ? null : personalDataCipher.encrypt(residentNumber),
                residentNumber == null ? null : ResidentRegistrationNumber.mask(residentNumber));
        // §13-6 — 연결코드는 등록 완료 시 쇼룸별로 고정 발급된다(재발급은 §14-1 연결·소통 영역에서 별도 처리).
        creator.reissueConnectionCode(ConnectionCodeGenerator.generateUnique(creatorRepository::existsByConnectionCode));

        // §22-1 · 온보딩 rev.3 [정정] — 쇼룸 주소는 계정 아이디(인스타그램 핸들) 기준으로 이 시점에 발급되고 이후 바뀌지 않는다.
        creator.assignShowroomAddressIfAbsent(showroomAddressFor(creator));
        // §22-1 — 공개용 인스타그램 URL의 기본값은 온보딩 채널 주소다(쇼룸 관리에서 독립 수정한다).
        creator.initializeInstagramUrlFromChannel();

        // §14-6 운영자 고정 채널 — 안내 문구가 쇼룸명을 쓰므로 승인 시점이 아니라 쇼룸명이 확정되는 이 시점에 연다.
        operatorChannelService.ensureCreatorChannel(creator);

        return authService.generateTokens(
                user.getUsername(),
                user.getRoleType(),
                user.getId(),
                false
        );
    }

    /**
     * 온보딩 화면의 읽기 전용 값 — 계정 아이디 · 예금주(본인 인증 실명 마스킹) · 쇼룸 주소 미리보기. 주소는 계정 아이디로 정해지므로
     * 쇼룸명을 입력하기 전에도 보여줄 수 있다. 미리보기는 선점이 아니다 — 제출 사이에 같은 핸들이 쓰이면 제출 때 꼬리표가 붙는다.
     */
    public CreatorRegistrationInfoResponse getRegistrationInfo(String registerTokenStr) {
        Creator creator = resolveNewCreatorFromRegisterToken(registerTokenStr);
        return CreatorRegistrationInfoResponse.from(creator, showroomAddressFor(creator));
    }

    private String showroomAddressFor(Creator creator) {
        if (creator.getShowroomAddress() != null && !creator.getShowroomAddress().isBlank()) {
            return creator.getShowroomAddress();
        }
        return ShowroomAddressGenerator.generateUnique(creator.getAccountId(), creatorRepository::existsByShowroomAddress);
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

        if (!ShowroomNamePolicy.isValidFormat(showroomName)) {
            return new ShowroomNameCheckResponse(
                    false,
                    "INVALID_FORMAT",
                    ShowroomNamePolicy.FORMAT_MESSAGE
            );
        }

        if (creatorRepository.existsByShowroomName(showroomName)) {
            return new ShowroomNameCheckResponse(
                    false,
                    "DUPLICATE",
                    ErrorCode.DUPLICATE_SHOWROOM_NAME.getMessage()
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
            // 비사업자는 주민등록번호가 필수다 — 원문을 메시지에 싣지 않는다.
            if (ResidentRegistrationNumber.normalize(request.getResidentRegistrationNumber()) == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호를 정확히 입력해 주세요.");
            }
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

    /**
     * 스튜디오 로그아웃(1017 설계서 5-4) — 이 세션의 refresh token 만 폐기한다. 인플루언서 로그인도 소비자 계정 refresh token 을 쓰므로
     * {@code /v1/user/auth/logout} 과 같은 저장소다. <b>앱 기기 푸시 토큰은 건드리지 않는다</b> — 스튜디오(웹)에서 로그아웃했다고
     * 소비자 앱 알림이 끊기면 안 된다. 토큰이 없거나 이미 폐기된 경우에도 성공으로 본다(멱등).
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REFRESH_TOKEN_LOGOUT);
        }
        userRefreshTokenRepository.deleteByRefreshToken(refreshToken);
    }
}

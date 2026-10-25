package showroomz.api.app.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.DTO.IdentityVerificationCompleteRequest;
import showroomz.api.app.auth.DTO.IdentityVerificationCompleteResponse;
import showroomz.api.app.auth.DTO.IdentityVerificationStartResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.IdentityVerification;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.IdentityVerificationRepository;
import showroomz.domain.member.user.type.IdentityFailReason;
import showroomz.domain.member.user.type.IdentityVerificationPurpose;
import showroomz.domain.member.user.type.IdentityVerificationStatus;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneProperties;
import showroomz.global.payment.portone.identity.FakeIdentityGateway;
import showroomz.global.payment.portone.identity.IdentityVerificationResult;
import showroomz.global.payment.portone.identity.PortOneIdentityGateway;
import showroomz.global.utils.MaskingUtils;
import showroomz.global.utils.PersonalDataCipher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * C0-2 · C15-2 본인인증 — 포트원 V2(KCP 휴대폰 본인확인) · 57 설계서 3 · 4절.
 *
 * <p>흐름은 「앱 → 포트원 SDK → 서버 조회」다(0-1). {@link #start} 가 id · storeId · channelKey 를 발급해 READY 행을 남기고, 앱이 SDK 창을
 * 끝낸 뒤 {@link #complete} 를 부르면 서버가 포트원에 직접 물어 판정(14세 · DI 누락 · 중복)하고 VERIFIED 로 보관한다. 가입
 * ({@link #consumeForSignup}) · 재인증({@link #consumeForReverify}) 은 그 행을 {@link Users} 로 옮기고 CONSUMED 로 닫는다.
 * 앱이 보낸 값은 기록만 하고 판정에 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    /** 가입 가능 최소 연령 (정보통신망법상 만 14세) */
    public static final int MIN_AGE = 14;
    /** 연타 멱등 창(3-1) — 이 안에 같은 용도의 READY 행이 있으면 새 id 를 찍지 않는다. */
    static final int REUSE_READY_SECONDS = 60;

    private final IdentityVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final PortOneIdentityGateway gateway;
    private final PersonalDataCipher cipher;
    private final PortOneProperties properties;

    // ------------------------------------------------------------------ 시작

    @Transactional
    public IdentityVerificationStartResponse start(String username, IdentityVerificationPurpose purpose, LocalDateTime now) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (purpose == IdentityVerificationPurpose.SIGNUP && user.getRoleType() != RoleType.GUEST) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        if (purpose == IdentityVerificationPurpose.REVERIFY) {
            if (user.getStatus() == UserStatus.WITHDRAWN) {
                throw new BusinessException(ErrorCode.USER_WITHDRAWN);
            }
            if (user.getRoleType() == RoleType.GUEST) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        }

        IdentityVerification row = verificationRepository
                .findRecentReady(user.getId(), purpose, now.minusSeconds(REUSE_READY_SECONDS))
                .orElseGet(() -> verificationRepository.save(IdentityVerification.ready(
                        IdentityVerificationIdGenerator.next(), user.getId(), purpose, gateway.channelKey(),
                        now, now.plusMinutes(properties.getIdentity().getTtlMinutes()))));
        if (gateway instanceof FakeIdentityGateway fake) {
            fake.bind(row.getId(), username);
        }
        return new IdentityVerificationStartResponse(row.getId(), gateway.storeId(), gateway.channelKey(), row.getExpiresAt());
    }

    // ------------------------------------------------------------------ 완료

    /** 실패도 행에 남겨야 한다(FAILED · 사유) — BusinessException 으로 거절하면서 상태 변경은 커밋한다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public IdentityVerificationCompleteResponse complete(String username, String identityVerificationId,
                                                         IdentityVerificationCompleteRequest request, LocalDateTime now) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        IdentityVerification row = verificationRepository.findById(identityVerificationId)
                .filter(v -> v.isOwnedBy(user.getId()))
                // 남의 id 를 찔러도 존재 여부를 흘리지 않는다(3-2 ①)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_NOT_FOUND));
        if (request != null) {
            row.recordClientResult(request.getIdentityVerificationTxId(), request.getCode(), request.getPgCode());
        }

        switch (row.getStatus()) {
            case CONSUMED -> throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_CONSUMED);
            // 저장된 사유 그대로(3-2 ②) — 포트원을 다시 묻지 않고, 처음 닫을 때와 같은 코드라 앱이 같은 화면(1d · 1e)으로 간다
            case FAILED -> throw new BusinessException(errorOf(row.getFailReason()));
            case VERIFIED -> {
                if (!row.isExpired(now)) {
                    return response(row);                       // 멱등
                }
            }
            case READY -> { /* 아래로 */ }
        }
        if (row.isExpired(now)) {
            row.failed(IdentityFailReason.NOT_COMPLETED, null, null, null);
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_EXPIRED);
        }

        IdentityVerificationResult result = lookup(identityVerificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_NOT_COMPLETED));
        if (result.isFailed()) {
            IdentityVerificationResult.Failure f = result.failure();
            row.failed(IdentityFailReason.PG_FAILED, f == null ? null : f.pgCode(), f == null ? null : f.pgMessage(), result.pgTxId());
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
        if (!result.isVerified()) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_NOT_COMPLETED);
        }

        IdentityVerificationResult.VerifiedCustomer c = result.customer();
        // ⑤ 14세 — 개인정보 없이 닫는다(0-8)
        if (isUnderMinAge(c.birthDate(), now.toLocalDate())) {
            row.failed(IdentityFailReason.UNDER_MIN_AGE, null, null, result.pgTxId());
            throw new BusinessException(ErrorCode.UNDER_MIN_AGE);
        }
        // ⑥ DI — KCP 는 항상 준다. 비면 설정 오류(0-5)
        if (c.di() == null || c.di().isBlank()) {
            log.error("본인인증 응답에 DI 가 없습니다 — KCP 채널 설정을 확인하세요. id: {}", identityVerificationId);
            row.failed(IdentityFailReason.DI_MISSING, null, "DI 누락", result.pgTxId());
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
        // ⑦ 중복 — 같은 DI 의 다른 활성 회원
        String diHash = cipher.hmac(c.di());
        if (properties.getIdentity().isDuplicateBlock()) {
            Optional<Users> other = userRepository.findActiveByIdentityDiHash(diHash).filter(u -> !u.getId().equals(user.getId()));
            if (other.isPresent()) {
                row.failed(IdentityFailReason.DUPLICATE_IDENTITY, null, null, result.pgTxId());
                throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
            }
        }
        if (row.getPurpose() == IdentityVerificationPurpose.REVERIFY
                && user.getIdentityDiHash() != null && !user.getIdentityDiHash().equals(diHash)) {
            // 타인 명의 재인증 — 정책 미정(13절 #7). 허용하되 흔적을 남긴다.
            log.warn("재인증 결과의 명의가 기존과 다릅니다 - userId: {}, identityVerificationId: {}", user.getId(), identityVerificationId);
        }
        if (c.ci() == null || c.ci().isBlank()) {
            log.warn("본인인증 응답에 CI 가 없습니다 — KCP 가맹점 CI 제공 설정을 확인하세요. id: {}", identityVerificationId);
        }

        row.verified(c.name(), c.birthDate(), c.gender(), c.phoneNumber(), c.operator(), c.foreigner(),
                cipher.encrypt(c.ci()), cipher.encrypt(c.di()), result.pgTxId(),
                result.verifiedAt() == null ? now : result.verifiedAt());
        return response(row);
    }

    private Optional<IdentityVerificationResult> lookup(String id) {
        try {
            return gateway.find(id);
        } catch (PaymentGatewayRejectedException e) {
            // 자격 · 채널 설정 오류 — 앱이 다시 눌러도 같다. 운영이 봐야 한다.
            log.error("포트원 본인인증 조회 거절 - id: {}, type: {}", id, e.getType());
            throw new BusinessException(ErrorCode.IDENTITY_GATEWAY_ERROR);
        } catch (PaymentGatewayException e) {
            log.warn("포트원 본인인증 조회 실패(결과 모름) - id: {} - {}", id, e.getMessage());
            throw new BusinessException(ErrorCode.IDENTITY_GATEWAY_ERROR);
        }
    }

    /** 닫힌 행의 사유 → 처음 닫을 때 낸 코드. 사유가 없는 옛 행은 일반 실패다. */
    static ErrorCode errorOf(IdentityFailReason reason) {
        if (reason == null) {
            return ErrorCode.IDENTITY_VERIFICATION_FAILED;
        }
        return switch (reason) {
            case UNDER_MIN_AGE -> ErrorCode.UNDER_MIN_AGE;
            case DUPLICATE_IDENTITY -> ErrorCode.IDENTITY_ALREADY_REGISTERED;
            case NOT_COMPLETED -> ErrorCode.IDENTITY_VERIFICATION_EXPIRED;
            case PG_FAILED, DI_MISSING -> ErrorCode.IDENTITY_VERIFICATION_FAILED;
        };
    }

    private static IdentityVerificationCompleteResponse response(IdentityVerification row) {
        return new IdentityVerificationCompleteResponse(IdentityVerificationStatus.VERIFIED, row.getPurpose(),
                MaskingUtils.maskName(row.getVerifiedName()), MaskingUtils.maskPhoneNumber(row.getVerifiedPhone()), row.getVerifiedAt());
    }

    // ------------------------------------------------------------------ 소비

    /** 회원가입(3-3) — 10분 내 VERIFIED · 미소비 SIGNUP 행을 Users 로 옮긴다. 없으면 400. */
    @Transactional
    public void consumeForSignup(Users user, LocalDateTime now) {
        consume(user, IdentityVerificationPurpose.SIGNUP, now);
    }

    /** 재인증(3-4) — REVERIFY 행만. SIGNUP 행을 집어 쓰지 못한다(0-4). */
    @Transactional
    public void consumeForReverify(Users user, LocalDateTime now) {
        consume(user, IdentityVerificationPurpose.REVERIFY, now);
    }

    private void consume(Users user, IdentityVerificationPurpose purpose, LocalDateTime now) {
        IdentityVerification row = verificationRepository.findLatestVerifiedUnconsumed(user.getId(), purpose, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_REQUIRED));
        String di = cipher.decrypt(row.getVerifiedDiEnc());
        user.applyIdentity(row.getVerifiedName(), row.getVerifiedBirthDate(), row.getVerifiedGender(), row.getVerifiedPhone(),
                row.getVerifiedOperator(), row.getVerifiedForeigner(), row.getVerifiedCiEnc(), row.getVerifiedDiEnc(),
                cipher.hmac(di), row.getVerifiedAt());
        row.consume(now);
        try {
            // UNIQUE(identity_di_hash) — 같은 DI 두 건이 동시에 소비되는 경쟁의 2차 방어(1-1). 서비스 판정은 complete 가 했다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }
    }

    // ------------------------------------------------------------------ 판정

    /**
     * 인증 결과 생년월일 기준 만 14세 미만 여부. 생년월일을 해석할 수 없으면 가입을 막지 않고 false 를 반환한다
     * (인증 단계에서 이미 걸러진 값이므로).
     */
    public static boolean isUnderMinAge(String birthDate, LocalDate today) {
        if (birthDate == null || birthDate.isEmpty()) {
            return false;
        }
        try {
            return Period.between(LocalDate.parse(birthDate), today).getYears() < MIN_AGE;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}

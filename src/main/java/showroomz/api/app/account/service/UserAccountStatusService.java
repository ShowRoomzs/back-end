package showroomz.api.app.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.account.dto.AccountStatusResponse;
import showroomz.api.app.account.dto.AccountStatusResponse.ReleaseNotice;
import showroomz.api.app.account.dto.AccountStatusResponse.Suspension;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 앱 계정 상태 · 1회 노출 확인(51-6 설계서 3절). 앱은 콜드 스타트 · 포그라운드 복귀 · 409 {@code USER_SUSPENDED} 수신 · 계정 상태 푸시 수신 때
 * 상태를 다시 읽는다.
 *
 * <p>「첫 로그인 직후 1회」는 <b>정지 후 첫 앱 진입</b>으로 읽는다 — 토큰이 살아 있어 다시 로그인하지 않는 사용자도 정지를 알아야 한다(0-7).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountStatusService {

    private final UserRepository userRepository;
    private final showroomz.domain.member.common.service.SanctionTermPolicy termPolicy;
    private final UserSanctionRepository sanctionRepository;
    private final OneToOneInquiryRepository inquiryRepository;

    public AccountStatusResponse getStatus(Long userId) {
        Users user = load(userId);
        if (user.getStatus() == UserStatus.SUSPENDED) {
            UserSanction open = sanctionRepository.findByUserIdAndReleasedAtIsNull(userId).orElse(null);
            return new AccountStatusResponse(UserStatus.SUSPENDED, suspension(open), null);
        }
        ReleaseNotice releaseNotice = sanctionRepository.findFirstByUserIdOrderBySuspendedAtDescIdDesc(userId)
                .filter(UserSanction::isReleaseNoticePending)
                .map(latest -> new ReleaseNotice(latest.getReleasedAt()))
                .orElse(null);
        return new AccountStatusResponse(user.getStatus(), null, releaseNotice);
    }

    /** 안내 시트를 보여줬다 — 열린 정지가 없으면(해제와 경합) 할 일이 없다. */
    @Transactional
    public void ackSuspensionNotice(Long userId) {
        load(userId);
        LocalDateTime now = LocalDateTime.now();
        sanctionRepository.findByUserIdAndReleasedAtIsNull(userId).ifPresent(sanction -> sanction.ackNotice(now));
    }

    /** 해제 토스트를 보여줬다 — 앱을 안 연 사이 정지 · 해제가 두 번 있었어도 토스트는 한 번이다. */
    @Transactional
    public void ackReleaseNotice(Long userId) {
        load(userId);
        LocalDateTime now = LocalDateTime.now();
        sanctionRepository.findByUserIdAndReleasedAtIsNotNullAndReleaseNoticeAckedAtIsNull(userId)
                .forEach(sanction -> sanction.ackReleaseNotice(now));
    }

    /** 정지 행 없이 정지된 옛 회원(옛 {@code PATCH /status})은 사유 · 일시를 지어내지 않는다 — 확인을 기록할 행도 없다. */
    private Suspension suspension(UserSanction open) {
        if (open == null) {
            return new Suspension(null, null, null, false, null, null);
        }
        Long pendingAppealId = inquiryRepository
                .findFirstBySanctionIdAndStatusOrderByIdDesc(open.getId(), InquiryStatus.WAITING)
                .map(OneToOneInquiry::getId)
                .orElse(null);
        return new Suspension(open.getReason().name(), open.getReason().getAppLabel(), open.getSuspendedAt(),
                open.isNoticePending(), pendingAppealId,
                showroomz.api.common.account.dto.MemberAccountStatusResponse.Term.of(open.getEndsOn(),
                        open.getSuspendedAt(), open.getAppealDeadlineAt(), termPolicy.appealReviewBusinessDays()));
    }

    private Users load(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }
        return user;
    }
}

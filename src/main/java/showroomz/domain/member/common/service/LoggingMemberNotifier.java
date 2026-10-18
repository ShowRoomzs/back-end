package showroomz.domain.member.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.member.common.type.MemberType;

/**
 * {@link MemberNotifier}의 <b>스텁</b> — 브랜드 · 인플루언서 알림 발송 인프라가 없어 로그만 남긴다(52 설계서 8-2 · 53 설계서 5절). 수신처
 * 원문(이메일)은 로그에 남기지 않는다 — 있는지만 적는다.
 */
@Slf4j
@Component
public class LoggingMemberNotifier implements MemberNotifier {

    @Override
    public void suspended(MemberType type, Long memberId, String reasonLabel) {
        log.info("[알림 스텁] {} 계정 정지 - id: {}, reason: {}", type.getLabel(), memberId, reasonLabel);
    }

    @Override
    public void released(MemberType type, Long memberId) {
        log.info("[알림 스텁] {} 계정 정지 해제 - id: {}", type.getLabel(), memberId);
    }

    @Override
    public void withdrawalApproved(MemberType type, Long memberId, String recipient) {
        log.info("[알림 스텁] {} 탈퇴 승인 - id: {}, hasRecipient: {}", type.getLabel(), memberId, recipient != null);
    }

    @Override
    public void withdrawalRejected(MemberType type, Long memberId, String reasonLabel) {
        log.info("[알림 스텁] {} 탈퇴 반려 - id: {}, reason: {}", type.getLabel(), memberId, reasonLabel);
    }

    @Override
    public void accountVerificationFailed(Long creatorId, String reasonLabel) {
        log.info("[알림 스텁] 인플루언서 정산 계좌 확인 실패 - creatorId: {}, reason: {}", creatorId, reasonLabel);
    }

    @Override
    public void businessTypeChangeApproved(Long creatorId, String businessEmail, String requestCode) {
        log.info("[알림 스텁] 인플루언서 사업자 구분 변경 승인 - creatorId: {}, hasRecipient: {}, code: {}", creatorId,
                businessEmail != null, requestCode);
    }

    @Override
    public void businessTypeChangeRejected(Long creatorId, String businessEmail, String requestCode, String reasonLabel) {
        log.info("[알림 스텁] 인플루언서 사업자 구분 변경 반려 - creatorId: {}, hasRecipient: {}, code: {}, reason: {}",
                creatorId, businessEmail != null, requestCode, reasonLabel);
    }
}

package showroomz.domain.member.creator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 인플루언서 제재 · 탈퇴 심사 알림 훅(§51-2 「사유가 알림으로 발송된다」 · 53 설계서 5절) — <b>스텁</b>이다. 알림 발송 인프라가 없어
 * 호출 지점만 두고 로그를 남긴다. 인프라가 생기면 이 클래스 한 곳이다. 운영팀 채널의 시스템 카드는 별도로 이미 등록된다.
 */
@Slf4j
@Component
public class CreatorMemberNotifier {

    public void suspended(Long creatorId, String reasonLabel) {
        log.info("[알림 스텁] 인플루언서 정지 - creatorId: {}, reason: {}", creatorId, reasonLabel);
    }

    public void released(Long creatorId) {
        log.info("[알림 스텁] 인플루언서 정지 해제 - creatorId: {}", creatorId);
    }

    public void withdrawalApproved(Long creatorId, String businessEmail) {
        log.info("[알림 스텁] 인플루언서 탈퇴 승인 - creatorId: {}, to: {}", creatorId, businessEmail);
    }

    public void withdrawalRejected(Long creatorId, String reasonLabel) {
        log.info("[알림 스텁] 인플루언서 탈퇴 반려 - creatorId: {}, reason: {}", creatorId, reasonLabel);
    }
}

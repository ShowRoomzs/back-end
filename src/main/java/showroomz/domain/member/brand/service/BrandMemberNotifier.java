package showroomz.domain.member.brand.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 브랜드 회원 조치 알림 훅(52 설계서 8-2) — 발송 인프라가 없어 로그만 남긴다. 「정지 사유가 알림으로 발송된다」(§51-2) · 반려 사유 통지(§51-5)는
 * 이 클래스 한 곳이 맡는다. 호출 지점은 전부 커밋 뒤다.
 */
@Slf4j
@Component
public class BrandMemberNotifier {

    public void suspended(Long marketId, String reasonLabel) {
        log.info("[알림 스텁] 브랜드 계정 정지 - marketId: {}, reason: {}", marketId, reasonLabel);
    }

    public void released(Long marketId) {
        log.info("[알림 스텁] 브랜드 계정 정지 해제 - marketId: {}", marketId);
    }

    /** 승인 시 수신처는 파기 전에 읽은 값이다. */
    public void withdrawalApproved(Long marketId, String email) {
        log.info("[알림 스텁] 브랜드 탈퇴 승인 - marketId: {}, hasRecipient: {}", marketId, email != null);
    }

    public void withdrawalRejected(Long marketId, String reasonLabel) {
        log.info("[알림 스텁] 브랜드 탈퇴 반려 - marketId: {}, reason: {}", marketId, reasonLabel);
    }
}

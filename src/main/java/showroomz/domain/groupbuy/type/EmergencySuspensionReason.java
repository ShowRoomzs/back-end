package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 긴급 직권 중단 사유 — 제17조③ 3종 고정. <b>ETC가 없다</b> — 자유 사유를 두면 급하지 않은 건이 새고
 * 사전 통지 제도가 형해화된다(§32-3). 라벨은 어드민 상세·이력 detail이 쓴다(32 설계 6-5).
 */
@Getter
@RequiredArgsConstructor
public enum EmergencySuspensionReason {
    CONSUMER_HARM("소비자 위해 방지"),
    AUTHORITY_ORDER("행정·사법기관의 명령"),
    DAMAGE_SURGE("피해 급증 우려");

    private final String label;
}

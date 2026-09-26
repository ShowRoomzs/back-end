package showroomz.domain.groupbuy.service.port;

import java.util.Optional;

/**
 * 정산 모듈 포트 — 어드민 정산 확인(32 설계 8-2 · 4-8 ⑤). <b>정산 상태머신은 공구에 두지 않는다</b>(30 설계 5-2) —
 * 공구가 아는 것은 {@code SETTLED} 하나고, 중간 단계(대기 · 운영자 확인)는 이 포트로 읽기만 한다.
 *
 * <p>정산 모듈이 생기기 전에는 {@link EmptyGroupBuySettlementGateway}가 empty를 돌려주고 정산 확인 버튼은 항상 닫힌다
 * — §33-4 #1 「정산 화면 착수 게이트」.
 */
public interface GroupBuySettlementGateway {

    /** 정산 단계 — 모르면 empty. 파트너·스튜디오 응답에는 싣지 않는다(운영자 내부 절차 · §32-6). */
    Optional<SettlementStage> readStage(Long groupBuyId);

    /** 정산대기 → 운영자 확인. 공구 테이블에 쓰지 않는다 — 확인 취소(이중 승인 반려)가 생겨도 두 곳이 어긋나지 않게. */
    void confirm(Long groupBuyId, Long operatorId);

    enum SettlementStage {
        WAITING, CONFIRMED, TRANSFERRED
    }
}

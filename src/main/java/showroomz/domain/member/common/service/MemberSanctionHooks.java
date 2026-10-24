package showroomz.domain.member.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.service.ContractSanctionHoldService;
import showroomz.domain.member.common.type.MemberType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 브랜드 · 인플루언서 정지 · 해제가 <b>상대방 쪽 진행 중 거래</b>에 미치는 효과(1017 설계서 8-7) — 정지 서비스 두 곳이 같은 훅을 부른다.
 *
 * <ul>
 *   <li>정지: 그 당사자의 체결 전 계약을 보류(서명 기한 정지) · 상대방에게 「상대 계정 사정으로 보류」 알림</li>
 *   <li>해제: 보류 해제(멈춘 만큼 서명 기한을 민다) · 상대방에게 「보류가 풀려 다시 진행」 알림</li>
 * </ul>
 *
 * <p>오픈 전 공구는 상태를 저장하지 않는다 — 오픈 스케줄러가 정지를 보고 건너뛰고, 화면의 「승인 보류」는 조립 시 파생한다(8-5).
 * 정지 사실 · 사유는 상대방에게 알리지 않는다(ui-partner-15 R1 · ui-studio-13 R1).
 */
@Component
@RequiredArgsConstructor
public class MemberSanctionHooks {

    private final ContractSanctionHoldService contractHolds;
    private final CounterpartyHoldNotifier counterpartyNotifier;

    @Transactional(propagation = Propagation.MANDATORY)
    public void onSuspended(MemberType type, Long memberId, LocalDateTime now) {
        switch (type) {
            case BRAND -> contractHolds.holdForMarket(memberId, now);
            case CREATOR -> contractHolds.holdForCreator(memberId, now);
            default -> {
                return;
            }
        }
        counterpartyNotifier.holdStarted(type, memberId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onReleased(MemberType type, Long memberId, LocalDateTime now) {
        List<Long> resumedContractIds = switch (type) {
            case BRAND -> contractHolds.resumeForMarket(memberId, now);
            case CREATOR -> contractHolds.resumeForCreator(memberId, now);
            default -> List.of();
        };
        if (type == MemberType.BRAND || type == MemberType.CREATOR) {
            counterpartyNotifier.holdResumed(type, memberId, resumedContractIds);
        }
    }
}

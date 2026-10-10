package showroomz.domain.settlement.adjustment.service;

import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.AdjustmentTurn;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;

import java.time.LocalDateTime;

/**
 * 차례 · 버튼 권한(44 이슈 스레드 설계서 1-5 · 3-8) — 서버가 전부 정한다. FE 는 식을 들지 않는다.
 *
 * <pre>
 * latest = seq 최대 제안
 * 협의 종결                         → CLOSED
 * latest PENDING · 뷰어가 제안자     → THEIR_TURN 「상대 응답 대기」
 * latest PENDING · 뷰어가 상대       → MY_TURN 「내 응답 필요」
 * latest REJECTED                   → OPEN_FLOOR 「협의 중」 — 양측 모두 제안 가능 · 반대한 쪽은 동의도 가능(§46 A-9 잠정)
 * </pre>
 * 반대 뒤의 차례는 시안이 없다 — 확정되면 이 클래스 한 곳만 고친다(10-2 #1). 뷰어가 운영자면(null) 버튼이 전부 닫힌다.
 */
public final class AdjustmentPermissionPolicy {

    private AdjustmentPermissionPolicy() {
    }

    public record Permissions(boolean canAccept, boolean canReject, boolean canCounter, boolean canSend) {
        public static final Permissions NONE = new Permissions(false, false, false, false);

        public boolean canRespond() {
            return canAccept || canReject || canCounter;
        }
    }

    public static AdjustmentTurn turnFor(AdjustmentStatus status, ProposalStatus latestStatus,
                                         SettlementParty latestProposer, SettlementParty viewer) {
        if (status != AdjustmentStatus.OPEN || latestStatus == null) {
            return AdjustmentTurn.CLOSED;
        }
        if (latestStatus == ProposalStatus.PENDING) {
            return viewer == latestProposer ? AdjustmentTurn.THEIR_TURN : AdjustmentTurn.MY_TURN;
        }
        return AdjustmentTurn.OPEN_FLOOR;
    }

    /** 전부 {@code OPEN ∧ now ≤ deadline_at} 전제. 「내가 보낸 요청 카드에는 처음부터 버튼이 없다」(T1)가 뷰어 = 제안자에서 나온다. */
    public static Permissions permissions(AdjustmentStatus status, LocalDateTime deadlineAt, ProposalStatus latestStatus,
                                          SettlementParty latestProposer, SettlementParty viewer, LocalDateTime now) {
        if (viewer == null || status != AdjustmentStatus.OPEN) {
            return new Permissions(false, false, false, viewer != null && status == AdjustmentStatus.OPEN);
        }
        boolean open = !now.isAfter(deadlineAt) && latestStatus != null;
        boolean otherSide = viewer != latestProposer;
        boolean pending = latestStatus == ProposalStatus.PENDING;
        boolean rejected = latestStatus == ProposalStatus.REJECTED;
        return new Permissions(
                open && (pending || rejected) && otherSide,
                open && pending && otherSide,
                open && ((pending && otherSide) || rejected),
                true);
    }
}

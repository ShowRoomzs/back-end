package showroomz.domain.order.service;

import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.UserClaimPhase;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 소비자 표시 단계의 유도(앱 클레임 설계서 4-2 표) — 라벨 · 보조 문구 · 목록 보조 · 할 일. <b>이 표가 정본이다</b> —
 * 주문 내역(C10) · 주문 상세(C10-1) · 반품·교환 상세(C10-5)가 같은 유도를 쓴다. 조회하지 않는다 — 입력은 호출자가
 * 읽어 둔 클레임(요청 포함)과 그 요청의 반려 재발송비 청구다.
 *
 * <p>로즈({@code actionRequired})는 고객이 해야 할 일이 있을 때만이다 — 회수 송장 미등록 · 재발송비 결제 필요 둘.
 * 시안에 없는 단계(검수 중 · 환불 대기 · 재발송 준비 · 나머지 검수 대기 · 폐기 종결)의 문구는 잠정이다(Q13).
 */
public final class UserClaimPresenter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");

    private UserClaimPresenter() {
    }

    public enum TodoType {
        REGISTER_COLLECTION_INVOICE, PAY_RESHIP_FEE
    }

    /** @param dueDate 기한 — 지났거나 없으면 null(라벨에도 날짜가 없다) */
    public record Todo(TodoType type, String label, LocalDate dueDate) {
    }

    /**
     * @param label          상세 상태 줄의 라벨 — 「반품 요청」 「교환 반려」
     * @param sub            상세 상태 줄의 보조 문구
     * @param actionRequired 로즈 — 고객이 해야 할 일이 있다
     * @param listSub        주문 내역 항목 행의 보조 문구 — 종결 단계는 null(항목이 원래 줄로 돌아간다)
     */
    public record View(UserClaimPhase phase, String label, String sub, boolean actionRequired, String listSub,
                       Todo todo) {
    }

    /**
     * @param rejectCharge 그 요청의 반려 재발송비 청구(가장 최근) — 없으면 null
     * @param today        기한이 지났는지의 기준
     */
    public static View present(OrderClaim claim, OrderClaimCharge rejectCharge, LocalDate today) {
        String type = claim.getType().getLabel();
        boolean rejected = claim.getRejectedAt() != null;
        OrderClaimCollection collection = claim.getCollection();
        return switch (claim.getStatus()) {
            case PAYMENT_PENDING -> view(UserClaimPhase.PAYMENT_PENDING, type + " 요청", "결제 대기", null);
            case REQUESTED -> {
                LocalDate due = notPassed(collection.getInvoiceDueAt().toLocalDate(), today);
                yield new View(UserClaimPhase.REQUESTED, type + " 요청", "회수 송장 입력 필요", true, "진행 중 · 요청",
                        new Todo(TodoType.REGISTER_COLLECTION_INVOICE, withDue("회수 송장 등록 필요", due), due));
            }
            case COLLECTING -> view(UserClaimPhase.COLLECTING, "회수중", collection.hasInvoice()
                    ? collection.getCarrier().getLabel() + " " + collection.getTrackingNumber() : null,
                    "진행 중 · 회수중");
            case ARRIVED, RECEIVED -> view(UserClaimPhase.INSPECTING, "검수 중", "브랜드가 상품을 확인하고 있어요",
                    "진행 중 · 검수 중");
            case REFUND_PENDING -> view(UserClaimPhase.APPROVED, type + " 승인", "검수 완료 · 환불 예정", "환불 처리 중");
            case RESHIP_READY -> rejected
                    ? view(UserClaimPhase.REJECTED_PREPARING, type + " 반려", preparingSub(claim, rejectCharge), "검수 반려")
                    : view(UserClaimPhase.RESHIP_PREPARING, type + " 승인", "새 상품 발송 준비 중", "진행 중 · 재발송 준비");
            case RESHIPPING -> rejected
                    ? view(UserClaimPhase.REJECTED_RESHIPPING, type + " 반려", "반려 상품 재발송 중", "검수 반려 · 재발송")
                    : view(UserClaimPhase.RESHIPPING, "재발송", "새 상품이 출발했어요", "진행 중 · 재발송");
            case REJECT_HOLD -> rejectHold(claim, rejectCharge, today, type);
            case COMPLETED -> completed(claim, type);
        };
    }

    /** 결제가 필요한지는 그 요청의 판정이 다 끝나야 정해진다 — 그 전에는 결제 블록을 열지 않는다. */
    private static View rejectHold(OrderClaim claim, OrderClaimCharge rejectCharge, LocalDate today, String type) {
        if (claim.getCollection().getFinalizedAt() == null) {
            return view(UserClaimPhase.REJECTED_WAITING, type + " 반려", "함께 보낸 상품을 검수하고 있어요", "검수 반려");
        }
        // 기한이 지나도 할 일은 남는다 — 날짜만 빠진다.
        LocalDate due = rejectCharge == null || rejectCharge.getDueAt() == null ? null
                : notPassed(rejectCharge.getDueAt().toLocalDate(), today);
        return new View(UserClaimPhase.REJECTED_PAY, type + " 반려", "재발송 배송비 결제 필요", true, "검수 반려",
                new Todo(TodoType.PAY_RESHIP_FEE, withDue("재발송 배송비 결제 필요", due), due));
    }

    private static String preparingSub(OrderClaim claim, OrderClaimCharge rejectCharge) {
        ClaimChargeStatus settled = rejectCharge == null ? null : rejectCharge.getStatus();
        if (settled == ClaimChargeStatus.DEDUCTED) {
            return "환불액에서 재발송비 차감";
        }
        if (settled == ClaimChargeStatus.COVERED && claim.getType() == ClaimType.EXCHANGE) {
            return "받은 상품을 다시 보내드려요";
        }
        return "반려 상품 발송 준비 중";
    }

    private static View completed(OrderClaim claim, String type) {
        ClaimResult result = claim.getResult();
        if (result == ClaimResult.REJECTED) {
            return claim.getDisposedAt() != null
                    ? view(UserClaimPhase.REJECTED_DISPOSED, type + " 반려", "보관 기간이 끝났어요", null)
                    : view(UserClaimPhase.REJECTED_DONE, type + " 반려", "반려 상품 수령 완료", null);
        }
        if (result == ClaimResult.CANCELLED) {
            return view(UserClaimPhase.CANCELLED, "요청 취소", null, null);
        }
        return view(UserClaimPhase.DONE, type + " 완료", null, null);
    }

    private static View view(UserClaimPhase phase, String label, String sub, String listSub) {
        return new View(phase, label, sub, false, listSub, null);
    }

    private static LocalDate notPassed(LocalDate due, LocalDate today) {
        return due.isBefore(today) ? null : due;
    }

    private static String withDue(String text, LocalDate due) {
        return due == null ? text : text + " · " + due.format(DAY) + "까지";
    }
}

package showroomz.domain.metrics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.entity.Settlement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BinaryOperator;

/**
 * 어드민 홈(1017 설계서 6절) · 운영 지표(7절)가 읽는 운영 큐 조회 — 큐별 <b>건수</b>는 각 모듈 요약이 이미 세므로 여기 두지 않는다.
 * 여기는 홈에만 필요한 것 — 건별 행(실패 · 기한 초과 · 집행 대기 · 기한 임박), 큐의 가장 오래된 진입 시각, 처리 시각 열 합,
 * 오늘 일정, 오늘 지표 — 만 모은다. 테이블이 모듈마다 흩어져 있어 JPQL 을 한 곳에 둔다.
 */
@Repository
public class AdminOperationsQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------ 건별 행

    /** 환불 실패 — 실패가 오래된 순(실패 시각이 없던 이전 행은 수정 시각). */
    public List<OrderRefundTask> findFailedRefunds(int limit) {
        return em.createQuery("SELECT t FROM OrderRefundTask t JOIN FETCH t.order "
                        + "WHERE t.status = showroomz.domain.order.type.RefundTaskStatus.FAILED "
                        + "ORDER BY COALESCE(t.failedAt, t.modifiedAt) ASC, t.id ASC", OrderRefundTask.class)
                .setMaxResults(limit).getResultList();
    }

    /** 사유 환불 집행 대기 — 운영자 편입 · 집행 전(어드민 환불 탭 PENDING 과 같은 조건). */
    public List<OrderRefundTask> findOperatorPendingRefunds(showroomz.domain.order.type.RefundMethod method, int limit) {
        // 수단으로 나눈다 — 계좌 송금(1021 설계서 4-5)은 집행이 아니라 「송금 대기」 큐다.
        return em.createQuery("SELECT t FROM OrderRefundTask t JOIN FETCH t.order "
                        + "WHERE t.origin = showroomz.domain.order.type.RefundTaskOrigin.OPERATOR "
                        + "AND t.method = :method "
                        + "AND t.status IN (showroomz.domain.order.type.RefundTaskStatus.PENDING, "
                        + "    showroomz.domain.order.type.RefundTaskStatus.EXECUTING) "
                        + "ORDER BY t.createdAt ASC, t.id ASC", OrderRefundTask.class)
                .setParameter("method", method)
                .setMaxResults(limit).getResultList();
    }

    /** 분배 실패 정산 — 실패가 오래된 순. [정산, 가장 이른 실패 시각]. */
    public List<Object[]> findPayoutFailedSettlements(int limit) {
        return em.createQuery("SELECT s, (SELECT MIN(p.failedAt) FROM SettlementPayout p WHERE p.settlementId = s.id "
                        + "    AND p.status = showroomz.domain.settlement.type.PayoutStatus.FAILED) "
                        + "FROM Settlement s JOIN FETCH s.contract "
                        + "WHERE s.status = showroomz.domain.settlement.type.SettlementStatus.PAYOUT_FAILED "
                        + "ORDER BY s.id ASC", Object[].class)
                .setMaxResults(limit).getResultList();
    }

    /** 1:1 문의 기한 초과 — 접수가 {@code createdBefore} 이전인 답변 대기. */
    public List<OneToOneInquiry> findInquiriesWaitingBefore(LocalDateTime createdBefore, int limit) {
        return em.createQuery("SELECT i FROM OneToOneInquiry i "
                        + "WHERE i.status = showroomz.domain.inquiry.type.InquiryStatus.WAITING AND i.createdAt < :before "
                        + "ORDER BY i.createdAt ASC, i.id ASC", OneToOneInquiry.class)
                .setParameter("before", createdBefore).setMaxResults(limit).getResultList();
    }

    public long countInquiriesWaitingBefore(LocalDateTime createdBefore) {
        return em.createQuery("SELECT COUNT(i) FROM OneToOneInquiry i "
                        + "WHERE i.status = showroomz.domain.inquiry.type.InquiryStatus.WAITING AND i.createdAt < :before",
                        Long.class)
                .setParameter("before", createdBefore).getSingleResult();
    }

    /** 오픈 승인 대기(어드민 공구 OPEN_REVIEW 큐와 같은 조건) — [공구 id, 공구명, 제출 시각]. */
    public List<Object[]> findOpenReviews() {
        return em.createQuery("SELECT g.id, c.title, gp.submittedAt FROM GroupBuyPost gp JOIN gp.groupBuy g JOIN g.contract c "
                        + "WHERE g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.PREPARING "
                        + "AND gp.reviewStatus = showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus.PENDING "
                        + "ORDER BY gp.submittedAt ASC, g.id ASC", Object[].class)
                .getResultList();
    }

    /** 브랜드 입점 신청 대기 — [신청 id, 브랜드명, 신청 시각]. 어드민 입점 심사 목록과 같은 범위(판매자 역할). */
    public List<Object[]> findPendingSellerApplications() {
        return em.createQuery("SELECT a.id, a.marketName, a.createdAt FROM SellerApplication a JOIN a.seller s "
                        + "WHERE a.status = showroomz.api.seller.auth.type.SellerStatus.PENDING "
                        + "AND s.roleType = showroomz.api.app.auth.entity.RoleType.SELLER "
                        + "ORDER BY a.createdAt ASC, a.id ASC", Object[].class)
                .getResultList();
    }

    /** 인플루언서 입점 신청 대기 — [신청 id, 계정, 신청 시각]. */
    public List<Object[]> findPendingCreatorApplications() {
        return em.createQuery("SELECT a.id, a.accountId, a.createdAt FROM CreatorApplication a "
                        + "WHERE a.status = showroomz.domain.member.creator.type.CreatorApplicationStatus.PENDING "
                        + "ORDER BY a.createdAt ASC, a.id ASC", Object[].class)
                .getResultList();
    }

    // ------------------------------------------------------------------ 큐의 가장 오래된 진입 시각

    public LocalDateTime oldestWaitingInquiry() {
        return single("SELECT MIN(i.createdAt) FROM OneToOneInquiry i "
                + "WHERE i.status = showroomz.domain.inquiry.type.InquiryStatus.WAITING");
    }

    public LocalDateTime oldestPendingChangeRequest() {
        return single("SELECT MIN(r.requestedAt) FROM ChangeRequest r "
                + "WHERE r.status = showroomz.domain.changerequest.type.ChangeRequestStatus.PENDING");
    }

    /** 탈퇴 승인 검토 — 브랜드 · 인플루언서 중 더 오래된 쪽. */
    public LocalDateTime oldestPendingWithdrawal() {
        LocalDateTime brand = single("SELECT MIN(w.requestedAt) FROM BrandWithdrawalRequest w "
                + "WHERE w.status = showroomz.domain.member.common.type.MemberWithdrawalStatus.PENDING");
        LocalDateTime creator = single("SELECT MIN(w.requestedAt) FROM CreatorWithdrawalRequest w "
                + "WHERE w.status = showroomz.domain.member.common.type.MemberWithdrawalStatus.PENDING");
        return earlier().apply(brand, creator);
    }

    public long countPendingCreatorWithdrawals() {
        return em.createQuery("SELECT COUNT(w) FROM CreatorWithdrawalRequest w "
                + "WHERE w.status = showroomz.domain.member.common.type.MemberWithdrawalStatus.PENDING", Long.class)
                .getSingleResult();
    }

    public LocalDateTime oldestUnrecoverableClawback() {
        return single("SELECT MIN(c.unrecoverableAt) FROM SettlementClawback c "
                + "WHERE c.status = showroomz.domain.settlement.type.ClawbackStatus.UNRECOVERABLE");
    }

    /** 반려 이의 — 연결 문의가 답변 대기인 반려 보류 클레임({@code OrderClaimRepository.countOpenDisputes}와 같은 조건). */
    public LocalDateTime oldestOpenDispute() {
        return single("SELECT MIN(c.disputedAt) FROM OrderClaim c, OneToOneInquiry i WHERE i.id = c.disputeInquiryId "
                + "AND c.status = showroomz.domain.order.type.ClaimStatus.REJECT_HOLD "
                + "AND i.status = showroomz.domain.inquiry.type.InquiryStatus.WAITING");
    }

    /** 공구 중단 · 조기 마감 요청 대기. */
    public LocalDateTime oldestPendingGroupBuyChangeRequest(showroomz.domain.groupbuy.type.ChangeRequestType type) {
        return em.createQuery("SELECT MIN(r.requestedAt) FROM GroupBuyChangeRequest r WHERE r.requestType = :type "
                        + "AND r.status = showroomz.domain.groupbuy.type.ChangeRequestStatus.PENDING", LocalDateTime.class)
                .setParameter("type", type).getSingleResult();
    }

    /**
     * 공구 소명 검토(56 E1) — 소명을 냈으면 제출 시각, 안 냈으면 소명 기한(지난 순간 큐에 든다). 조건은
     * {@code AdminGroupBuyQueuePredicate.appealReview}와 같다(통지 중 ∧ (제출됨 ∨ 기한 경과)).
     */
    public LocalDateTime oldestAppealReview(LocalDateTime now) {
        return em.createQuery("SELECT MIN(COALESCE(s.appealSubmittedAt, s.appealDeadlineAt)) FROM GroupBuyAdminSuspension s "
                        + "WHERE s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.NOTICED "
                        + "AND (s.appealSubmittedAt IS NOT NULL OR s.appealDeadlineAt < :now)", LocalDateTime.class)
                .setParameter("now", now).getSingleResult();
    }

    /** 상품 문의 삭제 요청(56 E1) — 요청 시각. 조건은 어드민 상품 문의 요약의 삭제 요청 건수와 같다(노출 축 DELETE_REQUESTED). */
    public LocalDateTime oldestProductInquiryDeleteRequest() {
        return single("SELECT MIN(i.deleteRequestedAt) FROM ProductInquiry i "
                + "WHERE i.exposureStatus = showroomz.domain.inquiry.type.InquiryExposureStatus.DELETE_REQUESTED");
    }

    /** 기한 내 1:1 문의 중 가장 오래된 접수 — 기한 초과분은 건별 행으로 따로 나간다. */
    public LocalDateTime oldestWaitingInquirySince(LocalDateTime createdFrom) {
        return em.createQuery("SELECT MIN(i.createdAt) FROM OneToOneInquiry i "
                        + "WHERE i.status = showroomz.domain.inquiry.type.InquiryStatus.WAITING AND i.createdAt >= :from",
                        LocalDateTime.class)
                .setParameter("from", createdFrom).getSingleResult();
    }

    // ------------------------------------------------------------------ 회원 수(사이드바)

    /** 인플루언서 회원 — 탈퇴 제외. */
    public long countCreatorMembers() {
        return em.createQuery("SELECT COUNT(c) FROM Creator c "
                + "WHERE c.status <> showroomz.domain.member.creator.type.CreatorStatus.WITHDRAWN", Long.class)
                .getSingleResult();
    }

    /** 소비자 회원 — 탈퇴 제외. */
    public long countUserMembers() {
        return em.createQuery("SELECT COUNT(u) FROM Users u WHERE u.roleType = showroomz.api.app.auth.entity.RoleType.USER "
                + "AND u.status <> showroomz.domain.member.user.type.UserStatus.WITHDRAWN", Long.class)
                .getSingleResult();
    }

    // ------------------------------------------------------------------ 처리 시각 열 합(오늘 처리 완료 · 운영 지표)

    /**
     * 기간 중 운영자가 처리한 건수 — 처리 시각 열이 있는 큐만(입점 심사 · 변경 요청 · 탈퇴 · 세금계산서 대조 · 발행 · 1:1 문의 ·
     * 신고 · 소명 · 오픈 승인 · 중단 · 조기 마감 요청) + 환불 집행 + 재분배. 배송 예외 · 차감 미회수는 처리 시각이 없어 빠진다.
     */
    public long countProcessed(LocalDateTime from, LocalDateTime to) {
        long sum = 0;
        for (String[] source : PROCESSED_SOURCES) {
            sum += em.createQuery("SELECT COUNT(x) FROM " + source[0] + " x WHERE " + where(source), Long.class)
                    .setParameter("from", from).setParameter("to", to).getSingleResult();
        }
        return sum;
    }

    /** 처리 시각 목록 — 운영 지표 「일별 처리 완료」 차트 버킷용({@link #countProcessed}와 같은 원천). */
    public List<LocalDateTime> processedTimes(LocalDateTime from, LocalDateTime to) {
        List<LocalDateTime> times = new java.util.ArrayList<>();
        for (String[] source : PROCESSED_SOURCES) {
            times.addAll(em.createQuery("SELECT " + source[1] + " FROM " + source[0] + " x WHERE " + where(source),
                            LocalDateTime.class)
                    .setParameter("from", from).setParameter("to", to).getResultList());
        }
        return times;
    }

    /** [엔티티, 처리 시각 식, 추가 조건]. 세금계산서 대조는 검증 · 반려 중 먼저 찍힌 쪽. */
    private static final String[][] PROCESSED_SOURCES = {
            {"SellerApplication", "x.processedAt", null},
            {"CreatorApplication", "x.processedAt", null},
            {"ChangeRequest", "x.processedAt", null},
            {"BrandWithdrawalRequest", "x.processedAt", null},
            {"CreatorWithdrawalRequest", "x.processedAt", null},
            {"SettlementTaxDocument", "COALESCE(x.verifiedAt, x.rejectedAt)",
                    "x.type = showroomz.domain.settlement.type.TaxDocumentType.CREATOR_TAX_INVOICE"},
            {"SettlementHistory", "x.occurredAt", "x.eventType IN ("
                    + "showroomz.domain.settlement.type.SettlementEventType.BRAND_INVOICE_ISSUED, "
                    + "showroomz.domain.settlement.type.SettlementEventType.PAYOUT_RETRIED)"},
            {"OneToOneInquiry", "x.answeredAt", null},
            {"PostReport", "x.handledAt", null},
            {"PostAppeal", "x.reviewedAt", null},
            {"GroupBuyPost", "x.reviewedAt", null},
            {"GroupBuyChangeRequest", "x.decidedAt", null},
            {"OrderRefundTask", "x.executedAt", null},
    };

    private static String where(String[] source) {
        String range = source[1] + " >= :from AND " + source[1] + " < :to";
        return source[2] == null ? range : source[2] + " AND " + range;
    }

    // ------------------------------------------------------------------ 오늘 일정(6-2)

    /** 오픈 — 준비완료 공구 중 시작 시각이 구간 안. */
    public List<GroupBuy> findOpeningBetween(LocalDateTime from, LocalDateTime to) {
        return em.createQuery("SELECT g FROM GroupBuy g JOIN FETCH g.contract JOIN FETCH g.market JOIN FETCH g.creator "
                        + "WHERE g.status = showroomz.domain.groupbuy.type.GroupBuyStatus.READY "
                        + "AND g.startAt >= :from AND g.startAt < :to ORDER BY g.startAt ASC, g.id ASC", GroupBuy.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
    }

    /** 종료 — 판매 중 공구 중 종료 시각이 구간 안. */
    public List<GroupBuy> findEndingBetween(LocalDateTime from, LocalDateTime to) {
        return em.createQuery("SELECT g FROM GroupBuy g JOIN FETCH g.contract JOIN FETCH g.market JOIN FETCH g.creator "
                        + "WHERE g.status IN (showroomz.domain.groupbuy.type.GroupBuyStatus.IN_PROGRESS, "
                        + "    showroomz.domain.groupbuy.type.GroupBuyStatus.SUSPENSION_SCHEDULED) "
                        + "AND g.endAt >= :from AND g.endAt < :to ORDER BY g.endAt ASC, g.id ASC", GroupBuy.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
    }

    /** 지급일이 그날인 지급 예정 · 요청 — [건수, 금액]. */
    public Object[] sumPayoutsDue(LocalDate date) {
        return em.createQuery("SELECT COUNT(p), COALESCE(SUM(p.amount), 0) FROM SettlementPayout p WHERE p.dueDate = :date "
                        + "AND p.status IN (showroomz.domain.settlement.type.PayoutStatus.SCHEDULED, "
                        + "    showroomz.domain.settlement.type.PayoutStatus.REQUESTED)", Object[].class)
                .setParameter("date", date).getSingleResult();
    }

    /** 확인 마감(조정 요청이 없으면 자동 확정)이 구간 안인 정산 확인 중. */
    public List<Settlement> findReviewDueBetween(LocalDateTime from, LocalDateTime to) {
        return em.createQuery("SELECT s FROM Settlement s JOIN FETCH s.contract "
                        + "WHERE s.status = showroomz.domain.settlement.type.SettlementStatus.REVIEWING "
                        + "AND s.reviewDueAt >= :from AND s.reviewDueAt < :to ORDER BY s.reviewDueAt ASC, s.id ASC",
                        Settlement.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
    }

    public List<SettlementAdjustment> findAdjustmentDeadlinesBetween(LocalDateTime from, LocalDateTime to) {
        return em.createQuery("SELECT a FROM SettlementAdjustment a "
                        + "WHERE a.status = showroomz.domain.settlement.adjustment.type.AdjustmentStatus.OPEN "
                        + "AND a.deadlineAt >= :from AND a.deadlineAt < :to ORDER BY a.deadlineAt ASC, a.id ASC",
                        SettlementAdjustment.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
    }

    /** 직권 중단 집행 예정 — 통지 상태 · 미집행. 집행은 운영자 수동이다. */
    public List<GroupBuyAdminSuspension> findSuspensionExecutionsBetween(LocalDateTime from, LocalDateTime to) {
        return em.createQuery("SELECT s FROM GroupBuyAdminSuspension s JOIN FETCH s.groupBuy g JOIN FETCH g.contract "
                        + "WHERE s.status = showroomz.domain.groupbuy.type.AdminSuspensionStatus.NOTICED "
                        + "AND s.executeScheduledAt >= :from AND s.executeScheduledAt < :to "
                        + "ORDER BY s.executeScheduledAt ASC, s.id ASC", GroupBuyAdminSuspension.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
    }

    // ------------------------------------------------------------------ 오늘 지표(6-4)

    /** 결제 주문 — 결제 시각 기준 · 뒤에 취소돼도 센다. */
    public long countPaidOrders(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(o) FROM Order o WHERE o.paidAt >= :from AND o.paidAt < :to", from, to);
    }

    /** 결제 거래액 — 결제 시각 기준 결제 금액 합 · 취소 포함. */
    public long sumPaidAmount(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paidAt >= :from AND o.paidAt < :to",
                from, to);
    }

    /** 소비자 가입. */
    public long countSignups(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(u) FROM Users u WHERE u.roleType = showroomz.api.app.auth.entity.RoleType.USER "
                + "AND u.createdAt >= :from AND u.createdAt < :to", from, to);
    }

    /** 반품 · 교환 접수(결제 대기 클레임 포함 — 접수 시각 기준). */
    public long countClaims(LocalDateTime from, LocalDateTime to) {
        return count("SELECT COUNT(c) FROM OrderClaim c WHERE c.requestedAt >= :from AND c.requestedAt < :to", from, to);
    }

    // ------------------------------------------------------------------

    private LocalDateTime single(String jpql) {
        return em.createQuery(jpql, LocalDateTime.class).getSingleResult();
    }

    private long count(String jpql, LocalDateTime from, LocalDateTime to) {
        Object value = em.createQuery(jpql).setParameter("from", from).setParameter("to", to).getSingleResult();
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static BinaryOperator<LocalDateTime> earlier() {
        return (a, b) -> a == null ? b : b == null ? a : (a.isBefore(b) ? a : b);
    }
}

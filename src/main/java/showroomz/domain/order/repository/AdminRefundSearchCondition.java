package showroomz.domain.order.repository;

import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 어드민 환불 관리(06c · 39 설계서 3-1) 목록 조건 — 탭이 상태 · 출처 · 기간 · 「일시」 열을 정하고, 경로 · 검색 · 정렬이 더해진다.
 *
 * @param statuses      탭의 상태 집합
 * @param origin        집행 대기 탭은 운영자 사유만 — 그 밖은 null(출처 불문)
 * @param executedFrom  완료 탭의 기간(집행 시각 기준) — 그 밖은 null
 * @param sources       경로 셀렉트 — null 이면 전체
 * @param refundTaskId  검색어가 환불번호로 읽히면 그 id
 * @param keyword       주문번호 · PG 거래번호(포트원 거래 id · paymentId) 정확 일치 — 환불번호 전용 검색(접두 {@code RFD-})이면 null
 * @param noMatch       검색어가 환불번호 형식인데 읽을 수 없다({@code RFD-abc}) — 결과 0건(전체로 떨어지지 않는다)
 * @param dateColumn    탭의 「일시」 열 — 최신순 정렬 기준
 * @param amountDesc    환불액 높은순(동률은 id 역순)
 */
public record AdminRefundSearchCondition(
        Set<RefundTaskStatus> statuses,
        RefundTaskOrigin origin,
        LocalDateTime executedFrom,
        Set<RefundTaskSource> sources,
        Long refundTaskId,
        String keyword,
        boolean noMatch,
        DateColumn dateColumn,
        boolean amountDesc
) {

    /** 「일시」 열 — 집행 대기 = 편입 시각 · 실패 = 마지막 실패 시각 · 완료 = 집행 시각. */
    public enum DateColumn {
        CREATED, MODIFIED, EXECUTED
    }
}

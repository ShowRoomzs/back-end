package showroomz.domain.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 어드민 환불 관리(06c) — 검색 3축 · 경로 · 정렬 · 기간 · 합계가 동적이라 QueryDSL 로 간다(39 설계서 0-5). */
public interface OrderRefundTaskRepositoryCustom {

    Page<OrderRefundTask> searchForAdmin(AdminRefundSearchCondition condition, Pageable pageable);

    /**
     * 탭 숫자 · 합계 — 상태 × 출처별 건수와 금액. 완료({@code DONE})는 집행 시각이 {@code doneFrom} 이후인 것만 센다(탭 숫자와
     * 목록 건수가 같아야 한다).
     */
    List<Stat> summarizeForAdmin(LocalDateTime doneFrom);

    /** 실패 건 중 가장 오래 기다린 것의 적재 시각 — 「소비자 대기 N일」의 기산(39 설계서 0-6). 실패가 없으면 null. */
    LocalDateTime oldestFailedCreatedAt();

    record Stat(RefundTaskStatus status, RefundTaskOrigin origin, long count, long amount) {
    }
}

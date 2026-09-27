package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyRemarkCode;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;

import java.time.LocalDateTime;

/** 파트너 공구 목록 행(A1) — 목록은 조회 전용이다. 실행은 상세에서만 시작한다(§30-1). */
@Schema(description = "공구 목록 행 — 조회 전용(실행 버튼·permissions 없음)")
public record GroupBuyListItem(

        @Schema(description = "공구 id — 상세 조회 경로에 쓴다", example = "18") Long groupBuyId,

        @Schema(description = "공구번호 — GB-생성일(YYYYMMDD)-순번", example = "GB-20260806-018") String groupBuyNumber,

        @Schema(description = "공구명 — 계약에서 읽는다(복사하지 않는다)", example = "글로우 크림 앵콜 공구")
        String title,

        @Schema(description = "인플루언서 표시명(쇼룸명)", example = "글로우_지민") String creatorName,

        @Schema(description = "상품 항목 수 — 공구 상품 = 계약 상품 전부", example = "2") long itemCount,

        @Schema(description = "시작 일시 — 계약 값 · 불변", example = "2026-08-14T10:00:00") LocalDateTime startAt,

        @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값", example = "2026-08-21T23:55:00") LocalDateTime endAt,

        @Schema(description = "게시물 상태 8종 — 저장하지 않고 공구 상태에서 파생한다. "
                + "NOT_WRITTEN(미작성) · WRITING(작성중) · PENDING_APPROVAL(승인대기) · REJECTED(반려) · "
                + "SCHEDULED(예약 — 승인됐고 시작 전) · EXPOSED(노출중) · HIDDEN(숨김) · CLOSED(종료)", example = "EXPOSED")
        GroupBuyPostStatus postStatus,

        @Schema(description = "게시물 상태 배지 문구 — FE가 그대로 쓴다", example = "노출중") String postStatusLabel,

        @Schema(description = "게시물 상태 배지 색 — NEUTRAL · INFO · WARNING · SUCCESS · DANGER", example = "SUCCESS")
        GroupBuyTone postStatusTone,

        @Schema(description = "공구 상태 7종 — PREPARING(준비중) · READY(준비완료) · IN_PROGRESS(진행중) · "
                + "SUSPENSION_SCHEDULED(중단 예정) · ENDED(종료) · SETTLED(정산완료) · SUSPENDED(중단). "
                + "탭 묶음이 아니라 개별 값이다 — 진행중 탭 안에서도 중단 예정은 그대로 내린다",
                example = "SUSPENSION_SCHEDULED")
        GroupBuyStatus status,

        @Schema(description = "공구 상태 배지 문구", example = "중단 예정") String statusLabel,

        @Schema(description = "공구 상태 배지 색 — NEUTRAL · INFO · WARNING · SUCCESS · DANGER", example = "WARNING")
        GroupBuyTone statusTone,

        @Schema(description = "비고 — 상태만으로 알 수 없는 예외만 최대 1개. 대부분 행은 null이다", nullable = true)
        Remark remark
) {

    @Schema(description = "비고 — 코드만 내린다. 문구는 FE가 고른다(설계서 4-2)")
    public record Remark(
            @Schema(description = "우선순위 순 — ADMIN_SUSPENSION_NOTICED(직권 중단 예정) > "
                    + "SUSPENSION_REQUEST_REVIEWING(중단 요청 검토 중 · 요청자 무관) > "
                    + "EARLY_CLOSE_REQUEST_REVIEWING(조기 마감 요청 검토 중) > EXTENSION_PENDING(연장 요청 응답 대기) > "
                    + "SUSPENDED_BY_ADMIN(운영자 직권 중단으로 종결). 여러 개가 걸리면 가장 앞의 하나만 내린다",
                    example = "ADMIN_SUSPENSION_NOTICED")
            GroupBuyRemarkCode code,
            @Schema(description = "소명 기한 — ADMIN_SUSPENSION_NOTICED일 때만 · 「소명 기한 MM.DD」 표기용",
                    example = "2026-08-19T23:59:59", nullable = true)
            LocalDateTime appealDeadlineAt
    ) {
    }
}

package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;

import java.time.LocalDateTime;

/**
 * 스튜디오 공구 목록 행(A1). 비고({@code remark})가 없다(§31-1) — 대신 {@code actionRequired} 하나로
 * 「내 조치 필요 먼저」 정렬에서 어디까지가 조치 대상인지 경계를 그린다. 무엇을 해야 하는지는 상세에서 본다.
 */
@Schema(description = "스튜디오 공구 목록 행 — 조회 전용(실행 버튼·permissions·비고 없음)")
public record CreatorGroupBuyListItem(

        @Schema(description = "공구 id — 상세 조회 경로에 쓴다", example = "41") Long groupBuyId,

        @Schema(description = "공구번호 — GB-생성일(YYYYMMDD)-순번", example = "GB-20260803-041") String groupBuyNumber,

        @Schema(description = "공구명 — 계약에서 읽는다", example = "여름 수분 세럼 공구") String title,

        @Schema(description = "브랜드명", example = "글로우랩") String brandName,

        @Schema(description = "상품 항목 수 — 공구 상품 = 계약 상품 전부", example = "2") long itemCount,

        @Schema(description = "시작 일시 — 계약 값 · 불변", example = "2026-08-14T10:00:00") LocalDateTime startAt,

        @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값", example = "2026-08-20T23:55:00") LocalDateTime endAt,

        @Schema(description = "「내 게시물」 열 — 게시물 8종(저장값이 아니라 공구 상태에서 파생). NOT_WRITTEN(미작성) · WRITING(작성중) · "
                + "PENDING_APPROVAL(승인대기) · REJECTED(반려) · SCHEDULED(예약 — 승인됐고 시작 전) · EXPOSED(노출중) · HIDDEN(숨김) · "
                + "CLOSED(종료 — 종결 3종 공통)", example = "NOT_WRITTEN")
        GroupBuyPostStatus postStatus,

        @Schema(description = "게시물 상태 배지 문구 — FE가 그대로 쓴다", example = "미작성") String postStatusLabel,

        @Schema(description = "게시물 상태 배지 색 — NEUTRAL · INFO · WARNING · SUCCESS · DANGER", example = "NEUTRAL")
        GroupBuyTone postStatusTone,

        @Schema(description = "공구 상태 7종 — PREPARING(준비중) · READY(준비완료) · IN_PROGRESS(진행중) · "
                + "SUSPENSION_SCHEDULED(중단 예정) · ENDED(종료) · SETTLED(정산완료) · SUSPENDED(중단). "
                + "탭 묶음이 아니라 개별 값이다 — 진행중 탭 안의 중단 예정도 그대로 내린다", example = "PREPARING")
        GroupBuyStatus status,

        @Schema(description = "공구 상태 배지 문구", example = "준비중") String statusLabel,

        @Schema(description = "공구 상태 배지 색 — NEUTRAL · INFO · WARNING · SUCCESS · DANGER", example = "NEUTRAL")
        GroupBuyTone statusTone,

        @Schema(description = "내 조치 필요 — GNB 배지·목록 헤더와 같은 판정식. 게시물 작성·재등록(준비중) · 숨김 게시물 수정 · "
                + "연장 응답(종료 전) · 이행 확인(종료 · 내 확인 전) 중 하나라도 해당", example = "true")
        boolean actionRequired
) {
}

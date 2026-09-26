package showroomz.api.admin.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;

import java.time.LocalDateTime;

/**
 * 어드민 공구 목록 행(A1 · 32 설계 3-2) — 7열 그대로다.
 *
 * <p>비고({@code remark})가 없고, 무엇이 걸렸는지({@code queueKind})도 싣지 않는다 — 「상세가 답한다」(§32-1).
 * 행에 종류를 내리면 FE가 배지 아래 보조 텍스트로 매달게 되고, 상태의 일부처럼 읽힌다. {@code permissions}도 없다 —
 * 목록에 실행 액션이 없다(모든 판정은 모달을 거친다).
 */
@Schema(description = "어드민 공구 목록 행")
public record AdminGroupBuyListItem(

        @Schema(example = "41") Long groupBuyId,

        @Schema(example = "GB-20260814-041") String groupBuyNumber,

        @Schema(description = "공구명 — 계약에서 읽는다", example = "여름 수분 세럼 공구") String title,

        @Schema(description = "인플루언서(쇼룸명)", example = "소연 쇼룸") String creatorName,

        @Schema(description = "브랜드명", example = "글로우랩") String brandName,

        @Schema(description = "상품 항목 수", example = "2") long itemCount,

        LocalDateTime startAt,

        @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값") LocalDateTime endAt,

        @Schema(description = "게시물 8종(파생값)", example = "PENDING_APPROVAL") GroupBuyPostStatus postStatus,

        @Schema(example = "승인대기") String postStatusLabel,

        @Schema(example = "INFO") GroupBuyTone postStatusTone,

        @Schema(description = "상태 7종 개별 값 — 진행중 탭 안의 중단 예정도 그대로 내린다", example = "PREPARING")
        GroupBuyStatus status,

        @Schema(example = "준비중") String statusLabel,

        @Schema(example = "NEUTRAL") GroupBuyTone statusTone,

        @Schema(description = "조치 큐 해당 — 배지·요약과 같은 판정식. 경고 배경은 FE가 탭으로 끈다(조치 필요 탭에서는 그리지 않는다)",
                example = "true")
        boolean actionRequired
) {
}

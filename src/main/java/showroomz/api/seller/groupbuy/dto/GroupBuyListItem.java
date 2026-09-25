package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyRemarkCode;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;

import java.time.LocalDateTime;

/** 파트너 공구 목록 행(A1) — 목록은 조회 전용이다. 실행은 상세에서만 시작한다(§30-1). */
@Schema(description = "공구 목록 행")
public record GroupBuyListItem(

        @Schema(example = "18") Long groupBuyId,

        @Schema(example = "GB-20260806-018") String groupBuyNumber,

        @Schema(description = "공구명 — 계약에서 읽는다(복사하지 않는다)", example = "글로우 크림 앵콜 공구")
        String title,

        @Schema(description = "인플루언서 표시명(쇼룸명)", example = "글로우_지민") String creatorName,

        @Schema(description = "상품 항목 수 — 공구 상품 = 계약 상품 전부", example = "2") long itemCount,

        LocalDateTime startAt,

        @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값") LocalDateTime endAt,

        @Schema(description = "게시물 8종 — 저장하지 않고 공구 상태에서 파생한다", example = "EXPOSED")
        GroupBuyPostStatus postStatus,

        @Schema(example = "노출중") String postStatusLabel,

        @Schema(example = "SUCCESS") GroupBuyTone postStatusTone,

        @Schema(description = "상태 — 탭 묶음이 아니라 7종 개별 값이다. 진행중 탭 안에서도 중단 예정은 그대로 내린다",
                example = "SUSPENSION_SCHEDULED")
        GroupBuyStatus status,

        @Schema(example = "중단 예정") String statusLabel,

        @Schema(example = "WARNING") GroupBuyTone statusTone,

        @Schema(description = "비고 — 상태만으로 알 수 없는 예외만. 대부분 행은 null이다", nullable = true)
        Remark remark
) {

    @Schema(description = "비고 — 코드만 내린다. 문구는 FE가 고른다(설계서 4-2)")
    public record Remark(
            GroupBuyRemarkCode code,
            @Schema(description = "ADMIN_SUSPENSION_NOTICED일 때만 — 「소명 기한 MM.DD」", nullable = true)
            LocalDateTime appealDeadlineAt
    ) {
    }
}

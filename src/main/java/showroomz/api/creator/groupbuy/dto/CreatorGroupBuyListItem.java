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
@Schema(description = "스튜디오 공구 목록 행")
public record CreatorGroupBuyListItem(

        @Schema(example = "41") Long groupBuyId,

        @Schema(example = "GB-20260814-041") String groupBuyNumber,

        @Schema(description = "공구명 — 계약에서 읽는다", example = "여름 수분 세럼 공구") String title,

        @Schema(description = "브랜드명", example = "글로우랩") String brandName,

        @Schema(description = "상품 항목 수", example = "2") long itemCount,

        LocalDateTime startAt,

        @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값") LocalDateTime endAt,

        @Schema(description = "「내 게시물」 열 — 게시물 8종(파생값)", example = "NOT_WRITTEN") GroupBuyPostStatus postStatus,

        @Schema(example = "미작성") String postStatusLabel,

        @Schema(example = "NEUTRAL") GroupBuyTone postStatusTone,

        @Schema(description = "상태 7종 개별 값 — 진행중 탭 안의 중단 예정도 그대로 내린다", example = "PREPARING")
        GroupBuyStatus status,

        @Schema(example = "준비중") String statusLabel,

        @Schema(example = "NEUTRAL") GroupBuyTone statusTone,

        @Schema(description = "내 조치 필요 — GNB 배지·목록 헤더와 같은 판정식", example = "true") boolean actionRequired
) {
}

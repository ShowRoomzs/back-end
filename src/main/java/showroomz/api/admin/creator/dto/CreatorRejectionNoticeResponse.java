package showroomz.api.admin.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.api.admin.creator.type.CreatorRejectionReasonType;
import showroomz.api.creator.auth.service.CreatorRejectionNotice;

import java.time.LocalDate;

@Schema(description = "인플루언서 입점 반려 — 신청자 통지 미리보기(1021 기-36). 실제 발송 메일과 같은 문안이다")
public record CreatorRejectionNoticeResponse(
        @Schema(description = "내부 사유 코드", example = "SUSPECTED_FAKE_FOLLOWERS") CreatorRejectionReasonType reasonType,
        @Schema(description = "내부 라벨(운영자 화면)", example = "허위 팔로워 의심") String internalLabel,
        @Schema(description = "통지 라벨(신청자에게 가는 사유)", example = "채널 운영 기준 미충족") String noticeLabel,
        @Schema(description = "반려일(오늘)", example = "2026-07-06") LocalDate rejectedOn,
        @Schema(description = "재신청 가능일 = 반려일 + 14일", example = "2026-07-20") LocalDate reapplyAvailableOn,
        @Schema(example = "[SHOWROOMZ] 쇼룸 스튜디오 신청 결과 안내") String subject,
        @Schema(description = "본문 — 줄바꿈(\n)으로 단락. 상세 사유는 담기지 않는다") String body) {

    public static CreatorRejectionNoticeResponse of(CreatorRejectionNotice notice) {
        return new CreatorRejectionNoticeResponse(notice.reasonType(), notice.reasonType().getDescription(),
                notice.noticeLabel(), notice.rejectedOn(), notice.reapplyAvailableOn(), notice.subject(), notice.body());
    }
}

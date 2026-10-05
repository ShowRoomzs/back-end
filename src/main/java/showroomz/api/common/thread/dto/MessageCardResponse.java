package showroomz.api.common.thread.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.message.service.MessageCardReader.CardView;
import showroomz.domain.message.type.MessageCardActionState;
import showroomz.domain.message.type.MessageCardTone;
import showroomz.domain.message.type.MessageCardType;

import java.time.LocalDateTime;

/**
 * 시스템 카드 — <b>파트너센터 · 쇼룸 스튜디오 응답용</b>(36 설계 4절 · 7절).
 *
 * <p>운영자 개인을 가리키는 값이 <b>하나도 없다</b> — 처리자 이름도, 운영자 id도 싣지 않는다. 화면에서 가리는 것으로는
 * 부족하므로 필드 자체를 두지 않는다(§36-4). 어드민 응답은 {@code AdminThreadDto.Card}가 따로 만든다 —
 * 두 DTO를 합치지 않는다.
 */
@Schema(description = "시스템 카드 — 절차의 기록(요청 · 처리 결과). messageType = SYSTEM일 때만 내려온다")
public record MessageCardResponse(
        @Schema(description = "카드 종류", example = "CONTRACT_RESEND_REQUEST") MessageCardType cardType,
        @Schema(description = "카드 제목", example = "요청 · 서명 안내 다시 받기") String title,
        @Schema(description = "카드 톤 — 조치가 남은 요청은 WARNING, 끝났거나 결과 카드는 NEUTRAL", example = "WARNING")
        MessageCardTone tone,
        @Schema(description = "계약 ID — 계약 상세 링크용", example = "41") Long contractId,
        @Schema(description = "계약번호", example = "CTR-20260813-041") String contractNumber,
        @Schema(description = "공구명", example = "겨울 리페어 크림 공구") String groupBuyTitle,
        @Schema(description = "카드 종류별 내용") Detail detail,
        @Schema(description = "카드의 액션 상태 — 재발송 요청 카드만", nullable = true) Action action) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            @Schema(description = "[재발송 요청] 요청자 — SELLER(브랜드) · CREATOR(인플루언서)", example = "CREATOR")
            String requesterType,
            @Schema(description = "[재발송 요청] 요청자 이름(브랜드명 · 쇼룸명)", example = "뷰티_소연") String requesterName,
            @Schema(description = "[재발송 요청] 요청 시각", example = "2026-08-14T08:50:00") LocalDateTime requestedAt,
            @Schema(description = "[직권 취소] 사유 — 사유 라벨 + 운영자가 적은 메모", example = "공구 일정 변경 — 조건 재협의")
            String reasonLabel,
            @Schema(description = "[직권 취소] 처리 시각", example = "2026-08-14T10:40:00") LocalDateTime processedAt,
            @Schema(description = "[직권 취소] 양측 통지 여부", example = "true") Boolean notifiedBothParties) {
    }

    public record Action(
            @Schema(description = "액션 종류", example = "RESEND_NOTICE") String type,
            @Schema(description = "PENDING(운영팀 확인 전) · DONE(재발송 완료 알림 전송됨) · CLOSED(계약이 서명 단계를 벗어나 닫힘)",
                    example = "PENDING") MessageCardActionState state,
            @Schema(description = "알림 전송 시각 — DONE일 때", example = "2026-08-14T08:55:00", nullable = true)
            LocalDateTime doneAt) {
    }

    public static MessageCardResponse from(CardView view) {
        if (view == null) {
            return null;
        }
        boolean resend = view.cardType() == MessageCardType.CONTRACT_RESEND_REQUEST;
        Detail detail = resend
                ? new Detail(view.requesterType(), view.requesterName(), view.requestedAt(), null, null, null)
                : new Detail(null, null, null, view.reasonLabel(), view.processedAt(), true);
        Action action = view.actionState() == null ? null
                : new Action(RESEND_NOTICE, view.actionState(), view.doneAt());
        return new MessageCardResponse(view.cardType(), view.title(), view.tone(), view.contractId(),
                view.contractNumber(), view.groupBuyTitle(), detail, action);
    }

    public static final String RESEND_NOTICE = "RESEND_NOTICE";
}

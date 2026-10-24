package showroomz.api.common.thread.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.member.common.service.SanctionTermPolicy;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.service.MessageCardReader.AdjustmentCard;
import showroomz.domain.message.service.MessageCardReader.CardView;
import showroomz.domain.message.type.MessageCardActionState;
import showroomz.domain.message.type.MessageCardTone;
import showroomz.domain.message.type.MessageCardType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 시스템 카드 — <b>파트너센터 · 쇼룸 스튜디오 응답용</b>(36 설계 4절 · 7절).
 *
 * <p>운영자 개인을 가리키는 값이 <b>하나도 없다</b> — 처리자 이름도, 운영자 id도 싣지 않는다. 화면에서 가리는 것으로는
 * 부족하므로 필드 자체를 두지 않는다(§36-4). 어드민 응답은 {@code AdminThreadDto.Card}가 따로 만든다 —
 * 두 DTO를 합치지 않는다.
 *
 * <p>정산 조정 카드(44 이슈 스레드 설계서 5-4)는 {@code detail.adjustment}에 사실을, {@code action}에 버튼 상태
 * ({@code ADJUSTMENT_RESPOND} · 결과 문구 · 뷰어가 지금 답할 수 있는지)를 싣는다.
 */
@Schema(description = "시스템 카드 — 절차의 기록(요청 · 처리 결과). messageType = SYSTEM일 때만 내려온다")
public record MessageCardResponse(
        @Schema(description = "카드 종류 — 계약(CONTRACT_*) · 정산 조정(SETTLEMENT_ADJUSTMENT_* 8종)", example = "CONTRACT_RESEND_REQUEST")
        MessageCardType cardType,
        @Schema(description = "카드 제목", example = "요청 · 서명 안내 다시 받기") String title,
        @Schema(description = "카드 톤 — 조치가 남은 요청은 WARNING, 끝났거나 결과 카드는 NEUTRAL. 정산 조정 카드는 전부 NEUTRAL",
                example = "WARNING")
        MessageCardTone tone,
        @Schema(description = "계약 ID — 계약 상세 링크용", example = "41") Long contractId,
        @Schema(description = "계약번호", example = "CTR-20260813-041") String contractNumber,
        @Schema(description = "공구명", example = "겨울 리페어 크림 공구") String groupBuyTitle,
        @Schema(description = "카드 종류별 내용") Detail detail,
        @Schema(description = "카드의 액션 상태 — 재발송 요청 카드 · 정산 조정 제안 카드(요청 · 다른 금액 제안)만", nullable = true)
        Action action) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            @Schema(description = "[재발송 요청] 요청자 — SELLER(브랜드) · CREATOR(인플루언서)", example = "CREATOR")
            String requesterType,
            @Schema(description = "[재발송 요청] 요청자 이름(브랜드명 · 쇼룸명)", example = "뷰티_소연") String requesterName,
            @Schema(description = "[재발송 요청] 요청 시각", example = "2026-08-14T08:50:00") LocalDateTime requestedAt,
            @Schema(description = "[직권 취소] 사유 — 사유 라벨 + 운영자가 적은 메모", example = "공구 일정 변경 — 조건 재협의")
            String reasonLabel,
            @Schema(description = "[직권 취소] 처리 시각", example = "2026-08-14T10:40:00") LocalDateTime processedAt,
            @Schema(description = "[직권 취소] 양측 통지 여부", example = "true") Boolean notifiedBothParties,
            @Schema(description = "[정산 조정] 카드의 사실 — 정산 조정 카드에만") AdjustmentDetail adjustment,
            @Schema(description = "[계정 이용 정지 · 해제] 사유 코드 · 라벨 · 상세 · 지급 정지 여부 — 1017 이후 카드에만(옛 카드는 reasonLabel 한 줄). "
                    + "정지 카드는 정지 기간 · 이의 신청 기한도(1021 이후)")
            SanctionDetail sanction,
            @Schema(description = "[취소 · 해지 동의 확인 · 해지 결과 · 직권 중단 사전 통지] 안내 카드의 사실(1021) — 버튼 없음 · 답글로 의견")
            NoticeDetail notice) {
    }

    /**
     * 안내 카드(1021 설계서 1-6 · 2-3). 「동의하시면 이 채널에 답해 주세요」 · 「통지만으로 판매는 멈추지 않습니다」 같은 고정 문구는 FE 가 {@code kind}로 고른다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NoticeDetail(
            @Schema(description = "CANCEL(계약 취소 동의) · TERMINATE(계약 해지 동의 · 해지 결과) · ADMIN_SUSPENSION(직권 중단 사전 통지)",
                    example = "TERMINATE") String kind,
            @Schema(description = "당사자에게 그대로 보이는 설명 — 요청 메모 · 해지 설명 · 통지 본문") String body,
            @Schema(description = "[해지 결과] 생성된 공구가 함께 종결됐는가") Boolean groupBuyClosed,
            Long groupBuyId,
            @Schema(example = "GRB-20260901-012") String groupBuyNumber,
            @Schema(description = "[직권 중단] 근거 호", example = "ART17_1_LAW") String reasonClause,
            @Schema(description = "[직권 중단] 근거 호 라벨") String reasonClauseLabel,
            @Schema(description = "[직권 중단] 집행 예정 시각") LocalDateTime executeScheduledAt,
            @Schema(description = "[직권 중단] 소명 · 의견 기한 — 양측 같은 기한") LocalDateTime appealDeadlineAt,
            @Schema(description = "[직권 중단] 상대 당사자에게도 같은 통지가 갔는가") Boolean counterpartyNoticed) {

        public static NoticeDetail of(MessageCardPayload.Notice n) {
            return n == null ? null : new NoticeDetail(n.kind(), n.body(), n.groupBuyClosed(), n.groupBuyId(),
                    n.groupBuyNumber(), n.reasonClause(), n.reasonClauseLabel(), n.executeScheduledAt(),
                    n.appealDeadlineAt(), n.counterpartyNoticed());
        }
    }

    /**
     * 계정 이용 정지 · 해제 카드의 사실(ui-partner-15 S7 · ui-studio-13 S8). 차단 · 계속 이용 목록은 {@code memberType}별 고정 문구라 FE 가 든다.
     * 정산 줄 — {@code payoutHeld = true} 「정산은 확정까지 진행되며, 정지가 풀리면 다음 영업일에 지급됩니다」 · false 「이번 사유는 지급에 영향이 없습니다」.
     *
     * <p>정지 기간 · 이의 신청 기한(1021 설계서 7-2 · 스튜디오 S8 「정지 기간 2026.10.08 14:20 ~ 2026.10.22 00:00 (14일)」 · 「이의 신청 기한
     * 2026.10.13까지」)은 정지 카드 스냅샷에서 읽는다 — 기간 시작은 카드의 {@code processedAt}. 1021 이전 정지 카드 · 해제 카드에는 없다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SanctionDetail(
            @Schema(description = "BRAND · CREATOR", example = "BRAND") String memberType,
            @Schema(description = "정지 사유 코드(정지 카드) · 해제 사유 코드(해제 카드)", example = "SETTLEMENT_INFO_MISMATCH") String reasonCode,
            @Schema(example = "정산 정보 불일치") String reasonLabel,
            @Schema(description = "상세 사유 · 해제 메모 — 운영자가 적은 경우만") String reasonDetail,
            @Schema(description = "[정지 카드] 지급 정지 사유인가") Boolean payoutHeld,
            @Schema(description = "[정지 카드] 정지 종료일(포함) — 기간 없는 정지 · 1021 이전 카드 · 해제 카드는 없음",
                    example = "2026-10-21", nullable = true) LocalDate endsOn,
            @Schema(description = "[정지 카드] 자동 해제 시각 = 종료일 다음 날 00:00 — 「~ 2026.10.22 00:00」. 종료일이 없으면 없음",
                    example = "2026-10-22T00:00:00", nullable = true) LocalDateTime autoReleaseAt,
            @Schema(description = "[정지 카드] 정지 기간(일) — 정지일(processedAt 의 날짜) · 종료일 포함 「(14일)」. 종료일이 없으면 없음",
                    example = "14", nullable = true) Integer periodDays,
            @Schema(description = "[정지 카드] 이의 신청 기한 — 통지 + N영업일 23:59:59 · 정지 시점 스냅샷(기한 뒤에도 접수한다). "
                    + "1021 이전 카드 · 해제 카드는 없음", example = "2026-10-13T23:59:59", nullable = true)
            LocalDateTime appealDeadlineAt) {

        /** @param processedAt 카드의 처리 시각 — 정지 카드면 정지 시각(기간 일수의 시작일) */
        public static SanctionDetail of(MessageCardPayload.Sanction s, LocalDateTime processedAt) {
            return s == null ? null
                    : new SanctionDetail(s.memberType(), s.reasonCode(), s.reasonLabel(), s.reasonDetail(), s.payoutHeld(),
                    s.endsOn(), SanctionTermPolicy.autoReleaseAt(s.endsOn()),
                    SanctionTermPolicy.periodDays(processedAt, s.endsOn()), s.appealDeadlineAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "정산 조정 카드의 사실(생성 시점 스냅샷) — 카드 종류마다 채워지는 칸이 다르다")
    public record AdjustmentDetail(
            Long settlementId,
            @Schema(example = "STL-2609-006") String settlementNumber,
            Long groupBuyId,
            Long adjustmentId,
            @Schema(description = "[요청 · 제안 · 동의 · 반대] 제안 id") Long proposalId,
            @Schema(description = "[요청 · 제안 · 동의 · 반대] 몇 번째 제안") Integer seq,
            @Schema(description = "[요청 · 제안] 제안자 — SELLER · CREATOR") String proposerType,
            @Schema(description = "[요청 · 제안] 제안자 이름(브랜드명 · 쇼룸명)") String proposerName,
            @Schema(description = "원래 리워드(공급가)") Long originalRewardAmount,
            @Schema(description = "[요청 · 제안 · 동의 · 반대] 제안 리워드(공급가)") Long rewardAmount,
            @Schema(description = "[요청 · 제안] 사유 — 다른 금액 제안은 없을 수 있다") String reason,
            @Schema(description = "[개설 · 요청 · 제안 · D-1] 합의 기한") LocalDateTime deadlineAt,
            @Schema(description = "[동의 · 반대] 응답한 쪽") String responderType,
            @Schema(description = "[동의 · 반대] 응답한 쪽 이름") String responderName,
            @Schema(description = "[동의 · 반대] 응답 시각") LocalDateTime respondedAt,
            @Schema(description = "[합의 · 만료] 확정 리워드") Long finalRewardAmount,
            @Schema(description = "[합의] 동의한 쪽") String agreedByType,
            @Schema(description = "[합의] 동의한 쪽 이름") String agreedByName,
            @Schema(description = "[합의 · 만료] 종결 시각") LocalDateTime closedAt) {

        public static AdjustmentDetail of(MessageCardPayload.Adjustment s) {
            return s == null ? null : new AdjustmentDetail(s.settlementId(), s.settlementNumber(), s.groupBuyId(),
                    s.adjustmentId(), s.proposalId(), s.seq(), s.proposerType(), s.proposerName(),
                    s.originalRewardAmount(), s.rewardAmount(), s.reason(), s.deadlineAt(), s.responderType(),
                    s.responderName(), s.respondedAt(), s.finalRewardAmount(), s.agreedByType(), s.agreedByName(),
                    s.closedAt());
        }
    }

    public record Action(
            @Schema(description = "액션 종류 — RESEND_NOTICE(재발송 요청) · ADJUSTMENT_RESPOND(정산 조정 제안)", example = "RESEND_NOTICE")
            String type,
            @Schema(description = "PENDING(조치 전 · 응답 대기) · DONE(처리됨 · 응답됨) · CLOSED(닫힘 · 응답 없이 만료 · 요청 철회)",
                    example = "PENDING") MessageCardActionState state,
            @Schema(description = "알림 전송 시각 · 제안 응답 시각 — DONE일 때", example = "2026-08-14T08:55:00", nullable = true)
            LocalDateTime doneAt,
            @Schema(description = "[정산 조정] 결과 문구 — 「동의」 · 「반대」 · 「다른 금액 제안으로 응답됨」 · 「기한 만료」 · 「요청 철회」"
                    + "(CLOSED 는 협의가 철회로 끝났으면 「요청 철회」, 그 밖은 「기한 만료」)", nullable = true)
            String resultLabel,
            @Schema(description = "[정산 조정] 뷰어가 지금 이 카드에 답할 수 있는가 — 최신 제안 ∧ 동의 · 반대 · 다른 금액 제안 중 하나라도 가능. "
                    + "버튼 종류는 GET …/threads/{threadId}/adjustment 의 permissions", example = "false")
            boolean respondable) {
    }

    /** 응답 버튼이 없는 화면(답할 수 있는 제안이 없음) — 정산 조정 카드의 {@code respondable}은 false. */
    public static MessageCardResponse from(CardView view) {
        return from(view, null);
    }

    /**
     * @param respondableProposalId 뷰어가 지금 답할 수 있는 제안 id — 이 카드가 그 제안의 카드면 {@code respondable = true}
     */
    public static MessageCardResponse from(CardView view, Long respondableProposalId) {
        if (view == null) {
            return null;
        }
        AdjustmentCard adjustment = view.adjustment();
        if (adjustment != null) {
            Action action = adjustment.actionState() == null ? null
                    : new Action(ADJUSTMENT_RESPOND, adjustment.actionState(), adjustment.respondedAt(),
                    adjustment.resultLabel(), respondableProposalId != null
                    && respondableProposalId.equals(adjustment.proposalId()));
            return new MessageCardResponse(view.cardType(), view.title(), view.tone(), view.contractId(),
                    view.contractNumber(), view.groupBuyTitle(),
                    new Detail(null, null, null, null, null, null, AdjustmentDetail.of(adjustment.snapshot()), null, null),
                    action);
        }
        boolean resend = view.cardType() == MessageCardType.CONTRACT_RESEND_REQUEST;
        Detail detail = resend
                ? new Detail(view.requesterType(), view.requesterName(), view.requestedAt(), null, null, null, null, null, null)
                : view.notice() != null
                ? new Detail(view.requesterType(), view.requesterName(), view.requestedAt(), view.reasonLabel(),
                        view.processedAt(), null, null, null, NoticeDetail.of(view.notice()))
                : new Detail(null, null, null, view.reasonLabel(), view.processedAt(), true, null,
                        SanctionDetail.of(view.sanction(), view.processedAt()), null);
        Action action = view.actionState() == null ? null
                : new Action(RESEND_NOTICE, view.actionState(), view.doneAt(), null, false);
        return new MessageCardResponse(view.cardType(), view.title(), view.tone(), view.contractId(),
                view.contractNumber(), view.groupBuyTitle(), detail, action);
    }

    public static final String RESEND_NOTICE = "RESEND_NOTICE";
    public static final String ADJUSTMENT_RESPOND = "ADJUSTMENT_RESPOND";
}

package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.type.MessageCardActionState;
import showroomz.domain.message.type.MessageCardTone;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageRefType;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustmentProposal;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentProposalRepository;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 시스템 카드 읽기(36 설계 4절) — 박힌 사실은 스냅샷에서, 액션 상태는 참조 객체에서 읽어 한 장으로 합친다.
 * 어드민 · 파트너센터 · 스튜디오가 같은 값을 받아 각자의 응답으로 옮긴다.
 *
 * <p>여기서 나가는 운영자는 <b>id</b>다. 이름을 붙이는 것은 어드민 직렬화의 일이다 — 상대 서피스는
 * 이 id를 응답에 옮기지 않는다(0-4).
 *
 * <p>정산 조정 카드(44 이슈 스레드 설계서 5-4)는 제안 · 협의 행을 페이지 전체에서 한 번에 읽어 「이전 카드는 응답 결과로 굳는다」를
 * 카드를 고쳐 쓰지 않고 만든다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageCardReader {

    private final ContractResendRequestRepository resendRequestRepository;
    private final SettlementAdjustmentProposalRepository proposalRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;

    /**
     * @param actionState 재발송 요청 카드만 — 결과 카드는 null
     * @param doneBy      알림을 보낸 운영자 id(어드민 전용)
     * @param processedBy 취소를 처리한 운영자 id(어드민 전용)
     * @param adjustment  정산 조정 카드만 — 그 밖은 null
     */
    public record CardView(MessageCardType cardType, String title, MessageCardTone tone,
                           Long contractId, String contractNumber, String groupBuyTitle,
                           String requesterType, String requesterName, LocalDateTime requestedAt,
                           String reasonLabel, LocalDateTime processedAt, Long processedBy,
                           MessageCardActionState actionState, LocalDateTime doneAt, Long doneBy,
                           Long noticeMessageId, AdjustmentCard adjustment) {
    }

    /**
     * 정산 조정 카드의 읽기값(5-4) — 스냅샷 + 참조 객체(협의 · 제안)의 지금 상태.
     *
     * @param actionState 제안 카드(요청 · 다른 금액 제안)만 — PENDING(응답 대기) · DONE(동의 · 반대 · 다른 금액 제안으로 응답됨) ·
     *                    CLOSED(응답 없이 만료). 그 밖은 null
     * @param resultLabel DONE · CLOSED 일 때 「동의」 · 「반대」 · 「다른 금액 제안으로 응답됨」 · 「기한 만료」
     */
    public record AdjustmentCard(MessageCardPayload.Adjustment snapshot, AdjustmentStatus adjustmentStatus,
                                 Long proposalId, Integer seq, ProposalStatus proposalStatus,
                                 String responderType, LocalDateTime respondedAt,
                                 MessageCardActionState actionState, String resultLabel) {
    }

    /** 메시지 id → 카드. 카드가 아닌 메시지는 결과에 없다. 재발송 요청 · 조정 제안의 상태는 페이지 전체를 한 번에 읽는다. */
    public Map<Long, CardView> read(Collection<Message> messages) {
        List<Message> cards = messages.stream().filter(Message::isCard).toList();
        if (cards.isEmpty()) {
            return Map.of();
        }
        List<Long> resendIds = refIds(cards, MessageRefType.CONTRACT_RESEND_REQUEST);
        Map<Long, ContractResendRequest> resends = resendIds.isEmpty() ? Map.of()
                : resendRequestRepository.findWithContractByIdIn(resendIds).stream()
                        .collect(Collectors.toMap(ContractResendRequest::getId, Function.identity()));

        Map<Long, SettlementAdjustmentProposal> proposals = Map.of();
        Map<Long, SettlementAdjustment> adjustments = Map.of();
        if (cards.stream().anyMatch(card -> card.getCardType() != null && card.getCardType().isSettlementAdjustment())) {
            List<Long> proposalIds = refIds(cards, MessageRefType.SETTLEMENT_ADJUSTMENT_PROPOSAL);
            proposals = proposalIds.isEmpty() ? Map.of() : proposalRepository.findAllById(proposalIds).stream()
                    .collect(Collectors.toMap(SettlementAdjustmentProposal::getId, Function.identity()));
            List<Long> adjustmentIds = cards.stream()
                    .filter(card -> card.getCardType() != null && card.getCardType().isSettlementAdjustment())
                    .map(card -> MessageCardPayload.parse(card.getCardPayload()).adjustment())
                    .filter(Objects::nonNull)
                    .map(MessageCardPayload.Adjustment::adjustmentId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            adjustments = adjustmentIds.isEmpty() ? Map.of() : adjustmentRepository.findAllById(adjustmentIds).stream()
                    .collect(Collectors.toMap(SettlementAdjustment::getId, Function.identity()));
        }

        Map<Long, CardView> views = new HashMap<>();
        for (Message card : cards) {
            views.put(card.getId(), card.getCardType() != null && card.getCardType().isSettlementAdjustment()
                    ? toAdjustmentView(card, proposals, adjustments)
                    : toView(card, resends.get(card.getRefId())));
        }
        return views;
    }

    private static List<Long> refIds(List<Message> cards, MessageRefType refType) {
        return cards.stream()
                .filter(m -> m.getRefType() == refType)
                .map(Message::getRefId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private CardView toView(Message card, ContractResendRequest resend) {
        MessageCardPayload p = MessageCardPayload.parse(card.getCardPayload());
        MessageCardType type = card.getCardType();
        if (type != MessageCardType.CONTRACT_RESEND_REQUEST) {
            // 결과 카드는 종결이다 — 조치가 남지 않는다.
            return new CardView(type, type.getTitle(), MessageCardTone.NEUTRAL,
                    p.contractId(), p.contractNumber(), p.groupBuyTitle(), null, null, null,
                    p.reasonLabel(), p.processedAt(), p.operatorId(), null, null, null, null, null);
        }
        MessageCardActionState state = actionState(resend);
        return new CardView(type, type.getTitle(),
                state == MessageCardActionState.PENDING ? MessageCardTone.WARNING : MessageCardTone.NEUTRAL,
                p.contractId(), p.contractNumber(), p.groupBuyTitle(),
                p.requesterType(), p.requesterName(), p.requestedAt(), null, null, null,
                state,
                resend == null ? null : resend.getHandledAt(),
                resend == null ? null : resend.getHandledBy(),
                resend == null ? null : resend.getNoticeMessageId(), null);
    }

    /**
     * 조정 카드 — 톤은 전부 NEUTRAL 이다(차례 색은 고정 카드 배지의 몫 · 5-4). 제안 카드의 버튼 상태는 제안 행에서 읽는다.
     */
    private CardView toAdjustmentView(Message card, Map<Long, SettlementAdjustmentProposal> proposals,
                                      Map<Long, SettlementAdjustment> adjustments) {
        MessageCardPayload p = MessageCardPayload.parse(card.getCardPayload());
        MessageCardType type = card.getCardType();
        MessageCardPayload.Adjustment snapshot = p.adjustment();
        SettlementAdjustment adjustment = snapshot == null || snapshot.adjustmentId() == null ? null
                : adjustments.get(snapshot.adjustmentId());
        SettlementAdjustmentProposal proposal = card.getRefType() == MessageRefType.SETTLEMENT_ADJUSTMENT_PROPOSAL
                ? proposals.get(card.getRefId()) : null;
        MessageCardActionState state = null;
        String resultLabel = null;
        if (type.isAdjustmentProposal()) {
            if (proposal == null || proposal.getStatus() == ProposalStatus.CLOSED
                    || (proposal.getStatus() == ProposalStatus.PENDING && adjustment != null
                    && adjustment.getStatus().isClosed())) {
                state = MessageCardActionState.CLOSED;
                resultLabel = ProposalStatus.CLOSED.getLabel();
            } else if (proposal.getStatus() == ProposalStatus.PENDING) {
                state = MessageCardActionState.PENDING;
            } else {
                state = MessageCardActionState.DONE;
                resultLabel = proposal.getStatus().getLabel();
            }
        }
        AdjustmentCard view = new AdjustmentCard(snapshot, adjustment == null ? null : adjustment.getStatus(),
                proposal == null ? null : proposal.getId(), proposal == null ? null : proposal.getSeq(),
                proposal == null ? null : proposal.getStatus(),
                proposal == null || proposal.getResponderType() == null ? null : proposal.getResponderType().name(),
                proposal == null ? null : proposal.getRespondedAt(), state, resultLabel);
        return new CardView(type, type.getTitle(), MessageCardTone.NEUTRAL, p.contractId(), p.contractNumber(),
                p.groupBuyTitle(), null, null, null, null, null, null, null, null, null, null, view);
    }

    /**
     * 알림 전에 계약이 서명 단계를 벗어났으면 닫힌 카드다 — 버튼이 남아 있으면 운영자가 「다시 보냈다」는
     * 거짓 안내를 보내게 된다. 요청 행이 사라진 카드도 같다.
     */
    private static MessageCardActionState actionState(ContractResendRequest resend) {
        if (resend == null) {
            return MessageCardActionState.CLOSED;
        }
        if (resend.isHandled()) {
            return MessageCardActionState.DONE;
        }
        return resend.getContract().getStatus() == ContractStatus.SIGNING
                ? MessageCardActionState.PENDING
                : MessageCardActionState.CLOSED;
    }
}

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
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageCardReader {

    private final ContractResendRequestRepository resendRequestRepository;

    /**
     * @param actionState 재발송 요청 카드만 — 결과 카드는 null
     * @param doneBy      알림을 보낸 운영자 id(어드민 전용)
     * @param processedBy 취소를 처리한 운영자 id(어드민 전용)
     */
    public record CardView(MessageCardType cardType, String title, MessageCardTone tone,
                           Long contractId, String contractNumber, String groupBuyTitle,
                           String requesterType, String requesterName, LocalDateTime requestedAt,
                           String reasonLabel, LocalDateTime processedAt, Long processedBy,
                           MessageCardActionState actionState, LocalDateTime doneAt, Long doneBy,
                           Long noticeMessageId) {
    }

    /** 메시지 id → 카드. 카드가 아닌 메시지는 결과에 없다. 재발송 요청의 상태는 페이지 전체를 한 번에 읽는다. */
    public Map<Long, CardView> read(Collection<Message> messages) {
        List<Message> cards = messages.stream().filter(Message::isCard).toList();
        if (cards.isEmpty()) {
            return Map.of();
        }
        List<Long> resendIds = cards.stream()
                .filter(m -> m.getRefType() == MessageRefType.CONTRACT_RESEND_REQUEST)
                .map(Message::getRefId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ContractResendRequest> resends = resendIds.isEmpty() ? Map.of()
                : resendRequestRepository.findWithContractByIdIn(resendIds).stream()
                        .collect(Collectors.toMap(ContractResendRequest::getId, Function.identity()));

        Map<Long, CardView> views = new HashMap<>();
        for (Message card : cards) {
            views.put(card.getId(), toView(card, resends.get(card.getRefId())));
        }
        return views;
    }

    private CardView toView(Message card, ContractResendRequest resend) {
        MessageCardPayload p = MessageCardPayload.parse(card.getCardPayload());
        MessageCardType type = card.getCardType();
        if (type != MessageCardType.CONTRACT_RESEND_REQUEST) {
            // 결과 카드는 종결이다 — 조치가 남지 않는다.
            return new CardView(type, type.getTitle(), MessageCardTone.NEUTRAL,
                    p.contractId(), p.contractNumber(), p.groupBuyTitle(), null, null, null,
                    p.reasonLabel(), p.processedAt(), p.operatorId(), null, null, null, null);
        }
        MessageCardActionState state = actionState(resend);
        return new CardView(type, type.getTitle(),
                state == MessageCardActionState.PENDING ? MessageCardTone.WARNING : MessageCardTone.NEUTRAL,
                p.contractId(), p.contractNumber(), p.groupBuyTitle(),
                p.requesterType(), p.requesterName(), p.requestedAt(), null, null, null,
                state,
                resend == null ? null : resend.getHandledAt(),
                resend == null ? null : resend.getHandledBy(),
                resend == null ? null : resend.getNoticeMessageId());
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

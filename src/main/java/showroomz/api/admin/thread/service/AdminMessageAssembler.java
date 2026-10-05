package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.thread.dto.AdminThreadDto.Card;
import showroomz.api.admin.thread.dto.AdminThreadDto.CardAction;
import showroomz.api.admin.thread.dto.AdminThreadDto.CardDetail;
import showroomz.api.admin.thread.dto.AdminThreadDto.MessageItem;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.api.common.thread.dto.MessageCardResponse;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.service.MessageCardReader;
import showroomz.domain.message.service.MessageCardReader.CardView;
import showroomz.domain.message.type.MessageCardActionState;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.ParticipantType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 어드민 메시지 응답 조립(36 설계 3-5 · 4절) — 운영자 이름이 붙는 유일한 지점이다.
 *
 * <p>이름은 페이지의 운영자 id 집합을 한 번에 읽는다(메시지당 조회 금지). 현재 이름이다 — 스냅샷하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AdminMessageAssembler {

    private final MessageAttachmentRepository attachments;
    private final MessageAttachmentService attachmentService;
    private final MessageCardReader cardReader;
    private final SellerRepository sellers;

    public List<MessageItem> assemble(MessageThread thread, List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttachmentSummary>> attachmentsByMessage = loadAttachments(messages);
        Map<Long, CardView> cards = cardReader.read(messages);
        Map<Long, String> operatorNames = loadOperatorNames(messages, cards);
        String memberName = AdminThreadAccess.memberNameOf(thread);
        return messages.stream()
                .map(m -> toItem(m, memberName, attachmentsByMessage.getOrDefault(m.getId(), List.of()),
                        cards.get(m.getId()), operatorNames))
                .toList();
    }

    public MessageItem assembleOne(MessageThread thread, Message message) {
        return assemble(thread, List.of(message)).get(0);
    }

    private MessageItem toItem(Message m, String memberName, List<AttachmentSummary> attachmentSummaries,
                               CardView card, Map<Long, String> operatorNames) {
        boolean byOperator = m.getSenderType() == ParticipantType.ADMIN;
        boolean systemSender = byOperator && isSystemOperator(m.getSenderId());
        boolean bubbleByOperator = byOperator && !m.isCard();
        return new MessageItem(m.getId(), m.getMessageType(), m.getSenderType(),
                bubbleByOperator,
                byOperator ? null : memberName,
                bubbleByOperator ? operatorNames.get(m.getSenderId()) : null,
                bubbleByOperator && (m.isAutoNotice() || systemSender),
                m.getContent(), attachmentSummaries, toCard(card, operatorNames), m.getCreatedAt());
    }

    private Card toCard(CardView view, Map<Long, String> operatorNames) {
        if (view == null) {
            return null;
        }
        boolean resend = view.cardType() == MessageCardType.CONTRACT_RESEND_REQUEST;
        CardDetail detail = resend
                ? new CardDetail(view.requesterType(), view.requesterName(), view.requestedAt(), null, null, null, null)
                : new CardDetail(null, null, null, view.reasonLabel(), view.processedAt(),
                        nameOf(view.processedBy(), operatorNames), true);
        CardAction action = view.actionState() == null ? null
                : new CardAction(MessageCardResponse.RESEND_NOTICE, view.actionState(),
                        view.actionState() == MessageCardActionState.PENDING,
                        view.doneAt(), nameOf(view.doneBy(), operatorNames), view.noticeMessageId());
        return new Card(view.cardType(), view.title(), view.tone(), view.contractId(), view.contractNumber(),
                view.groupBuyTitle(), detail, action);
    }

    private Map<Long, String> loadOperatorNames(List<Message> messages, Map<Long, CardView> cards) {
        Set<Long> ids = new HashSet<>();
        for (Message m : messages) {
            if (m.getSenderType() == ParticipantType.ADMIN && !isSystemOperator(m.getSenderId())) {
                ids.add(m.getSenderId());
            }
        }
        for (CardView card : cards.values()) {
            if (card.doneBy() != null) ids.add(card.doneBy());
            if (card.processedBy() != null) ids.add(card.processedBy());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (Seller seller : sellers.findAllById(ids)) {
            names.put(seller.getId(), seller.getName());
        }
        return names;
    }

    private static String nameOf(Long operatorId, Map<Long, String> operatorNames) {
        return operatorId == null ? null : operatorNames.get(operatorId);
    }

    /** 가입 안내처럼 개별 운영자가 아니라 시스템이 운영팀 이름으로 보낸 메시지. */
    private static boolean isSystemOperator(Long senderId) {
        return senderId == null || senderId == OperatorChannelService.SYSTEM_OPERATOR_ID;
    }

    private Map<Long, List<AttachmentSummary>> loadAttachments(List<Message> messages) {
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        return attachments.findByMessage_IdInOrderBySortOrderAsc(messageIds).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getMessage().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparing(MessageAttachment::getSortOrder,
                                                Comparator.nullsLast(Comparator.naturalOrder())))
                                        .map(attachmentService::toSummary)
                                        .toList())));
    }
}

package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.service.port.ContractChannelGateway;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageRefType;
import showroomz.domain.message.type.ParticipantType;

/**
 * 운영팀 채널 포트 구현(36 설계 5-1 · 6절).
 *
 * <p>카드의 보낸 사람은 그 카드를 생기게 한 주체다 — 요청 카드는 요청자(브랜드 = 마켓 id · 인플루언서 = 크리에이터 id),
 * 결과 카드는 처리 운영자. 안 읽은 수가 「본인이 보낸 것 제외」라 요청 카드는 운영팀 배지만, 결과 카드는 회원 배지만 올린다.
 *
 * <p>멱등키는 참조 객체에서 만든다 — 같은 요청 · 같은 계약에 카드가 두 장 붙지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MessageContractChannelGateway implements ContractChannelGateway {

    private static final String RESEND_CARD_KEY = "resend-card-%d";
    private static final String CANCEL_CARD_KEY = "contract-cancel-%d";

    private final OperatorChannelService operatorChannelService;
    private final MessageThreadService messageThreadService;

    @Override
    public ChannelCard postResendRequestCard(Contract contract, ContractResendRequest request) {
        boolean bySeller = request.getRequesterType() == ContractActorType.SELLER;
        MessageThread thread = channelOf(contract, request.getRequesterType());
        String requesterName = bySeller
                ? contract.getMarket().getMarketName()
                : contract.getCreator().getShowroomName();

        Message card = messageThreadService.postSystemCard(thread,
                bySeller ? ParticipantType.SELLER : ParticipantType.CREATOR,
                bySeller ? contract.getMarket().getId() : contract.getCreator().getId(),
                RESEND_CARD_KEY.formatted(request.getId()),
                MessageCardType.CONTRACT_RESEND_REQUEST, MessageRefType.CONTRACT_RESEND_REQUEST, request.getId(),
                MessageCardPayload.resendRequest(contract.getId(), contract.getContractNumber(), contract.getTitle(),
                        request.getRequesterType().name(), requesterName, request.getRequestedAt())).message();
        return new ChannelCard(thread.getId(), card.getId());
    }

    @Override
    public ChannelCard postAdminCanceledCard(Contract contract, ContractActorType requesterType, Long operatorId) {
        MessageThread thread = channelOf(contract, requesterType);
        Message card = messageThreadService.postSystemCard(thread, ParticipantType.ADMIN, operatorId,
                CANCEL_CARD_KEY.formatted(contract.getId()),
                MessageCardType.CONTRACT_ADMIN_CANCELED, MessageRefType.CONTRACT, contract.getId(),
                MessageCardPayload.adminCanceled(contract.getId(), contract.getContractNumber(), contract.getTitle(),
                        cancelReasonLabel(contract), contract.getClosedAt(), operatorId)).message();
        return new ChannelCard(thread.getId(), card.getId());
    }

    private MessageThread channelOf(Contract contract, ContractActorType party) {
        return switch (party) {
            case SELLER -> operatorChannelService.requireMarketChannel(contract.getMarket());
            case CREATOR -> operatorChannelService.requireCreatorChannel(contract.getCreator());
            default -> throw new IllegalArgumentException("운영팀 채널의 당사자가 아니다: " + party);
        };
    }

    /** 사유 라벨 + 메모 — 메모는 당사자에게 그대로 공개한다(36 설계 4-3 · 2026.10.05 확정). */
    private static String cancelReasonLabel(Contract contract) {
        String label = reasonLabel(contract.getCloseReasonCode());
        String memo = contract.getCloseReasonMemo();
        if (memo == null || memo.isBlank()) {
            return label;
        }
        return label == null ? memo : label + " — " + memo;
    }

    private static String reasonLabel(String code) {
        if (code == null) {
            return null;
        }
        try {
            return ContractCloseReasonCode.valueOf(code).getLabel();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }
}

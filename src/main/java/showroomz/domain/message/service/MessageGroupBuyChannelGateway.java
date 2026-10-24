package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageRefType;
import showroomz.domain.message.type.ParticipantType;

import java.util.ArrayList;
import java.util.List;

/**
 * 공구의 운영팀 채널 카드(1021 설계서 2-3 · 결정 노트 기-21 · 어드민 20a A8 ①) — 직권 중단 사전 통지 · 소명 요청을 <b>브랜드와 인플루언서
 * 양측</b> 운영팀 채널에 붙인다. 카드에는 버튼이 없다 — 의견은 답글로 받고 집행 · 철회는 공구 관리에서 한다.
 *
 * <p>등록은 호출자의 트랜잭션에 합류한다(통지와 카드가 함께 커밋된다). 멱등키는 통지 id 다.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class MessageGroupBuyChannelGateway {

    private static final String NOTICE_KEY = "groupbuy-suspension-notice-%d";

    private final OperatorChannelService operatorChannelService;
    private final MessageThreadService messageThreadService;

    /**
     * 카드마다 <b>그 채널의 상대 당사자</b>(브랜드 채널 → 인플루언서 · 인플루언서 채널 → 브랜드)를 담는다 — 「브랜드(퓨어랩)에도 같은 통지가
     * 갔습니다」(어드민 20a A8 ①). 나머지 사실은 두 카드가 같다.
     *
     * @return 브랜드 · 인플루언서 채널 순의 카드
     */
    public List<Card> postSuspensionNoticeCards(GroupBuy groupBuy, GroupBuyAdminSuspension notice, Long operatorId) {
        MessageThread brandChannel = operatorChannelService.requireMarketChannel(groupBuy.getMarket());
        MessageThread creatorChannel = groupBuy.getCreator() == null ? null
                : operatorChannelService.requireCreatorChannel(groupBuy.getCreator());
        boolean bothNoticed = creatorChannel != null;
        String brandName = groupBuy.getMarket().getMarketName();
        String showroomName = groupBuy.getCreator() == null ? null : groupBuy.getCreator().getShowroomName();

        List<Card> cards = new ArrayList<>();
        cards.add(post(brandChannel, groupBuy, notice, operatorId, bothNoticed,
                bothNoticed ? ParticipantType.CREATOR.name() : null, showroomName));
        if (creatorChannel != null) {
            cards.add(post(creatorChannel, groupBuy, notice, operatorId, true, ParticipantType.SELLER.name(), brandName));
        }
        return cards;
    }

    private Card post(MessageThread channel, GroupBuy groupBuy, GroupBuyAdminSuspension notice, Long operatorId,
                      boolean counterpartyNoticed, String counterpartyType, String counterpartyName) {
        MessageCardPayload payload = MessageCardPayload.notice(groupBuy.getContract().getId(),
                groupBuy.getContract().getContractNumber(), groupBuy.getContract().getTitle(), null, null, notice.getNoticedAt(),
                notice.getReasonClause() == null ? null : notice.getReasonClause().getLabel(), null, operatorId,
                new MessageCardPayload.Notice("ADMIN_SUSPENSION", notice.getNoticeBody(), null, groupBuy.getId(),
                        groupBuy.getGroupBuyNumber(),
                        notice.getReasonClause() == null ? null : notice.getReasonClause().name(),
                        notice.getReasonClause() == null ? null : notice.getReasonClause().getLabel(),
                        notice.getExecuteScheduledAt(), notice.getAppealDeadlineAt(), counterpartyNoticed,
                        counterpartyType, counterpartyNoticed ? counterpartyName : null));
        Message card = messageThreadService.postSystemCard(channel, ParticipantType.ADMIN, operatorId,
                NOTICE_KEY.formatted(notice.getId()), MessageCardType.GROUP_BUY_SUSPENSION_NOTICE,
                MessageRefType.GROUP_BUY_ADMIN_SUSPENSION, notice.getId(), payload).message();
        return new Card(channel.getId(), card.getId());
    }

    public record Card(Long threadId, Long messageId) {
    }
}

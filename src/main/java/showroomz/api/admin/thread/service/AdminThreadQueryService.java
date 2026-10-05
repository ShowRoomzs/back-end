package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.thread.AdminChannelMemberNumber;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelInfo;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelListItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.MessageList;
import showroomz.api.admin.thread.dto.AdminThreadDto.Summary;
import showroomz.api.admin.thread.dto.AdminThreadDto.TabSummary;
import showroomz.api.admin.thread.type.AdminChannelMemberStatus;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.ParticipantType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 어드민 소통 스레드 조회(36 설계 3절) — 운영팀 1:1 채널의 목록 · 배지 · 정보 바 · 메시지.
 *
 * <p>「새 대화」가 없다 — 모든 브랜드 · 인플루언서와 채널이 하나씩 미리 있고 찾기는 검색이다(§36-0).
 * 통신은 폴링이다(13-14 설계 0절) — 배지용 {@link #summary()}는 채널 수와 무관하게 쿼리 2회다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminThreadQueryService {

    private static final List<ConnectionType> OPERATOR_TYPES =
            List.of(ConnectionType.OPERATOR_MARKET, ConnectionType.OPERATOR_CREATOR);

    private final AdminThreadAccess access;
    private final MessageThreadRepository threads;
    private final MessageRepository messages;
    private final MessageThreadService messageThreadService;
    private final ContractResendRequestRepository resendRequests;
    private final AdminMessageAssembler messageAssembler;
    private final AdminChannelInfoReader infoReader;

    /**
     * 채널 목록 — 최근 메시지순, 메시지 0건 채널은 맨 아래다(정렬은 쿼리가 소유한다).
     * 검색어가 그 탭의 회원번호 접두로 시작하면 번호 축으로만 본다 — 숫자가 아니면 결과 0건이다.
     */
    public PageResponse<ChannelListItem> list(AdminChannelTab tab, String keyword, PagingRequest paging) {
        Pageable pageable = paging.toPageable(Sort.unsorted());
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Long memberId = null;
        if (AdminChannelMemberNumber.looksLikeMemberNumber(tab, trimmed)) {
            memberId = AdminChannelMemberNumber.parseOrNull(tab, trimmed);
            if (memberId == null) {
                return PageResponse.of(Page.empty(pageable));
            }
            trimmed = null;
        }
        Page<MessageThread> page = tab == AdminChannelTab.BRAND
                ? threads.findOperatorMarketChannels(memberId, trimmed, pageable)
                : threads.findOperatorCreatorChannels(memberId, trimmed, pageable);
        Map<Long, Long> unread = messageThreadService.countUnreadForOperatorTeam(
                page.getContent().stream().map(MessageThread::getId).toList());
        return PageResponse.of(page.map(thread -> toListItem(thread, unread.getOrDefault(thread.getId(), 0L))));
    }

    /**
     * 탭 배지 — 숫자의 뜻이 확정되지 않아(§36-9 B-7) 안 읽은 메시지 수와 미처리 카드 수를 함께 내린다.
     * 「미답 채널 수」는 SLA 정의가 있어야 계산할 수 있어 만들지 않는다.
     */
    public Summary summary() {
        Map<ConnectionType, Long> unread = new HashMap<>();
        for (Object[] row : messages.sumUnreadForOperatorTeamByConnectionType(
                OPERATOR_TYPES, MessageThreadService.OPERATOR_TEAM_PARTICIPANT_ID)) {
            unread.put((ConnectionType) row[0], (Long) row[1]);
        }
        Map<ContractActorType, Long> pending = new HashMap<>();
        for (Object[] row : resendRequests.countPendingCardsByRequesterType()) {
            pending.put((ContractActorType) row[0], (Long) row[1]);
        }
        return new Summary(
                new TabSummary(unread.getOrDefault(ConnectionType.OPERATOR_MARKET, 0L),
                        pending.getOrDefault(ContractActorType.SELLER, 0L)),
                new TabSummary(unread.getOrDefault(ConnectionType.OPERATOR_CREATOR, 0L),
                        pending.getOrDefault(ContractActorType.CREATOR, 0L)),
                null);
    }

    /** 스레드 헤더 + 접이식 정보 바 — 접혀 있어도 요약 줄이 보이므로 스레드 진입 시 1회 부른다. */
    public ChannelInfo info(Long threadId) {
        MessageThread thread = access.requireOperatorChannel(threadId);
        Connection connection = thread.getConnection();
        AdminChannelTab tab = AdminThreadAccess.tabOf(thread);
        AdminChannelMemberStatus status = AdminThreadAccess.memberStatusOf(thread);
        Long memberId = AdminThreadAccess.memberIdOf(thread);
        if (tab == AdminChannelTab.BRAND) {
            Market market = connection.getMarket();
            return new ChannelInfo(thread.getId(), tab, market.getMarketName(), market.getMarketImageUrl(),
                    AdminChannelMemberNumber.format(tab, memberId), memberId, status, status.isWritable(),
                    infoReader.brandProfile(market), infoReader.brandProgress(market),
                    infoReader.openIssueThreads(market.getId(), null));
        }
        Creator creator = connection.getCreator();
        return new ChannelInfo(thread.getId(), tab, creator.getShowroomName(), AdminThreadAccess.memberImageOf(thread),
                AdminChannelMemberNumber.format(tab, memberId), memberId, status, status.isWritable(),
                infoReader.creatorProfile(creator), infoReader.creatorProgress(creator),
                infoReader.openIssueThreads(null, creator.getId()));
    }

    /** 최신순 커서 페이징 — size+1개를 읽어 hasNext를 판정한다(count 쿼리 없이). */
    public MessageList messages(Long threadId, Long cursor, int size) {
        MessageThread thread = access.requireOperatorChannel(threadId);
        List<Message> fetched = messageThreadService.getMessages(thread, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<Message> page = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new MessageList(messageAssembler.assemble(thread, page), nextCursor, hasNext);
    }

    private ChannelListItem toListItem(MessageThread thread, long unread) {
        Connection connection = thread.getConnection();
        AdminChannelTab tab = AdminThreadAccess.tabOf(thread);
        AdminChannelMemberStatus status = AdminThreadAccess.memberStatusOf(thread);
        Long memberId = AdminThreadAccess.memberIdOf(thread);
        boolean brand = tab == AdminChannelTab.BRAND;
        return new ChannelListItem(thread.getId(), tab, AdminThreadAccess.memberNameOf(thread),
                AdminThreadAccess.memberImageOf(thread), AdminChannelMemberNumber.format(tab, memberId), memberId,
                brand ? connection.getMarket().getSeller().getName() : null,
                brand ? null : connection.getCreator().getBusinessType(),
                status, thread.getLastMessagePreview(),
                thread.getLastMessageSenderType() == ParticipantType.ADMIN,
                thread.getLastMessageAt(), unread, status.isWritable());
    }
}

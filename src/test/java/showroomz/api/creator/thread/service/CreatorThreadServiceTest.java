package showroomz.api.creator.thread.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.api.creator.thread.dto.ThreadListItem;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreatorThreadServiceTest {

    private static final String CREATOR_EMAIL = "soyeon@showroomz.co.kr";
    private static final long CREATOR_ID = 12L;
    private static final long THREAD_ID = 55L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private MessageThreadRepository messageThreadRepository;
    @Mock
    private MessageThreadService messageThreadService;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;
    @Mock
    private MessageAttachmentService messageAttachmentService;
    @Mock
    private ContractRepository contractRepository;

    @InjectMocks
    private CreatorThreadService creatorThreadService;

    private Creator me;

    @BeforeEach
    void setUp() {
        me = Creator.builder().id(CREATOR_ID).showroomName("뷰티_소연").build();
    }

    private void givenAuthenticatedCreator() {
        Users user = new Users();
        given(userRepository.findByUsername(CREATOR_EMAIL)).willReturn(Optional.of(user));
        given(creatorRepository.findByUser(user)).willReturn(Optional.of(me));
    }

    private static Market market() {
        Market market = new Market();
        market.setId(7L);
        market.setMarketName("글로우랩");
        market.setMarketImageUrl("https://cdn.example.com/market/7.png");
        return market;
    }

    private MessageThread connectedPairThread() {
        Connection connection = Connection.requestPair(market(), me);
        connection.markConnected();
        return MessageThread.builder().id(THREAD_ID).connection(connection).status(ThreadStatus.OPEN).build();
    }

    @Test
    @DisplayName("상대 브랜드 목록 항목은 브랜드명·대표 이미지를 내려주고, 받은 계약이 없으면 [계약 확인] 게이트가 false다 (S1)")
    void exposesCounterpartBrand() {
        givenAuthenticatedCreator();
        given(messageThreadRepository.findOpenThreadsForCreator(eq(me), eq(ThreadStatus.OPEN), isNull(), any()))
                .willReturn(new PageImpl<>(List.of(connectedPairThread())));
        given(messageThreadService.countUnreadByThreadIds(List.of(THREAD_ID), ParticipantType.CREATOR, CREATOR_ID))
                .willReturn(Map.of(THREAD_ID, 2L));
        given(contractRepository.findMarketIdsWithReceivedContract(
                CREATOR_ID, List.of(7L), ContractStatus.RECEIVED_BY_CREATOR)).willReturn(List.of());

        ThreadListItem item = creatorThreadService.getThreads(CREATOR_EMAIL, null, new PagingRequest())
                .getContent().get(0);

        assertThat(item.isOperatorChannel()).isFalse();
        assertThat(item.getCounterpartName()).isEqualTo("글로우랩");
        assertThat(item.getCounterpartImageUrl()).isEqualTo("https://cdn.example.com/market/7.png");
        assertThat(item.isHasContract()).isFalse();
        assertThat(item.getUnreadCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("이 브랜드에게서 받은 계약이 있으면 [계약 확인] 게이트가 true다 — 페이지 전체를 한 쿼리로 본다 (§14-2)")
    void hasContractWhenReceivedFromBrand() {
        givenAuthenticatedCreator();
        given(messageThreadRepository.findOpenThreadsForCreator(eq(me), eq(ThreadStatus.OPEN), isNull(), any()))
                .willReturn(new PageImpl<>(List.of(connectedPairThread())));
        given(messageThreadService.countUnreadByThreadIds(List.of(THREAD_ID), ParticipantType.CREATOR, CREATOR_ID))
                .willReturn(Map.of());
        // 가시성 집합은 상세 조회와 같은 「도착한 계약」이다 — 작성중·검토 중인 계약은 세지 않는다.
        given(contractRepository.findMarketIdsWithReceivedContract(
                CREATOR_ID, List.of(7L), ContractStatus.RECEIVED_BY_CREATOR)).willReturn(List.of(7L));

        ThreadListItem item = creatorThreadService.getThreads(CREATOR_EMAIL, null, new PagingRequest())
                .getContent().get(0);

        assertThat(item.isHasContract()).isTrue();
    }

    @Test
    @DisplayName("운영자 채널은 상대 브랜드가 없으므로 아바타 없이 고정 표시명만 내려준다 (S2)")
    void operatorChannelHasNoCounterpartImage() {
        MessageThread thread = MessageThread.builder()
                .id(THREAD_ID).connection(Connection.createOperatorCreator(me)).status(ThreadStatus.OPEN).build();

        givenAuthenticatedCreator();
        given(messageThreadRepository.findOpenThreadsForCreator(eq(me), eq(ThreadStatus.OPEN), isNull(), any()))
                .willReturn(new PageImpl<>(List.of(thread)));
        given(messageThreadService.countUnreadByThreadIds(List.of(THREAD_ID), ParticipantType.CREATOR, CREATOR_ID))
                .willReturn(Map.of());

        ThreadListItem item = creatorThreadService.getThreads(CREATOR_EMAIL, null, new PagingRequest())
                .getContent().get(0);

        assertThat(item.isOperatorChannel()).isTrue();
        assertThat(item.getCounterpartName()).isEqualTo("SHOWROOMZ 운영팀");
        assertThat(item.getCounterpartImageUrl()).isNull();
        // 운영자 채널에는 계약이 없다 — 조회할 브랜드가 없으므로 쿼리도 나가지 않는다.
        assertThat(item.isHasContract()).isFalse();
        verify(contractRepository, never()).findMarketIdsWithReceivedContract(any(), any(), any());
    }

    @Test
    @DisplayName("브랜드명 검색어는 공백을 정리해서 넘기고, 빈 값이면 전체 조회(null)로 넘긴다")
    void normalizesSearchKeyword() {
        givenAuthenticatedCreator();
        given(messageThreadRepository.findOpenThreadsForCreator(eq(me), eq(ThreadStatus.OPEN), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        creatorThreadService.getThreads(CREATOR_EMAIL, "  글로우  ", new PagingRequest());
        creatorThreadService.getThreads(CREATOR_EMAIL, "   ", new PagingRequest());

        verify(messageThreadRepository)
                .findOpenThreadsForCreator(eq(me), eq(ThreadStatus.OPEN), eq("글로우"), any());
        verify(messageThreadRepository)
                .findOpenThreadsForCreator(eq(me), eq(ThreadStatus.OPEN), isNull(), any());
    }

    /** 브랜드(SELLER)가 올린 첨부 — 다운로드는 업로더가 아니라 스레드 참가자 기준이어야 한다. */
    private static MessageAttachment counterpartAttachment(MessageThread thread) {
        return MessageAttachment.pending(thread, ParticipantType.SELLER, 7L, AttachmentType.DOCUMENT,
                "uploads/message/55/uuid.pdf", "https://cdn.example.com/uuid.pdf",
                "계약서.pdf", "pdf", "application/pdf", 2048L);
    }

    @Test
    @DisplayName("내 스레드의 첨부면 상대(브랜드)가 올린 것도 다운로드 URL 발급으로 위임한다 (§13-8)")
    void delegatesDownloadForCounterpartAttachment() {
        MessageThread thread = connectedPairThread();
        MessageAttachment attachment = counterpartAttachment(thread);
        AttachmentDownloadResponse expected =
                new AttachmentDownloadResponse(501L, "https://s3.example/download", "계약서.pdf", 2048L, 300L);

        givenAuthenticatedCreator();
        given(messageAttachmentRepository.findById(501L)).willReturn(Optional.of(attachment));
        given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.of(thread));
        given(messageAttachmentService.createDownloadUrl(attachment, ParticipantType.CREATOR, CREATOR_ID))
                .willReturn(expected);

        assertThat(creatorThreadService.getDownloadUrl(CREATOR_EMAIL, 501L)).isSameAs(expected);
    }

    @Test
    @DisplayName("남의 스레드에 속한 첨부는 다운로드 URL을 발급하지 않는다")
    void deniesDownloadForOtherCreatorsThread() {
        Creator otherCreator = Creator.builder().id(999L).showroomName("남의쇼룸").build();
        Connection connection = Connection.requestPair(market(), otherCreator);
        connection.markConnected();
        MessageThread otherThread = MessageThread.builder()
                .id(THREAD_ID).connection(connection).status(ThreadStatus.OPEN).build();

        givenAuthenticatedCreator();
        given(messageAttachmentRepository.findById(501L))
                .willReturn(Optional.of(counterpartAttachment(otherThread)));
        given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.of(otherThread));

        assertThatThrownBy(() -> creatorThreadService.getDownloadUrl(CREATOR_EMAIL, 501L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.THREAD_ACCESS_DENIED);
        verify(messageAttachmentService, never()).createDownloadUrl(any(), any(), any());
    }
}

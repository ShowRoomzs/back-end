package showroomz.api.seller.thread.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.thread.dto.SendMessageRequest;
import showroomz.api.seller.thread.dto.ThreadListItem;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.AttachmentStatus;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerThreadServiceTest {

    private static final String SELLER_EMAIL = "brand@showroomz.co.kr";
    private static final long MARKET_ID = 7L;
    private static final long THREAD_ID = 1L;

    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private MessageThreadRepository messageThreadRepository;
    @Mock
    private MessageThreadService messageThreadService;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;
    @Mock
    private MessageAttachmentService messageAttachmentService;

    @InjectMocks
    private SellerThreadService sellerThreadService;

    private Market market;
    private MessageThread myThread;

    @BeforeEach
    void setUp() {
        market = new Market();
        market.setId(MARKET_ID);
        market.setMarketName("코코브라운");

        Creator creator = Creator.builder().id(12L).showroomName("뷰티_소연").build();
        Connection connection = Connection.requestPair(market, creator);
        connection.markConnected();
        myThread = MessageThread.builder().id(THREAD_ID).connection(connection).status(ThreadStatus.OPEN).build();
    }

    private void givenAuthenticatedSeller() {
        Seller seller = new Seller();
        given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.of(seller));
        given(marketRepository.findBySeller(seller)).willReturn(Optional.of(market));
    }

    private static SendMessageRequest sendRequest(String clientMessageId, String content, List<Long> attachmentIds) {
        SendMessageRequest request = new SendMessageRequest();
        request.setClientMessageId(clientMessageId);
        request.setContent(content);
        request.setAttachmentIds(attachmentIds);
        return request;
    }

    @Nested
    @DisplayName("스레드 접근")
    class ThreadAccess {

        @Test
        @DisplayName("없는 스레드 id는 404다")
        void unknownThreadIsNotFound() {
            givenAuthenticatedSeller();
            given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sellerThreadService.markRead(SELLER_EMAIL, THREAD_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.THREAD_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 브랜드의 스레드는 열 수 없다")
        void otherMarketsThreadIsDenied() {
            Market other = new Market();
            other.setId(99L);
            Connection foreign = Connection.requestPair(other, Creator.builder().id(12L).showroomName("뷰티_소연").build());
            foreign.markConnected();
            MessageThread foreignThread = MessageThread.builder()
                    .id(THREAD_ID).connection(foreign).status(ThreadStatus.OPEN).build();

            givenAuthenticatedSeller();
            given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.of(foreignThread));

            assertThatThrownBy(() -> sellerThreadService.markRead(SELLER_EMAIL, THREAD_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.THREAD_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("스레드 목록")
    class ThreadList {

        @Test
        @DisplayName("상대 아바타(A1)는 CREATOR가 아니라 USERS의 프로필 이미지를 내려준다")
        void exposesCounterpartProfileImage() {
            Users user = new Users();
            user.setProfileImageUrl("https://cdn.example.com/profiles/12.png");
            Creator creator = Creator.builder().id(12L).showroomName("뷰티_소연").user(user).build();
            Connection connection = Connection.requestPair(market, creator);
            connection.markConnected();
            MessageThread thread = MessageThread.builder()
                    .id(THREAD_ID).connection(connection).status(ThreadStatus.OPEN).build();

            givenAuthenticatedSeller();
            given(messageThreadRepository.findOpenThreadsForMarket(eq(market), eq(ThreadStatus.OPEN), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(thread)));
            given(messageThreadService.countUnreadByThreadIds(List.of(THREAD_ID), ParticipantType.SELLER, MARKET_ID))
                    .willReturn(Map.of(THREAD_ID, 2L));

            ThreadListItem item = sellerThreadService.getThreads(SELLER_EMAIL, null, new PagingRequest())
                    .getContent().get(0);

            assertThat(item.getCounterpartName()).isEqualTo("뷰티_소연");
            assertThat(item.getCounterpartImageUrl()).isEqualTo("https://cdn.example.com/profiles/12.png");
            assertThat(item.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
            assertThat(item.getUnreadCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("운영자 채널은 상대 크리에이터가 없으므로 아바타 없이 고정 표시명만 내려준다 (A2)")
        void operatorChannelHasNoCounterpartImage() {
            Connection operator = Connection.createOperatorMarket(market);
            MessageThread thread = MessageThread.builder()
                    .id(THREAD_ID).connection(operator).status(ThreadStatus.OPEN).build();

            givenAuthenticatedSeller();
            given(messageThreadRepository.findOpenThreadsForMarket(eq(market), eq(ThreadStatus.OPEN), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(thread)));
            given(messageThreadService.countUnreadByThreadIds(List.of(THREAD_ID), ParticipantType.SELLER, MARKET_ID))
                    .willReturn(Map.of());

            ThreadListItem item = sellerThreadService.getThreads(SELLER_EMAIL, null, new PagingRequest())
                    .getContent().get(0);

            assertThat(item.isOperatorChannel()).isTrue();
            assertThat(item.getCounterpartName()).isEqualTo("SHOWROOMZ 운영팀");
            assertThat(item.getCounterpartImageUrl()).isNull();
        }

        @Test
        @DisplayName("쇼룸명 검색어는 공백을 정리해서 넘기고, 빈 값이면 전체 조회(null)로 넘긴다")
        void normalizesSearchKeyword() {
            givenAuthenticatedSeller();
            given(messageThreadRepository.findOpenThreadsForMarket(eq(market), eq(ThreadStatus.OPEN), any(), any()))
                    .willReturn(new PageImpl<>(List.of()));

            sellerThreadService.getThreads(SELLER_EMAIL, "  민지  ", new PagingRequest());
            sellerThreadService.getThreads(SELLER_EMAIL, "   ", new PagingRequest());

            verify(messageThreadRepository)
                    .findOpenThreadsForMarket(eq(market), eq(ThreadStatus.OPEN), eq("민지"), any());
            verify(messageThreadRepository)
                    .findOpenThreadsForMarket(eq(market), eq(ThreadStatus.OPEN), isNull(), any());
        }
    }

    @Nested
    @DisplayName("메시지 전송")
    class SendMessage {

        @Test
        @DisplayName("내 스레드면 SELLER·marketId로 도메인 전송을 위임한다")
        void delegatesToDomainService() {
            givenAuthenticatedSeller();
            given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.of(myThread));

            Message saved = Message.create(myThread, ParticipantType.SELLER, MARKET_ID, "uuid-1", "안녕하세요");
            ReflectionTestUtils.setField(saved, "id", 100L);
            given(messageThreadService.sendMessage(
                    myThread, ParticipantType.SELLER, MARKET_ID, "uuid-1", "안녕하세요", null))
                    .willReturn(new MessageThreadService.SendResult(saved, true));
            given(messageAttachmentRepository.findByMessage_IdInOrderBySortOrderAsc(List.of(100L)))
                    .willReturn(List.of());

            SellerThreadService.SendMessageOutcome outcome = sellerThreadService.sendMessage(
                    SELLER_EMAIL, THREAD_ID, sendRequest("uuid-1", "안녕하세요", null));

            assertThat(outcome.created()).isTrue();
            assertThat(outcome.item().isMine()).isTrue();
            assertThat(outcome.item().getContent()).isEqualTo("안녕하세요");
        }

        @Test
        @DisplayName("첨부 id를 그대로 넘겨 메시지에 연결한다")
        void passesAttachmentIds() {
            givenAuthenticatedSeller();
            given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.of(myThread));

            Message saved = Message.create(myThread, ParticipantType.SELLER, MARKET_ID, "uuid-1", null);
            ReflectionTestUtils.setField(saved, "id", 100L);
            given(messageThreadService.sendMessage(
                    eq(myThread), eq(ParticipantType.SELLER), eq(MARKET_ID),
                    eq("uuid-1"), isNull(), eq(List.of(501L, 502L))))
                    .willReturn(new MessageThreadService.SendResult(saved, true));
            given(messageAttachmentRepository.findByMessage_IdInOrderBySortOrderAsc(List.of(100L)))
                    .willReturn(List.of());

            sellerThreadService.sendMessage(
                    SELLER_EMAIL, THREAD_ID, sendRequest("uuid-1", null, List.of(501L, 502L)));

            verify(messageThreadService).sendMessage(
                    myThread, ParticipantType.SELLER, MARKET_ID, "uuid-1", null, List.of(501L, 502L));
        }
    }

    @Nested
    @DisplayName("파일 첨부")
    class Attachment {

        @Test
        @DisplayName("presign은 내 스레드 검증 후 첨부 서비스에 위임한다")
        void presignDelegatesAfterOwnershipCheck() {
            givenAuthenticatedSeller();
            given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.of(myThread));
            PresignRequest request = new PresignRequest();
            request.setFileName("a.jpg");
            request.setContentType("image/jpeg");
            request.setSizeBytes(10L);
            PresignResponse expected = new PresignResponse(501L, "https://upload", "image/jpeg", 900L);
            given(messageAttachmentService.createPresignedUpload(
                    myThread, ParticipantType.SELLER, MARKET_ID, request))
                    .willReturn(expected);

            PresignResponse response = sellerThreadService.createPresignedUpload(SELLER_EMAIL, THREAD_ID, request);

            assertThat(response).isSameAs(expected);
        }

        @Test
        @DisplayName("complete는 marketId 소유자로 첨부 서비스에 위임한다")
        void completeDelegatesWithMarketId() {
            givenAuthenticatedSeller();
            CompleteAttachmentRequest request = new CompleteAttachmentRequest();
            request.setDurationSeconds(58);
            AttachmentSummary expected = new AttachmentSummary(
                    501L, AttachmentStatus.UPLOADED, AttachmentType.VIDEO,
                    "https://cdn/a.mp4", "a.mp4", "mp4", 100L, 58, null);
            given(messageAttachmentService.completeUpload(
                    ParticipantType.SELLER, MARKET_ID, 501L, 58))
                    .willReturn(expected);

            AttachmentSummary summary = sellerThreadService.completeUpload(SELLER_EMAIL, 501L, request);

            assertThat(summary).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("읽음 처리")
    class MarkRead {

        @Test
        @DisplayName("내 스레드면 SELLER·marketId로 읽음 처리를 위임한다")
        void delegatesMarkRead() {
            givenAuthenticatedSeller();
            given(messageThreadRepository.findById(THREAD_ID)).willReturn(Optional.of(myThread));

            sellerThreadService.markRead(SELLER_EMAIL, THREAD_ID);

            verify(messageThreadService).markRead(myThread, ParticipantType.SELLER, MARKET_ID);
        }
    }
}

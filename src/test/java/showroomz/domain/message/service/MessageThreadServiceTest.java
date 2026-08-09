package showroomz.domain.message.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.market.entity.Market;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.entity.ThreadParticipant;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.repository.ThreadParticipantRepository;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.AllowedAttachmentExtensions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MessageThreadServiceTest {

    private static final long MY_ID = 7L;
    private static final long THREAD_ID = 1L;

    @Mock
    private MessageThreadRepository messageThreadRepository;
    @Mock
    private ThreadParticipantRepository threadParticipantRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;

    @InjectMocks
    private MessageThreadService messageThreadService;

    private final MessageThread openThread = thread(ThreadStatus.OPEN);

    private static MessageThread thread(ThreadStatus status) {
        return MessageThread.builder().id(THREAD_ID).status(status).build();
    }

    private static MessageAttachment attachment(long id, MessageThread thread, long uploaderId,
                                                 AttachmentStatus status, long sizeBytes) {
        return MessageAttachment.builder()
                .id(id)
                .thread(thread)
                .uploaderType(ParticipantType.SELLER)
                .uploaderId(uploaderId)
                .status(status)
                .attachmentType(AttachmentType.IMAGE)
                .sizeBytes(sizeBytes)
                .build();
    }

    private void givenNoExistingMessage() {
        given(messageRepository.findByThreadAndClientMessageId(any(), any())).willReturn(Optional.empty());
    }

    private void givenMessageSaved() {
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("스레드 활성화")
    class ActivateThread {

        @Test
        @DisplayName("스레드가 없으면 CONNECTION에 묶인 OPEN 스레드를 새로 만든다 (§1-3)")
        void createsNewThreadWhenMissing() {
            Connection connection = Connection.createOperatorMarket(new Market());
            given(messageThreadRepository.findByConnection(connection)).willReturn(Optional.empty());
            given(messageThreadRepository.save(any(MessageThread.class))).willAnswer(inv -> {
                MessageThread saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", THREAD_ID);
                return saved;
            });

            MessageThread result = messageThreadService.activateThread(connection);

            assertThat(result.getId()).isEqualTo(THREAD_ID);
            assertThat(result.getStatus()).isEqualTo(ThreadStatus.OPEN);
            assertThat(result.getConnection()).isSameAs(connection);
        }

        @Test
        @DisplayName("휴면 스레드가 있으면 같은 행을 다시 OPEN으로 연다 — 대화 기록을 보존하기 위해서다")
        void reopensExistingDormantThread() {
            Connection connection = Connection.createOperatorMarket(new Market());
            MessageThread dormant = MessageThread.builder()
                    .id(THREAD_ID).connection(connection).status(ThreadStatus.DORMANT).build();
            given(messageThreadRepository.findByConnection(connection)).willReturn(Optional.of(dormant));

            MessageThread result = messageThreadService.activateThread(connection);

            assertThat(result).isSameAs(dormant);
            assertThat(result.getStatus()).isEqualTo(ThreadStatus.OPEN);
            verify(messageThreadRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("읽음 처리")
    class MarkRead {

        @Test
        @DisplayName("메시지가 없으면 참가자 행을 만들지 않는다")
        void noMessagesSkipsParticipant() {
            given(messageRepository.findTopByThreadOrderByIdDesc(openThread)).willReturn(Optional.empty());

            messageThreadService.markRead(openThread, ParticipantType.SELLER, MY_ID);

            verify(threadParticipantRepository, never()).save(any());
        }

        @Test
        @DisplayName("최신 메시지 id로 읽음 위치를 갱신한다")
        void updatesLastReadMessageId() {
            Message latest = Message.builder().id(42L).thread(openThread)
                    .senderType(ParticipantType.CREATOR).senderId(1L)
                    .clientMessageId("c").content("hi").build();
            given(messageRepository.findTopByThreadOrderByIdDesc(openThread)).willReturn(Optional.of(latest));
            given(threadParticipantRepository.findByThreadAndParticipantTypeAndParticipantId(
                    openThread, ParticipantType.SELLER, MY_ID))
                    .willReturn(Optional.empty());
            given(threadParticipantRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            messageThreadService.markRead(openThread, ParticipantType.SELLER, MY_ID);

            ArgumentCaptor<ThreadParticipant> saved = ArgumentCaptor.forClass(ThreadParticipant.class);
            verify(threadParticipantRepository).save(saved.capture());
            assertThat(saved.getValue().getLastReadMessageId()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("메시지 전송")
    class SendMessage {

        @Test
        @DisplayName("같은 clientMessageId로 재전송하면 새로 저장하지 않고 기존 메시지를 그대로 돌려준다 (§13-10)")
        void resendWithSameClientMessageIdIsIdempotent() {
            Message existing = Message.create(openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "안녕하세요");
            given(messageRepository.findByThreadAndClientMessageId(openThread, "uuid-1"))
                    .willReturn(Optional.of(existing));

            MessageThreadService.SendResult result = messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "안녕하세요", null);

            assertThat(result.created()).isFalse();
            assertThat(result.message()).isSameAs(existing);
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("멱등 재전송에서는 첨부 연결을 다시 시도하지 않는다 — 재시도하면 이미 붙은 첨부 때문에 실패한다 (§4-5)")
        void resendDoesNotRelinkAttachments() {
            Message existing = Message.create(openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문");
            given(messageRepository.findByThreadAndClientMessageId(openThread, "uuid-1"))
                    .willReturn(Optional.of(existing));

            messageThreadService.sendMessage(openThread, ParticipantType.SELLER, MY_ID,
                    "uuid-1", "본문", List.of(10L));

            verifyNoInteractions(messageAttachmentRepository);
        }

        @Test
        @DisplayName("본문과 첨부가 둘 다 비면 거부한다")
        void emptyMessageIsRejected() {
            givenNoExistingMessage();

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "   ", List.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MESSAGE_EMPTY);
        }

        @Test
        @DisplayName("휴면 스레드에는 작성할 수 없다 — 캐시된 threadId 직접 호출에 대한 방어선 (§2)")
        void dormantThreadRejectsWrite() {
            MessageThread dormant = thread(ThreadStatus.DORMANT);
            given(messageRepository.findByThreadAndClientMessageId(any(), any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    dormant, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.THREAD_DORMANT);
        }

        @Test
        @DisplayName("정상 전송이면 저장하고 목록 미리보기를 본문으로 갱신한다")
        void sendsAndUpdatesPreview() {
            givenNoExistingMessage();
            givenMessageSaved();

            MessageThreadService.SendResult result = messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "촬영본 보내드렸습니다", null);

            assertThat(result.created()).isTrue();
            assertThat(openThread.getLastMessagePreview()).isEqualTo("촬영본 보내드렸습니다");
        }

        @Test
        @DisplayName("첨부만 보내면 미리보기를 첨부 종류 문구로 대체한다 (§13-11)")
        void attachmentOnlyMessageUsesFallbackPreview() {
            MessageAttachment first = attachment(10L, openThread, MY_ID, AttachmentStatus.UPLOADED, 100L);
            MessageAttachment second = attachment(11L, openThread, MY_ID, AttachmentStatus.UPLOADED, 100L);
            givenNoExistingMessage();
            givenMessageSaved();
            given(messageAttachmentRepository.findAllByIdIn(List.of(10L, 11L)))
                    .willReturn(List.of(first, second));
            given(messageAttachmentRepository.attachToMessage(
                    anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), anyLong(), any()))
                    .willReturn(1);

            messageThreadService.sendMessage(openThread, ParticipantType.SELLER, MY_ID,
                    "uuid-1", null, List.of(10L, 11L));

            assertThat(openThread.getLastMessagePreview()).isEqualTo("사진 2개");
        }
    }

    @Nested
    @DisplayName("첨부 검증 (§4-5)")
    class AttachmentValidation {

        @Test
        @DisplayName("첨부 개수가 20개를 넘으면 거부한다")
        void tooManyAttachments() {
            givenNoExistingMessage();
            List<Long> ids = IntStream.rangeClosed(1, AllowedAttachmentExtensions.MAX_ATTACHMENT_COUNT + 1)
                    .mapToObj(Long::valueOf)
                    .toList();

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", ids))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_COUNT_EXCEEDED);
        }

        @Test
        @DisplayName("존재하지 않는 첨부 id는 거부한다")
        void unknownAttachment() {
            givenNoExistingMessage();
            given(messageAttachmentRepository.findAllByIdIn(List.of(99L))).willReturn(List.of());

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", List.of(99L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("다른 스레드에 올린 첨부는 붙일 수 없다 — 핵심 방어선 (§4-5 검증 3)")
        void attachmentFromAnotherThreadIsRejected() {
            MessageThread otherThread = MessageThread.builder().id(999L).status(ThreadStatus.OPEN).build();
            givenNoExistingMessage();
            given(messageAttachmentRepository.findAllByIdIn(List.of(10L)))
                    .willReturn(List.of(attachment(10L, otherThread, MY_ID, AttachmentStatus.UPLOADED, 100L)));

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", List.of(10L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("타인이 올린 첨부는 붙일 수 없다")
        void attachmentOwnedByAnotherUserIsRejected() {
            givenNoExistingMessage();
            given(messageAttachmentRepository.findAllByIdIn(List.of(10L)))
                    .willReturn(List.of(attachment(10L, openThread, 999L, AttachmentStatus.UPLOADED, 100L)));

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", List.of(10L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("업로드가 끝나지 않은 첨부는 붙일 수 없다")
        void pendingAttachmentIsRejected() {
            givenNoExistingMessage();
            given(messageAttachmentRepository.findAllByIdIn(List.of(10L)))
                    .willReturn(List.of(attachment(10L, openThread, MY_ID, AttachmentStatus.PENDING, 100L)));

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", List.of(10L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_NOT_UPLOADED);
        }

        @Test
        @DisplayName("이미 다른 메시지에 붙은 첨부는 거부한다 (§4-5 검증 4)")
        void alreadyAttachedIsRejected() {
            MessageAttachment linked = attachment(10L, openThread, MY_ID, AttachmentStatus.UPLOADED, 100L);
            ReflectionTestUtils.setField(
                    linked, "message", Message.create(openThread, ParticipantType.SELLER, MY_ID, "old", "x"));
            givenNoExistingMessage();
            given(messageAttachmentRepository.findAllByIdIn(List.of(10L))).willReturn(List.of(linked));

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", List.of(10L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_ALREADY_ATTACHED);
        }

        @Test
        @DisplayName("첨부 총 용량이 500MB를 넘으면 거부한다")
        void totalSizeExceeded() {
            long half = AllowedAttachmentExtensions.MAX_TOTAL_SIZE_BYTES / 2 + 1;
            givenNoExistingMessage();
            given(messageAttachmentRepository.findAllByIdIn(List.of(10L, 11L))).willReturn(List.of(
                    attachment(10L, openThread, MY_ID, AttachmentStatus.UPLOADED, half),
                    attachment(11L, openThread, MY_ID, AttachmentStatus.UPLOADED, half)));

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", List.of(10L, 11L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_SIZE_EXCEEDED);
        }

        @Test
        @DisplayName("조건부 UPDATE가 0행이면 전체를 실패시킨다 — 검증~연결 사이 경합에 대한 최종 방어선")
        void conditionalUpdateMissRollsBack() {
            givenNoExistingMessage();
            givenMessageSaved();
            given(messageAttachmentRepository.findAllByIdIn(List.of(10L)))
                    .willReturn(List.of(attachment(10L, openThread, MY_ID, AttachmentStatus.UPLOADED, 100L)));
            given(messageAttachmentRepository.attachToMessage(
                    anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), anyLong(), any()))
                    .willReturn(0);

            assertThatThrownBy(() -> messageThreadService.sendMessage(
                    openThread, ParticipantType.SELLER, MY_ID, "uuid-1", "본문", List.of(10L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_ALREADY_ATTACHED);
        }
    }

    @Nested
    @DisplayName("안 읽은 수 집계")
    class UnreadCount {

        @Test
        @DisplayName("스레드가 없으면 쿼리 자체를 돌지 않는다 — IN () 은 유효한 SQL이 아니다")
        void emptyThreadListSkipsQuery() {
            Map<Long, Long> result = messageThreadService.countUnreadByThreadIds(
                    List.of(), ParticipantType.SELLER, MY_ID);

            assertThat(result).isEmpty();
            verify(messageRepository, never()).countUnreadByThreadIds(anyList(), any(), anyLong());
        }

        @Test
        @DisplayName("여러 스레드를 한 번의 쿼리로 집계한다 — 배지는 폴링 대상이라 스레드당 쿼리를 돌면 안 된다")
        void aggregatesInSingleQuery() {
            given(messageRepository.countUnreadByThreadIds(
                    List.of(1L, 2L), ParticipantType.SELLER, MY_ID))
                    .willReturn(List.of(new Object[]{1L, 3L}, new Object[]{2L, 0L}));

            Map<Long, Long> result = messageThreadService.countUnreadByThreadIds(
                    List.of(1L, 2L), ParticipantType.SELLER, MY_ID);

            assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 3L, 2L, 0L));
            verify(messageRepository).countUnreadByThreadIds(anyList(), eq(ParticipantType.SELLER), eq(MY_ID));
        }

        @Test
        @DisplayName("합계도 같은 한 번의 쿼리를 쓴다")
        void sumUsesSameSingleQuery() {
            given(messageRepository.countUnreadByThreadIds(
                    List.of(1L, 2L), ParticipantType.CREATOR, MY_ID))
                    .willReturn(List.of(new Object[]{1L, 3L}, new Object[]{2L, 4L}));

            long total = messageThreadService.sumUnread(List.of(1L, 2L), ParticipantType.CREATOR, MY_ID);

            assertThat(total).isEqualTo(7L);
        }
    }
}

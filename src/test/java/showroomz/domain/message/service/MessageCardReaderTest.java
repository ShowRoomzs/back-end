package showroomz.domain.message.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.service.MessageCardReader.CardView;
import showroomz.domain.message.type.MessageCardActionState;
import showroomz.domain.message.type.MessageCardTone;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageRefType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 시스템 카드 읽기(36 설계 4절) — 박힌 사실은 스냅샷에서, 액션 상태 · 톤은 참조 객체(재발송 요청 · 계약 상태)에서 온다.
 */
@ExtendWith(MockitoExtension.class)
class MessageCardReaderTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 8, 14, 8, 50);
    private static final LocalDateTime NOTIFIED_AT = LocalDateTime.of(2026, 8, 14, 8, 55);

    @Mock
    private ContractResendRequestRepository resendRequestRepository;

    @InjectMocks
    private MessageCardReader reader;

    private final MessageThread thread = MessageThread.builder().id(1L).status(ThreadStatus.OPEN).build();

    private Message resendCard(long messageId, long requestId) {
        return Message.builder().id(messageId).thread(thread).senderType(ParticipantType.CREATOR).senderId(5L)
                .clientMessageId("resend-card-" + requestId)
                .messageType(showroomz.domain.message.type.MessageType.SYSTEM)
                .cardType(MessageCardType.CONTRACT_RESEND_REQUEST)
                .refType(MessageRefType.CONTRACT_RESEND_REQUEST).refId(requestId)
                .cardPayload(MessageCardPayload.resendRequest(41L, "CTR-20260813-041", "겨울 리페어 크림 공구",
                        "CREATOR", "뷰티_소연", REQUESTED_AT).toJson())
                .content(MessageCardType.CONTRACT_RESEND_REQUEST.getTitle())
                .build();
    }

    private Message cancelCard(long messageId) {
        return Message.builder().id(messageId).thread(thread).senderType(ParticipantType.ADMIN).senderId(3L)
                .clientMessageId("contract-cancel-41")
                .messageType(showroomz.domain.message.type.MessageType.SYSTEM)
                .cardType(MessageCardType.CONTRACT_ADMIN_CANCELED)
                .refType(MessageRefType.CONTRACT).refId(41L)
                .cardPayload(MessageCardPayload.adminCanceled(41L, "CTR-20260813-034", "여름 수분 세럼 공구",
                        "공구 일정 변경 — 조건 재협의", NOTIFIED_AT, 3L).toJson())
                .content(MessageCardType.CONTRACT_ADMIN_CANCELED.getTitle())
                .build();
    }

    private Message bubble(long messageId) {
        return Message.create(thread, ParticipantType.SELLER, 5L, "uuid-" + messageId, "안녕하세요");
    }

    private ContractResendRequest request(long id, ContractStatus contractStatus, Long handledBy, Long noticeId) {
        Contract contract = Contract.builder().id(41L).status(contractStatus).build();
        return ContractResendRequest.builder().id(id).contract(contract).requesterType(ContractActorType.CREATOR)
                .requesterId(5L).requestedAt(REQUESTED_AT)
                .handledAt(handledBy == null ? null : NOTIFIED_AT).handledBy(handledBy).noticeMessageId(noticeId)
                .build();
    }

    @Test
    @DisplayName("말풍선만 있는 페이지는 카드가 없고 참조 객체를 읽지 않는다")
    void bubblesOnlyNeedNoLookup() {
        assertThat(reader.read(List.of(bubble(1L), bubble(2L)))).isEmpty();
        verify(resendRequestRepository, never()).findWithContractByIdIn(any());
    }

    @Test
    @DisplayName("알림 전 · 서명 진행중이면 PENDING · 경고 톤이고, 스냅샷의 사실이 그대로 나온다")
    void pendingWhileSigning() {
        given(resendRequestRepository.findWithContractByIdIn(any())).willReturn(
                List.of(request(9L, ContractStatus.SIGNING, null, null)));

        CardView view = reader.read(List.of(resendCard(100L, 9L))).get(100L);

        assertThat(view.actionState()).isEqualTo(MessageCardActionState.PENDING);
        assertThat(view.tone()).isEqualTo(MessageCardTone.WARNING);
        assertThat(view.title()).isEqualTo("요청 · 서명 안내 다시 받기");
        assertThat(view.contractId()).isEqualTo(41L);
        assertThat(view.contractNumber()).isEqualTo("CTR-20260813-041");
        assertThat(view.groupBuyTitle()).isEqualTo("겨울 리페어 크림 공구");
        assertThat(view.requesterType()).isEqualTo("CREATOR");
        assertThat(view.requesterName()).isEqualTo("뷰티_소연");
        assertThat(view.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(view.doneAt()).isNull();
        assertThat(view.doneBy()).isNull();
    }

    @Test
    @DisplayName("알림을 보냈으면 DONE · 중립 톤이고 처리 운영자 id · 시각 · 안내 메시지 id가 나온다 — 계약이 그 뒤 끝났어도 DONE이다")
    void doneAfterNotice() {
        given(resendRequestRepository.findWithContractByIdIn(any())).willReturn(
                List.of(request(9L, ContractStatus.CONCLUSION_PENDING, 3L, 777L)));

        CardView view = reader.read(List.of(resendCard(100L, 9L))).get(100L);

        assertThat(view.actionState()).isEqualTo(MessageCardActionState.DONE);
        assertThat(view.tone()).isEqualTo(MessageCardTone.NEUTRAL);
        assertThat(view.doneAt()).isEqualTo(NOTIFIED_AT);
        assertThat(view.doneBy()).isEqualTo(3L);
        assertThat(view.noticeMessageId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("알림 전에 계약이 서명 단계를 벗어나면 CLOSED · 중립 톤이다 — 버튼이 남으면 거짓 안내가 나간다")
    void closedWhenContractLeftSigning() {
        for (ContractStatus status : List.of(ContractStatus.CONCLUSION_PENDING, ContractStatus.CANCELED,
                ContractStatus.EXPIRED, ContractStatus.DECLINED, ContractStatus.CONCLUDED)) {
            given(resendRequestRepository.findWithContractByIdIn(any())).willReturn(
                    List.of(request(9L, status, null, null)));

            CardView view = reader.read(List.of(resendCard(100L, 9L))).get(100L);

            assertThat(view.actionState()).as(status.name()).isEqualTo(MessageCardActionState.CLOSED);
            assertThat(view.tone()).as(status.name()).isEqualTo(MessageCardTone.NEUTRAL);
        }
    }

    @Test
    @DisplayName("요청 행이 사라진 카드도 CLOSED다 — 카드 한 장 때문에 대화 조회가 실패하지 않는다")
    void closedWhenRequestMissing() {
        given(resendRequestRepository.findWithContractByIdIn(any())).willReturn(List.of());

        CardView view = reader.read(List.of(resendCard(100L, 9L))).get(100L);

        assertThat(view.actionState()).isEqualTo(MessageCardActionState.CLOSED);
        assertThat(view.contractNumber()).isEqualTo("CTR-20260813-041");
    }

    @Test
    @DisplayName("직권 취소 결과 카드는 액션이 없고 항상 중립 톤이다 — 처리자는 운영자 id로만 나온다")
    void cancelResultCardHasNoAction() {
        CardView view = reader.read(List.of(cancelCard(200L))).get(200L);

        assertThat(view.cardType()).isEqualTo(MessageCardType.CONTRACT_ADMIN_CANCELED);
        assertThat(view.title()).isEqualTo("계약 직권 취소 처리됨");
        assertThat(view.tone()).isEqualTo(MessageCardTone.NEUTRAL);
        assertThat(view.actionState()).isNull();
        assertThat(view.reasonLabel()).isEqualTo("공구 일정 변경 — 조건 재협의");
        assertThat(view.processedAt()).isEqualTo(NOTIFIED_AT);
        assertThat(view.processedBy()).isEqualTo(3L);
        verify(resendRequestRepository, never()).findWithContractByIdIn(any());
    }

    @Test
    @DisplayName("한 페이지의 재발송 요청은 한 번에 읽는다 — 카드마다 조회하지 않는다")
    void readsRequestsOfPageAtOnce() {
        given(resendRequestRepository.findWithContractByIdIn(any())).willReturn(List.of(
                request(9L, ContractStatus.SIGNING, null, null),
                request(10L, ContractStatus.SIGNING, 3L, 778L)));

        Map<Long, CardView> views = reader.read(List.of(
                resendCard(100L, 9L), bubble(101L), resendCard(102L, 10L), cancelCard(103L)));

        assertThat(views).containsOnlyKeys(100L, 102L, 103L);
        assertThat(views.get(100L).actionState()).isEqualTo(MessageCardActionState.PENDING);
        assertThat(views.get(102L).actionState()).isEqualTo(MessageCardActionState.DONE);
        verify(resendRequestRepository).findWithContractByIdIn(argThat((Collection<Long> ids) ->
                ids.size() == 2 && ids.containsAll(List.of(9L, 10L))));
    }
}

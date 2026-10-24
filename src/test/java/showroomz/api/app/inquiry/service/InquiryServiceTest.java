package showroomz.api.app.inquiry.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.inquiry.dto.InquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.InquiryUpdateRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 1:1 문의에 연결하는 주문은 <b>본인 주문</b>이어야 한다(선행 수정 계획서 3-9).
 *
 * <p>문의 카드는 연결된 주문의 상품명·썸네일·주문번호를 그린다. 남의 주문 ID를 받으면 그 주문 정보가 내 화면에 나온다.
 * 없는 주문도 같은 코드로 막는다 — 존재 여부를 흘리지 않기 위해서다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MY_ORDER_ID = 100L;
    private static final Long OTHERS_ORDER_ID = 200L;

    @Mock
    private OneToOneInquiryRepository inquiryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserSanctionRepository sanctionRepository;

    @InjectMocks
    private InquiryService inquiryService;

    private Users user;

    @BeforeEach
    void setUp() {
        user = new Users();
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Nested
    @DisplayName("문의 등록")
    class Register {

        @BeforeEach
        void givenUser() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        }

        @Test
        @DisplayName("본인 주문을 연결하면 등록된다")
        void ownOrder() {
            given(orderRepository.existsByIdAndUser_Id(MY_ORDER_ID, USER_ID)).willReturn(true);

            inquiryService.registerInquiry(USER_ID, registerRequest(MY_ORDER_ID));

            ArgumentCaptor<OneToOneInquiry> saved = ArgumentCaptor.forClass(OneToOneInquiry.class);
            verify(inquiryRepository).save(saved.capture());
            assertThat(saved.getValue().getOrderId()).isEqualTo(MY_ORDER_ID);
        }

        @Test
        @DisplayName("남의 주문이나 없는 주문을 연결하면 ORDER_ACCESS_DENIED로 막고 저장하지 않는다")
        void othersOrder() {
            given(orderRepository.existsByIdAndUser_Id(OTHERS_ORDER_ID, USER_ID)).willReturn(false);

            assertThatThrownBy(() -> inquiryService.registerInquiry(USER_ID, registerRequest(OTHERS_ORDER_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
            verify(inquiryRepository, never()).save(any());
        }

        @Test
        @DisplayName("주문 없이 등록하면 주문을 조회하지 않는다")
        void noOrder() {
            inquiryService.registerInquiry(USER_ID, registerRequest(null));

            verify(orderRepository, never()).existsByIdAndUser_Id(anyLong(), anyLong());
            verify(inquiryRepository).save(any(OneToOneInquiry.class));
        }
    }

    @Nested
    @DisplayName("문의 수정")
    class Update {

        private static final Long INQUIRY_ID = 10L;

        private OneToOneInquiry inquiry;

        @BeforeEach
        void givenInquiry() {
            inquiry = OneToOneInquiry.builder()
                    .user(user)
                    .type(CsCategory.DELIVERY)
                    .content("배송이 언제 오나요?")
                    .orderId(MY_ORDER_ID)
                    .build();
            given(inquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));
        }

        @Test
        @DisplayName("남의 주문으로 바꾸려 하면 ORDER_ACCESS_DENIED로 막고 기존 연결을 유지한다")
        void othersOrder() {
            given(orderRepository.existsByIdAndUser_Id(OTHERS_ORDER_ID, USER_ID)).willReturn(false);

            assertThatThrownBy(() -> inquiryService.updateInquiry(USER_ID, INQUIRY_ID, updateRequest(OTHERS_ORDER_ID)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
            assertThat(inquiry.getOrderId()).isEqualTo(MY_ORDER_ID);
        }

        @Test
        @DisplayName("주문 연결을 빼는 수정은 주문을 조회하지 않는다")
        void removeOrder() {
            inquiryService.updateInquiry(USER_ID, INQUIRY_ID, updateRequest(null));

            verify(orderRepository, never()).existsByIdAndUser_Id(anyLong(), anyLong());
            assertThat(inquiry.getOrderId()).isNull();
        }
    }

    /**
     * 「계정 이용 정지」 소명(C20 1d · 51-6 설계서 5절) — 정지 중에만 · 유형 계정 · 주문 · 클레임 없이 · 정지당 답변 전 1건. 문의에 정지 행이
     * 붙어야 어드민 해제 모달이 「소명 문의 번호」를 찾는다.
     */
    @Nested
    @DisplayName("정지 소명 문의")
    class SanctionAppeal {

        private static final Long SANCTION_ID = 3L;

        private UserSanction open;

        @BeforeEach
        void givenUser() {
            // 수정 경로는 회원을 다시 읽지 않는다
            lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            open = UserSanction.suspend(USER_ID, UserSuspensionReason.ABUSIVE_LANGUAGE, null, 7L, "김운영",
                    LocalDateTime.of(2026, 10, 10, 14, 20));
            ReflectionTestUtils.setField(open, "id", SANCTION_ID);
        }

        private void givenSuspended() {
            user.setStatus(UserStatus.SUSPENDED);
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.of(open));
        }

        @Test
        @DisplayName("정지 중이면 유형 계정 · 주문 없이 정지 행을 붙여 등록한다")
        void registersWithSanction() {
            givenSuspended();
            given(inquiryRepository.findFirstBySanctionIdAndStatusOrderByIdDesc(SANCTION_ID, InquiryStatus.WAITING))
                    .willReturn(Optional.empty());
            given(inquiryRepository.save(any(OneToOneInquiry.class))).willAnswer(returnsFirstArg());

            inquiryService.registerInquiry(USER_ID, appealRequest(CsCategory.ACCOUNT, null, null));

            ArgumentCaptor<OneToOneInquiry> saved = ArgumentCaptor.forClass(OneToOneInquiry.class);
            verify(inquiryRepository).save(saved.capture());
            assertThat(saved.getValue().getSanctionId()).isEqualTo(SANCTION_ID);
            assertThat(saved.getValue().getType()).isEqualTo(CsCategory.ACCOUNT);
            assertThat(saved.getValue().getOrderId()).isNull();
            assertThat(saved.getValue().isSanctionAppeal()).isTrue();
            assertThat(saved.getValue().typeName()).isEqualTo(OneToOneInquiry.SANCTION_APPEAL_TYPE_NAME);
            verify(orderRepository, never()).existsByIdAndUser_Id(anyLong(), anyLong());
        }

        @Test
        @DisplayName("정지 중이 아니면 409 — 이 유형은 정지 상태에서만 목록에 나타난다")
        void notSuspended() {
            user.setStatus(UserStatus.NORMAL);

            assertThatThrownBy(() -> inquiryService.registerInquiry(USER_ID, appealRequest(CsCategory.ACCOUNT, null, null)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SANCTION_APPEAL_NOT_AVAILABLE);
            verify(inquiryRepository, never()).save(any());
            verify(sanctionRepository, never()).findByUserIdAndReleasedAtIsNull(anyLong());
        }

        @Test
        @DisplayName("정지 행 없이 정지된 옛 회원도 409 — 붙일 정지가 없다")
        void legacySuspensionHasNoSanctionRow() {
            user.setStatus(UserStatus.SUSPENDED);
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> inquiryService.registerInquiry(USER_ID, appealRequest(CsCategory.ACCOUNT, null, null)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SANCTION_APPEAL_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("유형이 계정이 아니거나 주문 · 클레임을 붙이면 400 — 관련 주문 칸이 없는 유형이다")
        void wrongShape() {
            givenSuspended();

            assertThatThrownBy(() -> inquiryService.registerInquiry(USER_ID, appealRequest(CsCategory.DELIVERY, null, null)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> inquiryService.registerInquiry(USER_ID, appealRequest(CsCategory.ACCOUNT, MY_ORDER_ID, null)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> inquiryService.registerInquiry(USER_ID, appealRequest(CsCategory.ACCOUNT, null, 3008L)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            verify(inquiryRepository, never()).save(any());
        }

        @Test
        @DisplayName("답변 전 소명 문의가 이미 있으면 409 — 반려 이의와 같은 1건 규칙")
        void onlyOneWaitingAppeal() {
            givenSuspended();
            OneToOneInquiry waiting = OneToOneInquiry.builder().user(user).type(CsCategory.ACCOUNT).content("이의")
                    .sanctionId(SANCTION_ID).build();
            given(inquiryRepository.findFirstBySanctionIdAndStatusOrderByIdDesc(SANCTION_ID, InquiryStatus.WAITING))
                    .willReturn(Optional.of(waiting));

            assertThatThrownBy(() -> inquiryService.registerInquiry(USER_ID, appealRequest(CsCategory.ACCOUNT, null, null)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SANCTION_APPEAL_ALREADY_EXISTS);
            verify(inquiryRepository, never()).save(any());
        }

        @Test
        @DisplayName("소명 문의는 내용 · 사진만 고친다 — 유형을 바꾸거나 주문을 붙이면 400")
        void updateKeepsShape() {
            OneToOneInquiry appeal = OneToOneInquiry.builder().user(user).type(CsCategory.ACCOUNT).content("이의")
                    .sanctionId(SANCTION_ID).build();
            given(inquiryRepository.findById(10L)).willReturn(Optional.of(appeal));

            assertThatThrownBy(() -> inquiryService.updateInquiry(USER_ID, 10L, update(CsCategory.SERVICE, null)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> inquiryService.updateInquiry(USER_ID, 10L, update(CsCategory.ACCOUNT, MY_ORDER_ID)))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            inquiryService.updateInquiry(USER_ID, 10L, update(CsCategory.ACCOUNT, null));

            assertThat(appeal.getContent()).isEqualTo("배송 문의를 고칩니다.");
            assertThat(appeal.getSanctionId()).isEqualTo(SANCTION_ID);
        }

        private InquiryRegisterRequest appealRequest(CsCategory type, Long orderId, Long claimId) {
            InquiryRegisterRequest request = new InquiryRegisterRequest();
            ReflectionTestUtils.setField(request, "type", type);
            ReflectionTestUtils.setField(request, "content", "정지 사유를 확인하고 싶어요");
            ReflectionTestUtils.setField(request, "orderId", orderId);
            ReflectionTestUtils.setField(request, "claimId", claimId);
            ReflectionTestUtils.setField(request, "sanctionAppeal", true);
            return request;
        }

        private InquiryUpdateRequest update(CsCategory type, Long orderId) {
            InquiryUpdateRequest request = updateRequest(orderId);
            ReflectionTestUtils.setField(request, "type", type);
            return request;
        }
    }

    private static InquiryRegisterRequest registerRequest(Long orderId) {
        InquiryRegisterRequest request = new InquiryRegisterRequest();
        ReflectionTestUtils.setField(request, "type", CsCategory.DELIVERY);
        ReflectionTestUtils.setField(request, "content", "배송이 언제 오나요?");
        ReflectionTestUtils.setField(request, "orderId", orderId);
        return request;
    }

    private static InquiryUpdateRequest updateRequest(Long orderId) {
        InquiryUpdateRequest request = new InquiryUpdateRequest();
        ReflectionTestUtils.setField(request, "type", CsCategory.DELIVERY);
        ReflectionTestUtils.setField(request, "content", "배송 문의를 고칩니다.");
        ReflectionTestUtils.setField(request, "orderId", orderId);
        return request;
    }
}

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
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
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

package showroomz.api.admin.productinquiry.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDeleteDecision;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDto;
import showroomz.api.admin.productinquiry.repository.AdminProductInquiryQueryRepository;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.repository.ProductInquiryHistoryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryAdminDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;
import showroomz.domain.inquiry.type.ProductInquiryRejectReason;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 상품 문의 모니터링 (§18) — 운영자는 답변하지 않는다. 하는 일은 삭제 집행과 삭제 요청 반려뿐이다.
 *
 * <p>이 두 동작이 건드리는 축이 다른 것이 핵심이다. 삭제 집행은 노출 축을 DELETED로 끝내고,
 * 반려는 노출 축만 NORMAL로 되돌린다 — <b>답변 축은 손대지 않으므로</b> 반려하면 요청 직전 상태
 * (답변대기든 답변완료든)로 정확히 복귀한다. 축이 섞이면 반려가 답변을 지우는 회귀가 생긴다.
 */
@ExtendWith(MockitoExtension.class)
class AdminProductInquiryServiceTest {

    private static final long INQUIRY_ID = 55L;
    private static final long OPERATOR_ID = 3L;

    @Mock
    private ProductInquiryRepository productInquiryRepository;
    @Mock
    private ProductInquiryHistoryRepository productInquiryHistoryRepository;
    @Mock
    private AdminProductInquiryQueryRepository adminProductInquiryQueryRepository;
    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private AdminProductInquiryService adminProductInquiryService;

    private Users user() {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users("mia", "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    private Product product() {
        Market market = new Market();
        market.setMarketName("소연 뷰티");

        Product product = new Product();
        product.setProductId(100L);
        product.setName("수분 진정 토너");
        product.setMarket(market);
        return product;
    }

    private ProductInquiry inquiry() {
        ProductInquiry inquiry = ProductInquiry.builder()
                .user(user())
                .product(product())
                .type(ProductInquiryType.RESTOCK)
                .content("재입고 언제 되나요?")
                .secret(false)
                .imageUrls(List.of())
                .build();
        ReflectionTestUtils.setField(inquiry, "id", INQUIRY_ID);
        return inquiry;
    }

    private void givenInquiry(ProductInquiry inquiry) {
        given(productInquiryRepository.findByIdWithUserAndProduct(INQUIRY_ID)).willReturn(Optional.of(inquiry));
    }

    private AdminProductInquiryDeleteDecision.ExecuteRequest executeRequest(
            ProductInquiryAdminDeleteReason reason, String detail) {
        AdminProductInquiryDeleteDecision.ExecuteRequest request =
                new AdminProductInquiryDeleteDecision.ExecuteRequest();
        ReflectionTestUtils.setField(request, "reason", reason);
        ReflectionTestUtils.setField(request, "detail", detail);
        return request;
    }

    private AdminProductInquiryDeleteDecision.RejectRequest rejectRequest(
            ProductInquiryRejectReason reason, String detail) {
        AdminProductInquiryDeleteDecision.RejectRequest request =
                new AdminProductInquiryDeleteDecision.RejectRequest();
        ReflectionTestUtils.setField(request, "reason", reason);
        ReflectionTestUtils.setField(request, "detail", detail);
        return request;
    }

    @Nested
    @DisplayName("삭제 집행 (§18-5)")
    class ExecuteDelete {

        /** 삭제 요청이 없어도 집행할 수 있다 — 운영자가 순찰 중 발견한 건이 이 경로로 들어온다. */
        @Test
        @DisplayName("삭제 요청이 없어도 답변대기 건을 바로 집행할 수 있다")
        void canDeleteWithoutBrandRequest() {
            ProductInquiry inquiry = inquiry();
            givenInquiry(inquiry);

            adminProductInquiryService.executeDelete(INQUIRY_ID, OPERATOR_ID,
                    executeRequest(ProductInquiryAdminDeleteReason.ADVERTISEMENT, null));

            assertThat(inquiry.isDeleted()).isTrue();
            assertThat(inquiry.getExposureStatus()).isEqualTo(InquiryExposureStatus.DELETED);
            assertThat(inquiry.getDeleteReasonType()).isEqualTo(ProductInquiryAdminDeleteReason.ADVERTISEMENT);
            assertThat(inquiry.getDeleteProcessedBy()).isEqualTo(OPERATOR_ID);
            assertThat(inquiry.getDeletedAt()).isNotNull();
        }

        /** 답변 축은 건드리지 않는다 — 삭제는 노출을 내리는 일이고 답변 사실을 지우는 일이 아니다. */
        @Test
        @DisplayName("답변완료 건을 집행해도 답변 축은 그대로 남는다")
        void answeredAxisSurvivesDeletion() {
            ProductInquiry inquiry = inquiry();
            inquiry.registerAnswer("다음 주 입고 예정입니다.");
            givenInquiry(inquiry);

            adminProductInquiryService.executeDelete(INQUIRY_ID, OPERATOR_ID,
                    executeRequest(ProductInquiryAdminDeleteReason.ABUSE, null));

            assertThat(inquiry.isDeleted()).isTrue();
            assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
            assertThat(inquiry.getAnswerContent()).isEqualTo("다음 주 입고 예정입니다.");
        }

        @Test
        @DisplayName("집행하면 처리 이력에 사유가 함께 남는다")
        void deletionIsLoggedWithReason() {
            givenInquiry(inquiry());

            adminProductInquiryService.executeDelete(INQUIRY_ID, OPERATOR_ID,
                    executeRequest(ProductInquiryAdminDeleteReason.PRIVACY_EXPOSURE, null));

            ArgumentCaptor<ProductInquiryHistory> captor = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getHistoryType()).isEqualTo(ProductInquiryHistoryType.DELETE_EXECUTED);
            assertThat(captor.getValue().getDetail())
                    .isEqualTo(ProductInquiryAdminDeleteReason.PRIVACY_EXPOSURE.getDescription());
            assertThat(captor.getValue().getActorId()).isEqualTo(OPERATOR_ID);
        }

        @Test
        @DisplayName("기타 사유는 상세 설명이 없으면 거절한다 — 내부 기록이 사유 없이 남으면 안 된다")
        void etcReasonRequiresDetail() {
            ProductInquiry inquiry = inquiry();
            givenInquiry(inquiry);

            assertThatThrownBy(() -> adminProductInquiryService.executeDelete(INQUIRY_ID, OPERATOR_ID,
                    executeRequest(ProductInquiryAdminDeleteReason.ETC, "   ")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_DELETE_REASON_DETAIL_REQUIRED);

            assertThat(inquiry.isDeleted()).isFalse();
            verify(productInquiryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("기타 사유에 상세 설명이 있으면 집행되고 공백은 다듬어 저장한다")
        void etcReasonWithDetailIsAccepted() {
            ProductInquiry inquiry = inquiry();
            givenInquiry(inquiry);

            adminProductInquiryService.executeDelete(INQUIRY_ID, OPERATOR_ID,
                    executeRequest(ProductInquiryAdminDeleteReason.ETC, "  경쟁사 유도 링크  "));

            assertThat(inquiry.isDeleted()).isTrue();
            assertThat(inquiry.getDeleteReasonDetail()).isEqualTo("경쟁사 유도 링크");
        }

        @Test
        @DisplayName("이미 집행된 건은 다시 집행하지 않는다 — 처리 시각이 덮어써지면 안 된다")
        void alreadyDeletedIsRejected() {
            ProductInquiry inquiry = inquiry();
            inquiry.executeDelete(ProductInquiryAdminDeleteReason.ABUSE, null, OPERATOR_ID);
            givenInquiry(inquiry);

            assertThatThrownBy(() -> adminProductInquiryService.executeDelete(INQUIRY_ID, OPERATOR_ID,
                    executeRequest(ProductInquiryAdminDeleteReason.ADVERTISEMENT, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_DELETED);
        }

        @Test
        @DisplayName("없는 문의면 404를 낸다")
        void unknownInquiryIsRejected() {
            given(productInquiryRepository.findByIdWithUserAndProduct(INQUIRY_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminProductInquiryService.executeDelete(INQUIRY_ID, OPERATOR_ID,
                    executeRequest(ProductInquiryAdminDeleteReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }
    }

    @Nested
    @DisplayName("삭제 요청 반려 (§18-6)")
    class RejectDeleteRequest {

        /**
         * 반려의 계약은 "요청 직전 상태로 정확히 복귀"다. 답변완료 건이 반려 후 답변대기로 돌아가면
         * 브랜드가 이미 답한 문의에 다시 답하라는 알림을 받는다.
         */
        @Test
        @DisplayName("반려하면 노출만 정상으로 돌아가고 답변완료 상태는 유지된다")
        void rejectRestoresExposureAndKeepsAnswer() {
            ProductInquiry inquiry = inquiry();
            inquiry.registerAnswer("다음 주 입고 예정입니다.");
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);
            givenInquiry(inquiry);

            adminProductInquiryService.rejectDeleteRequest(INQUIRY_ID, OPERATOR_ID,
                    rejectRequest(ProductInquiryRejectReason.NORMAL_INQUIRY, null));

            assertThat(inquiry.getExposureStatus()).isEqualTo(InquiryExposureStatus.NORMAL);
            assertThat(inquiry.isDeleteRequested()).isFalse();
            assertThat(inquiry.isDeleted()).isFalse();
            assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
            assertThat(inquiry.getAnswerContent()).isEqualTo("다음 주 입고 예정입니다.");
        }

        @Test
        @DisplayName("답변대기 건을 반려하면 답변대기로 돌아간다")
        void rejectKeepsWaitingStatus() {
            ProductInquiry inquiry = inquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ABUSE, null);
            givenInquiry(inquiry);

            adminProductInquiryService.rejectDeleteRequest(INQUIRY_ID, OPERATOR_ID,
                    rejectRequest(ProductInquiryRejectReason.NOT_QUALIFYING, null));

            assertThat(inquiry.getExposureStatus()).isEqualTo(InquiryExposureStatus.NORMAL);
            assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.WAITING);
        }

        /** 반려 사유는 요청 브랜드에게 전달되므로 판단 근거가 문의에 남아 있어야 한다. */
        @Test
        @DisplayName("반려 사유와 처리자가 문의에 기록되고 이력에도 남는다")
        void rejectionIsRecordedForRequestingBrand() {
            ProductInquiry inquiry = inquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);
            givenInquiry(inquiry);

            adminProductInquiryService.rejectDeleteRequest(INQUIRY_ID, OPERATOR_ID,
                    rejectRequest(ProductInquiryRejectReason.INSUFFICIENT_EVIDENCE, null));

            assertThat(inquiry.getDeleteRejectReasonType())
                    .isEqualTo(ProductInquiryRejectReason.INSUFFICIENT_EVIDENCE);
            assertThat(inquiry.getDeleteProcessedBy()).isEqualTo(OPERATOR_ID);
            assertThat(inquiry.getDeleteReviewedAt()).isNotNull();

            ArgumentCaptor<ProductInquiryHistory> captor = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getHistoryType()).isEqualTo(ProductInquiryHistoryType.DELETE_REJECTED);
            assertThat(captor.getValue().getDetail())
                    .isEqualTo(ProductInquiryRejectReason.INSUFFICIENT_EVIDENCE.getDescription());
        }

        /** 요청이 없으면 화면에 버튼 자체가 없다 — API를 직접 찔러도 같은 판단이어야 한다. */
        @Test
        @DisplayName("삭제 요청이 없는 건은 반려할 수 없다")
        void rejectWithoutRequestIsRejected() {
            ProductInquiry inquiry = inquiry();
            givenInquiry(inquiry);

            assertThatThrownBy(() -> adminProductInquiryService.rejectDeleteRequest(INQUIRY_ID, OPERATOR_ID,
                    rejectRequest(ProductInquiryRejectReason.NOT_QUALIFYING, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_DELETE_NOT_REQUESTED);

            verify(productInquiryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 집행된 건도 반려 대상이 아니다")
        void rejectAfterDeletionIsRejected() {
            ProductInquiry inquiry = inquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ABUSE, null);
            inquiry.executeDelete(ProductInquiryAdminDeleteReason.ABUSE, null, OPERATOR_ID);
            givenInquiry(inquiry);

            assertThatThrownBy(() -> adminProductInquiryService.rejectDeleteRequest(INQUIRY_ID, OPERATOR_ID,
                    rejectRequest(ProductInquiryRejectReason.NOT_QUALIFYING, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_DELETE_NOT_REQUESTED);
        }

        @Test
        @DisplayName("기타 사유는 상세 설명이 없으면 거절한다 — 브랜드에게 빈 사유가 전달되면 안 된다")
        void etcReasonRequiresDetail() {
            ProductInquiry inquiry = inquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);
            givenInquiry(inquiry);

            assertThatThrownBy(() -> adminProductInquiryService.rejectDeleteRequest(INQUIRY_ID, OPERATOR_ID,
                    rejectRequest(ProductInquiryRejectReason.ETC, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_DELETE_REASON_DETAIL_REQUIRED);

            assertThat(inquiry.isDeleteRequested()).isTrue();
        }

        /**
         * 반려 후 브랜드가 다시 요청할 수 있다 — 이때 이전 반려 흔적이 남아 있으면
         * 상세 화면이 "검토 중"과 "반려됨"을 동시에 표시한다.
         */
        @Test
        @DisplayName("반려된 건에 브랜드가 다시 요청하면 이전 반려 흔적은 지워진다")
        void reRequestClearsPreviousRejection() {
            ProductInquiry inquiry = inquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);
            givenInquiry(inquiry);
            adminProductInquiryService.rejectDeleteRequest(INQUIRY_ID, OPERATOR_ID,
                    rejectRequest(ProductInquiryRejectReason.NOT_QUALIFYING, "정상 문의로 판단"));

            inquiry.requestDelete(ProductInquiryDeleteReason.BRAND_COMPARISON, null);

            assertThat(inquiry.isDeleteRequested()).isTrue();
            assertThat(inquiry.getDeleteRejectReasonType()).isNull();
            assertThat(inquiry.getDeleteRejectReasonDetail()).isNull();
            assertThat(inquiry.getDeleteReviewedAt()).isNull();
        }
    }

    @Test
    @DisplayName("유형 필터 옵션은 정의된 문의 유형을 모두 라벨과 함께 내려준다")
    void typeOptionsCoverEveryType() {
        List<AdminProductInquiryDto.TypeOption> options = adminProductInquiryService.getTypeOptions();

        assertThat(options).hasSize(ProductInquiryType.values().length);
        assertThat(options).allSatisfy(option -> {
            assertThat(option.getCode()).isNotNull();
            assertThat(option.getLabel()).isNotBlank();
        });
    }
}

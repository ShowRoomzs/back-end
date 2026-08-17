package showroomz.api.seller.inquiry.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.inquiry.dto.SellerInquiryDeleteRequest;
import showroomz.api.seller.inquiry.repository.SellerInquiryQueryRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.repository.ProductInquiryHistoryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryAdminDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
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
 * 파트너센터 문의 관리 (§23-4~5) — 파트너센터는 <b>답변의 유일한 작성 창구</b>다.
 *
 * <p>브랜드가 할 수 있는 것은 답변 등록·수정과 삭제 <b>요청</b>까지다. 집행과 반려는 운영자 몫이고
 * (§23-6), 그 경계가 무너지면 브랜드가 불리한 문의를 스스로 지울 수 있게 된다 — 이 테스트의 중심이다.
 *
 * <p>세 가지 잠금을 확인한다: 답변은 1회만, 삭제 요청은 재요청 불가(취소도 불가),
 * 운영자 검토 중인 건은 브랜드도 손댈 수 없다. 그리고 <b>내 마켓 문의만</b> 열린다 —
 * 다른 브랜드의 문의가 열리면 경쟁사 문의를 읽고 지울 수 있다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerInquiryServiceTest {

    private static final String SELLER_EMAIL = "brand@showroomz.test";
    private static final long MY_MARKET_ID = 20L;
    private static final long OTHER_MARKET_ID = 21L;
    private static final long INQUIRY_ID = 55L;

    @Mock
    private ProductInquiryRepository productInquiryRepository;
    @Mock
    private ProductInquiryHistoryRepository productInquiryHistoryRepository;
    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private SellerInquiryQueryRepository sellerInquiryQueryRepository;

    @InjectMocks
    private SellerInquiryService sellerInquiryService;

    private Seller seller;

    private Seller newSeller() {
        Seller created = new Seller(SELLER_EMAIL, "encoded", "김담당", "010-1111-2222", LocalDateTime.now());
        ReflectionTestUtils.setField(created, "id", 3L);
        return created;
    }

    /** 로그인한 브랜드가 MY_MARKET_ID의 주인이라는 기본 배선. */
    private void givenMyMarket() {
        seller = newSeller();

        Market market = new Market();
        market.setMarketName("소연 뷰티");
        ReflectionTestUtils.setField(market, "id", MY_MARKET_ID);

        given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.of(seller));
        given(marketRepository.findBySeller(seller)).willReturn(Optional.of(market));
    }

    private Users user() {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users("mia", "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    private ProductInquiry inquiryOfMarket(long marketId) {
        Market market = new Market();
        market.setMarketName("어느 브랜드");
        ReflectionTestUtils.setField(market, "id", marketId);

        Product product = new Product();
        product.setProductId(100L);
        product.setName("수분 진정 토너");
        product.setMarket(market);

        ProductInquiry inquiry = ProductInquiry.builder()
                .user(user())
                .product(product)
                .type(ProductInquiryType.RESTOCK)
                .content("재입고 언제 되나요?")
                .secret(false)
                .imageUrls(List.of())
                .build();
        ReflectionTestUtils.setField(inquiry, "id", INQUIRY_ID);
        return inquiry;
    }

    private ProductInquiry givenMyInquiry() {
        ProductInquiry inquiry = inquiryOfMarket(MY_MARKET_ID);
        given(productInquiryRepository.findByIdWithUserAndProduct(INQUIRY_ID)).willReturn(Optional.of(inquiry));
        return inquiry;
    }

    private SellerInquiryDeleteRequest deleteRequest(ProductInquiryDeleteReason reason, String detail) {
        SellerInquiryDeleteRequest request = new SellerInquiryDeleteRequest();
        ReflectionTestUtils.setField(request, "reason", reason);
        ReflectionTestUtils.setField(request, "detail", detail);
        return request;
    }

    @Nested
    @DisplayName("답변 등록 (§23-4)")
    class RegisterAnswer {

        @Test
        @DisplayName("답변대기 건에 답변하면 답변완료로 바뀌고 이력이 남는다")
        void answeringWaitingInquirySucceeds() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();

            sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "다음 주 입고 예정입니다.");

            assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
            assertThat(inquiry.getAnswerContent()).isEqualTo("다음 주 입고 예정입니다.");
            assertThat(inquiry.getAnsweredAt()).isNotNull();

            ArgumentCaptor<ProductInquiryHistory> captor = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getHistoryType()).isEqualTo(ProductInquiryHistoryType.ANSWERED);
        }

        @Test
        @DisplayName("앞뒤 공백은 다듬어 저장한다")
        void answerIsTrimmed() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();

            sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "  다음 주 입고 예정입니다.  ");

            assertThat(inquiry.getAnswerContent()).isEqualTo("다음 주 입고 예정입니다.");
        }

        /** 답변은 1회만 등록할 수 있다 — 두 번째부터는 수정 경로로 가야 등록 시각이 보존된다. */
        @Test
        @DisplayName("이미 답변한 건에는 다시 등록할 수 없다")
        void secondAnswerIsRejected() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.registerAnswer("첫 답변");

            assertThatThrownBy(() -> sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "두 번째 답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_ANSWERED);

            assertThat(inquiry.getAnswerContent()).isEqualTo("첫 답변");
        }

        /** 검토 중에 답변이 붙으면 운영자가 판단한 대상과 화면에 보이는 내용이 달라진다. */
        @Test
        @DisplayName("운영자 검토 중인 건에는 답변할 수 없다")
        void answeringUnderReviewIsRejected() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);

            assertThatThrownBy(() -> sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_UNDER_DELETE_REVIEW);
        }

        @Test
        @DisplayName("삭제 집행된 건에는 답변할 수 없다")
        void answeringDeletedIsRejected() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.executeDelete(ProductInquiryAdminDeleteReason.ABUSE, null, 1L);

            assertThatThrownBy(() -> sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_DELETED);
        }
    }

    @Nested
    @DisplayName("답변 수정 (§23-4)")
    class ModifyAnswer {

        /** 공개 콘텐츠라 잘못된 안내를 고칠 경로가 필요하다 — 없으면 브랜드가 삭제 요청으로 우회한다. */
        @Test
        @DisplayName("답변을 고치면 등록 시각은 유지되고 수정 시각이 새로 남는다")
        void modifyKeepsAnsweredAtAndStampsModifiedAt() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.registerAnswer("첫 답변");
            LocalDateTime answeredAt = inquiry.getAnsweredAt();

            sellerInquiryService.modifyAnswer(SELLER_EMAIL, INQUIRY_ID, "고친 답변");

            assertThat(inquiry.getAnswerContent()).isEqualTo("고친 답변");
            assertThat(inquiry.getAnsweredAt()).isEqualTo(answeredAt);
            assertThat(inquiry.getAnswerModifiedAt()).isNotNull();
        }

        /** 조용한 수정을 막는 것이 이 경로의 요점이다 — 이력이 남지 않으면 수정 사실을 알 수 없다. */
        @Test
        @DisplayName("수정은 처리 이력에 기록된다")
        void modifyIsLogged() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.registerAnswer("첫 답변");

            sellerInquiryService.modifyAnswer(SELLER_EMAIL, INQUIRY_ID, "고친 답변");

            ArgumentCaptor<ProductInquiryHistory> captor = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getHistoryType()).isEqualTo(ProductInquiryHistoryType.ANSWER_MODIFIED);
        }

        @Test
        @DisplayName("아직 답변이 없으면 수정할 것이 없다")
        void modifyWithoutAnswerIsRejected() {
            givenMyMarket();
            givenMyInquiry();

            assertThatThrownBy(() -> sellerInquiryService.modifyAnswer(SELLER_EMAIL, INQUIRY_ID, "고친 답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_NOT_ANSWERED);
        }

        @Test
        @DisplayName("운영자 검토 중인 건의 답변은 고칠 수 없다")
        void modifyUnderReviewIsRejected() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.registerAnswer("첫 답변");
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);

            assertThatThrownBy(() -> sellerInquiryService.modifyAnswer(SELLER_EMAIL, INQUIRY_ID, "고친 답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_UNDER_DELETE_REVIEW);
        }

        @Test
        @DisplayName("삭제 집행된 건의 답변은 고칠 수 없다")
        void modifyDeletedIsRejected() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.registerAnswer("첫 답변");
            inquiry.executeDelete(ProductInquiryAdminDeleteReason.ABUSE, null, 1L);

            assertThatThrownBy(() -> sellerInquiryService.modifyAnswer(SELLER_EMAIL, INQUIRY_ID, "고친 답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_DELETED);
        }
    }

    @Nested
    @DisplayName("삭제 요청 (§23-5)")
    class RequestDelete {

        /** 요청은 집행이 아니다 — 노출 축만 검토 중으로 바뀌고 문의는 계속 게시된다. */
        @Test
        @DisplayName("요청하면 검토 중이 되지만 삭제되지는 않는다")
        void requestMarksUnderReviewWithoutDeleting() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();

            sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.ADVERTISEMENT, null));

            assertThat(inquiry.getExposureStatus()).isEqualTo(InquiryExposureStatus.DELETE_REQUESTED);
            assertThat(inquiry.isDeleted()).isFalse();
            assertThat(inquiry.getDeleteRequestedAt()).isNotNull();
        }

        /** 답변 축은 건드리지 않는다 — 반려되면 요청 직전 상태로 그대로 돌아가야 한다. */
        @Test
        @DisplayName("답변완료 건을 요청해도 답변 축은 그대로다")
        void requestKeepsAnswerAxis() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.registerAnswer("답변");

            sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.BRAND_COMPARISON, null));

            assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
            assertThat(inquiry.getAnswerContent()).isEqualTo("답변");
        }

        @Test
        @DisplayName("요청 사유가 이력에 남아 운영자가 판단 근거로 본다")
        void requestReasonIsLogged() {
            givenMyMarket();
            givenMyInquiry();

            sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.PRIVACY_EXPOSURE, null));

            ArgumentCaptor<ProductInquiryHistory> captor = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getHistoryType()).isEqualTo(ProductInquiryHistoryType.DELETE_REQUESTED);
            assertThat(captor.getValue().getDetail())
                    .isEqualTo(ProductInquiryDeleteReason.PRIVACY_EXPOSURE.getDescription());
        }

        /** 요청 후 취소는 불가하고 재요청도 막힌다 — 같은 건이 검토 큐에 중복으로 쌓이면 안 된다. */
        @Test
        @DisplayName("이미 요청한 건은 다시 요청할 수 없다")
        void duplicateRequestIsRejected() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);

            assertThatThrownBy(() -> sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_DELETE_ALREADY_REQUESTED);
        }

        @Test
        @DisplayName("이미 집행된 건은 요청 대상이 아니다")
        void requestAfterDeletionIsRejected() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();
            inquiry.executeDelete(ProductInquiryAdminDeleteReason.ABUSE, null, 1L);

            assertThatThrownBy(() -> sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_DELETED);
        }

        @Test
        @DisplayName("기타 사유는 상세 설명이 없으면 거절한다 — 운영자가 판단할 근거가 없다")
        void etcReasonRequiresDetail() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();

            assertThatThrownBy(() -> sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.ETC, "   ")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_DELETE_REASON_DETAIL_REQUIRED);

            assertThat(inquiry.isDeleteRequested()).isFalse();
            verify(productInquiryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("기타 사유에 상세 설명이 있으면 요청되고 공백은 다듬어 저장한다")
        void etcReasonWithDetailIsAccepted() {
            givenMyMarket();
            ProductInquiry inquiry = givenMyInquiry();

            sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.ETC, "  경쟁사 유도 링크  "));

            assertThat(inquiry.isDeleteRequested()).isTrue();
            assertThat(inquiry.getDeleteRequestDetail()).isEqualTo("경쟁사 유도 링크");
        }
    }

    @Nested
    @DisplayName("마켓 격리")
    class MarketIsolation {

        /** 다른 브랜드의 문의가 열리면 경쟁사 문의를 읽고 삭제 요청까지 걸 수 있다. */
        @Test
        @DisplayName("다른 마켓의 문의에는 답변할 수 없다")
        void cannotAnswerOtherMarketsInquiry() {
            givenMyMarket();
            given(productInquiryRepository.findByIdWithUserAndProduct(INQUIRY_ID))
                    .willReturn(Optional.of(inquiryOfMarket(OTHER_MARKET_ID)));

            assertThatThrownBy(() -> sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("다른 마켓의 문의에는 삭제 요청도 걸 수 없다")
        void cannotRequestDeleteOnOtherMarketsInquiry() {
            givenMyMarket();
            ProductInquiry other = inquiryOfMarket(OTHER_MARKET_ID);
            given(productInquiryRepository.findByIdWithUserAndProduct(INQUIRY_ID)).willReturn(Optional.of(other));

            assertThatThrownBy(() -> sellerInquiryService.requestDelete(SELLER_EMAIL, INQUIRY_ID,
                    deleteRequest(ProductInquiryDeleteReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            assertThat(other.isDeleteRequested()).isFalse();
        }

        @Test
        @DisplayName("없는 문의면 404를 낸다")
        void unknownInquiryIsRejected() {
            givenMyMarket();
            given(productInquiryRepository.findByIdWithUserAndProduct(INQUIRY_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }

        @Test
        @DisplayName("토큰의 판매자를 못 찾으면 인증 오류다")
        void unknownSellerIsRejected() {
            given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AUTH_INFO);
        }

        /** 마켓 등록을 마치지 않은 판매자는 문의 화면 자체가 성립하지 않는다. */
        @Test
        @DisplayName("마켓이 없는 판매자는 문의를 열 수 없다")
        void sellerWithoutMarketIsRejected() {
            seller = newSeller();
            given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.of(seller));
            given(marketRepository.findBySeller(seller)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sellerInquiryService.registerAnswer(SELLER_EMAIL, INQUIRY_ID, "답변"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }
    }
}

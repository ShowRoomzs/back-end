package showroomz.api.app.inquiry.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.inquiry.dto.InquiryCategoryResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryRegisterRequest;
import showroomz.api.app.inquiry.dto.ProductInquiryResponse;
import showroomz.api.app.inquiry.dto.ProductInquiryUpdateRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.entity.ProductInquiryHistory;
import showroomz.domain.inquiry.repository.ProductInquiryHistoryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryAdminDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;
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
 * C7-1 상품 문의 — 소비자가 쓰고 브랜드가 답한다.
 *
 * <p>여기서 지키는 축은 두 개다 (§23-1). <b>답변 축</b>은 답변이 달린 뒤 작성자가 글을 고치거나
 * 지우지 못하게 막고, <b>노출 축</b>은 브랜드가 삭제를 요청해 운영자 검토 중이거나 이미 집행된 건을
 * 작성자도 손대지 못하게 막는다. 두 축이 섞이면 "삭제 요청이 반려됐는데 답변이 사라지는" 식으로
 * 어긋나므로, 상태별로 어떤 오류가 나가는지를 하나씩 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class ProductInquiryServiceTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long PRODUCT_ID = 100L;
    private static final long INQUIRY_ID = 55L;

    @Mock
    private ProductInquiryRepository productInquiryRepository;
    @Mock
    private ProductInquiryHistoryRepository productInquiryHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private showroomz.domain.product.repository.ProductRepository productRepository;

    @InjectMocks
    private ProductInquiryService productInquiryService;

    private Users user(long id) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users("mia" + id, "미아", "mia" + id + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** 응답 조립이 상품 → 마켓까지 타고 들어가므로 마켓도 함께 채운다. */
    private Product product() {
        Market market = new Market();
        market.setMarketName("소연 뷰티");

        Product product = new Product();
        product.setProductId(PRODUCT_ID);
        product.setName("수분 진정 토너");
        product.setThumbnailUrl("https://cdn.showroomz.test/toner.jpg");
        product.setMarket(market);
        return product;
    }

    /** 작성자가 USER_ID인 문의 — 상태는 각 테스트가 필요한 만큼만 옮긴다. */
    private ProductInquiry inquiry() {
        ProductInquiry inquiry = ProductInquiry.builder()
                .user(user(USER_ID))
                .product(product())
                .type(ProductInquiryType.RESTOCK)
                .content("재입고 언제 되나요?")
                .secret(false)
                .imageUrls(List.of())
                .build();
        ReflectionTestUtils.setField(inquiry, "id", INQUIRY_ID);
        return inquiry;
    }

    private ProductInquiryRegisterRequest registerRequest(boolean secret, List<String> imageUrls) {
        ProductInquiryRegisterRequest request = new ProductInquiryRegisterRequest();
        ReflectionTestUtils.setField(request, "type", ProductInquiryType.INGREDIENT_USAGE);
        ReflectionTestUtils.setField(request, "content", "민감성 피부도 쓸 수 있나요?");
        ReflectionTestUtils.setField(request, "secret", secret);
        ReflectionTestUtils.setField(request, "imageUrls", imageUrls);
        return request;
    }

    private ProductInquiryUpdateRequest updateRequest() {
        ProductInquiryUpdateRequest request = new ProductInquiryUpdateRequest();
        ReflectionTestUtils.setField(request, "type", ProductInquiryType.DELIVERY);
        ReflectionTestUtils.setField(request, "content", "배송 얼마나 걸려요?");
        ReflectionTestUtils.setField(request, "imageUrls", List.of("https://cdn.showroomz.test/1.jpg"));
        return request;
    }

    @Nested
    @DisplayName("문의 등록 (§23-3)")
    class Register {

        @Test
        @DisplayName("등록하면 답변대기·정상 노출로 시작하고 등록 이력이 함께 남는다")
        void registeredInquiryStartsWaitingAndLogsHistory() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product()));

            productInquiryService.registerInquiry(USER_ID, PRODUCT_ID, registerRequest(false, List.of()));

            ArgumentCaptor<ProductInquiry> saved = ArgumentCaptor.forClass(ProductInquiry.class);
            verify(productInquiryRepository).save(saved.capture());
            assertThat(saved.getValue().isAnswered()).isFalse();
            assertThat(saved.getValue().isDeleteRequested()).isFalse();
            assertThat(saved.getValue().isDeleted()).isFalse();
            assertThat(saved.getValue().getType()).isEqualTo(ProductInquiryType.INGREDIENT_USAGE);

            ArgumentCaptor<ProductInquiryHistory> history = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(history.capture());
            assertThat(history.getValue().getHistoryType()).isEqualTo(ProductInquiryHistoryType.REGISTERED);
        }

        /** 이력 문구로 비밀글 여부가 드러나야 브랜드가 상세에서 공개 범위를 알 수 있다 (§23-3). */
        @Test
        @DisplayName("비밀글이면 등록 이력에 비밀글로 표기된다")
        void secretInquiryIsMarkedInHistory() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product()));

            productInquiryService.registerInquiry(USER_ID, PRODUCT_ID, registerRequest(true, List.of()));

            ArgumentCaptor<ProductInquiryHistory> history = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(history.capture());
            assertThat(history.getValue().getDetail()).isEqualTo("비밀글");
        }

        @Test
        @DisplayName("공개글이면 이력에 별도 표기를 붙이지 않는다")
        void publicInquiryHasNoVisibilityNote() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product()));

            productInquiryService.registerInquiry(USER_ID, PRODUCT_ID, registerRequest(false, List.of()));

            ArgumentCaptor<ProductInquiryHistory> history = ArgumentCaptor.forClass(ProductInquiryHistory.class);
            verify(productInquiryHistoryRepository).save(history.capture());
            assertThat(history.getValue().getDetail()).isNull();
        }

        @Test
        @DisplayName("없는 상품에는 문의를 남길 수 없다")
        void unknownProductIsRejected() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productInquiryService.registerInquiry(
                    USER_ID, PRODUCT_ID, registerRequest(false, List.of())))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

            verify(productInquiryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("내 문의 조회")
    class Detail {

        @Test
        @DisplayName("상세에는 상품 대표 이미지가 함께 실린다")
        void detailCarriesProductThumbnail() {
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry()));

            ProductInquiryResponse response = productInquiryService.getInquiryDetail(USER_ID, INQUIRY_ID);

            assertThat(response.getProductImageUrl()).isEqualTo("https://cdn.showroomz.test/toner.jpg");
            assertThat(response.getShopName()).isEqualTo("소연 뷰티");
        }

        @Test
        @DisplayName("남의 문의는 열 수 없다 — 존재 여부가 아니라 권한으로 막는다")
        void otherUsersInquiryIsForbidden() {
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry()));

            assertThatThrownBy(() -> productInquiryService.getInquiryDetail(OTHER_USER_ID, INQUIRY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        /** 삭제 집행된 문의는 질문·답변이 함께 소비자 화면에서 내려간다 (§23-5). */
        @Test
        @DisplayName("삭제 집행된 문의는 작성자에게도 보이지 않는다")
        void deletedInquiryIsHiddenFromWriter() {
            ProductInquiry inquiry = inquiry();
            inquiry.executeDelete(ProductInquiryAdminDeleteReason.ABUSE, null, 1L);
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            assertThatThrownBy(() -> productInquiryService.getInquiryDetail(USER_ID, INQUIRY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }

        /** 검토 중에도 문의는 계속 게시된다 — 요청만으로 내려가면 브랜드가 삭제를 우회할 수 있다. */
        @Test
        @DisplayName("삭제 요청 검토 중인 문의는 그대로 보인다")
        void deleteRequestedInquiryIsStillVisible() {
            ProductInquiry inquiry = inquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            assertThat(productInquiryService.getInquiryDetail(USER_ID, INQUIRY_ID)).isNotNull();
        }

        @Test
        @DisplayName("없는 문의면 404를 낸다")
        void unknownInquiryIsRejected() {
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productInquiryService.getInquiryDetail(USER_ID, INQUIRY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }
    }

    @Nested
    @DisplayName("수정·삭제 가능 조건")
    class Mutation {

        @Test
        @DisplayName("답변 전이면 유형·내용·사진을 고칠 수 있다")
        void waitingInquiryCanBeEdited() {
            ProductInquiry inquiry = inquiry();
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            productInquiryService.updateInquiry(USER_ID, INQUIRY_ID, updateRequest());

            assertThat(inquiry.getType()).isEqualTo(ProductInquiryType.DELIVERY);
            assertThat(inquiry.getContent()).isEqualTo("배송 얼마나 걸려요?");
            assertThat(inquiry.getImageUrls()).containsExactly("https://cdn.showroomz.test/1.jpg");
        }

        /** 비밀글 여부는 작성 시점 값을 유지한다 — 수정 요청에 그 항목이 아예 없다. */
        @Test
        @DisplayName("수정해도 비밀글 여부는 그대로다")
        void editKeepsVisibility() {
            ProductInquiry inquiry = ProductInquiry.builder()
                    .user(user(USER_ID))
                    .product(product())
                    .type(ProductInquiryType.RESTOCK)
                    .content("재입고 언제 되나요?")
                    .secret(true)
                    .imageUrls(List.of())
                    .build();
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            productInquiryService.updateInquiry(USER_ID, INQUIRY_ID, updateRequest());

            assertThat(inquiry.isSecret()).isTrue();
        }

        @Test
        @DisplayName("답변이 달린 뒤에는 고칠 수 없다 — 답변의 전제가 바뀌면 안 된다")
        void answeredInquiryCannotBeEdited() {
            ProductInquiry inquiry = inquiry();
            inquiry.registerAnswer("다음 주 입고 예정입니다.");
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            assertThatThrownBy(() -> productInquiryService.updateInquiry(USER_ID, INQUIRY_ID, updateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        @Test
        @DisplayName("답변이 달린 뒤에는 지울 수도 없다")
        void answeredInquiryCannotBeDeleted() {
            ProductInquiry inquiry = inquiry();
            inquiry.registerAnswer("다음 주 입고 예정입니다.");
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            assertThatThrownBy(() -> productInquiryService.deleteInquiry(USER_ID, INQUIRY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_ANSWERED);

            verify(productInquiryRepository, never()).delete(any());
        }

        /**
         * 검토 중 작성자가 스스로 지우면 운영자가 판단할 대상이 사라진다 — 반려 근거도 함께 사라지므로 막는다.
         */
        @Test
        @DisplayName("삭제 요청 검토 중이면 작성자도 손댈 수 없다")
        void inquiryUnderDeleteReviewIsLocked() {
            ProductInquiry inquiry = inquiry();
            inquiry.requestDelete(ProductInquiryDeleteReason.ADVERTISEMENT, null);
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            assertThatThrownBy(() -> productInquiryService.deleteInquiry(USER_ID, INQUIRY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_UNDER_DELETE_REVIEW);
        }

        @Test
        @DisplayName("이미 삭제 집행된 문의는 다시 지울 수 없다")
        void deletedInquiryCannotBeDeletedAgain() {
            ProductInquiry inquiry = inquiry();
            inquiry.executeDelete(ProductInquiryAdminDeleteReason.ABUSE, null, 1L);
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            assertThatThrownBy(() -> productInquiryService.deleteInquiry(USER_ID, INQUIRY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_ALREADY_DELETED);
        }

        @Test
        @DisplayName("남의 문의는 지울 수 없다")
        void otherUsersInquiryCannotBeDeleted() {
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry()));

            assertThatThrownBy(() -> productInquiryService.deleteInquiry(OTHER_USER_ID, INQUIRY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(productInquiryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("답변 전 · 정상 노출이면 삭제된다")
        void waitingInquiryIsDeleted() {
            ProductInquiry inquiry = inquiry();
            given(productInquiryRepository.findById(INQUIRY_ID)).willReturn(Optional.of(inquiry));

            productInquiryService.deleteInquiry(USER_ID, INQUIRY_ID);

            verify(productInquiryRepository).delete(inquiry);
        }
    }

    @Test
    @DisplayName("문의 유형 목록은 정의된 유형을 모두 설명과 함께 내려준다")
    void categoriesCoverEveryType() {
        List<InquiryCategoryResponse> categories = productInquiryService.getProductInquiryCategories();

        assertThat(categories).hasSize(ProductInquiryType.values().length);
        assertThat(categories).allSatisfy(category -> {
            assertThat(category.getKey()).isNotBlank();
            assertThat(category.getDescription()).isNotBlank();
        });
    }
}

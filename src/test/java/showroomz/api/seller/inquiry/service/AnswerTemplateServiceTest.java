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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.inquiry.dto.AnswerTemplateDeleteRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateRegisterRequest;
import showroomz.api.seller.inquiry.dto.AnswerTemplateUpdateRequest;
import showroomz.domain.inquiry.entity.AnswerTemplate;
import showroomz.domain.inquiry.repository.AnswerTemplateRepository;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 답변 템플릿 — 브랜드가 자주 쓰는 답변을 저장해 두는 개인 자산이다.
 *
 * <p>두 축을 지킨다. <b>소유권</b>: 조회·수정·삭제가 모두 (id, sellerId)로 좁혀지므로 다른 브랜드의
 * 템플릿은 애초에 잡히지 않아야 한다 — 템플릿에는 응대 문구와 정책이 담겨 새면 영업 정보가 샌다.
 *
 * <p><b>사용 여부 기본값</b>: 목록은 기본적으로 사용 중인 템플릿만 보여 준다. 이 기본값이 뒤집히면
 * 답변 작성 화면의 추천 목록에 쓰지 않기로 한 문구가 섞여 나간다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnswerTemplateServiceTest {

    private static final String SELLER_EMAIL = "brand@showroomz.test";
    private static final long SELLER_ID = 3L;
    private static final long TEMPLATE_ID = 9L;

    @Mock
    private AnswerTemplateRepository answerTemplateRepository;
    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private AnswerTemplateService answerTemplateService;

    private Seller seller;

    private Seller givenSeller() {
        seller = new Seller(SELLER_EMAIL, "encoded", "김담당", "010-1111-2222", LocalDateTime.now());
        ReflectionTestUtils.setField(seller, "id", SELLER_ID);
        given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.of(seller));
        return seller;
    }

    private AnswerTemplate template(boolean isActive) {
        AnswerTemplate template = AnswerTemplate.builder()
                .seller(seller)
                .title("재입고 안내")
                .category(ProductInquiryType.RESTOCK)
                .content("이번 주 내로 재입고 예정입니다.")
                .isActive(isActive)
                .build();
        ReflectionTestUtils.setField(template, "id", TEMPLATE_ID);
        return template;
    }

    private AnswerTemplateRegisterRequest registerRequest(Boolean isActive) {
        AnswerTemplateRegisterRequest request = new AnswerTemplateRegisterRequest();
        ReflectionTestUtils.setField(request, "title", "재입고 안내");
        ReflectionTestUtils.setField(request, "category", ProductInquiryType.RESTOCK);
        ReflectionTestUtils.setField(request, "content", "이번 주 내로 재입고 예정입니다.");
        ReflectionTestUtils.setField(request, "isActive", isActive);
        return request;
    }

    private AnswerTemplateUpdateRequest updateRequest(Boolean isActive) {
        AnswerTemplateUpdateRequest request = new AnswerTemplateUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "재입고 안내 - 수정본");
        ReflectionTestUtils.setField(request, "category", ProductInquiryType.DELIVERY);
        ReflectionTestUtils.setField(request, "content", "고친 문구");
        ReflectionTestUtils.setField(request, "isActive", isActive);
        return request;
    }

    private PagingRequest paging() {
        return new PagingRequest();
    }

    private void givenEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modifiedAt"));
        Page<AnswerTemplate> page = new PageImpl<>(List.of(), pageable, 0);
        given(answerTemplateRepository.findTemplates(anyLong(), any(), any(), any(), any())).willReturn(page);
    }

    @Nested
    @DisplayName("등록")
    class Register {

        @Test
        @DisplayName("등록하면 내 템플릿으로 저장되고 생성된 식별자를 돌려준다")
        void templateIsSavedForMe() {
            givenSeller();
            given(answerTemplateRepository.save(any(AnswerTemplate.class))).willAnswer(invocation -> {
                AnswerTemplate saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", TEMPLATE_ID);
                return saved;
            });

            assertThat(answerTemplateService.registerTemplate(SELLER_EMAIL, registerRequest(true))
                    .getTemplateId()).isEqualTo(TEMPLATE_ID);

            ArgumentCaptor<AnswerTemplate> captor = ArgumentCaptor.forClass(AnswerTemplate.class);
            verify(answerTemplateRepository).save(captor.capture());
            assertThat(captor.getValue().getSeller()).isSameAs(seller);
            assertThat(captor.getValue().getCategory()).isEqualTo(ProductInquiryType.RESTOCK);
        }

        /** 사용 여부를 보내지 않으면 "사용"이 기본이다 — 만들자마자 안 보이면 만든 의미가 없다. */
        @Test
        @DisplayName("사용 여부를 생략하면 사용 중으로 만든다")
        void omittedActiveFlagDefaultsToTrue() {
            givenSeller();
            given(answerTemplateRepository.save(any(AnswerTemplate.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            answerTemplateService.registerTemplate(SELLER_EMAIL, registerRequest(null));

            ArgumentCaptor<AnswerTemplate> captor = ArgumentCaptor.forClass(AnswerTemplate.class);
            verify(answerTemplateRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("사용 안 함으로 지정해 만들 수도 있다")
        void inactiveTemplateCanBeCreated() {
            givenSeller();
            given(answerTemplateRepository.save(any(AnswerTemplate.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            answerTemplateService.registerTemplate(SELLER_EMAIL, registerRequest(false));

            ArgumentCaptor<AnswerTemplate> captor = ArgumentCaptor.forClass(AnswerTemplate.class);
            verify(answerTemplateRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("토큰의 판매자를 못 찾으면 저장하지 않는다")
        void unknownSellerIsRejected() {
            given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.empty());

            assertThatThrownBy(() -> answerTemplateService.registerTemplate(SELLER_EMAIL, registerRequest(true)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AUTH_INFO);

            verify(answerTemplateRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("목록 조회")
    class ListTemplates {

        /** 답변 작성 화면의 추천 목록에 쓰지 않기로 한 문구가 섞이면 안 된다. */
        @Test
        @DisplayName("기본은 사용 중인 템플릿만 조회한다")
        void defaultShowsActiveOnly() {
            givenSeller();
            givenEmptyPage();

            answerTemplateService.getTemplates(SELLER_EMAIL, null, null, null, paging());

            verify(answerTemplateRepository).findTemplates(
                    eq(SELLER_ID), eq(Boolean.TRUE), any(), any(), any());
        }

        @Test
        @DisplayName("includeInactive를 켜면 사용 여부로 걸러내지 않는다")
        void includeInactiveDropsTheFilter() {
            givenSeller();
            givenEmptyPage();

            answerTemplateService.getTemplates(SELLER_EMAIL, true, null, null, paging());

            verify(answerTemplateRepository).findTemplates(
                    eq(SELLER_ID), eq(null), any(), any(), any());
        }

        @Test
        @DisplayName("includeInactive를 false로 보내도 사용 중만 조회한다")
        void explicitFalseKeepsActiveFilter() {
            givenSeller();
            givenEmptyPage();

            answerTemplateService.getTemplates(SELLER_EMAIL, false, null, null, paging());

            verify(answerTemplateRepository).findTemplates(
                    eq(SELLER_ID), eq(Boolean.TRUE), any(), any(), any());
        }

        /** 공백만 담긴 검색어를 그대로 넘기면 아무것도 안 걸리는 LIKE가 나간다. */
        @Test
        @DisplayName("공백만 있는 검색어는 검색 조건에서 빼고 나머지는 다듬어 넘긴다")
        void blankKeywordIsDropped() {
            givenSeller();
            givenEmptyPage();

            answerTemplateService.getTemplates(SELLER_EMAIL, null, null, "   ", paging());
            verify(answerTemplateRepository).findTemplates(anyLong(), any(), any(), eq(null), any());

            answerTemplateService.getTemplates(SELLER_EMAIL, null, null, "  재입고  ", paging());
            verify(answerTemplateRepository).findTemplates(anyLong(), any(), any(), eq("재입고"), any());
        }

        @Test
        @DisplayName("카테고리 필터는 그대로 전달된다")
        void categoryFilterIsPassedThrough() {
            givenSeller();
            givenEmptyPage();

            answerTemplateService.getTemplates(SELLER_EMAIL, null, ProductInquiryType.RESTOCK, null, paging());

            verify(answerTemplateRepository).findTemplates(
                    anyLong(), any(), eq(ProductInquiryType.RESTOCK), any(), any());
        }

        /** 최근에 손댄 템플릿이 위로 와야 방금 고친 문구를 바로 쓸 수 있다. */
        @Test
        @DisplayName("최근 수정순으로 정렬해 조회한다")
        void sortedByRecentlyModified() {
            givenSeller();
            givenEmptyPage();

            answerTemplateService.getTemplates(SELLER_EMAIL, null, null, null, paging());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(answerTemplateRepository).findTemplates(anyLong(), any(), any(), any(), captor.capture());
            assertThat(captor.getValue().getSort().getOrderFor("modifiedAt")).isNotNull();
            assertThat(captor.getValue().getSort().getOrderFor("modifiedAt").isDescending()).isTrue();
        }

        @Test
        @DisplayName("조회는 내 판매자 식별자로 좁혀진다")
        void listIsScopedToMe() {
            givenSeller();
            givenEmptyPage();

            answerTemplateService.getTemplates(SELLER_EMAIL, null, null, null, paging());

            verify(answerTemplateRepository).findTemplates(eq(SELLER_ID), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("단건 조회 · 수정 · 삭제")
    class Mutation {

        @Test
        @DisplayName("내 템플릿은 열린다")
        void ownTemplateIsReadable() {
            givenSeller();
            given(answerTemplateRepository.findByIdAndSellerId(TEMPLATE_ID, SELLER_ID))
                    .willReturn(Optional.of(template(true)));

            assertThat(answerTemplateService.getTemplate(SELLER_EMAIL, TEMPLATE_ID).getTitle())
                    .isEqualTo("재입고 안내");
        }

        /** 조회 자체를 (id, sellerId)로 좁혀 다른 브랜드의 템플릿은 애초에 잡히지 않는다. */
        @Test
        @DisplayName("다른 브랜드의 템플릿은 잡히지 않아 열 수 없다")
        void otherSellersTemplateIsNotReadable() {
            givenSeller();
            given(answerTemplateRepository.findByIdAndSellerId(TEMPLATE_ID, SELLER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> answerTemplateService.getTemplate(SELLER_EMAIL, TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }

        @Test
        @DisplayName("수정하면 제목·카테고리·내용·사용 여부가 함께 바뀐다")
        void updateAppliesEveryField() {
            givenSeller();
            AnswerTemplate template = template(true);
            given(answerTemplateRepository.findByIdAndSellerId(TEMPLATE_ID, SELLER_ID))
                    .willReturn(Optional.of(template));

            answerTemplateService.updateTemplate(SELLER_EMAIL, TEMPLATE_ID, updateRequest(false));

            assertThat(template.getTitle()).isEqualTo("재입고 안내 - 수정본");
            assertThat(template.getCategory()).isEqualTo(ProductInquiryType.DELIVERY);
            assertThat(template.getContent()).isEqualTo("고친 문구");
            assertThat(template.isActive()).isFalse();
        }

        @Test
        @DisplayName("다른 브랜드의 템플릿은 수정할 수 없다")
        void otherSellersTemplateIsNotUpdatable() {
            givenSeller();
            given(answerTemplateRepository.findByIdAndSellerId(TEMPLATE_ID, SELLER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    answerTemplateService.updateTemplate(SELLER_EMAIL, TEMPLATE_ID, updateRequest(true)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }

        /**
         * 일괄 삭제도 (ids, sellerId)로 조회한 결과만 지운다 — 남의 식별자를 섞어 보내도
         * 조회에서 걸러지므로 내 것만 사라진다.
         */
        @Test
        @DisplayName("일괄 삭제는 내 소유로 확인된 것만 지운다")
        void bulkDeleteOnlyRemovesMine() {
            givenSeller();
            AnswerTemplate mine = template(true);
            given(answerTemplateRepository.findAllByIdInAndSellerId(List.of(TEMPLATE_ID, 999L), SELLER_ID))
                    .willReturn(List.of(mine));

            AnswerTemplateDeleteRequest request = new AnswerTemplateDeleteRequest();
            ReflectionTestUtils.setField(request, "templateIds", List.of(TEMPLATE_ID, 999L));

            answerTemplateService.deleteTemplates(SELLER_EMAIL, request);

            verify(answerTemplateRepository).deleteAll(List.of(mine));
        }

        @Test
        @DisplayName("내 것이 하나도 없으면 아무것도 지우지 않는다")
        void bulkDeleteWithNoMatchesDeletesNothing() {
            givenSeller();
            given(answerTemplateRepository.findAllByIdInAndSellerId(any(), eq(SELLER_ID)))
                    .willReturn(List.of());

            AnswerTemplateDeleteRequest request = new AnswerTemplateDeleteRequest();
            ReflectionTestUtils.setField(request, "templateIds", List.of(999L));

            answerTemplateService.deleteTemplates(SELLER_EMAIL, request);

            verify(answerTemplateRepository).deleteAll(List.of());
        }
    }
}

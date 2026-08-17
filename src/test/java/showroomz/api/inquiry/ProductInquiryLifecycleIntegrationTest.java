package showroomz.api.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 문의 전체 수명주기 (§18 · §23) — 소비자 · 브랜드 · 운영자 <b>세 당사자를 한 흐름에서</b> 태운다.
 *
 * <p>단위 테스트는 각 서비스가 자기 몫을 하는지만 본다. 정작 깨지기 쉬운 곳은 경계다 —
 * 브랜드가 답변한 뒤 소비자 화면에서 수정 버튼이 사라지는지, 운영자가 반려한 뒤 브랜드가
 * <b>다시</b> 답변을 고칠 수 있게 돌아오는지는 세 축이 만나야 확인된다.
 *
 * <p>통합 하네스가 {@code open-in-view=false}라 상세 응답이 상품·마켓·작성자를 지연 로딩에 맡겼다면
 * 직렬화에서 터진다 — 조회 쿼리의 페치 조인도 이 테스트가 함께 지킨다.
 */
@DisplayName("[통합] 상품 문의 수명주기 (소비자 ↔ 브랜드 ↔ 운영자)")
class ProductInquiryLifecycleIntegrationTest extends IntegrationTestSupport {

    private static final String CONSUMER_REGISTER = "/v1/user/products/%d/inquiries";
    private static final String CONSUMER_LIST = "/v1/user/product-inquiries";
    private static final String CONSUMER_DETAIL = "/v1/user/product-inquiries/%d";
    private static final String SELLER_LIST = "/v1/seller/inquiries";
    private static final String SELLER_DETAIL = "/v1/seller/inquiries/%d";
    private static final String SELLER_ANSWER = "/v1/seller/inquiries/%d/answer";
    private static final String SELLER_DELETE_REQUEST = "/v1/seller/inquiries/%d/delete-request";
    private static final String ADMIN_LIST = "/v1/admin/product-inquiries";
    private static final String ADMIN_DETAIL = "/v1/admin/product-inquiries/%d";
    private static final String ADMIN_EXECUTE = "/v1/admin/product-inquiries/%d/delete-request/execute";
    private static final String ADMIN_REJECT = "/v1/admin/product-inquiries/%d/delete-request/reject";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private String consumerToken;
    private String brandToken;
    private String otherBrandToken;
    private String operatorToken;
    private Long productId;

    @BeforeEach
    void setUpParties() {
        Users consumer = createConsumer("mia");
        consumerToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());

        BrandFixture.Brand brand = fixture.createBrand("brand@showroomz.test", "소연뷰티");
        brandToken = sellerToken(brand.seller());

        BrandFixture.Brand rival = fixture.createBrand("rival@showroomz.test", "경쟁뷰티");
        otherBrandToken = sellerToken(rival.seller());

        Seller operator = fixture.createAdmin("admin@showroomz.test", "운영자김");
        operatorToken = adminToken(operator);

        productId = createProduct(brand, "수분 진정 토너");
    }

    // ------------------------------------------------------------------ 시나리오

    @Nested
    @DisplayName("등록 → 답변")
    class RegisterThenAnswer {

        @Test
        @DisplayName("소비자가 남긴 문의가 브랜드 목록에 답변대기로 잡힌다")
        void consumerInquiryReachesBrandQueue() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);

            mockMvc.perform(get(SELLER_LIST).header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                    .andExpect(jsonPath("$.waitingCount").value(1))
                    .andExpect(jsonPath("$.content[0].inquiryId").value(inquiryId));
        }

        /** 브랜드는 실명·연락처를 볼 수 없고 가린 닉네임만 본다 (§23-3). */
        @Test
        @DisplayName("브랜드 상세에는 작성자 닉네임이 가려져 내려간다")
        void brandSeesMaskedNickname() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);

            mockMvc.perform(get(SELLER_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.writerName").value("미****"));
        }

        @Test
        @DisplayName("브랜드가 답변하면 소비자 상세에 답변이 실린다")
        void answerIsVisibleToConsumer() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);

            answer(inquiryId, "다음 주 입고 예정입니다.");

            mockMvc.perform(get(CONSUMER_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, consumerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ANSWERED"))
                    .andExpect(jsonPath("$.answerContent").value("다음 주 입고 예정입니다."));
        }

        /** 답변은 1회만 등록할 수 있다 — 두 번째부터는 수정 경로여야 등록 시각이 보존된다. */
        @Test
        @DisplayName("두 번째 답변 등록은 거절된다")
        void secondAnswerIsRejected() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);
            answer(inquiryId, "첫 답변");

            mockMvc.perform(post(SELLER_ANSWER.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, brandToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("answerContent", "두 번째 답변"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));
        }

        /** 답변이 달린 문의를 작성자가 고치면 답변의 전제가 바뀐다. */
        @Test
        @DisplayName("답변이 달린 뒤에는 소비자가 문의를 고칠 수 없다")
        void consumerCannotEditAfterAnswer() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);
            answer(inquiryId, "다음 주 입고 예정입니다.");

            mockMvc.perform(put(CONSUMER_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, consumerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("type", "DELIVERY", "content", "질문을 바꿔봅니다"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));
        }

        @Test
        @DisplayName("답변 수정은 등록 시각을 유지하고 수정 시각을 새로 남긴다")
        void modifyKeepsAnsweredAt() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);
            answer(inquiryId, "첫 답변");

            mockMvc.perform(patch(SELLER_ANSWER.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, brandToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("answerContent", "고친 답변"))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(SELLER_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(jsonPath("$.answerContent").value("고친 답변"))
                    .andExpect(jsonPath("$.answeredAt").isNotEmpty())
                    .andExpect(jsonPath("$.answerModifiedAt").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("삭제 요청 → 반려 (§18-6)")
    class RequestThenReject {

        /**
         * 반려의 계약은 "요청 직전 상태로 정확히 복귀"다. 답변완료 건이 반려 후 답변대기로 돌아가면
         * 브랜드가 이미 답한 문의에 다시 답하라는 알림을 받는다.
         */
        @Test
        @DisplayName("답변완료 건을 요청 후 반려하면 답변완료로 그대로 돌아온다")
        void rejectRestoresAnsweredState() throws Exception {
            Long inquiryId = registerInquiry("경쟁사가 더 좋대요", false);
            answer(inquiryId, "성분 차이를 안내드립니다.");
            requestDelete(inquiryId, "BRAND_COMPARISON", null);

            reject(inquiryId, "NORMAL_INQUIRY", null);

            mockMvc.perform(get(ADMIN_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ANSWERED"))
                    .andExpect(jsonPath("$.exposureStatus").value("NORMAL"))
                    .andExpect(jsonPath("$.answerContent").value("성분 차이를 안내드립니다."))
                    .andExpect(jsonPath("$.canReject").value(false));
        }

        /** 검토 중에도 문의는 계속 게시된다 — 요청만으로 내려가면 브랜드가 삭제를 우회할 수 있다. */
        @Test
        @DisplayName("검토 중에도 소비자는 자기 문의를 계속 볼 수 있다")
        void inquiryStaysVisibleUnderReview() throws Exception {
            Long inquiryId = registerInquiry("경쟁사가 더 좋대요", false);
            requestDelete(inquiryId, "BRAND_COMPARISON", null);

            mockMvc.perform(get(CONSUMER_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, consumerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("검토 중에는 소비자도 브랜드도 손댈 수 없다")
        void bothSidesAreLockedUnderReview() throws Exception {
            Long inquiryId = registerInquiry("경쟁사가 더 좋대요", false);
            requestDelete(inquiryId, "BRAND_COMPARISON", null);

            mockMvc.perform(put(CONSUMER_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, consumerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("type", "ETC", "content", "고쳐볼게요"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INQUIRY_UNDER_DELETE_REVIEW"));

            mockMvc.perform(post(SELLER_ANSWER.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, brandToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("answerContent", "답변 달아볼게요"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INQUIRY_UNDER_DELETE_REVIEW"));
        }

        /** 요청 후 취소는 불가하고 재요청도 막힌다 — 같은 건이 검토 큐에 중복으로 쌓이면 안 된다. */
        @Test
        @DisplayName("요청 중인 건을 다시 요청하면 거절된다")
        void duplicateRequestIsRejected() throws Exception {
            Long inquiryId = registerInquiry("경쟁사가 더 좋대요", false);
            requestDelete(inquiryId, "BRAND_COMPARISON", null);

            mockMvc.perform(post(SELLER_DELETE_REQUEST.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, brandToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reason", "ABUSE"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INQUIRY_DELETE_ALREADY_REQUESTED"));
        }

        /** 반려된 건은 사유를 보완해 재요청할 수 있다 (§23-5). */
        @Test
        @DisplayName("반려된 뒤에는 사유를 보완해 다시 요청할 수 있다")
        void canRequestAgainAfterRejection() throws Exception {
            Long inquiryId = registerInquiry("경쟁사가 더 좋대요", false);
            requestDelete(inquiryId, "BRAND_COMPARISON", null);
            reject(inquiryId, "INSUFFICIENT_EVIDENCE", null);

            requestDelete(inquiryId, "ETC", "타사 판매 링크가 본문에 있습니다");

            mockMvc.perform(get(ADMIN_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(jsonPath("$.exposureStatus").value("DELETE_REQUESTED"))
                    .andExpect(jsonPath("$.canReject").value(true))
                    .andExpect(jsonPath("$.deleteRequest.underReview").value(true))
                    .andExpect(jsonPath("$.deleteRequest.rejected").value(false));
        }

        @Test
        @DisplayName("삭제 요청이 없는 건은 반려할 수 없다")
        void rejectWithoutRequestIsRejected() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);

            mockMvc.perform(post(ADMIN_REJECT.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, operatorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reason", "NOT_QUALIFYING"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INQUIRY_DELETE_NOT_REQUESTED"));
        }
    }

    @Nested
    @DisplayName("삭제 집행 (§18-5)")
    class ExecuteDelete {

        /** 질문과 브랜드 답변이 <b>함께</b> 소비자 화면에서 내려간다. */
        @Test
        @DisplayName("집행하면 소비자 상세와 목록에서 함께 사라진다")
        void deletedInquiryDisappearsFromConsumer() throws Exception {
            Long inquiryId = registerInquiry("부적절한 내용", false);
            answer(inquiryId, "답변");

            execute(inquiryId, "ABUSE", null);

            mockMvc.perform(get(CONSUMER_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, consumerToken))
                    .andExpect(status().isNotFound());
        }

        /** 삭제 요청 유무와 무관하게 운영자가 직접 집행할 수 있다 — 순찰 중 발견한 건이 이 경로다. */
        @Test
        @DisplayName("삭제 요청이 없어도 운영자가 바로 집행할 수 있다")
        void operatorCanDeleteWithoutBrandRequest() throws Exception {
            Long inquiryId = registerInquiry("부적절한 내용", false);

            execute(inquiryId, "ADVERTISEMENT", null);

            mockMvc.perform(get(ADMIN_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(jsonPath("$.exposureStatus").value("DELETED"))
                    .andExpect(jsonPath("$.canExecuteDelete").value(false));
        }

        @Test
        @DisplayName("기타 사유는 상세 설명이 없으면 거절한다")
        void etcReasonRequiresDetail() throws Exception {
            Long inquiryId = registerInquiry("부적절한 내용", false);

            mockMvc.perform(post(ADMIN_EXECUTE.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, operatorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reason", "ETC"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이미 집행된 건은 다시 집행할 수 없다")
        void doubleExecutionIsRejected() throws Exception {
            Long inquiryId = registerInquiry("부적절한 내용", false);
            execute(inquiryId, "ABUSE", null);

            mockMvc.perform(post(ADMIN_EXECUTE.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, operatorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reason", "ADVERTISEMENT"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_DELETED"));
        }

        /** 집행 후에도 답변 축은 보존된다 — 삭제는 노출을 내리는 일이고 답변 사실을 지우는 일이 아니다. */
        @Test
        @DisplayName("집행해도 운영자 화면에서는 답변 내용이 보존된다")
        void answerSurvivesForOperator() throws Exception {
            Long inquiryId = registerInquiry("부적절한 내용", false);
            answer(inquiryId, "브랜드 답변입니다.");

            execute(inquiryId, "ABUSE", null);

            mockMvc.perform(get(ADMIN_DETAIL.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(jsonPath("$.status").value("ANSWERED"))
                    .andExpect(jsonPath("$.answerContent").value("브랜드 답변입니다."));
        }
    }

    @Nested
    @DisplayName("권한 경계")
    class Authorization {

        /** 다른 브랜드의 문의가 열리면 경쟁사 문의를 읽고 삭제 요청까지 걸 수 있다. */
        @Test
        @DisplayName("다른 브랜드는 남의 문의를 목록에서 보지 못한다")
        void rivalBrandSeesNothing() throws Exception {
            registerInquiry("재입고 언제 되나요?", false);

            mockMvc.perform(get(SELLER_LIST).header(HttpHeaders.AUTHORIZATION, otherBrandToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }

        @Test
        @DisplayName("다른 브랜드는 남의 문의에 답변할 수 없다")
        void rivalBrandCannotAnswer() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);

            mockMvc.perform(post(SELLER_ANSWER.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, otherBrandToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("answerContent", "가로채기"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("소비자 토큰으로는 운영자 모니터링에 접근할 수 없다")
        void consumerCannotReachAdminMonitoring() throws Exception {
            mockMvc.perform(get(ADMIN_LIST).header(HttpHeaders.AUTHORIZATION, consumerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("브랜드 토큰으로는 삭제 집행을 할 수 없다 — 집행은 운영자 몫이다")
        void brandCannotExecuteDelete() throws Exception {
            Long inquiryId = registerInquiry("재입고 언제 되나요?", false);

            mockMvc.perform(post(ADMIN_EXECUTE.formatted(inquiryId))
                            .header(HttpHeaders.AUTHORIZATION, brandToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("reason", "ABUSE"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("비로그인은 어느 창구에도 들어오지 못한다")
        void anonymousIsRejectedEverywhere() throws Exception {
            mockMvc.perform(get(CONSUMER_LIST)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(SELLER_LIST)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(ADMIN_LIST)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("운영자 목록 집계")
    class AdminAggregation {

        /**
         * 상태 탭은 <b>서로 겹치지 않는다</b> — 답변대기·답변완료 탭은 노출이 정상인 건만 센다.
         * 그래서 답변대기 건에 삭제 요청이 걸리면 답변대기 탭에서 빠져 삭제 요청 탭으로 옮겨간다.
         * 겹치게 세면 탭 숫자의 합이 전체보다 커져 운영자가 같은 건을 두 번 처리한다.
         */
        @Test
        @DisplayName("상태 탭은 겹치지 않는다 — 삭제 요청이 걸리면 답변대기에서 빠진다")
        void statusCountsAreMutuallyExclusive() throws Exception {
            registerInquiry("답변 기다리는 건", false);
            Long answered = registerInquiry("답변 받은 건", false);
            answer(answered, "답변");
            Long requested = registerInquiry("삭제 요청된 건", false);
            requestDelete(requested, "ABUSE", null);

            mockMvc.perform(get(ADMIN_LIST).header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCounts.all").value(3))
                    .andExpect(jsonPath("$.statusCounts.waiting").value(1))
                    .andExpect(jsonPath("$.statusCounts.answered").value(1))
                    .andExpect(jsonPath("$.statusCounts.deleteRequested").value(1))
                    .andExpect(jsonPath("$.statusCounts.deleted").value(0))
                    .andExpect(jsonPath("$.deleteRequestedCount").value(1));
        }

        /** 반려로 노출이 정상으로 돌아오면 원래 답변 축의 탭으로 복귀한다. */
        @Test
        @DisplayName("반려하면 답변대기 탭으로 돌아온다")
        void rejectedInquiryReturnsToWaitingTab() throws Exception {
            Long inquiryId = registerInquiry("삭제 요청된 건", false);
            requestDelete(inquiryId, "ABUSE", null);

            reject(inquiryId, "NORMAL_INQUIRY", null);

            mockMvc.perform(get(ADMIN_LIST).header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(jsonPath("$.statusCounts.waiting").value(1))
                    .andExpect(jsonPath("$.statusCounts.deleteRequested").value(0));
        }

        @Test
        @DisplayName("집행하면 삭제 탭으로 옮겨간다")
        void executedInquiryMovesToDeletedTab() throws Exception {
            Long inquiryId = registerInquiry("부적절한 내용", false);

            execute(inquiryId, "ABUSE", null);

            mockMvc.perform(get(ADMIN_LIST).header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(jsonPath("$.statusCounts.all").value(1))
                    .andExpect(jsonPath("$.statusCounts.waiting").value(0))
                    .andExpect(jsonPath("$.statusCounts.deleted").value(1));
        }

        /** 배지가 0이 되지 않으면 운영자가 처리할 건이 없는데도 계속 알림이 남는다. */
        @Test
        @DisplayName("삭제 요청 배지는 처리하면 0으로 돌아간다")
        void deleteRequestBadgeClearsAfterDecision() throws Exception {
            Long inquiryId = registerInquiry("삭제 요청된 건", false);
            requestDelete(inquiryId, "ABUSE", null);

            mockMvc.perform(get(ADMIN_LIST + "/summary").header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(jsonPath("$.deleteRequestedCount").value(1));

            reject(inquiryId, "NORMAL_INQUIRY", null);

            mockMvc.perform(get(ADMIN_LIST + "/summary").header(HttpHeaders.AUTHORIZATION, operatorToken))
                    .andExpect(jsonPath("$.deleteRequestedCount").value(0));
        }
    }

    // ------------------------------------------------------------------ 단계 (steps)

    private Long registerInquiry(String content, boolean secret) throws Exception {
        MvcResult result = mockMvc.perform(post(CONSUMER_REGISTER.formatted(productId))
                        .header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "type", "RESTOCK",
                                "content", content,
                                "secret", secret))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("inquiryId").asLong();
    }

    private void answer(Long inquiryId, String answerContent) throws Exception {
        mockMvc.perform(post(SELLER_ANSWER.formatted(inquiryId))
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("answerContent", answerContent))))
                .andExpect(status().isNoContent());
    }

    private void requestDelete(Long inquiryId, String reason, String detail) throws Exception {
        mockMvc.perform(post(SELLER_DELETE_REQUEST.formatted(inquiryId))
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body(reason, detail))))
                .andExpect(status().isNoContent());
    }

    private void reject(Long inquiryId, String reason, String detail) throws Exception {
        mockMvc.perform(post(ADMIN_REJECT.formatted(inquiryId))
                        .header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body(reason, detail))))
                .andExpect(status().isNoContent());
    }

    private void execute(Long inquiryId, String reason, String detail) throws Exception {
        mockMvc.perform(post(ADMIN_EXECUTE.formatted(inquiryId))
                        .header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body(reason, detail))))
                .andExpect(status().isNoContent());
    }

    private Map<String, Object> body(String reason, String detail) {
        return detail == null ? Map.of("reason", reason) : Map.of("reason", reason, "detail", detail);
    }

    // ------------------------------------------------------------------ 픽스처

    private Users createConsumer(String username) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(new Users(
                username, "미아", username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now));
    }

    private Long createProduct(BrandFixture.Brand brand, String name) {
        Category category = new Category();
        category.setName("스킨케어");
        category.setOrder(1);
        categoryRepository.save(category);

        Product product = new Product();
        product.setMarket(brand.market());
        product.setCategory(category);
        product.setName(name);
        product.setRegularPrice(32000);
        product.setSalePrice(24000);
        product.setThumbnailUrl("https://cdn.showroomz.test/toner.jpg");
        return productRepository.save(product).getProductId();
    }
}

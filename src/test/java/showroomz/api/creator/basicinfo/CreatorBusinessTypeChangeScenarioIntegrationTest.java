package showroomz.api.creator.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.changerequest.service.CreatorBusinessTypeChangeApplier;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.entity.ChangeRequestItem;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorAdminHistory;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.service.CreatorAdminHistoryRecorder;
import showroomz.domain.member.common.service.MemberNotifier;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentType;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인플루언서 사업자 구분 변경 — 테스트 시나리오(`dev/브랜드, 쇼룸 정보/브랜드 정보 변경/06-16_브랜드정보변경_테스트_시나리오.md`) 보완분.
 * 기본 흐름(BT-01 ~ BT-09 · C3a · C5b)은 {@link CreatorBusinessTypeChangeIntegrationTest}가 보고, 여기서는 그 문서 12절에서 비어 있던
 * 경계 · 권한 · 요청자가 섞인 큐 · 대조표 행 값 · 승인 전후 정산 스냅샷 · 동시 처리 · 개인정보를 본다. 판정은 16 설계서 본문이 아니라
 * 구현 계획서 2-2 정본을 반영한 코드 기준이다(시나리오 1-3).
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("사업자 구분 변경 — 시나리오 보완(접수 경계 · 큐 · 대조표 · 승인 전후 정산 · 동시 처리 · 회귀 · 개인정보)")
class CreatorBusinessTypeChangeScenarioIntegrationTest extends SettlementTestSupport {

    private static final String STUDIO = "/v1/creator/basic-info";
    private static final String REQUESTS = STUDIO + "/business-type-change-requests";
    private static final String QUEUE = "/v1/admin/change-requests";
    private static final String RESIDENT = "950812-2345678";
    private static final String RESIDENT_BACK = "2345678";
    private static final String EVIDENCE_URL = "https://cdn.showroomz.test/creator-document/evidence.pdf";
    private static final long TEN_MB = 10L * 1024 * 1024;

    @Autowired private ChangeRequestRepository brandChangeRequests;
    @Autowired private CreatorAdminHistoryRepository adminHistories;
    @Autowired private SettlementTaxDocumentRepository taxDocuments;

    @MockitoSpyBean private MemberNotifier notifier;
    @MockitoSpyBean private CreatorBusinessTypeChangeApplier applier;
    @MockitoSpyBean private CreatorAdminHistoryRecorder historyRecorder;

    // ================================================================== 5-1 생성

    @Nested
    @DisplayName("스튜디오 접수 — 경계 · 정규화 · 상태 · 권한")
    class Create {

        @Test
        @DisplayName("[BT-01] 행 · 항목 — 요청자 CREATOR · market/담당자/사유 NULL · 항목 2개는 현재 값 서버 스냅샷 + 순서")
        void storedShape() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));

            Map<String, Object> row = jdbc.queryForMap("SELECT requester_type, market_id, creator_id, requester_name, reason "
                    + "FROM brand_change_request WHERE request_id = ?", id);
            assertThat(row.get("requester_type")).isEqualTo("CREATOR");
            assertThat(row.get("market_id")).isNull();
            assertThat(((Number) row.get("creator_id")).longValue()).isEqualTo(creator.getId());
            assertThat(row.get("requester_name")).isNull();
            assertThat(row.get("reason")).isNull();
            assertThat(items(id)).containsExactly(
                    "CREATOR_BUSINESS_TYPE|INDIVIDUAL|BUSINESS|0",
                    "CREATOR_BUSINESS_REG_NUMBER|null|214-37-55120|1");
        }

        @Test
        @DisplayName("[ST-01 · BT-02 ② ③] 방향 미선택 400 · 번호 생략 400 · 앞뒤 공백은 지워 저장 · 증빙 빈 문자열 400")
        void inputBoundaries() throws Exception {
            Map<String, Object> noTarget = body(null);
            noTarget.put("businessRegistrationNumber", "214-37-55120");
            create(creatorToken, noTarget).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("바꿀 사업자 구분을 선택해 주세요."));

            create(creatorToken, body("BUSINESS")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("올바른 사업자등록번호 형식으로 입력해 주세요. (예: 000-00-00000)"));

            for (String field : List.of("evidenceFileUrl", "evidenceFileName")) {
                Map<String, Object> blank = body("BUSINESS");
                blank.put("businessRegistrationNumber", "214-37-55120");
                blank.put(field, " ");
                create(creatorToken, blank).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_EVIDENCE_REQUIRED"));
            }
            Map<String, Object> noSize = body("BUSINESS");
            noSize.put("businessRegistrationNumber", "214-37-55120");
            noSize.remove("evidenceFileSize");
            create(creatorToken, noSize).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_EVIDENCE_REQUIRED"));
            assertThat(creatorRows()).isZero();

            long id = created(toBusiness(creatorToken, "  214-37-55120  "));
            assertThat(items(id)).contains("CREATOR_BUSINESS_REG_NUMBER|null|214-37-55120|1");
        }

        @Test
        @DisplayName("[ST-02] 주민번호 형식 오류(12자리 · 13월 · 32일 · 성별 9 · 생략) — 400 · 응답 · 로그에 입력값이 없다")
        void residentNumberFormat(CapturedOutput output) throws Exception {
            makeBusinessCreator();
            List<String> invalid = new ArrayList<>(List.of("950812-234567", "951312-2345678", "950832-2345678", "950812-9345678"));
            invalid.add(null);
            for (String rrn : invalid) {
                String response = toIndividual(creatorToken, rrn).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                        .andExpect(jsonPath("$.message").value("주민등록번호를 정확히 입력해 주세요."))
                        .andReturn().getResponse().getContentAsString();
                if (rrn != null) {
                    assertThat(response).as(rrn).doesNotContain(rrn.substring(7));
                }
            }
            assertThat(creatorRows()).isZero();
            assertThat(output.getAll()).doesNotContain("234567").doesNotContain("9345678");
        }

        @Test
        @DisplayName("[ST-03] 주민번호 하이픈 · 공백은 정규화 — 암호문 복호 = 13자리 · 항목 = 성별 자리까지 가린 마스킹")
        void residentNumberNormalized() throws Exception {
            makeBusinessCreator();
            long id = created(toIndividual(creatorToken, "9508122345678"));
            assertThat(personalDataCipher.decrypt(request(id).getResidentNumberEnc())).isEqualTo("9508122345678");
            assertThat(items(id)).contains("CREATOR_RESIDENT_NUMBER|null|950812-*******|1");

            postAs(REQUESTS + "/" + id + "/cancel", creatorToken).andExpect(status().isNoContent());
            long spaced = created(toIndividual(creatorToken, "950812 2345678"));
            assertThat(personalDataCipher.decrypt(request(spaced).getResidentNumberEnc())).isEqualTo("9508122345678");
        }

        @Test
        @DisplayName("[ST-04] 증빙 10MB 는 통과 · 10MB + 1byte 는 413 FILE_SIZE_EXCEEDED")
        void evidenceSizeLimit() throws Exception {
            Map<String, Object> over = body("BUSINESS");
            over.put("businessRegistrationNumber", "214-37-55120");
            over.put("evidenceFileSize", TEN_MB + 1);
            create(creatorToken, over).andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.code").value("FILE_SIZE_EXCEEDED"))
                    .andExpect(jsonPath("$.message").value("파일 크기가 10MB를 초과했습니다. 10MB 이하의 파일을 선택해 주세요."));
            assertThat(creatorRows()).isZero();

            over.put("evidenceFileSize", TEN_MB);
            create(creatorToken, over).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("[ST-05 · ST-06] 탈퇴 회원은 409 CREATOR_WITHDRAWN(형식 검사보다 먼저) · 정지 회원은 접수된다")
        void requesterStatus() throws Exception {
            jdbc.update("UPDATE creator SET status = 'WITHDRAWN' WHERE creator_id = ?", creator.getId());
            toBusiness(creatorToken, "214-37-55120").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
            toBusiness(creatorToken, "bad").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
            assertThat(creatorRows()).isZero();

            jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id = ?", creator.getId());
            toBusiness(creatorToken, "214-37-55120").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("[ST-07] 옛 형식 마스킹(900101-1******)이 저장된 인플루언서 — 항목 현재 값은 성별 자리까지 가린 표기로 스냅샷")
        void legacyMaskSnapshot() throws Exception {
            makeBusinessCreator();
            jdbc.update("UPDATE creator SET resident_registration_number_masked = '900101-1******' WHERE creator_id = ?",
                    creator.getId());

            long id = created(toIndividual(creatorToken, RESIDENT));

            assertThat(items(id)).contains("CREATOR_RESIDENT_NUMBER|900101-*******|950812-*******|1");
        }

        @Test
        @DisplayName("[ST-08] 요청 코드는 브랜드 · 인플루언서가 같은 연도 순번을 쓴다")
        void sharedRequestCode() throws Exception {
            String prefix = "CHG-" + Year.now().getValue() + "-";
            long brandRequest = brandBusinessInfoRequest();
            long creatorRequest = created(toBusiness(creatorToken, "214-37-55120"));

            assertThat(request(brandRequest).getRequestCode()).isEqualTo(prefix + "0001");
            assertThat(request(creatorRequest).getRequestCode()).isEqualTo(prefix + "0002");
        }

        @Test
        @DisplayName("[ST-10] 브랜드 · 소비자 토큰은 403 · 토큰 없음은 401 — 행이 생기지 않는다")
        void authorization() throws Exception {
            Map<String, Object> body = body("BUSINESS");
            body.put("businessRegistrationNumber", "214-37-55120");

            create(brandToken, body).andExpect(status().isForbidden());
            create(consumerToken, body).andExpect(status().isForbidden());
            mockMvc.perform(post(REQUESTS).contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                    .andExpect(status().isUnauthorized());
            assertThat(creatorRows()).isZero();
        }
    }

    // ================================================================== 5-2 · 5-3 취소 · 확인

    @Nested
    @DisplayName("스튜디오 취소 · 결과 확인")
    class CancelAndAcknowledge {

        @Test
        @DisplayName("[ST-11 · ST-14] 처리 끝난 요청은 취소 400 · 취소 뒤에는 같은 방향으로 새 행이 열린다")
        void cancelOnlyPending() throws Exception {
            long rejected = created(toBusiness(creatorToken, "214-37-55120"));
            reject(rejected, "LICENSE_UNREADABLE").andExpect(status().isOk());
            postAs(REQUESTS + "/" + rejected + "/cancel", creatorToken).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));

            long pending = created(toBusiness(creatorToken, "214-37-55120"));
            postAs(REQUESTS + "/" + pending + "/cancel", creatorToken).andExpect(status().isNoContent());
            postAs(REQUESTS + "/" + pending + "/cancel", creatorToken).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
            ChangeRequest canceled = request(pending);
            assertThat(canceled.getProcessedAt()).isNotNull();
            assertThat(canceled.getProcessedBy()).isNull();

            long reopened = created(toBusiness(creatorToken, "214-37-55120"));
            assertThat(reopened).isNotEqualTo(pending);
            assertThat(events(creator.getId())).containsExactly(CreatorAdminEventType.BUSINESS_TYPE_CHANGE_REJECTED);
        }

        @Test
        @DisplayName("[ST-12 · ST-13 · ST-17] 남의 요청 · 브랜드 요청 id 는 403 · 없는 id 는 404")
        void ownership() throws Exception {
            Creator other = createCreator("뷰티_예린", "yerin");
            long othersRequest = created(toBusiness(tokenOf(other), "214-37-55120"));
            reject(othersRequest, "LICENSE_UNREADABLE").andExpect(status().isOk());
            long brandRequest = brandBusinessInfoRequest();

            for (long id : List.of(othersRequest, brandRequest)) {
                postAs(REQUESTS + "/" + id + "/cancel", creatorToken).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ACCESS_DENIED"));
                postAs(REQUESTS + "/" + id + "/acknowledge", creatorToken).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ACCESS_DENIED"));
            }
            postAs(REQUESTS + "/999999/cancel", creatorToken).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_FOUND"));
            postAs(REQUESTS + "/999999/acknowledge", creatorToken).andExpect(status().isNotFound());
            assertThat(request(brandRequest).getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
        }

        @Test
        @DisplayName("[ST-15 · ST-16] 대기 · 취소 건 확인은 400 · 처리 건 확인은 두 번 눌러도 첫 시각 그대로")
        void acknowledge() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));
            postAs(REQUESTS + "/" + id + "/acknowledge", creatorToken).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("처리가 끝난 요청만 확인할 수 있습니다."));
            postAs(REQUESTS + "/" + id + "/cancel", creatorToken).andExpect(status().isNoContent());
            postAs(REQUESTS + "/" + id + "/acknowledge", creatorToken).andExpect(status().isBadRequest());

            long approved = created(toBusiness(creatorToken, "214-37-55120"));
            approve(approved).andExpect(status().isOk());
            postAs(REQUESTS + "/" + approved + "/acknowledge", creatorToken).andExpect(status().isNoContent());
            LocalDateTime first = request(approved).getResultAcknowledgedAt();
            postAs(REQUESTS + "/" + approved + "/acknowledge", creatorToken).andExpect(status().isNoContent());
            assertThat(first).isNotNull();
            assertThat(request(approved).getResultAcknowledgedAt()).isEqualTo(first);
        }
    }

    // ================================================================== 5-4 정산 정보 탭

    @Nested
    @DisplayName("스튜디오 정산 정보 탭 — 배너 · 버튼 · 등록증")
    class SettlementTab {

        @Test
        @DisplayName("[ST-18] 요청이 없으면 배너 없음 · 버튼은 현재 구분의 반대 방향")
        void noRequest() throws Exception {
            creatorGet(STUDIO + "/settlement").andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessTypeChange").doesNotExist())
                    .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"));
            makeBusinessCreator();
            creatorGet(STUDIO + "/settlement")
                    .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_INDIVIDUAL"));
        }

        @Test
        @DisplayName("[ST-19 · ST-20] 대기 배너 — 등록번호 · 증빙 메타 · 계좌 변경 플래그 false / 비사업자 전환은 마스킹 주민번호만")
        void pendingBanner() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));
            creatorGet(STUDIO + "/settlement")
                    .andExpect(jsonPath("$.businessTypeChange.requestId").value(id))
                    .andExpect(jsonPath("$.businessTypeChange.requestCode").value(request(id).getRequestCode()))
                    .andExpect(jsonPath("$.businessTypeChange.type").value("TO_BUSINESS"))
                    .andExpect(jsonPath("$.businessTypeChange.evidence.url").value(EVIDENCE_URL))
                    .andExpect(jsonPath("$.businessTypeChange.evidence.fileSize").value(1_258_291))
                    .andExpect(jsonPath("$.businessTypeChange.evidence.submittedAt").isNotEmpty())
                    .andExpect(jsonPath("$.businessTypeChange.rejectReason").doesNotExist())
                    .andExpect(jsonPath("$.businessTypeChange.accountChangeRequiredOnApproval").value(false));
            postAs(REQUESTS + "/" + id + "/cancel", creatorToken).andExpect(status().isNoContent());

            makeBusinessCreator();
            created(toIndividual(creatorToken, RESIDENT));
            String body = creatorGet(STUDIO + "/settlement")
                    .andExpect(jsonPath("$.businessTypeChange.type").value("TO_INDIVIDUAL"))
                    .andExpect(jsonPath("$.businessTypeChange.maskedResidentNumber").value("950812-*******"))
                    .andExpect(jsonPath("$.businessTypeChange.businessRegistrationNumber").doesNotExist())
                    .andExpect(jsonPath("$.actions.businessTypeChange").value("CANCEL"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(RESIDENT_BACK);
        }

        @Test
        @DisplayName("[ST-23 · ST-24] 비사업자 전환 승인 뒤 등록증 없음 · 다시 사업자 전환하면 등록증 = 가장 최근 승인 증빙 · 확정일 = 마지막 승인")
        void licenseFollowsLatestApproval() throws Exception {
            long first = created(toBusiness(creatorToken, "214-37-55120", "등록증_첫번째.pdf"));
            approve(first).andExpect(status().isOk());

            long back = created(toIndividual(creatorToken, RESIDENT));
            approve(back).andExpect(status().isOk());
            creatorGet(STUDIO + "/settlement")
                    .andExpect(jsonPath("$.businessType.value").value("INDIVIDUAL"))
                    .andExpect(jsonPath("$.business.license").doesNotExist())
                    .andExpect(jsonPath("$.businessTypeChange.status").value("APPROVED"))
                    .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"));

            long second = created(toBusiness(creatorToken, "214-37-55121", "등록증_두번째.pdf"));
            approve(second).andExpect(status().isOk());
            JsonNode tab = json(creatorGet(STUDIO + "/settlement"));
            assertThat(tab.at("/business/license/fileName").asText()).isEqualTo("등록증_두번째.pdf");
            // 전역 직렬화 형식(JacksonConfig) — 초 단위 + 문자 'Z'(시간대 변환 없음).
            LocalDateTime confirmedAt = LocalDateTime.parse(tab.at("/businessType/confirmedAt").asText().replace("Z", ""));
            assertThat(confirmedAt).isEqualTo(request(second).getProcessedAt().withNano(0));
            assertThat(confirmedAt).isAfterOrEqualTo(request(first).getProcessedAt().withNano(0));
        }

        @Test
        @DisplayName("[ST-25] 다른 인플루언서의 요청은 내 정산 정보에 보이지 않는다")
        void othersBannerHidden() throws Exception {
            Creator other = createCreator("뷰티_예린", "yerin");
            created(toBusiness(tokenOf(other), "214-37-55120"));

            creatorGet(STUDIO + "/settlement").andExpect(jsonPath("$.businessTypeChange").doesNotExist())
                    .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"));
        }
    }

    // ================================================================== 6 어드민 목록

    @Nested
    @DisplayName("어드민 목록 — 요청자가 섞인 정렬 · 건수 · 검색 · 배지 · 반려 사유")
    class Queue {

        private long brandInfo;
        private long creatorPending;
        private long brandAccount;
        private long creatorApproved;

        /** 브랜드 사업자 정보(72h 전) · 지민 사업자 전환(49h 전) · 브랜드 정산 계좌(24h 전) · 예린 승인 1건. */
        @BeforeEach
        void mixedQueue() throws Exception {
            fixture.createBank("004", "국민은행");
            brandInfo = brandBusinessInfoRequest();
            creatorPending = created(toBusiness(creatorToken, "214-37-55120"));
            brandAccount = changeRequests.createSettlement(brandToken, "004", "1234567890123", "글로우랩");
            Creator other = createCreator("뷰티_예린", "yerin");
            creatorApproved = created(toBusiness(tokenOf(other), "214-37-55199"));
            approve(creatorApproved).andExpect(status().isOk());

            LocalDateTime now = LocalDateTime.now();
            fixture.backdateRequestedAt(brandInfo, now.minusHours(72));
            fixture.backdateRequestedAt(creatorPending, now.minusHours(49));
            fixture.backdateRequestedAt(brandAccount, now.minusHours(24));
        }

        @Test
        @DisplayName("[AL-01 · AL-02 · AL-03] 대기 건은 요청자 무관 하나의 정렬(오래 기다린 순) · 행 모양 · SLA 48h")
        void orderingAndRows() throws Exception {
            JsonNode pending = json(adminGet(QUEUE + "?status=PENDING").andExpect(status().isOk()));
            assertThat(ids(pending)).containsExactly(brandInfo, creatorPending, brandAccount);
            JsonNode creatorRow = pending.at("/content/1");
            assertThat(creatorRow.at("/requester/type").asText()).isEqualTo("CREATOR");
            assertThat(creatorRow.at("/requester/id").asLong()).isEqualTo(creator.getId());
            assertThat(creatorRow.at("/requester/name").asText()).isEqualTo("글로우_지민");
            assertThat(creatorRow.has("brandName") && !creatorRow.get("brandName").isNull()).isFalse();
            assertThat(creatorRow.at("/slaExceeded").asBoolean()).isTrue();
            assertThat(creatorRow.at("/elapsedText").asText()).isNotBlank();
            JsonNode brandRow = pending.at("/content/0");
            assertThat(brandRow.at("/requester/type").asText()).isEqualTo("BRAND");
            assertThat(brandRow.at("/requester/id").asLong()).isEqualTo(brand.marketId());
            assertThat(brandRow.at("/brandName").asText()).isEqualTo("글로우랩");
            assertThat(pending.at("/content/2/slaExceeded").asBoolean()).isFalse();

            JsonNode all = json(adminGet(QUEUE + "?status=ALL"));
            assertThat(ids(all)).containsExactly(brandInfo, creatorPending, brandAccount, creatorApproved);
            assertThat(all.at("/content/3/elapsedText").isMissingNode() || all.at("/content/3/elapsedText").isNull()).isTrue();
            assertThat(all.at("/content/3/slaExceeded").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("[AL-05 · AL-06 · AL-07] 탭 건수 · GNB 배지에 인플루언서 건 포함 · 탭 필터는 요청자 무관")
        void countsAndFilters() throws Exception {
            adminGet(QUEUE).andExpect(jsonPath("$.statusCounts.pending").value(3))
                    .andExpect(jsonPath("$.statusCounts.approved").value(1))
                    .andExpect(jsonPath("$.statusCounts.all").value(4));
            adminGet(QUEUE + "/summary").andExpect(jsonPath("$.pendingCount").value(3));
            assertThat(ids(json(adminGet(QUEUE + "?status=APPROVED")))).containsExactly(creatorApproved);
            assertThat(ids(json(adminGet(QUEUE + "?status=REJECTED")))).isEmpty();
        }

        @Test
        @DisplayName("[AL-04] 검색 — 쇼룸명만 맞으면 인플루언서만 · 브랜드명만 맞으면 브랜드만 · 둘 다면 둘 다 · 건수도 같은 조건")
        void keywordSearch() throws Exception {
            assertThat(ids(json(adminGet(QUEUE + "?status=ALL&keyword=지민")))).containsExactly(creatorPending);
            assertThat(ids(json(adminGet(QUEUE + "?status=ALL&keyword=예린")))).containsExactly(creatorApproved);
            assertThat(ids(json(adminGet(QUEUE + "?status=ALL&keyword=랩")))).containsExactly(brandInfo, brandAccount);
            JsonNode both = json(adminGet(QUEUE + "?status=ALL&keyword=글로우"));
            assertThat(ids(both)).containsExactly(brandInfo, creatorPending, brandAccount);
            assertThat(both.at("/statusCounts/pending").asLong()).isEqualTo(3);
            assertThat(both.at("/statusCounts/approved").asLong()).isZero();
            adminGet(QUEUE + "?status=ALL&keyword=지민").andExpect(jsonPath("$.statusCounts.pending").value(1))
                    .andExpect(jsonPath("$.statusCounts.all").value(1));
        }

        @Test
        @DisplayName("[AD-35] 이전 · 다음은 요청자를 넘나든다 — 인플루언서 건 양옆이 브랜드 건")
        void prevNextAcrossRequesters() throws Exception {
            adminGet(QUEUE + "/" + creatorPending).andExpect(status().isOk())
                    .andExpect(jsonPath("$.prevRequestId").value(brandInfo))
                    .andExpect(jsonPath("$.nextRequestId").value(brandAccount));
            adminGet(QUEUE + "/" + brandAccount).andExpect(jsonPath("$.prevRequestId").value(creatorPending));
        }
    }

    @Test
    @DisplayName("[AL-08 · AL-09] 반려 사유 — 두 방향 같은 6종 · 시안 C8b 표기 · 기타만 상세 필수 · 브랜드 목록은 그대로")
    void rejectReasons() throws Exception {
        for (String type : List.of("TO_BUSINESS", "TO_INDIVIDUAL")) {
            JsonNode reasons = json(adminGet(QUEUE + "/reject-reasons?type=" + type).andExpect(status().isOk()));
            assertThat(StreamSupport.stream(reasons.spliterator(), false)
                    .map(r -> r.get("code").asText() + "|" + r.get("label").asText() + "|" + r.get("detailRequired").asBoolean())
                    .toList()).as(type).containsExactly(
                    "LICENSE_UNREADABLE|사업자등록증 판독 불가 (흐림·잘림)|false",
                    "LICENSE_OWNER_NAME_MISMATCH|등록증의 대표자명이 본인 실명과 다름|false",
                    "BUSINESS_NUMBER_MISMATCH|사업자등록번호가 등록증과 다름|false",
                    "BUSINESS_SUSPENDED_OR_CLOSED|휴업 · 폐업 상태의 사업자|false",
                    "CLOSURE_NOT_CONFIRMED|폐업 사실이 확인되지 않음 (비사업자 전환)|false",
                    "OTHER|기타|true");
        }
        adminGet(QUEUE + "/reject-reasons?type=BUSINESS_INFO").andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("EVIDENCE_MISSING"));
        adminGet(QUEUE + "/reject-reasons?type=SETTLEMENT_ACCOUNT").andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("[AL-10] 인플루언서 토큰은 어드민 큐에 403")
    void creatorTokenCannotUseQueue() throws Exception {
        long id = created(toBusiness(creatorToken, "214-37-55120"));
        mockMvc.perform(get(QUEUE).header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isForbidden());
        postAs(QUEUE + "/" + id + "/approve", creatorToken).andExpect(status().isForbidden());
        assertThat(request(id).getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
    }

    // ================================================================== 7 어드민 상세

    @Nested
    @DisplayName("어드민 상세 — 대조표 행 값 · 유지 정산 집합 · 생년월일 대조 · 처리 후 축소")
    class Detail {

        @Test
        @DisplayName("[AD-01 ~ AD-08] 사업자 전환 — 6행 값 전부 · 참고 항목 · 라벨 · 증빙 · 「승인하면」 유지 정산 = 같은 구분 ∧ 미지급(실패 포함)")
        void toBusinessDetail() throws Exception {
            registerCreatorAccount();
            Settlement reviewing = seedSettlement("A 공구", 1_000_000, SettlementStatus.REVIEWING);
            Settlement adjusting = seedSettlement("B 공구", 1_000_000, SettlementStatus.ADJUSTING);
            Settlement scheduled = seedSettlement("C 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
            Settlement failed = seedSettlement("D 공구", 1_000_000, SettlementStatus.PAYOUT_FAILED);
            seedSettlement("E 공구", 1_000_000, SettlementStatus.PAID);
            Settlement otherSnapshot = seedSettlement("F 공구", 1_000_000, SettlementStatus.REVIEWING);
            jdbc.update("UPDATE settlement SET creator_business_type = 'BUSINESS' WHERE settlement_id = ?", otherSnapshot.getId());
            long id = created(toBusiness(creatorToken, "214-37-55120"));

            JsonNode d = json(adminGet(QUEUE + "/" + id).andExpect(status().isOk()));
            assertThat(d.at("/brandName").isMissingNode() || d.at("/brandName").isNull()).isTrue();
            assertThat(d.at("/marketId").isMissingNode() || d.at("/marketId").isNull()).isTrue();
            assertThat(d.at("/reason").isMissingNode() || d.at("/reason").isNull()).isTrue();
            assertThat(rows(d.get("diff"))).containsExactly(
                    "CREATOR_BUSINESS_TYPE|개인 (비사업자)|개인사업자|true|false|REQUEST",
                    "CREATOR_BUSINESS_REG_NUMBER|null|214-37-55120|true|false|REQUEST",
                    "LICENSE_COMPANY_REPRESENTATIVE|null|null|false|false|DOCUMENT",
                    "PAYOUT_METHOD|원천징수 3.3%|세금계산서 · 리워드 + 부가세 10%|true|false|DERIVED",
                    "ACCOUNT_HOLDER|김지민|null|false|false|CHECK",
                    "SETTLEMENT_ACCOUNT|" + d.at("/diff/5/currentValue").asText() + "|null|false|false|LIVE");
            assertThat(d.at("/diff/5/currentValue").asText()).startsWith("국민은행 ");
            assertThat(d.toString()).doesNotContain("123456789012");
            assertThat(d.at("/holderCheck/requestedHolder").isMissingNode() || d.at("/holderCheck/requestedHolder").isNull()).isTrue();
            assertThat(d.at("/compareItems/2/key").asText()).isEqualTo("OPENING_DATE");
            assertThat(d.at("/compareItems/2/mustBeBefore").asText()).isNotBlank();
            assertThat(d.at("/compareItems").size()).isEqualTo(3);
            assertThat(d.at("/referenceItems").size()).isEqualTo(1);
            assertThat(d.at("/referenceItems/0/label").asText()).isEqualTo("정산 계좌 예금주");
            assertThat(d.at("/changedFieldLabels/0").asText()).isEqualTo("사업자 구분");
            assertThat(d.at("/changedFieldLabels/1").asText()).isEqualTo("사업자등록번호");
            assertThat(d.at("/evidence/extension").asText()).isEqualTo("pdf");
            assertThat(d.at("/evidence/fileUrl").asText()).isEqualTo(EVIDENCE_URL);
            assertThat(d.at("/approvalImpact/accountHolderRuleAfter").asText()).isEqualTo("REPRESENTATIVE_NAME");

            List<Long> retained = new ArrayList<>();
            d.at("/approvalImpact/retainedSettlements").forEach(s -> retained.add(s.get("settlementId").asLong()));
            assertThat(retained).containsExactly(reviewing.getId(), adjusting.getId(), scheduled.getId(), failed.getId());
            assertThat(d.at("/approvalImpact/retainedSettlements/3/status").asText()).isEqualTo("PAYOUT_FAILED");
        }

        @Test
        @DisplayName("[AD-11 ~ AD-14] 비사업자 전환 — 6행 값 · 실명 규칙 · 대조 값 4행 · 참고 항목 2개 · 지급 방식 반대 방향")
        void toIndividualDetail() throws Exception {
            makeBusinessCreator();
            registerCreatorAccount();
            jdbc.update("UPDATE creator SET birthday = '1995-08-12' WHERE creator_id = ?", creator.getId());
            long id = created(toIndividual(creatorToken, RESIDENT));

            JsonNode d = json(adminGet(QUEUE + "/" + id).andExpect(status().isOk()));
            assertThat(rows(d.get("diff"))).containsExactly(
                    "CREATOR_BUSINESS_TYPE|개인사업자|개인 (비사업자)|true|false|REQUEST",
                    "CREATOR_BUSINESS_REG_NUMBER|312-81-40177|폐업|true|false|DERIVED",
                    "CREATOR_RESIDENT_NUMBER|null|950812-*******|true|false|REQUEST",
                    "PAYOUT_METHOD|세금계산서 · 리워드 + 부가세 10%|원천징수 3.3%|true|false|DERIVED",
                    "ACCOUNT_HOLDER|김지민|null|false|false|CHECK",
                    "SETTLEMENT_ACCOUNT|" + d.at("/diff/5/currentValue").asText() + "|null|false|false|LIVE");
            assertThat(d.at("/holderCheck/rule").asText()).isEqualTo("REAL_NAME");
            assertThat(d.at("/approvalImpact/payoutMethodBefore").asText()).isEqualTo("TAX_INVOICE");
            assertThat(d.at("/approvalImpact/payoutMethodAfter").asText()).isEqualTo("WITHHOLDING_3_3");
            assertThat(d.at("/approvalImpact/accountHolderRuleAfter").asText()).isEqualTo("REAL_NAME");
            List<String> compare = new ArrayList<>();
            d.get("compareItems").forEach(c -> compare.add(c.get("key").asText() + "|" + c.path("value").asText("null")));
            assertThat(compare).containsExactly("REPRESENTATIVE_NAME|김지민", "BIRTHDAY|1995-08-12",
                    "BUSINESS_REG_NUMBER|312-81-40177", "CLOSURE_DATE|null");
            assertThat(d.at("/compareItems/3/mustBeBefore").asText()).isNotBlank();
            assertThat(d.at("/referenceItems/1/label").asText()).isEqualTo("정산 계좌 예금주");
            assertThat(d.at("/changedFieldLabels/1").asText()).isEqualTo("주민등록번호");
            assertThat(d.toString()).doesNotContain(RESIDENT_BACK);
        }

        @Test
        @DisplayName("[AD-23 · AD-24 · AD-25] 생년월일 대조 — 세기 판정 · 값 없음/형식 다름 null · 암호문이 깨져도 200 + null")
        void birthdayCheckEdges() throws Exception {
            makeBusinessCreator();
            jdbc.update("UPDATE creator SET birthday = '2001-03-04' WHERE creator_id = ?", creator.getId());
            long born2000s = created(toIndividual(creatorToken, "010304-3234567"));
            assertThat(birthdayCheck(born2000s)).isEqualTo("MATCH");
            cancel(born2000s);
            long born1900s = created(toIndividual(creatorToken, "010304-1234567"));
            assertThat(birthdayCheck(born1900s)).isEqualTo("MISMATCH");

            jdbc.update("UPDATE creator SET birthday = NULL WHERE creator_id = ?", creator.getId());
            assertThat(birthdayCheck(born1900s)).isNull();
            jdbc.update("UPDATE creator SET birthday = '1995' WHERE creator_id = ?", creator.getId());
            assertThat(birthdayCheck(born1900s)).isNull();

            jdbc.update("UPDATE creator SET birthday = '2001-03-04' WHERE creator_id = ?", creator.getId());
            jdbc.update("UPDATE brand_change_request SET resident_number_enc = 'broken-cipher' WHERE request_id = ?", born1900s);
            assertThat(birthdayCheck(born1900s)).isNull();
        }

        @Test
        @DisplayName("[AD-26 · AD-31] 반려 뒤 — 실명 · 생년월일 마스킹 · 생년월일 대조 null · 「승인하면」 없음 · 이력 접수 → 반려(운영자 이름)")
        void afterRejection() throws Exception {
            makeBusinessCreator();
            registerCreatorAccount();
            jdbc.update("UPDATE creator SET birthday = '1995-08-12' WHERE creator_id = ?", creator.getId());
            long id = created(toIndividual(creatorToken, RESIDENT));
            reject(id, "CLOSURE_NOT_CONFIRMED").andExpect(status().isOk());

            JsonNode d = json(adminGet(QUEUE + "/" + id + "?status=ALL").andExpect(status().isOk()));
            assertThat(d.at("/holderCheck/currentHolder").asText()).isEqualTo("김*민");
            assertThat(d.at("/compareItems/0/value").asText()).isEqualTo("김*민");
            assertThat(d.at("/compareItems/1/value").asText()).isEqualTo("1995.**.**");
            assertThat(d.at("/compareItems/1/serverCheck").isMissingNode() || d.at("/compareItems/1/serverCheck").isNull()).isTrue();
            assertThat(d.at("/approvalImpact").isMissingNode() || d.at("/approvalImpact").isNull()).isTrue();
            assertThat(d.at("/diff/2/requestedValue").asText()).isEqualTo("950812-*******");
            assertThat(d.at("/history").size()).isEqualTo(2);
            assertThat(d.at("/history/0/actorLabel").asText()).isEqualTo("쇼룸 스튜디오");
            assertThat(d.at("/history/1/event").asText()).isEqualTo("REJECTED");
            assertThat(d.at("/history/1/actorLabel").asText()).isEqualTo("김운영");
        }

        @Test
        @DisplayName("[AD-32 · AD-33 · AD-34] 승인 뒤 대조표는 요청 시점 값 · 취소 이력 주체는 스튜디오 · 처리 운영자가 없으면 「운영자」")
        void afterApprovalAndCancel() throws Exception {
            long approved = created(toBusiness(creatorToken, "214-37-55120"));
            approve(approved).andExpect(status().isOk());
            adminGet(QUEUE + "/" + approved + "?status=ALL")
                    .andExpect(jsonPath("$.diff[0].currentValue").value("개인 (비사업자)"))
                    .andExpect(jsonPath("$.diff[1].currentValue").doesNotExist())
                    .andExpect(jsonPath("$.history[1].event").value("APPROVED"))
                    .andExpect(jsonPath("$.history[1].actorLabel").value("김운영"))
                    .andExpect(jsonPath("$.approvalImpact").doesNotExist());

            jdbc.update("UPDATE brand_change_request SET processed_by = 987654 WHERE request_id = ?", approved);
            adminGet(QUEUE + "/" + approved + "?status=ALL").andExpect(jsonPath("$.history[1].actorLabel").value("운영자"));

            long canceled = created(toIndividual(creatorToken, RESIDENT));
            cancel(canceled);
            adminGet(QUEUE + "/" + canceled + "?status=ALL")
                    .andExpect(jsonPath("$.history[1].event").value("CANCELED"))
                    .andExpect(jsonPath("$.history[1].actorLabel").value("쇼룸 스튜디오"))
                    .andExpect(jsonPath("$.approvalImpact").doesNotExist());
        }
    }

    // ================================================================== 8 승인

    @Nested
    @DisplayName("승인 — 반영 · 이력 · 알림 · 정산 스냅샷 · 가드 · 동시 처리")
    class Approve {

        @Test
        @DisplayName("[BT-05 ④ ⑤ ⑥ ⑦] 사업자 전환 — 이력 detail · 업무 이메일 알림 1회 · 승인 전 정산은 비사업자로 남고 원천징수 · 이후 정산은 사업자")
        void toBusinessKeepsOldSnapshot() throws Exception {
            registerCreatorResidentNumber();
            registerCreatorAccount();
            registerSellerAccount();
            confirmedGroup(creamVariant, 1);
            endGroupBuy();
            Settlement before = generate(LocalDateTime.now().withNano(0));
            assertThat(before.getCreatorBusinessType()).isEqualTo(CreatorBusinessType.INDIVIDUAL);
            long withholdingBefore = before.getWithholdingAmount();
            assertThat(withholdingBefore).isPositive();

            long id = created(toBusiness(creatorToken, "214-37-55120"));
            approve(id).andExpect(status().isOk());

            CreatorAdminHistory history = onlyHistory(creator.getId());
            assertThat(history.getEventType()).isEqualTo(CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED);
            assertThat(history.getActorName()).isEqualTo("김운영");
            assertThat(history.getDetail()).isEqualTo("개인 (비사업자) → 개인사업자 · " + request(id).getRequestCode());
            assertThat(history.getRefType()).isEqualTo("CHANGE_REQUEST");
            assertThat(history.getRefId()).isEqualTo(id);
            verify(notifier, times(1)).businessTypeChangeApproved(creator.getId(), "jimin-biz@showroomz.test",
                    request(id).getRequestCode());

            // 승인 전에 만든 정산 — 스냅샷 · 금액 불변 · 확정해도 비사업자 경로(원천징수 · 주민번호 보류 없음).
            Settlement retained = confirm(settlement(before.getId()));
            assertThat(retained.getCreatorBusinessType()).isEqualTo(CreatorBusinessType.INDIVIDUAL);
            assertThat(retained.getWithholdingAmount()).isEqualTo(withholdingBefore);
            assertThat(payout(retained.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            assertThat(taxDocuments.findBySettlementIdAndTypeAndClawbackIdIsNull(retained.getId(),
                    TaxDocumentType.CREATOR_TAX_INVOICE)).isEmpty();

            // 승인 뒤에 만든 정산 — 사업자 스냅샷 · 원천징수 0 · 부가세 · 확정하면 인플루언서 세금계산서 입력 대기.
            openNextGroupBuy(creator);
            confirmedGroup(creamVariant, 1);
            endGroupBuy();
            Settlement after = confirm(generate(LocalDateTime.now().withNano(0)));
            assertThat(after.getCreatorBusinessType()).isEqualTo(CreatorBusinessType.BUSINESS);
            assertThat(after.getWithholdingAmount()).isZero();
            assertThat(after.getCreatorVatAmount()).isPositive();
            SettlementTaxDocument invoice = taxDocuments.findBySettlementIdAndTypeAndClawbackIdIsNull(after.getId(),
                    TaxDocumentType.CREATOR_TAX_INVOICE).orElseThrow();
            assertThat(invoice.getCounterpartyRegNumber()).isEqualTo("214-37-55120");
        }

        @Test
        @DisplayName("[BT-06 ③ ④ ⑤] 비사업자 전환 — 이력 detail · 승인 전 사업자 정산은 세금계산서(등록번호 유지) · 이후 정산은 원천징수 · 주민번호 보류 없음")
        void toIndividualKeepsOldSnapshot() throws Exception {
            makeBusinessCreator();
            registerCreatorAccount();
            registerSellerAccount();
            confirmedGroup(creamVariant, 1);
            endGroupBuy();
            Settlement before = generate(LocalDateTime.now().withNano(0));
            assertThat(before.getCreatorBusinessType()).isEqualTo(CreatorBusinessType.BUSINESS);

            long id = created(toIndividual(creatorToken, RESIDENT));
            approve(id).andExpect(status().isOk());
            assertThat(onlyHistory(creator.getId()).getDetail())
                    .isEqualTo("개인사업자 → 개인 (비사업자) · " + request(id).getRequestCode());

            Settlement retained = confirm(settlement(before.getId()));
            assertThat(retained.getCreatorBusinessType()).isEqualTo(CreatorBusinessType.BUSINESS);
            assertThat(taxDocuments.findBySettlementIdAndTypeAndClawbackIdIsNull(retained.getId(),
                    TaxDocumentType.CREATOR_TAX_INVOICE).orElseThrow().getCounterpartyRegNumber()).isEqualTo("312-81-40177");
            assertThat(payout(retained.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

            openNextGroupBuy(creator);
            confirmedGroup(creamVariant, 1);
            endGroupBuy();
            Settlement after = confirm(generate(LocalDateTime.now().withNano(0)));
            assertThat(after.getCreatorBusinessType()).isEqualTo(CreatorBusinessType.INDIVIDUAL);
            assertThat(after.getWithholdingAmount()).isPositive();
            assertThat(payout(after.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        }

        @Test
        @DisplayName("[AP-01 · AP-02] 승인 시점 탈퇴 회원은 409 CREATOR_WITHDRAWN(반영 · 이력 · 알림 0) · 정지 회원은 승인된다")
        void requesterStatus() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));
            jdbc.update("UPDATE creator SET status = 'WITHDRAWN' WHERE creator_id = ?", creator.getId());
            approve(id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
            assertUntouched(id, CreatorBusinessType.INDIVIDUAL);

            jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id = ?", creator.getId());
            approve(id).andExpect(status().isOk());
            assertThat(reloadCreator().getBusinessType()).isEqualTo(CreatorBusinessType.BUSINESS);
        }

        @Test
        @DisplayName("[BT-08 ② · AP-03] PG 셀러가 연결된 인플루언서도 반려는 된다 · 빈 문자열 PG id 는 연결 없음으로 승인된다")
        void pgGuardOnlyBlocksApproval() throws Exception {
            jdbc.update("UPDATE creator SET pg_partner_id = 'FAKE-SELLER-1' WHERE creator_id = ?", creator.getId());
            long rejected = created(toBusiness(creatorToken, "214-37-55120"));
            approve(rejected).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_PG_RESYNC_PENDING"));
            reject(rejected, "LICENSE_UNREADABLE").andExpect(status().isOk());

            jdbc.update("UPDATE creator SET pg_partner_id = '' WHERE creator_id = ?", creator.getId());
            long approved = created(toBusiness(creatorToken, "214-37-55120"));
            approve(approved).andExpect(status().isOk());
        }

        @Test
        @DisplayName("[AP-04] 취소된 건은 승인 · 반려 모두 400 CHANGE_REQUEST_NOT_PENDING")
        void canceledCannotBeProcessed() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));
            cancel(id);
            approve(id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
            reject(id, "LICENSE_UNREADABLE").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
            verify(notifier, never()).businessTypeChangeApproved(any(), any(), any());
        }

        @Test
        @DisplayName("[AP-05] 승인 트랜잭션 안에서 이력 저장이 실패하면 회원 구분 · 요청 상태가 함께 롤백되고 알림은 나가지 않는다")
        void rollbackOnHistoryFailure() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));
            // 기록기는 @Transactional(MANDATORY) 프록시 안에 스파이가 있다 — 스터빙은 프록시를 건너 대상에 건다.
            CreatorAdminHistoryRecorder target = AopTestUtils.getUltimateTargetObject(historyRecorder);
            doThrow(new IllegalStateException("이력 저장 실패(테스트)")).when(target)
                    .byOperator(any(), any(), any(), any(), any(), any(), any(), any());

            approve(id).andExpect(status().is5xxServerError());

            assertUntouched(id, CreatorBusinessType.INDIVIDUAL);
            verify(notifier, never()).businessTypeChangeApproved(any(), any(), any());
        }

        @Test
        @DisplayName("[AP-11] 처리 직전 다른 운영자가 먼저 처리(버전 증가) — 400 CHANGE_REQUEST_NOT_PENDING · 반영 · 이력 · 알림 0")
        void concurrentProcessingLoses() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));
            // 반영이 끝나고 플러시하기 전에 다른 트랜잭션이 이긴 상황 — 같은 커넥션에서 버전만 올려 낙관적 잠금 충돌을 만든다.
            doAnswer(invocation -> {
                Object applied = invocation.callRealMethod();
                jdbc.update("UPDATE brand_change_request SET version = version + 1 WHERE request_id = ?", id);
                return applied;
            }).when(applier).apply(any(), any());

            approve(id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));

            assertUntouched(id, CreatorBusinessType.INDIVIDUAL);
            verify(notifier, never()).businessTypeChangeApproved(any(), any(), any());
        }
    }

    // ================================================================== 9 반려

    @Nested
    @DisplayName("반려 — 사유 검증 · 방향 공통 사유 · 암호문 · 상세 사유 원문")
    class Reject {

        @Test
        @DisplayName("[BT-07 ② · RJ-01] 기타 + 공백 상세는 400 · 501자는 400 · 500자는 200 · 사유 없음 400")
        void reasonValidation() throws Exception {
            long id = created(toBusiness(creatorToken, "214-37-55120"));
            String url = QUEUE + "/" + id + "/reject";

            adminPost(url, Map.of("reasonType", "OTHER", "reasonDetail", "   ")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REJECT_DETAIL_REQUIRED"));
            adminPost(url, Map.of("reasonType", "OTHER", "reasonDetail", "가".repeat(501))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            adminPost(url, Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(request(id).getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
            verify(notifier, never()).businessTypeChangeRejected(any(), any(), any(), any());

            adminPost(url, Map.of("reasonType", "OTHER", "reasonDetail", "가".repeat(500))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("[RJ-02 · RJ-03 · RJ-04] 방향 공통 사유 · 비사업자 전환 반려는 암호문을 지운다 · 상세 사유는 응답 · 배너에 가공 없이")
        void rejectToIndividual() throws Exception {
            long toBusiness = created(toBusiness(creatorToken, "214-37-55120"));
            reject(toBusiness, "CLOSURE_NOT_CONFIRMED").andExpect(status().isOk());

            makeBusinessCreator();
            long id = created(toIndividual(creatorToken, RESIDENT));
            String detail = "서류 일부가 가려져 있습니다.";
            adminPost(QUEUE + "/" + id + "/reject", Map.of("reasonType", "OTHER", "reasonDetail", detail))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rejectReason").value("기타"))
                    .andExpect(jsonPath("$.rejectReasonDetail").value(detail));

            assertThat(request(id).getResidentNumberEnc()).isNull();
            assertThat(reloadCreator().getResidentRegistrationNumberEnc()).isNull();
            assertThat(reloadCreator().getBusinessType()).isEqualTo(CreatorBusinessType.BUSINESS);
            assertThat(jdbc.queryForObject("SELECT reject_reason FROM brand_change_request WHERE request_id = ?",
                    String.class, id)).isEqualTo("OTHER");
            creatorGet(STUDIO + "/settlement")
                    .andExpect(jsonPath("$.businessTypeChange.rejectReason.code").value("OTHER"))
                    .andExpect(jsonPath("$.businessTypeChange.rejectReasonDetail").value(detail));
            verify(notifier).businessTypeChangeRejected(creator.getId(), "jimin-biz@showroomz.test",
                    request(id).getRequestCode(), "기타");
        }
    }

    // ================================================================== 10 인플루언서 상세

    @Test
    @DisplayName("[BT-09 ① ③] 인플루언서 상세 — 대기 중 요청 코드 · 시각 · 처리 이력은 승인 · 반려만(접수 · 취소는 남기지 않는다)")
    void creatorDetailLink() throws Exception {
        long canceled = created(toBusiness(creatorToken, "214-37-55120"));
        cancel(canceled);
        long rejected = created(toBusiness(creatorToken, "214-37-55120"));
        adminGet("/v1/admin/creators/" + creator.getId())
                .andExpect(jsonPath("$.settlementAccount.pendingBusinessTypeChange.requestCode")
                        .value(request(rejected).getRequestCode()))
                .andExpect(jsonPath("$.settlementAccount.pendingBusinessTypeChange.requestedAt").isNotEmpty());
        reject(rejected, "LICENSE_UNREADABLE").andExpect(status().isOk());
        long approved = created(toBusiness(creatorToken, "214-37-55120"));
        approve(approved).andExpect(status().isOk());

        assertThat(events(creator.getId())).containsExactlyInAnyOrder(
                CreatorAdminEventType.BUSINESS_TYPE_CHANGE_REJECTED, CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED);
    }

    // ================================================================== 4 브랜드 경로 회귀

    @Nested
    @DisplayName("브랜드 경로 회귀 — 셀러 API 가드 · 브랜드 상세 · 처리")
    class BrandRegression {

        @Test
        @DisplayName("[RG-03 · RG-04] 셀러 GET /fields 인플루언서 유형 400 · 인플루언서 요청 id 로 확인하면 403")
        void sellerGuards() throws Exception {
            mockMvc.perform(get("/v1/seller/change-requests/fields?type=TO_BUSINESS").header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_TYPE_NOT_ALLOWED"));
            mockMvc.perform(get("/v1/seller/change-requests/latest?type=TO_INDIVIDUAL").header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_TYPE_NOT_ALLOWED"));

            long id = created(toBusiness(creatorToken, "214-37-55120"));
            approve(id).andExpect(status().isOk());
            postAs("/v1/seller/change-requests/" + id + "/acknowledge", brandToken).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ACCESS_DENIED"));
            assertThat(request(id).getResultAcknowledgedAt()).isNull();
        }

        @Test
        @DisplayName("[RG-05] 브랜드 상세 — requester(BRAND · marketId · 브랜드명) · source 는 REQUEST/LIVE 만 · 인플루언서 전용 필드 없음")
        void brandDetailShape() throws Exception {
            long id = brandBusinessInfoRequest();

            JsonNode d = json(adminGet(QUEUE + "/" + id).andExpect(status().isOk()));
            assertThat(d.at("/requester/type").asText()).isEqualTo("BRAND");
            assertThat(d.at("/requester/id").asLong()).isEqualTo(brand.marketId());
            assertThat(d.at("/requester/name").asText()).isEqualTo("글로우랩");
            assertThat(d.at("/brandName").asText()).isEqualTo("글로우랩");
            assertThat(d.at("/marketId").asLong()).isEqualTo(brand.marketId());
            assertThat(d.at("/requesterName").asText()).isNotBlank();
            List<String> sources = new ArrayList<>();
            d.get("diff").forEach(r -> sources.add(r.get("fieldKey").asText() + "|" + r.get("source").asText()));
            assertThat(sources).contains("MARKET_NAME|REQUEST", "BUSINESS_TYPE|LIVE", "BUSINESS_REG_NUMBER|LIVE");
            assertThat(sources).allMatch(s -> s.endsWith("|REQUEST") || s.endsWith("|LIVE"));
            for (String field : List.of("/compareItems", "/approvalImpact")) {
                assertThat(d.at(field).isMissingNode() || d.at(field).isNull()).as(field).isTrue();
            }
            assertThat(d.at("/evidence/documentLabel").asText()).isEqualTo("사업자등록증");
            assertThat(d.at("/history/0/actorLabel").asText()).isEqualTo("브랜드 파트너센터");
        }

        @Test
        @DisplayName("[RG-06 · RG-08] 브랜드 건 — 인플루언서 사유로 반려 400 · 승인은 응답 requester BRAND · 인플루언서 이력 · 알림 0")
        void brandProcessing() throws Exception {
            long id = brandBusinessInfoRequest();
            reject(id, "LICENSE_UNREADABLE").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH"));

            approve(id).andExpect(status().isOk())
                    .andExpect(jsonPath("$.requester.type").value("BRAND"))
                    .andExpect(jsonPath("$.brandName").isNotEmpty());

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_admin_history", Long.class)).isZero();
            verify(notifier, never()).businessTypeChangeApproved(any(), any(), any());
            verify(mailService).sendChangeRequestApprovedEmail(anyString(), anyString(), anyString(), any());
        }
    }

    // ================================================================== 11 개인정보

    @Test
    @DisplayName("[PV-01 · PV-02 · PV-05] 비사업자 전환 전 과정 — 응답 · 로그에 주민번호 원문 없음 · 처리 끝난 행에 암호문 0 · 열람 로그 0")
    void residentNumberNeverLeaks(CapturedOutput output) throws Exception {
        makeBusinessCreator();
        jdbc.update("UPDATE creator SET birthday = '1995-08-12' WHERE creator_id = ?", creator.getId());
        StringBuilder responses = new StringBuilder();

        long canceled = created(toIndividual(creatorToken, RESIDENT));
        responses.append(contentOf(adminGet(QUEUE + "/" + canceled)));
        cancel(canceled);
        long rejected = created(toIndividual(creatorToken, RESIDENT));
        responses.append(contentOf(reject(rejected, "CLOSURE_NOT_CONFIRMED")));
        long approved = created(toIndividual(creatorToken, RESIDENT));
        responses.append(contentOf(adminGet(QUEUE + "/" + approved)));
        responses.append(contentOf(approve(approved)));
        responses.append(contentOf(creatorGet(STUDIO + "/settlement")));
        responses.append(contentOf(adminGet(QUEUE + "?status=ALL")));

        assertThat(responses.toString()).doesNotContain(RESIDENT_BACK).doesNotContain("9508122345678");
        assertThat(output.getAll()).doesNotContain(RESIDENT_BACK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brand_change_request "
                + "WHERE status <> 'PENDING' AND resident_number_enc IS NOT NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_privacy_access_log", Long.class)).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private void makeBusinessCreator() {
        makeCreatorBusiness();
        jdbc.update("UPDATE creator SET business_registration_number = ? WHERE creator_id = ?", "312-81-40177", creator.getId());
    }

    private void assertUntouched(long requestId, CreatorBusinessType expected) {
        assertThat(request(requestId).getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
        assertThat(reloadCreator().getBusinessType()).isEqualTo(expected);
        assertThat(events(creator.getId())).isEmpty();
    }

    private long brandBusinessInfoRequest() throws Exception {
        return changeRequests.createBusinessInfo(brandToken, "브랜드명을 바꿉니다", Map.of("MARKET_NAME", "글로우랩 리뉴얼"));
    }

    private String tokenOf(Creator owner) {
        return bearerToken(owner.getUser().getUsername(), RoleType.CREATOR, owner.getUser().getId());
    }

    private ResultActions toBusiness(String token, String businessRegistrationNumber) throws Exception {
        return toBusiness(token, businessRegistrationNumber, "사업자등록증.pdf");
    }

    private ResultActions toBusiness(String token, String businessRegistrationNumber, String fileName) throws Exception {
        Map<String, Object> body = body("BUSINESS");
        body.put("businessRegistrationNumber", businessRegistrationNumber);
        body.put("evidenceFileName", fileName);
        return create(token, body);
    }

    private ResultActions toIndividual(String token, String residentNumber) throws Exception {
        Map<String, Object> body = body("INDIVIDUAL");
        body.put("residentRegistrationNumber", residentNumber);
        body.put("evidenceFileName", "폐업사실증명원.pdf");
        return create(token, body);
    }

    private Map<String, Object> body(String targetType) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("evidenceFileUrl", EVIDENCE_URL);
        body.put("evidenceFileName", "사업자등록증.pdf");
        body.put("evidenceFileSize", 1_258_291);
        return body;
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(REQUESTS).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions postAs(String url, String token) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions approve(long requestId) throws Exception {
        return adminPost(QUEUE + "/" + requestId + "/approve", Map.of());
    }

    private ResultActions reject(long requestId, String reasonType) throws Exception {
        return adminPost(QUEUE + "/" + requestId + "/reject", Map.of("reasonType", reasonType));
    }

    private void cancel(long requestId) throws Exception {
        postAs(REQUESTS + "/" + requestId + "/cancel", creatorToken).andExpect(status().isNoContent());
    }

    private long created(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("requestId").asLong();
    }

    private String contentOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private String birthdayCheck(long requestId) throws Exception {
        JsonNode node = json(adminGet(QUEUE + "/" + requestId).andExpect(status().isOk())).at("/compareItems/1/serverCheck");
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private ChangeRequest request(long requestId) {
        return brandChangeRequests.findById(requestId).orElseThrow();
    }

    /** {@code fieldKey|current|requested|sortOrder} — 저장 순서대로. */
    private List<String> items(long requestId) {
        return inTransaction(() -> request(requestId).getItems().stream()
                .sorted(java.util.Comparator.comparing(ChangeRequestItem::getSortOrder))
                .map(i -> i.getFieldKey().name() + "|" + i.getCurrentValue() + "|" + i.getRequestedValue() + "|" + i.getSortOrder())
                .toList());
    }

    /** {@code fieldKey|current|requested|changed|locked|source}. */
    private static List<String> rows(JsonNode diff) {
        List<String> rows = new ArrayList<>();
        diff.forEach(r -> rows.add(String.join("|", r.get("fieldKey").asText(), text(r, "currentValue"),
                text(r, "requestedValue"), r.get("changed").asText(), r.get("locked").asText(), r.get("source").asText())));
        return rows;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "null" : value.asText();
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(row -> ids.add(row.get("requestId").asLong()));
        return ids;
    }

    private long creatorRows() {
        return brandChangeRequests.findAll().stream()
                .filter(r -> r.getRequesterType() == ChangeRequesterType.CREATOR).count();
    }

    private Creator reloadCreator() {
        return creatorRepository.findById(creator.getId()).orElseThrow();
    }

    private List<CreatorAdminEventType> events(Long creatorId) {
        return adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creatorId).stream()
                .map(CreatorAdminHistory::getEventType).toList();
    }

    private CreatorAdminHistory onlyHistory(Long creatorId) {
        List<CreatorAdminHistory> histories = adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creatorId);
        assertThat(histories).hasSize(1);
        return histories.get(0);
    }
}

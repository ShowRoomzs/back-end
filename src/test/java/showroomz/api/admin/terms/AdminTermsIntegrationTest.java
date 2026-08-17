package showroomz.api.admin.terms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.terms.service.TermsEffectuationService;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §21 어드민 약관·정책 관리 통합 테스트 — 등록 → 목록 → 개정 → 시행 전환 → 소비자 노출까지 태운다. */
@DisplayName("[통합] 어드민 약관·정책 관리")
class AdminTermsIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate FIRST_EFFECTIVE_DATE = LocalDate.now().plusDays(10);
    private static final LocalDate SECOND_EFFECTIVE_DATE = LocalDate.now().plusDays(40);

    @Autowired
    private TermsEffectuationService termsEffectuationService;

    private Seller operator;
    private String opsToken;

    @BeforeEach
    void setUpActors() {
        operator = fixture.createAdmin("ops@showroomz.test", "김운영");
        opsToken = adminToken(operator);
    }

    @Test
    @DisplayName("문서를 등록하면 v1.0 시행 예정으로 잡히고, 시행일 전에는 소비자에게 노출되지 않는다")
    void registersDocumentAsScheduled() throws Exception {
        Long documentId = registerDocument();

        mockMvc.perform(get("/v1/admin/terms").header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").value(documentId))
                .andExpect(jsonPath("$.content[0].version").value("v1.0"))
                .andExpect(jsonPath("$.content[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.content[0].statusName").value("시행 예정"))
                .andExpect(jsonPath("$.content[0].targetName").value("소비자"))
                .andExpect(jsonPath("$.content[0].canRegisterNewVersion").value(true))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.scheduledCount").value(1))
                .andExpect(jsonPath("$.supersededCount").value(0))
                .andExpect(jsonPath("$.typeCounts[0].type").value("ALL"))
                .andExpect(jsonPath("$.typeCounts[0].count").value(1));

        // 시행일 전이라 소비자 화면에는 아직 없다
        mockMvc.perform(get("/v1/common/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/v1/common/terms/{documentId}", documentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("역행 버전 번호는 서버가 막는다")
    void rejectsBackwardVersionNumber() throws Exception {
        Long documentId = registerDocument();

        mockMvc.perform(post("/v1/admin/terms/{documentId}/versions", documentId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(versionRequest("0.9", SECOND_EFFECTIVE_DATE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("새 버전을 등록하면 이력에 쌓이고, 시행일이 지나면 배치가 교체하며 과거 버전은 남는다")
    void effectuatesNewVersionAndKeepsHistory() throws Exception {
        Long documentId = registerDocument();

        mockMvc.perform(post("/v1/admin/terms/{documentId}/versions", documentId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(versionRequest("2.0", SECOND_EFFECTIVE_DATE))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION));

        mockMvc.perform(get("/v1/admin/terms/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].version").value("v2.0"))
                .andExpect(jsonPath("$.versions[0].registrantName").value("김운영"))
                .andExpect(jsonPath("$.pastVersionCount").value(0));

        // 첫 버전 시행일 → v1.0 시행중
        termsEffectuationService.effectuateDueVersions(FIRST_EFFECTIVE_DATE);

        mockMvc.perform(get("/v1/common/terms").param("target", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value("v1.0"));

        // 두 번째 버전 시행일 → v2.0 시행중, v1.0 과거 버전
        termsEffectuationService.effectuateDueVersions(SECOND_EFFECTIVE_DATE);

        mockMvc.perform(get("/v1/admin/terms/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.version").value("v2.0"))
                // 과거 버전은 동의 기록이 참조하므로 삭제하지 않는다
                .andExpect(jsonPath("$.pastVersionCount").value(1))
                .andExpect(jsonPath("$.versions.length()").value(2));

        mockMvc.perform(get("/v1/common/terms/{documentId}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v2.0"))
                .andExpect(jsonPath("$.content").value("제1조(목적) 개정 원문"));
    }

    @Test
    @DisplayName("버전 상세는 조회 전용이며 시행 기간·다음 버전·이동 지점을 함께 내려준다")
    void showsVersionDetailWithNavigation() throws Exception {
        Long documentId = registerDocument();

        mockMvc.perform(post("/v1/admin/terms/{documentId}/versions", documentId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(versionRequest("2.0", SECOND_EFFECTIVE_DATE))))
                .andExpect(status().isCreated());

        termsEffectuationService.effectuateDueVersions(SECOND_EFFECTIVE_DATE);

        long firstVersionId = findVersionId(documentId, "v1.0");

        mockMvc.perform(get("/v1/admin/terms/{documentId}/versions/{versionId}", documentId, firstVersionId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1.0"))
                .andExpect(jsonPath("$.status").value("PAST"))
                .andExpect(jsonPath("$.statusName").value("과거 버전"))
                .andExpect(jsonPath("$.effectiveStartDate").value(FIRST_EFFECTIVE_DATE.toString()))
                .andExpect(jsonPath("$.effectiveEndDate").value(SECOND_EFFECTIVE_DATE.minusDays(1).toString()))
                .andExpect(jsonPath("$.nextVersion").value("v2.0"))
                .andExpect(jsonPath("$.replacedAt").value(SECOND_EFFECTIVE_DATE.toString()))
                .andExpect(jsonPath("$.previousVersionId").doesNotExist())
                .andExpect(jsonPath("$.nextVersionId").exists());
    }

    /** 버전 이력에서 버전 표기로 ID를 찾는다 — 이력 행 클릭으로 버전 상세에 들어가는 흐름과 같다. */
    private long findVersionId(Long documentId, String version) throws Exception {
        String body = mockMvc.perform(get("/v1/admin/terms/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, opsToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode node : objectMapper.readTree(body).get("versions")) {
            if (version.equals(node.get("version").asText())) {
                return node.get("versionId").asLong();
            }
        }
        throw new IllegalStateException("버전 이력에 " + version + " 이 없습니다.");
    }

    private Long registerDocument() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "소비자 이용약관");
        request.put("type", "TERMS_OF_SERVICE");
        request.put("target", "USER");
        request.put("effectiveDate", FIRST_EFFECTIVE_DATE.toString());
        request.put("content", "제1조(목적) 최초 원문");

        String location = mockMvc.perform(post("/v1/admin/terms")
                        .header(HttpHeaders.AUTHORIZATION, opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private Map<String, Object> versionRequest(String versionNumber, LocalDate effectiveDate) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("versionNumber", versionNumber);
        request.put("effectiveDate", effectiveDate.toString());
        request.put("content", "제1조(목적) 개정 원문");
        return request;
    }
}

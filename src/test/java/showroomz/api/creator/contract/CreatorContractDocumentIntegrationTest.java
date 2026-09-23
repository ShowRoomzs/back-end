package showroomz.api.creator.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.api.admin.contract.dto.AdminContractDto.DownloadResponse;
import showroomz.api.admin.contract.service.ContractDocumentStorage;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S6 계약 문서 카드 · 표준 조항 — 스튜디오가 내려받을 수 있는 것은 <b>체결완료 계약의 서명본·감사추적인증서 둘뿐</b>이다.
 *
 * <p>두 문서는 운영자가 모두싸인에서 내려받아 업로드한 파일이고(API 미도입) 체결 처리 시 「발급」된다.
 * 검토용 생성본(GENERATED_DRAFT)은 운영자·브랜드용이라 같은 경로로 요청해도 내려가지 않는다.
 *
 * <p>스토리지는 목으로 둔다 — presign은 실제 AWS 자격 증명을 요구한다. 여기서 볼 것은
 * URL을 <b>어떤 조건에서 내주는가</b>이지 서명 문자열이 아니다.
 */
@DisplayName("[통합] 쇼룸 스튜디오 계약 문서 · 조항")
class CreatorContractDocumentIntegrationTest extends CreatorContractTestSupport {

    @MockitoBean
    private ContractDocumentStorage contractDocumentStorage;

    @BeforeEach
    void stubStorage() {
        when(contractDocumentStorage.download(any())).thenAnswer(invocation -> {
            ContractDocument document = invocation.getArgument(0);
            return new DownloadResponse(
                    "https://signed.test/" + document.getDocumentType(),
                    document.getOriginalName(), document.getSizeBytes(), 300, null);
        });
    }

    @Test
    @DisplayName("검토용 생성본은 같은 경로로도 내려가지 않고 문서 카드에도 없다")
    void hidesGeneratedDraft() throws Exception {
        Long contractId = concludedContract();
        registerDocument(contractId, ContractDocumentType.GENERATED_DRAFT, "생성본.pdf");
        registerDocument(contractId, ContractDocumentType.SIGNED_PDF, "계약서.pdf");
        registerDocument(contractId, ContractDocumentType.AUDIT_TRAIL, "감사추적.pdf");

        document(contractId, ContractDocumentType.GENERATED_DRAFT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_NOT_FOUND"));

        // 경로만 막고 목록에 남기면 화면에 못 여는 카드가 생긴다.
        detail(contractId)
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[?(@.documentType == 'GENERATED_DRAFT')]").isEmpty());
    }

    @Test
    @DisplayName("두 문서 중 하나만 올라와 있으면 문서 카드 다운로드 권한은 닫혀 있다")
    void requiresBothDocumentsForDownloadPermission() throws Exception {
        Long contractId = concludedContract();
        registerDocument(contractId, ContractDocumentType.SIGNED_PDF, "계약서.pdf");

        detail(contractId)
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.permissions.canDownloadDocuments").value(false));
        // 올라와 있는 한 장은 받을 수 있고, 없는 한 장은 404다.
        document(contractId, ContractDocumentType.SIGNED_PDF).andExpect(status().isOk());
        document(contractId, ContractDocumentType.AUDIT_TRAIL)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("종결된 계약은 문서가 남아 있어도 내려주지 않는다 — 계약이 성립하지 않았다")
    void refusesDocumentsOfClosedContracts() throws Exception {
        Long contractId = declinedContract();
        registerDocument(contractId, ContractDocumentType.SIGNED_PDF, "계약서.pdf");

        document(contractId, ContractDocumentType.SIGNED_PDF).andExpect(status().isNotFound());
        detail(contractId).andExpect(jsonPath("$.documents").isEmpty());
    }

    @Test
    @DisplayName("남의 계약·도착 전 계약의 문서와 조항은 존재 여부 자체를 숨긴다(404)")
    void hidesDocumentsAndClausesOfInvisibleContracts() throws Exception {
        Creator other = createOtherCreator();
        Long othersConcluded = saveContractFor(other, ContractStatus.CONCLUDED, contract -> {
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
            contract.conclude(LocalDateTime.now());
        });
        registerDocument(othersConcluded, ContractDocumentType.SIGNED_PDF, "계약서.pdf");
        Long notYetSent = reviewRejectedContract();

        for (Long hidden : new Long[]{othersConcluded, notYetSent}) {
            document(hidden, ContractDocumentType.SIGNED_PDF)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONTRACT_NOT_RECEIVED"));
            mockMvc.perform(get(CONTRACTS + "/" + hidden + "/clauses").header(HttpHeaders.AUTHORIZATION, myToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONTRACT_NOT_RECEIVED"));
        }
    }

    @Test
    @DisplayName("종결된 계약의 조항도 읽을 수 있다 — 계약에 고정된 버전이다")
    void closedContractClausesRemainReadable() throws Exception {
        Long contractId = expiredContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId + "/clauses").header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clauseVersionId").value(clauseVersion.getId()))
                .andExpect(jsonPath("$.clauses[0].fullTitle").value("제3조 최저가 정책"));
    }

    @Test
    @DisplayName("없는 문서 종류는 잘못된 요청(400)이다 — 서버 오류로 새지 않는다")
    void rejectsUnknownDocumentType() throws Exception {
        Long contractId = concludedContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId + "/documents/CONTRACT")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isBadRequest());
    }
}

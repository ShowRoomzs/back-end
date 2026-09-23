package showroomz.api.seller.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.contract.dto.AdminContractDto.DownloadResponse;
import showroomz.api.admin.contract.service.ContractDocumentStorage;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §25-3 #3 · 설계서 4-1 — 체결 문서 다운로드.
 *
 * <p>브랜드가 받을 수 있는 것은 <b>체결완료 계약의 서명본·감사추적인증서 둘뿐</b>이다.
 * 검토용 생성본(GENERATED_DRAFT)은 어드민 검토 화면의 것이라 같은 경로로 요청해도 내려가지 않는다.
 *
 * <p>스토리지는 목으로 둔다 — presign은 실제 AWS 자격 증명을 요구해서 환경에 따라 결과가 달라진다.
 * 여기서 확인할 것은 URL을 <b>어떤 조건에서 내주는가</b>이지 서명 문자열이 아니다.
 */
@DisplayName("[통합] 파트너센터 계약 문서")
class SellerContractDocumentIntegrationTest extends SellerContractTestSupport {

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
    @DisplayName("체결완료 계약의 서명본·감사추적인증서만 내려준다")
    void servesConcludedDocuments() throws Exception {
        Contract concluded = seedInStatus(ContractStatus.CONCLUDED);
        registerDocument(concluded, ContractDocumentType.SIGNED_PDF, "계약서.pdf");
        registerDocument(concluded, ContractDocumentType.AUDIT_TRAIL, "감사추적.pdf");

        document(concluded.getId(), ContractDocumentType.SIGNED_PDF)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("SIGNED_PDF"))
                .andExpect(jsonPath("$.documentTypeLabel").value("서명 완료 계약서"))
                .andExpect(jsonPath("$.downloadUrl").value("https://signed.test/SIGNED_PDF"))
                .andExpect(jsonPath("$.originalName").value("계약서.pdf"))
                .andExpect(jsonPath("$.uploadedAt").exists());

        document(concluded.getId(), ContractDocumentType.AUDIT_TRAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentTypeLabel").value("감사 추적 인증서"));

        detail(concluded.getId())
                .andExpect(jsonPath("$.documents.length()").value(2))
                .andExpect(jsonPath("$.documents[0].downloadUrl").exists());
    }

    @Test
    @DisplayName("검토용 생성본은 같은 경로로도 내려가지 않는다 — 어드민 검토 화면의 것이다")
    void hidesGeneratedDraftFromBrand() throws Exception {
        Contract concluded = seedInStatus(ContractStatus.CONCLUDED);
        registerDocument(concluded, ContractDocumentType.GENERATED_DRAFT, "생성본.pdf");
        registerDocument(concluded, ContractDocumentType.SIGNED_PDF, "계약서.pdf");

        document(concluded.getId(), ContractDocumentType.GENERATED_DRAFT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_NOT_FOUND"));

        // 상세의 문서 목록에서도 빠진다 — 경로만 막고 목록에 남기면 화면에 못 여는 줄이 생긴다.
        detail(concluded.getId())
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].type").value("SIGNED_PDF"));
    }

    @Test
    @DisplayName("체결 전에는 문서가 올라와 있어도 내려주지 않는다")
    void refusesBeforeConclusion() throws Exception {
        Contract signing = seedInStatus(ContractStatus.SIGNING);
        registerDocument(signing, ContractDocumentType.SIGNED_PDF, "계약서.pdf");

        document(signing.getId(), ContractDocumentType.SIGNED_PDF)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_NOT_FOUND"));
        detail(signing.getId()).andExpect(jsonPath("$.documents").isEmpty());

        // 체결됐지만 아직 업로드 전이면 같은 404다.
        document(seedInStatus(ContractStatus.CONCLUDED).getId(), ContractDocumentType.SIGNED_PDF)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("남의 브랜드 문서는 404가 아니라 403이다")
    void blocksOtherBrandsDocument() throws Exception {
        Contract concluded = seedInStatus(ContractStatus.CONCLUDED);
        registerDocument(concluded, ContractDocumentType.SIGNED_PDF, "계약서.pdf");

        BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "아더랩");
        mockMvc.perform(get(CONTRACTS + "/" + concluded.getId() + "/documents/SIGNED_PDF")
                        .header(HttpHeaders.AUTHORIZATION, sellerToken(other.seller())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_OWNED_BY_SELLER"));
    }

    // ------------------------------------------------------------------ 헬퍼

    private ResultActions document(long contractId, ContractDocumentType type) throws Exception {
        return mockMvc.perform(get(CONTRACTS + "/" + contractId + "/documents/" + type.name())
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private void registerDocument(Contract contract, ContractDocumentType type, String originalName) {
        contractDocumentRepository.save(ContractDocument.builder()
                .contract(contract)
                .documentType(type)
                .s3Key("contracts/%d/%s.pdf".formatted(contract.getId(), type.name()))
                .originalName(originalName)
                .sizeBytes(2_048L)
                .contentType("application/pdf")
                .uploadedAt(LocalDateTime.now().withNano(0))
                .build());
    }
}

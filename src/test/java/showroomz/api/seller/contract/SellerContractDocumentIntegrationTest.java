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
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §25-3 #3 · 설계서 4-1 — 계약 문서 다운로드.
 *
 * <p>체결 전(검토 대기 ~ 체결 처리 대기)에는 <b>운영자가 내려받는 계약서 생성본</b>을 같은 파일로 받는다.
 * 체결완료 후에는 서명본·감사추적인증서 둘뿐이고 생성본은 내려가지 않는다.
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
    @DisplayName("체결 전에는 운영자가 받는 계약서 생성본을 같은 파일로 내려준다")
    void servesGeneratedDraftBeforeConclusion() throws Exception {
        for (ContractStatus status : new ContractStatus[]{
                ContractStatus.REVIEW_PENDING, ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING}) {
            Contract contract = seedInStatus(status);
            registerGeneratedDraft(contract);

            document(contract.getId(), ContractDocumentType.GENERATED_DRAFT)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentType").value("GENERATED_DRAFT"))
                    .andExpect(jsonPath("$.documentTypeLabel").value("계약서 생성본"))
                    .andExpect(jsonPath("$.downloadUrl").value("https://signed.test/GENERATED_DRAFT"));
            detail(contract.getId())
                    .andExpect(jsonPath("$.documents.length()").value(1))
                    .andExpect(jsonPath("$.documents[0].type").value("GENERATED_DRAFT"));
        }
    }

    @Test
    @DisplayName("이전 제출본으로 만든 생성본은 내려주지 않는다 — 지금 제출본의 계약서가 아니다")
    void hidesStaleGeneratedDraft() throws Exception {
        Contract signing = seedInStatus(ContractStatus.SIGNING);
        registerDocument(signing, ContractDocumentType.GENERATED_DRAFT, "생성본.pdf");

        document(signing.getId(), ContractDocumentType.GENERATED_DRAFT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_NOT_FOUND"));
        detail(signing.getId()).andExpect(jsonPath("$.documents").isEmpty());
    }

    @Test
    @DisplayName("반려·종결 계약은 생성본이 남아 있어도 내려주지 않는다")
    void hidesGeneratedDraftOutsideProgress() throws Exception {
        for (ContractStatus status : new ContractStatus[]{
                ContractStatus.REVIEW_REJECTED, ContractStatus.EXPIRED, ContractStatus.CANCELED}) {
            Contract contract = seedInStatus(status);
            registerGeneratedDraft(contract);

            document(contract.getId(), ContractDocumentType.GENERATED_DRAFT)
                    .andExpect(status().isNotFound());
            detail(contract.getId()).andExpect(jsonPath("$.documents").isEmpty());
        }
    }

    @Test
    @DisplayName("체결완료 후에는 생성본이 내려가지 않는다 — 체결 문서로 바뀐다")
    void hidesGeneratedDraftAfterConclusion() throws Exception {
        Contract concluded = seedInStatus(ContractStatus.CONCLUDED);
        registerGeneratedDraft(concluded);
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

    // ── 검토 요청 시 제출본 생성 ────────────────────────────────────────────

    @Test
    @DisplayName("검토 요청이 커밋 이후 제출본 PDF를 만든다 — 응답은 기다리지 않고, 다시 조회하면 실리며 SYSTEM 이력이 남는다")
    void reviewRequestGeneratesSubmittedDraft() throws Exception {
        doReturn("%PDF-1.7 test".getBytes()).when(renderer).render(anyString(), anyString());
        when(contractDocumentStorage.putGenerated(anyLong(), any()))
                .thenAnswer(invocation -> "contracts/" + invocation.getArgument(0) + "/documents/generated.pdf");

        long contractId = draftReadyForReview();
        // 응답은 커밋 직전에 조립된다 — 생성본은 그 뒤에 만들어지므로 이 응답에는 없다(렌더링을 기다리지 않는다).
        reviewRequest(contractId).andExpect(status().isOk()).andExpect(jsonPath("$.documents").isEmpty());
        detail(contractId).andExpect(jsonPath("$.documents[0].type").value("GENERATED_DRAFT"));

        document(contractId, ContractDocumentType.GENERATED_DRAFT).andExpect(status().isOk());
        Contract contract = contractRepository.findById(contractId).orElseThrow();
        ContractDocument generated = contractDocumentRepository
                .findByContractIdAndDocumentType(contractId, ContractDocumentType.GENERATED_DRAFT).orElseThrow();
        assertThat(generated.getSourceReviewRequestedAt()).isEqualTo(contract.getReviewRequestedAt());
        assertThat(generated.getUploadedBy()).isNull();
        assertThat(contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contractId))
                .filteredOn(h -> h.getEventType() == ContractEventType.CONTRACT_PDF_GENERATED)
                .singleElement()
                .satisfies(h -> assertThat(h.getActorType()).isEqualTo(ContractActorType.SYSTEM));
    }

    @Test
    @DisplayName("제출본 생성이 실패해도 검토 요청은 성공한다 — 생성본은 운영자 첫 다운로드가 만든다")
    void reviewRequestSurvivesGenerationFailure() throws Exception {
        // 기본 스텁이 렌더링 실패다(IntegrationTestSupport).
        long contractId = draftReadyForReview();
        reviewRequest(contractId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.documents").isEmpty());

        assertThat(contractRepository.findById(contractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.REVIEW_PENDING);
        assertThat(contractDocumentRepository
                .findByContractIdAndDocumentType(contractId, ContractDocumentType.GENERATED_DRAFT)).isEmpty();
        assertThat(contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contractId))
                .extracting(ContractHistory::getEventType)
                .contains(ContractEventType.REVIEW_REQUESTED)
                .doesNotContain(ContractEventType.CONTRACT_PDF_GENERATED);
    }

    @Test
    @DisplayName("S3 업로드가 실패해도 검토 요청은 성공한다")
    void reviewRequestSurvivesUploadFailure() throws Exception {
        doReturn("%PDF-1.7 test".getBytes()).when(renderer).render(anyString(), anyString());
        when(contractDocumentStorage.putGenerated(anyLong(), any())).thenThrow(new RuntimeException("S3 down"));

        long contractId = draftReadyForReview();
        reviewRequest(contractId).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").isEmpty());
        assertThat(contractRepository.findById(contractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.REVIEW_PENDING);
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
        registerDocument(contract, type, originalName, null);
    }

    /** 운영자 다운로드가 만든 생성본처럼 지금 제출 시각을 원본으로 기록한다. */
    private void registerGeneratedDraft(Contract contract) {
        LocalDateTime submittedAt = contractRepository.findById(contract.getId()).orElseThrow().getReviewRequestedAt();
        registerDocument(contract, ContractDocumentType.GENERATED_DRAFT, "생성본.pdf", submittedAt);
    }

    private void registerDocument(Contract contract, ContractDocumentType type, String originalName,
                                  LocalDateTime sourceReviewRequestedAt) {
        contractDocumentRepository.save(ContractDocument.builder()
                .contract(contract)
                .documentType(type)
                .s3Key("contracts/%d/%s.pdf".formatted(contract.getId(), type.name()))
                .originalName(originalName)
                .sizeBytes(2_048L)
                .contentType("application/pdf")
                .uploadedAt(LocalDateTime.now().withNano(0))
                .sourceReviewRequestedAt(sourceReviewRequestedAt)
                .build());
    }
}

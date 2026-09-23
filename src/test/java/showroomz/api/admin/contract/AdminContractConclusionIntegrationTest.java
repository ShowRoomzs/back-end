package showroomz.api.admin.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시안 B4(체결 처리 대기 · 체결 문서 카드) · C4(체결 완료 처리) · B5(체결완료).
 *
 * <p>체결 완료가 이 화면에서 유일한 불가역 조치다. 서명 PDF·감사추적인증서 두 파일이 다 올라와야 열리고
 * (필수 미입력 = 버튼 비활성이므로 서버는 {@code kind: REQUIRED}로 막는다), 누른 뒤에는 서명 기록과
 * 체결 문서가 영구히 잠긴다. 체결 문서는 양측이 각자 내려받는 단일 원본이다.
 */
@DisplayName("[통합] 어드민 계약 체결 문서·체결 처리 (B4·B5·C4)")
class AdminContractConclusionIntegrationTest extends AdminContractTestSupport {

    private static final String SIGNED_PDF_NAME = "서명완료_여름수분세럼공구.pdf";
    private static final String AUDIT_TRAIL_NAME = "감사추적인증서.pdf";

    // ------------------------------------------------------------------ B4 체결 문서

    @Test
    @DisplayName("B4: 서명 PDF만 올라온 상태 — 체결 완료가 비활성이고 서버는 에러 문구 없는 REQUIRED로 막는다")
    void conclusionNeedsBothDocuments() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);

        upload(c, ContractDocumentType.SIGNED_PDF, SIGNED_PDF_NAME).andExpect(status().isOk());

        detail(c).andExpect(jsonPath("$.documents[?(@.type=='SIGNED_PDF')].exists").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.documents[?(@.type=='AUDIT_TRAIL')].exists").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.permissions.canUploadDocument").value(true))
                .andExpect(jsonPath("$.permissions.canConclude").value(false));
        conclude(c).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_REQUIRED"))
                .andExpect(jsonPath("$.kind").value("REQUIRED"));
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.CONCLUSION_PENDING);

        upload(c, ContractDocumentType.AUDIT_TRAIL, AUDIT_TRAIL_NAME).andExpect(status().isOk());
        detail(c).andExpect(jsonPath("$.permissions.canConclude").value(true));
    }

    @Test
    @DisplayName("B4 업로드: 15분 업로드 URL → 등록 · 모두싸인 파일명에 계약번호를 접두하고 처리자를 이력에 남긴다")
    void uploadsSignedDocument() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);

        String presigned = body(presign(c, ContractDocumentType.SIGNED_PDF, "application/pdf", SIGNED_PDF_NAME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900)));
        String key = readString(presigned, "$.s3Key");
        assertThat(key).startsWith("contracts/" + c.getId() + "/uploads/" + admin.getId() + "/SIGNED_PDF/").endsWith(".pdf");

        register(c, ContractDocumentType.SIGNED_PDF, key, SIGNED_PDF_NAME).andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value(c.getContractNumber() + "_" + SIGNED_PDF_NAME))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));

        ContractDocument stored = documents.findByContractIdAndDocumentType(c.getId(), ContractDocumentType.SIGNED_PDF).orElseThrow();
        assertThat(stored.getUploadedBy()).isEqualTo(admin.getId());
        // 업로드 원본 키가 아니라 별도 보관 키로 봉인한다 — 옛 PUT URL로 증거를 바꿀 수 없다.
        assertThat(stored.getS3Key()).isNotEqualTo(key).startsWith("contracts/" + c.getId() + "/documents/");
        verify(storage).sealUpload(eq(key), eq(c.getId()), eq(1_200_000L));

        ContractHistory uploaded = historyOf(c).getLast();
        assertThat(uploaded.getEventType()).isEqualTo(ContractEventType.DOCUMENT_UPLOADED);
        assertThat(uploaded.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
        assertThat(uploaded.getDetail()).contains("SIGNED_PDF").contains("업로드");
    }

    @Test
    @DisplayName("B4 [교체]: 체결 전에는 같은 자리에 다시 올릴 수 있고 옛 파일은 커밋 뒤 지운다")
    void replacesDocumentBeforeConclusion() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        upload(c, ContractDocumentType.SIGNED_PDF, "잘못 올린 파일.pdf").andExpect(status().isOk());
        String oldKey = documents.findByContractIdAndDocumentType(c.getId(), ContractDocumentType.SIGNED_PDF).orElseThrow().getS3Key();

        upload(c, ContractDocumentType.SIGNED_PDF, SIGNED_PDF_NAME).andExpect(status().isOk());

        assertThat(documents.findByContractIdOrderByDocumentTypeAsc(c.getId())).hasSize(1)
                .first().extracting(ContractDocument::getOriginalName).isEqualTo(c.getContractNumber() + "_" + SIGNED_PDF_NAME);
        verify(storage).deleteAfterCommit(oldKey);
        assertThat(historyOf(c).getLast().getDetail()).contains("교체");
    }

    @Test
    @DisplayName("B4: 체결 전에는 삭제도 된다 — 삭제 후 체결 완료가 다시 닫힌다 · 없는 문서 삭제는 404")
    void deletesDocumentBeforeConclusion() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        upload(c, ContractDocumentType.SIGNED_PDF, SIGNED_PDF_NAME).andExpect(status().isOk());
        upload(c, ContractDocumentType.AUDIT_TRAIL, AUDIT_TRAIL_NAME).andExpect(status().isOk());

        deleteDocument(c, ContractDocumentType.AUDIT_TRAIL).andExpect(status().isNoContent());

        detail(c).andExpect(jsonPath("$.permissions.canConclude").value(false));
        assertThat(historyOf(c).getLast().getEventType()).isEqualTo(ContractEventType.DOCUMENT_DELETED);
        assertThat(historyOf(c).getLast().getActorDisplayName()).isEqualTo(OPERATOR_NAME);
        deleteDocument(c, ContractDocumentType.AUDIT_TRAIL).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("B4 업로드 검증: PDF만 · 경로 없는 파일명만 · 생성본 자리는 운영자가 올리지 못한다")
    void validatesUploadRequest() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);

        presign(c, ContractDocumentType.SIGNED_PDF, "image/png", "서명.pdf").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_INVALID"));
        presign(c, ContractDocumentType.SIGNED_PDF, "application/pdf", "서명.docx").andExpect(status().isBadRequest());
        presign(c, ContractDocumentType.SIGNED_PDF, "application/pdf", "../서명.pdf").andExpect(status().isBadRequest());
        presign(c, ContractDocumentType.GENERATED_DRAFT, "application/pdf", "계약서.pdf").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("B4 등록 검증: 이 계약·이 운영자·이 문서 종류로 발급한 업로드 키만 받는다")
    void registerAcceptsOnlyOwnUploadKey() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        Contract other = seed(ContractStatus.CONCLUSION_PENDING);
        String uuid = java.util.UUID.randomUUID() + ".pdf";

        for (String foreignKey : new String[]{
                "contracts/" + other.getId() + "/uploads/" + admin.getId() + "/SIGNED_PDF/" + uuid,   // 다른 계약
                "contracts/" + c.getId() + "/uploads/" + (admin.getId() + 1) + "/SIGNED_PDF/" + uuid, // 다른 운영자
                "contracts/" + c.getId() + "/uploads/" + admin.getId() + "/AUDIT_TRAIL/" + uuid,      // 다른 종류
                "contracts/" + c.getId() + "/documents/" + uuid}) {                                   // 봉인된 키
            register(c, ContractDocumentType.SIGNED_PDF, foreignKey, SIGNED_PDF_NAME).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_INVALID"));
        }
        assertThat(documents.findByContractIdOrderByDocumentTypeAsc(c.getId())).isEmpty();
    }

    @Test
    @DisplayName("체결 문서 카드는 체결 처리 대기에만 있다 — 서명 진행중에는 업로드할 수 없다")
    void uploadsOnlyWhilePendingConclusion() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        detail(c).andExpect(jsonPath("$.permissions.canUploadDocument").value(false));
        presign(c, ContractDocumentType.SIGNED_PDF, "application/pdf", SIGNED_PDF_NAME).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
        String key = "contracts/" + c.getId() + "/uploads/" + admin.getId() + "/SIGNED_PDF/" + java.util.UUID.randomUUID() + ".pdf";
        register(c, ContractDocumentType.SIGNED_PDF, key, SIGNED_PDF_NAME).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ C4

    @Test
    @DisplayName("C4 체결 완료: 계약이 성립하고 처리자 실명 이력 · 양측 통지 · 양측 화면 체결완료 · 공구는 공구 모듈이 붙기 전까지 만들었다고 응답하지 않는다")
    void concludesContract() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        upload(c, ContractDocumentType.SIGNED_PDF, SIGNED_PDF_NAME).andExpect(status().isOk());
        upload(c, ContractDocumentType.AUDIT_TRAIL, AUDIT_TRAIL_NAME).andExpect(status().isOk());

        conclude(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUDED"));

        Contract saved = reload(c);
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.CONCLUDED);
        assertThat(saved.getConcludedAt()).isAfterOrEqualTo(now);
        assertThat(saved.getGroupBuyId()).isNull();

        ContractHistory concluded = historyOf(c).getLast();
        assertThat(concluded.getEventType()).isEqualTo(ContractEventType.CONCLUDED);
        assertThat(concluded.getActorDisplayName()).isEqualTo(OPERATOR_NAME);

        detail(c).andExpect(jsonPath("$.contract.statusLabel").value("체결완료"))
                .andExpect(jsonPath("$.contract.statusTone").value("SUCCESS"))
                .andExpect(jsonPath("$.stepper.concludedAt").exists())
                .andExpect(jsonPath("$.stepper.concludedActorName").value(OPERATOR_NAME))
                .andExpect(jsonPath("$.groupBuy.groupBuyId").doesNotExist());
        sellerDetail(c).andExpect(jsonPath("$.status").value("CONCLUDED"));
        creatorDetail(c).andExpect(jsonPath("$.status").value("CONCLUDED"));
        summary().andExpect(jsonPath("$.queues.CONCLUSION").value(0)).andExpect(jsonPath("$.tabCounts.CONCLUDED").value(1));
        verify(notifier).notifyBothParties(argThat(x -> x.getId().equals(c.getId())), eq("CONCLUDED"));
    }

    @Test
    @DisplayName("C4 진입 조건은 서버가 다시 본다 — 체결 처리 대기가 아니면 문서가 있어도 409")
    void conclusionOnlyFromConclusionPending() throws Exception {
        for (ContractStatus status : new ContractStatus[]{ContractStatus.REVIEW_PENDING, ContractStatus.SIGNING,
                ContractStatus.EXPIRED, ContractStatus.CANCELED}) {
            Contract c = seed(status);
            attach(c, ContractDocumentType.SIGNED_PDF);
            attach(c, ContractDocumentType.AUDIT_TRAIL);
            conclude(c).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
            detail(c).andExpect(jsonPath("$.permissions.canConclude").value(false));
            assertThat(reload(c).getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("브랜드가 [계약 취소]한 뒤에는 체결할 수 없다 — 공구 생성과 종결이 동시에 일어나지 않는다")
    void cannotConcludeAfterBrandCancel() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        attach(c, ContractDocumentType.SIGNED_PDF);
        attach(c, ContractDocumentType.AUDIT_TRAIL);

        mockMvc.perform(post(SELLER_CONTRACTS + "/" + c.getId() + "/cancel").header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SCHEDULE_CHANGE\"}"))
                .andExpect(status().isOk());

        conclude(c).andExpect(status().isConflict());
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.CANCELED);
    }

    // ------------------------------------------------------------------ B5

    @Test
    @DisplayName("B5 체결완료: 액션이 없고 서명 기록·체결 문서가 영구히 잠긴다(교체·삭제 불가)")
    void concludedContractIsLocked() throws Exception {
        Contract c = seed(ContractStatus.CONCLUDED);
        attach(c, ContractDocumentType.SIGNED_PDF);
        attach(c, ContractDocumentType.AUDIT_TRAIL);

        detail(c).andExpect(jsonPath("$.permissions.canApprove").value(false))
                .andExpect(jsonPath("$.permissions.canReject").value(false))
                .andExpect(jsonPath("$.permissions.canUpdateSignature").value(false))
                .andExpect(jsonPath("$.permissions.canConclude").value(false))
                .andExpect(jsonPath("$.permissions.canExpire").value(false))
                .andExpect(jsonPath("$.permissions.canHandleResend").value(false))
                .andExpect(jsonPath("$.permissions.canUploadDocument").value(false));

        presign(c, ContractDocumentType.SIGNED_PDF, "application/pdf", SIGNED_PDF_NAME).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_LOCKED"));
        String key = "contracts/" + c.getId() + "/uploads/" + admin.getId() + "/SIGNED_PDF/" + java.util.UUID.randomUUID() + ".pdf";
        register(c, ContractDocumentType.SIGNED_PDF, key, SIGNED_PDF_NAME).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_LOCKED"));
        deleteDocument(c, ContractDocumentType.AUDIT_TRAIL).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_LOCKED"));
        signatures(c, null, null, c.getVersion()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_SIGNATURE_UPDATE_LOCKED"));
        conclude(c).andExpect(status().isConflict());
        expire(c, true).andExpect(status().isConflict());
        handleResend(c).andExpect(status().isConflict());

        assertThat(documents.findByContractIdOrderByDocumentTypeAsc(c.getId())).hasSize(2);
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.CONCLUDED);
    }

    @Test
    @DisplayName("B5: 체결 문서는 단일 원본 — 운영자·브랜드·인플루언서가 같은 파일을 각자 새 URL로 받는다")
    void concludedDocumentsAreSharedSingleSource() throws Exception {
        Contract c = seed(ContractStatus.CONCLUDED);
        attach(c, ContractDocumentType.SIGNED_PDF);
        attach(c, ContractDocumentType.AUDIT_TRAIL);

        for (ContractDocumentType type : new ContractDocumentType[]{ContractDocumentType.SIGNED_PDF, ContractDocumentType.AUDIT_TRAIL}) {
            String admin = body(downloadDocument(c, type).andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiresInSeconds").value(300)));
            String seller = body(mockMvc.perform(get(SELLER_CONTRACTS + "/" + c.getId() + "/documents/" + type)
                    .header(HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isOk()));
            String studio = body(mockMvc.perform(get(CREATOR_CONTRACTS + "/" + c.getId() + "/documents/" + type)
                    .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isOk()));
            assertThat(readString(seller, "$.downloadUrl")).isEqualTo(readString(admin, "$.downloadUrl"));
            assertThat(readString(studio, "$.downloadUrl")).isEqualTo(readString(admin, "$.downloadUrl"));
        }
    }

    @Test
    @DisplayName("서명 전(체결 전)에는 양측이 체결 문서를 받을 수 없다 — 운영자만 올리고 확인한다")
    void partiesCannotDownloadBeforeConclusion() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        attach(c, ContractDocumentType.SIGNED_PDF);

        downloadDocument(c, ContractDocumentType.SIGNED_PDF).andExpect(status().isOk());
        mockMvc.perform(get(SELLER_CONTRACTS + "/" + c.getId() + "/documents/SIGNED_PDF")
                .header(HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isNotFound());
        mockMvc.perform(get(CREATOR_CONTRACTS + "/" + c.getId() + "/documents/SIGNED_PDF")
                .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isNotFound());
        downloadDocument(c, ContractDocumentType.AUDIT_TRAIL).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_NOT_FOUND"));
    }
}

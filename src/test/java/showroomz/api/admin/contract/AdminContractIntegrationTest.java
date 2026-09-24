package showroomz.api.admin.contract;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.api.admin.contract.service.*;
import showroomz.api.app.auth.entity.*;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.repository.*;
import showroomz.domain.contract.type.*;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminContractIntegrationTest extends IntegrationTestSupport {
    private static final String BASE = "/v1/admin/contracts";
    @Autowired ContractRepository contracts;
    @Autowired ContractHistoryRepository histories;
    @Autowired ContractDocumentRepository documents;
    @Autowired ContractResendRequestRepository resends;
    @Autowired CreatorRepository creators;
    @Autowired UserRepository users;
    @Autowired AdminContractCommandService commands;
    @MockitoBean ContractDocumentStorage storage;
    @MockitoBean ContractPdfRenderer renderer;
    private BrandFixture.Brand brand;
    private Seller admin;
    private Creator creator;
    private String token;
    private final LocalDateTime now = LocalDateTime.now().withNano(0);
    private int sequence;

    @BeforeEach void actors() {
        brand = fixture.createBrand("contract-brand@test.local", "계약 브랜드");
        admin = fixture.createAdmin("contract-admin@test.local", "김운영");
        token = adminToken(admin);
        Users user = users.save(new Users("contract-creator", "creator", "creator@test.local", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, now, now));
        creator = creators.save(Creator.builder().user(user).showroomName("계약 쇼룸").realName("김서명")
                .snsType(showroomz.domain.market.type.SnsType.INSTAGRAM).channelUrl("https://instagram.com/test")
                .accountId("test-creator").followerCount(1000)
                .businessEmail("creator@test.local").businessType(CreatorBusinessType.INDIVIDUAL).build());
        when(storage.download(any())).thenAnswer(a -> {
            ContractDocument d = a.getArgument(0);
            return new DownloadResponse("https://signed.test/" + d.getDocumentType(), d.getOriginalName(), d.getSizeBytes(), 300, d.getSourceReviewRequestedAt());
        });
        when(storage.sealUpload(anyString(), anyLong(), anyLong())).thenReturn("sealed.pdf");
        when(storage.putGenerated(anyLong(), any())).thenReturn("generated.pdf");
        when(renderer.render(anyString(), anyString())).thenReturn("%PDF-test".getBytes());
    }

    @Test void hidesDraftsAndRequiresAdmin() throws Exception {
        Contract draft = contract(ContractStatus.DRAFT);
        mockMvc.perform(get(BASE).header("Authorization", sellerToken(brand.seller()))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + draft.getId()).header("Authorization", token)).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/summary").header("Authorization", token)).andExpect(jsonPath("$.tabCounts.ALL").value(0));
        mockMvc.perform(get(BASE).param("size", "-1").header("Authorization", token)).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE).param("tab", "DRAFT").header("Authorization", token)).andExpect(status().isBadRequest());
    }

    @Test void summaryCountsRejectedOnlyInAllAndDeduplicatesResends() throws Exception {
        contract(ContractStatus.DRAFT);
        Contract review = contract(ContractStatus.REVIEW_PENDING);
        contract(ContractStatus.REVIEW_PENDING);
        contract(ContractStatus.CONCLUSION_PENDING);
        contract(ContractStatus.CONCLUDED);
        contract(ContractStatus.REVIEW_REJECTED);
        contract(ContractStatus.CANCELED);
        contract(ContractStatus.EXPIRED);
        Contract overdue = contract(ContractStatus.SIGNING);
        contract(ContractStatus.SIGNING);
        contract(ContractStatus.SIGNING);
        inTransaction(() -> { contracts.findById(overdue.getId()).orElseThrow().approveReview(now.minusDays(3), now.minusDays(1), now); return null; });
        resends.save(ContractResendRequest.of(overdue, ContractActorType.SELLER, brand.marketId(), now.minusHours(4)));
        resends.save(ContractResendRequest.of(overdue, ContractActorType.CREATOR, creator.getId(), now.minusHours(3)));
        mockMvc.perform(get(BASE + "/summary").header("Authorization", token))
                .andExpect(jsonPath("$.tabCounts.ALL").value(10)).andExpect(jsonPath("$.tabCounts.CLOSED").value(2))
                .andExpect(jsonPath("$.queues.REVIEW").value(2)).andExpect(jsonPath("$.queues.EXPIRY").value(1))
                .andExpect(jsonPath("$.queues.RESEND").value(1)).andExpect(jsonPath("$.actionRequiredCount").value(5));
        mockMvc.perform(get(BASE).param("queue", "RESEND").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].contractId").value(overdue.getId()));
        for (String keyword : List.of("계약 브랜드", "계약 쇼룸", review.getContractNumber(), review.getTitle())) {
            mockMvc.perform(get(BASE).param("keyword", keyword).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].permissions").doesNotExist());
        }
        mockMvc.perform(get(BASE).param("keyword", "없는 검색어").header("Authorization", token))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test void approvalValidatesChecklistTimesAndRecordsTwoEvents() throws Exception {
        Contract c = contract(ContractStatus.REVIEW_PENDING);
        approve(c, new ApproveRequest(null, true, true, now.minusHours(1), now.plusDays(2)), 400);
        approve(c, new ApproveRequest(true, true, true, now.plusDays(1), now.plusDays(2)), 400);
        approve(c, new ApproveRequest(true, true, true, now.minusDays(5), now.plusDays(2)), 400);
        approve(c, new ApproveRequest(true, true, true, now.minusHours(1), now.minusHours(2)), 400);
        approve(c, new ApproveRequest(true, true, true, now.minusHours(1), now.plusDays(2)), 200);
        assertThat(histories.findByContractIdOrderByOccurredAtAscIdAsc(c.getId())).extracting(ContractHistory::getEventType)
                .containsExactly(ContractEventType.SIGNATURE_SENT, ContractEventType.REVIEW_APPROVED);
        assertThat(histories.findByContractIdOrderByOccurredAtAscIdAsc(c.getId())).allMatch(h -> h.getActorDisplayName().equals("김운영"));
        mockMvc.perform(get(BASE + "/" + c.getId()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contract.status").value("SIGNING"))
                .andExpect(jsonPath("$.warningFlags").doesNotExist()).andExpect(jsonPath("$.stepper.reviewApprovedActorName").value("김운영"));
        approve(c, new ApproveRequest(true, true, true, now.minusHours(1), now.plusDays(2)), 409);
    }

    @Test void rejectionAlwaysRequiresDetailAndCannotBeReversed() throws Exception {
        Contract c = contract(ContractStatus.REVIEW_PENDING);
        mockMvc.perform(post(BASE + "/" + c.getId() + "/review/reject").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new RejectRequest(ContractReviewRejectReason.INFO_MISMATCH, " "))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTRACT_REJECT_DETAIL_REQUIRED"));
        mockMvc.perform(post(BASE + "/" + c.getId() + "/review/reject").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new RejectRequest(ContractReviewRejectReason.INFO_MISMATCH, "가".repeat(1000)))))
                .andExpect(status().isOk());
        assertThat(histories.findByContractIdOrderByOccurredAtAscIdAsc(c.getId()).getFirst().getDetail()).contains("가".repeat(1000));
        approve(c, new ApproveRequest(true, true, true, now.minusHours(1), now.plusDays(2)), 409);
    }

    @Test void signaturesAreReversibleVersionedAndAllowLateSigning() throws Exception {
        Contract c = contract(ContractStatus.SIGNING);
        inTransaction(() -> { contracts.findById(c.getId()).orElseThrow().approveReview(now.minusDays(3), now.minusDays(1), now); return null; });
        long version = contracts.findById(c.getId()).orElseThrow().getVersion();
        signature(c, new SignatureRequest(now.minusHours(1), now.minusHours(2), version), 200);
        assertThat(contracts.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.CONCLUSION_PENDING);
        signature(c, new SignatureRequest(null, null, version), 409);
        signature(c, new SignatureRequest(null, now.minusHours(2), version + 1), 200);
        signature(c, new SignatureRequest(null, now.minusHours(2), version + 2), 200);
        assertThat(contracts.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(histories.findByContractIdOrderByOccurredAtAscIdAsc(c.getId()).getLast().getDetail()).contains("변경 없음");
        signature(c, new SignatureRequest(now.plusDays(1), null, version + 3), 400);
    }

    @Test void conclusionRequiresBothDocumentsAndPermanentlyLocks() throws Exception {
        Contract c = contract(ContractStatus.CONCLUSION_PENDING);
        mockMvc.perform(post(BASE + "/" + c.getId() + "/conclude").header("Authorization", token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.kind").value("REQUIRED"));
        document(c, ContractDocumentType.SIGNED_PDF);
        document(c, ContractDocumentType.AUDIT_TRAIL);
        mockMvc.perform(get(BASE + "/" + c.getId()).header("Authorization", token)).andExpect(jsonPath("$.permissions.canConclude").value(true));
        mockMvc.perform(post(BASE + "/" + c.getId() + "/conclude").header("Authorization", token)).andExpect(status().isOk());
        signature(c, new SignatureRequest(null, null, 1L), 409);
        mockMvc.perform(delete(BASE + "/" + c.getId() + "/documents/SIGNED_PDF").header("Authorization", token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_LOCKED"));
        mockMvc.perform(post(BASE + "/" + c.getId() + "/conclude").header("Authorization", token)).andExpect(status().isConflict());
        assertThat(contracts.findById(c.getId()).orElseThrow().getGroupBuyId()).isNull();
    }

    @Test void expiryRequiresElapsedDeadlineAndDashboardRecheck() throws Exception {
        Contract c = contract(ContractStatus.SIGNING);
        expire(c, true, 409);
        inTransaction(() -> { contracts.findById(c.getId()).orElseThrow().approveReview(now.minusDays(3), now.minusDays(1), now); return null; });
        expire(c, false, 400);
        signature(c, new SignatureRequest(now.minusHours(1), null, contracts.findById(c.getId()).orElseThrow().getVersion()), 200);
        expire(c, true, 200);
        Contract expired = contracts.findById(c.getId()).orElseThrow();
        assertThat(expired.getCloseActorType()).isEqualTo(ContractActorType.ADMIN);
        assertThat(expired.getCloseReasonCode()).isNull();
        expire(contract(ContractStatus.CONCLUSION_PENDING), true, 409);
    }

    @Test void resendHandlesBothPartiesWithoutChangingDeadline() throws Exception {
        Contract c = contract(ContractStatus.SIGNING);
        resends.save(ContractResendRequest.of(c, ContractActorType.SELLER, brand.marketId(), now));
        resends.save(ContractResendRequest.of(c, ContractActorType.CREATOR, creator.getId(), now));
        for (int i = 0; i < 2; i++) mockMvc.perform(post(BASE + "/" + c.getId() + "/resend/handle").header("Authorization", token)).andExpect(status().isOk());
        assertThat(resends.findByContractIdOrderByRequestedAtDescIdDesc(c.getId())).allMatch(r -> r.isHandled() && r.getHandledBy().equals(admin.getId()));
        assertThat(contracts.findById(c.getId()).orElseThrow().getSignatureDeadlineAt()).isEqualTo(c.getSignatureDeadlineAt());
    }

    @Test void documentsCanBeReplacedAndDeletedOnlyBeforeConclusion() throws Exception {
        Contract c = contract(ContractStatus.CONCLUSION_PENDING);
        String key = "contracts/" + c.getId() + "/uploads/" + admin.getId() + "/SIGNED_PDF/" + UUID.randomUUID() + ".pdf";
        var request = new RegisterDocumentRequest(ContractDocumentType.SIGNED_PDF, key, "계약서.pdf", 100L);
        for (int i = 0; i < 2; i++) mockMvc.perform(post(BASE + "/" + c.getId() + "/documents").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request))).andExpect(status().isOk());
        assertThat(documents.findByContractIdInTypeOrder(c.getId())).hasSize(1);
        verify(storage).deleteAfterCommit("sealed.pdf");
        mockMvc.perform(delete(BASE + "/" + c.getId() + "/documents/SIGNED_PDF").header("Authorization", token)).andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/" + c.getId() + "/documents").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new RegisterDocumentRequest(ContractDocumentType.SIGNED_PDF, "other-contract.pdf", "x.pdf", 100L))))
                .andExpect(status().isBadRequest());
    }

    @Test void generatedPdfCacheIsInvalidatedByResubmissionAlsoForSeller() throws Exception {
        Contract c = contract(ContractStatus.REVIEW_PENDING);
        for (int i = 0; i < 2; i++) mockMvc.perform(get(BASE + "/" + c.getId() + "/document-draft").header("Authorization", token)).andExpect(status().isOk());
        verify(renderer, times(1)).render(anyString(), anyString());
        inTransaction(() -> {
            Contract managed = contracts.findById(c.getId()).orElseThrow();
            managed.applyReviewRequested(c.getContractNumber(), null, null, now);
            return null;
        });
        mockMvc.perform(get(BASE + "/" + c.getId() + "/documents/GENERATED_DRAFT").header("Authorization", token)).andExpect(status().isNotFound());
        // 브랜드도 같은 판정이다 — 이전 제출본의 생성본은 지금 계약서가 아니다.
        mockMvc.perform(get("/v1/seller/contracts/" + c.getId() + "/documents/GENERATED_DRAFT").header("Authorization", sellerToken(brand.seller())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/" + c.getId() + "/document-draft").header("Authorization", token)).andExpect(status().isOk());
        verify(renderer, times(2)).render(anyString(), anyString());
        mockMvc.perform(get("/v1/seller/contracts/" + c.getId() + "/documents/GENERATED_DRAFT").header("Authorization", sellerToken(brand.seller())))
                .andExpect(status().isOk());
    }

    @Test void simultaneousSignatureUpdatesAcceptOnlyOneVersion() throws Exception {
        Contract c = contract(ContractStatus.SIGNING);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger(), conflicts = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    commands.updateSignatures(c.getId(), admin.getId(), new SignatureRequest(now.minusHours(1), null, c.getVersion()));
                    success.incrementAndGet();
                } catch (showroomz.global.error.exception.BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(showroomz.global.error.exception.ErrorCode.CONTRACT_MODIFIED_ELSEWHERE);
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
        }
        assertThat(success.get()).isEqualTo(1); assertThat(conflicts.get()).isEqualTo(1);
    }

    @Test void conclusionRacingSignatureClearCannotConcludeUnsignedContract() throws Exception {
        Contract c = contract(ContractStatus.CONCLUSION_PENDING);
        document(c, ContractDocumentType.SIGNED_PDF); document(c, ContractDocumentType.AUDIT_TRAIL);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (boolean conclude : List.of(true, false)) futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (conclude) commands.conclude(c.getId(), admin.getId());
                    else commands.updateSignatures(c.getId(), admin.getId(), new SignatureRequest(null, null, c.getVersion()));
                    success.incrementAndGet();
                } catch (showroomz.global.error.exception.BusinessException e) {
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(409);
                } catch (InterruptedException e) { throw new RuntimeException(e); }
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
        }
        assertThat(success.get()).isEqualTo(1);
        Contract saved = contracts.findById(c.getId()).orElseThrow();
        if (saved.getStatus() == ContractStatus.CONCLUDED) {
            assertThat(saved.getBrandSignedAt()).isNotNull(); assertThat(saved.getCreatorSignedAt()).isNotNull();
        } else {
            assertThat(saved.getStatus()).isEqualTo(ContractStatus.SIGNING);
            assertThat(saved.getConcludedAt()).isNull();
        }
    }

    @Test void failedPdfGenerationDoesNotPersistCacheOrHistory() throws Exception {
        Contract c = contract(ContractStatus.REVIEW_PENDING);
        when(renderer.render(anyString(), anyString())).thenThrow(new showroomz.global.error.exception.BusinessException(
                showroomz.global.error.exception.ErrorCode.CONTRACT_PDF_GENERATION_FAILED));
        mockMvc.perform(get(BASE + "/" + c.getId() + "/document-draft").header("Authorization", token)).andExpect(status().isServiceUnavailable());
        assertThat(documents.findByContractIdInTypeOrder(c.getId())).isEmpty();
        assertThat(histories.findByContractIdOrderByOccurredAtAscIdAsc(c.getId())).isEmpty();
        assertThat(contracts.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(ContractStatus.REVIEW_PENDING);
    }

    private Contract contract(ContractStatus status) {
        return contracts.saveAndFlush(Contract.builder().market(brand.market()).creator(creator).title("테스트 공구 " + (++sequence))
                .contractNumber("CTR-20260923-" + sequence).status(status).reviewRequestedAt(now.minusDays(4))
                .signatureRequestedAt(status == ContractStatus.SIGNING || status == ContractStatus.CONCLUSION_PENDING ? now.minusDays(3) : null)
                .signatureDeadlineAt(now.plusDays(3)).brandSignedAt(status == ContractStatus.CONCLUSION_PENDING ? now.minusHours(1) : null)
                .creatorSignedAt(status == ContractStatus.CONCLUSION_PENDING ? now.minusHours(2) : null)
                .signatureAsOf(now).groupBuyStartAt(now.plusDays(7)).groupBuyEndAt(now.plusDays(14)).fixedFeeAmount(0).build());
    }
    private void document(Contract c, ContractDocumentType type) {
        documents.save(ContractDocument.builder().contract(c).documentType(type).s3Key("sealed-" + type).originalName("x.pdf").sizeBytes(100L).uploadedAt(now).build());
    }
    private void approve(Contract c, ApproveRequest request, int code) throws Exception {
        mockMvc.perform(post(BASE + "/" + c.getId() + "/review/approve").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request))).andExpect(status().is(code));
    }
    private void signature(Contract c, SignatureRequest request, int code) throws Exception {
        mockMvc.perform(put(BASE + "/" + c.getId() + "/signatures").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(request))).andExpect(status().is(code));
    }
    private void expire(Contract c, boolean checked, int code) throws Exception {
        mockMvc.perform(post(BASE + "/" + c.getId() + "/expire").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new ExpireRequest(checked)))).andExpect(status().is(code));
    }
}

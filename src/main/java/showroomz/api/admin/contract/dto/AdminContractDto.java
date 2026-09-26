package showroomz.api.admin.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import showroomz.api.seller.contract.dto.ContractDetailResponse;
import showroomz.domain.contract.type.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AdminContractDto {
    private AdminContractDto() {}

    public record ApproveRequest(Boolean recipientsRegistered, Boolean documentUploaded, Boolean requestSent,
                                 LocalDateTime signatureRequestedAt, LocalDateTime signatureDeadlineAt) {}
    public record RejectRequest(@NotNull ContractReviewRejectReason reasonCode, @Size(max = 1000) String reasonDetail) {}
    public record SignatureRequest(LocalDateTime brandSignedAt, LocalDateTime creatorSignedAt,
                                   @NotNull @PositiveOrZero Long version) {}
    public record ExpireRequest(Boolean dashboardRechecked) {}
    /** 운영자 [계약 취소] — 서명 요청 발송 이후 체결 전. 모두싸인 서명 요청을 먼저 거둬야 한다(API 미도입). */
    public record CancelRequest(Boolean signatureRequestWithdrawn, @NotNull ContractCloseReasonCode reasonCode,
                                @Size(max = 1000) String memo) {}
    @Schema(description = "체결 문서 업로드 URL 발급 요청")
    public record PresignRequest(
            @Schema(description = "올릴 문서 종류 — GENERATED_DRAFT는 서버가 만드는 문서라 업로드할 수 없다",
                    allowableValues = {"SIGNED_PDF", "AUDIT_TRAIL"}, example = "SIGNED_PDF",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ContractDocumentType documentType,
            @Schema(description = "application/pdf 고정", allowableValues = {"application/pdf"},
                    example = "application/pdf", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String contentType,
            @Schema(description = "원본 파일명 — .pdf로 끝나고 200자 이하, / · \\ · 제어문자 불가",
                    example = "서명완료_계약서.pdf", maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String fileName) {}
    public record RegisterDocumentRequest(@NotNull ContractDocumentType documentType,
                                          @NotBlank @Size(max = 512) String s3Key,
                                          @NotBlank @Size(max = 200) String fileName,
                                          @NotNull @Positive Long sizeBytes) {}
    @Schema(description = "체결 문서 업로드 URL")
    public record PresignResponse(
            @Schema(description = "업로드 위치 — 등록 API(POST /documents)에 그대로 보낸다",
                    example = "contracts/12/uploads/3/SIGNED_PDF/0b6f2c1e-8a4d-4c1b-9d3e-5f7a2b8c9d10.pdf")
            String s3Key,
            @Schema(description = "S3 presigned PUT URL — 파일 바이트를 이 URL로 직접 PUT한다. 헤더는 Content-Type 하나만")
            String uploadUrl,
            @Schema(description = "PUT 요청에 그대로 실을 Content-Type", example = "application/pdf")
            String contentType,
            @Schema(description = "uploadUrl 유효 시간(초)", example = "900")
            long expiresInSeconds) {}
    public record DownloadResponse(String downloadUrl, String fileName, Long sizeBytes, long expiresInSeconds,
                                   LocalDateTime sourceReviewRequestedAt) {}
    /** @param groupBuyNumber 체결 처리 응답에만 — 체결 트랜잭션이 함께 만든 공구의 번호(공구 설계서 2-4). 그 외 null */
    public record ProcessResponse(Long contractId, ContractStatus status, Long version, String groupBuyNumber) {}
    public record ListItem(Long contractId, String contractNumber, String title, String brandName, String creatorName,
                           int itemCount, LocalDateTime startAt, LocalDateTime endAt, LocalDateTime reviewRequestedAt,
                           ContractStatus status, String statusLabel, ContractStatusTone statusTone) {}
    public record Summary(Map<AdminContractQueue, Long> queues, Map<AdminContractTab, Long> tabCounts,
                          long actionRequiredCount) {}
    /** @param email 모두싸인 수신자 등록용 — 브랜드 계정 이메일({@code Seller.email}). 계약서 PDF의 「브랜드_이메일」과 같은 값 */
    public record Brand(Long marketId, String name, String link,
                        @Schema(description = "모두싸인 수신자 등록용 브랜드 이메일(계정 이메일)", example = "contact@glowlab.kr")
                        String email) {}
    /** @param email 모두싸인 수신자 등록용 — 인플루언서 비즈니스 이메일({@code Creator.businessEmail}). 계약서 PDF의 「인플루언서_이메일」과 같은 값 */
    public record Creator(Long creatorId, String name, String link,
                          @Schema(description = "모두싸인 수신자 등록용 인플루언서 비즈니스 이메일", example = "hayun.biz@gmail.com")
                          String email) {}
    public record ContractInfo(Long contractId, String contractNumber, String title, ContractStatus status,
                               String statusLabel, ContractStatusTone statusTone, LocalDateTime startAt,
                               LocalDateTime endAt, Integer days, Brand brand, Creator creator, Long threadId) {}
    public record Stepper(LocalDateTime reviewApprovedAt, String reviewApprovedActorName,
                          LocalDateTime signatureRequestedAt, int signedCount, LocalDateTime concludedAt,
                          String concludedActorName) {}
    public record Review(LocalDateTime requestedAt, String waitingElapsed, LocalDateTime approvedAt,
                         LocalDateTime rejectedAt, ContractDetailResponse.Review.RejectReason rejectReason) {}
    public record Signature(LocalDateTime requestedAt, LocalDateTime deadlineAt, LocalDateTime brandSignedAt,
                            LocalDateTime creatorSignedAt, LocalDateTime asOf, String asOfActorName,
                            long deadlinePassedDays) {}
    public record Document(ContractDocumentType type, boolean exists, String fileName, String downloadUrl,
                           LocalDateTime uploadedAt, LocalDateTime sourceReviewRequestedAt) {}
    public record Resend(long pendingCount, LocalDateTime lastRequestedAt, ContractActorType lastRequesterType) {}
    public record GroupBuy(Long groupBuyId, String groupBuyNumber, String status) {}
    public record Permissions(boolean canApprove, boolean canReject, boolean canUpdateSignature,
                               boolean canConclude, boolean canExpire, boolean canHandleResend,
                               boolean canUploadDocument, boolean canCancel) {}
    public record History(ContractEventType eventType, ContractActorType actorType, Long actorId,
                          String actorDisplayName, String detail, LocalDateTime occurredAt) {}
    public record Detail(ContractInfo contract, Stepper stepper, Review review, Signature signature,
                         List<ContractDetailResponse.Item> items, ContractDetailResponse.Content content,
                         ContractDetailResponse.FixedFee fixedFee, ContractDetailResponse.Settlement settlement,
                         ContractDetailResponse.Closure closure, List<Document> documents, Resend resend,
                         GroupBuy groupBuy, Permissions permissions, List<History> history, Long version) {}
}

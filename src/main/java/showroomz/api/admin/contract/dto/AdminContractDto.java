package showroomz.api.admin.contract.dto;

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
    public record PresignRequest(@NotNull ContractDocumentType documentType,
                                 @NotBlank String contentType, @NotBlank @Size(max = 200) String fileName) {}
    public record RegisterDocumentRequest(@NotNull ContractDocumentType documentType,
                                          @NotBlank @Size(max = 512) String s3Key,
                                          @NotBlank @Size(max = 200) String fileName,
                                          @NotNull @Positive Long sizeBytes) {}
    public record PresignResponse(String s3Key, String uploadUrl, String contentType, long expiresInSeconds) {}
    public record DownloadResponse(String downloadUrl, String fileName, Long sizeBytes, long expiresInSeconds,
                                   LocalDateTime sourceReviewRequestedAt) {}
    public record ProcessResponse(Long contractId, ContractStatus status, Long version) {}
    public record ListItem(Long contractId, String contractNumber, String title, String brandName, String creatorName,
                           int itemCount, LocalDateTime startAt, LocalDateTime endAt, LocalDateTime reviewRequestedAt,
                           ContractStatus status, String statusLabel, ContractStatusTone statusTone) {}
    public record Summary(Map<AdminContractQueue, Long> queues, Map<AdminContractTab, Long> tabCounts,
                          long actionRequiredCount) {}
    public record Brand(Long marketId, String name, String link) {}
    public record Creator(Long creatorId, String name, String link) {}
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
                               boolean canUploadDocument) {}
    public record History(ContractEventType eventType, ContractActorType actorType, Long actorId,
                          String actorDisplayName, String detail, LocalDateTime occurredAt) {}
    public record Detail(ContractInfo contract, Stepper stepper, Review review, Signature signature,
                         List<ContractDetailResponse.Item> items, ContractDetailResponse.Content content,
                         ContractDetailResponse.FixedFee fixedFee, ContractDetailResponse.Settlement settlement,
                         ContractDetailResponse.Closure closure, List<Document> documents, Resend resend,
                         GroupBuy groupBuy, Permissions permissions, List<History> history, Long version) {}
}

package showroomz.api.admin.contract.dto;

import showroomz.api.common.sanction.SanctionHoldView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import showroomz.api.seller.contract.dto.ContractDetailResponse;
import showroomz.domain.contract.type.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AdminContractDto {
    private AdminContractDto() {}

    @Schema(description = "모두싸인 발송을 완료한 검토 승인 기록")
    public record ApproveRequest(
            @Schema(description = "모두싸인 수신자 등록 확인 — true 필수", example = "true") Boolean recipientsRegistered,
            @Schema(description = "계약서 업로드 확인 — true 필수", example = "true") Boolean documentUploaded,
            @Schema(description = "양측 서명 요청 발송 확인 — true 필수", example = "true") Boolean requestSent,
            @Schema(description = "실제 발송 시각 — 검토 요청 이후·현재 이전", example = "2026-09-21T09:50:00") LocalDateTime signatureRequestedAt,
            @Schema(description = "서명 기한 — 발송 시각보다 나중", example = "2026-09-28T23:59:59") LocalDateTime signatureDeadlineAt) {}
    @Schema(description = "검토 반려 사유")
    public record RejectRequest(
            @Schema(description = "반려 사유 코드", allowableValues = {"AGREEMENT_MISMATCH", "INFO_MISMATCH", "OBLIGATION_UNVERIFIABLE", "TYPO_OR_OMISSION", "ACCOUNT_STATUS", "ETC"}, example = "INFO_MISMATCH", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ContractReviewRejectReason reasonCode,
            @Schema(description = "브랜드에게 전달할 설명 — 공백 불가, 최대 1000자", example = "사업자 정보가 계약서와 다릅니다.", maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
            @Size(max = 1000) String reasonDetail) {}
    @Schema(description = "서명 시각 전체 갱신 — 미서명 또는 서명 취소 시 null")
    public record SignatureRequest(
            @Schema(description = "브랜드 서명 시각. 발송 이후·현재 이전 또는 null", example = "2026-09-22T11:00:00", nullable = true) LocalDateTime brandSignedAt,
            @Schema(description = "인플루언서 서명 시각. 발송 이후·현재 이전 또는 null", example = "2026-09-22T12:00:00", nullable = true) LocalDateTime creatorSignedAt,
            @Schema(description = "상세 조회에서 받은 낙관적 잠금 버전", example = "2", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @PositiveOrZero Long version) {}
    public record ExpireRequest(@Schema(description = "모두싸인 대시보드 재확인 여부 — true 필수", example = "true") Boolean dashboardRechecked) {}
    /** 운영자 [계약 취소] — 서명 요청 발송 이후 체결 전. 모두싸인 서명 요청을 먼저 거둬야 한다(API 미도입). */
    @Schema(description = "서명 요청 회수 후 계약 취소 기록")
    public record CancelRequest(
                                @Schema(description = "모두싸인 서명 요청 회수 확인 — true 필수", example = "true") Boolean signatureRequestWithdrawn,
                                @Schema(description = "취소 사유(C6 · 1021 기-06) — PARTY_RENEGOTIATION · PARTY_CANNOT_PROCEED · CONTENT_ERROR_AFTER_REVIEW · ETC. "
                                        + "구 선택지(CONDITION_REVIEW 등)도 받는다. ETC일 때 memo 필수",
                                        allowableValues = {"PARTY_RENEGOTIATION", "PARTY_CANNOT_PROCEED", "CONTENT_ERROR_AFTER_REVIEW", "ETC",
                                                "CONDITION_REVIEW", "OUT_OF_STOCK", "SCHEDULE_CHANGE", "NEGOTIATION_STOPPED"},
                                        example = "PARTY_RENEGOTIATION", requiredMode = Schema.RequiredMode.REQUIRED)
                                @NotNull ContractCloseReasonCode reasonCode,
                                @Schema(description = "상세 사유 — ETC일 때 필수, 최대 1000자", example = "일정 조정 요청", maxLength = 1000, nullable = true)
                                @Size(max = 1000) String memo,
                                @Schema(description = "취소 요청자 — SELLER(브랜드) · CREATOR(인플루언서) · ADMIN(운영자 직권). 필수",
                                        allowableValues = {"SELLER", "CREATOR", "ADMIN"}, example = "SELLER")
                                ContractActorType requesterType,
                                @Schema(description = "요청 경로 — 요청자가 브랜드·인플루언서면 필수, ADMIN이면 보내지 않는다",
                                        example = "THREAD", nullable = true)
                                ContractCancelRequestChannel requestChannel,
                                @Schema(description = "요청 시각 — 요청자가 브랜드·인플루언서면 필수(현재 이전 · 계약 생성 이후), ADMIN이면 보내지 않는다",
                                        example = "2026-08-14T10:05:00", nullable = true)
                                LocalDateTime requestedAt,
                                @Schema(description = "상대방 동의를 운영팀 채널에서 확인함(C6 · 1021 기-06) — 요청자가 브랜드 · 인플루언서면 true 필수. "
                                        + "직권(ADMIN)은 생략 가능", example = "true", nullable = true)
                                Boolean counterpartyConsentConfirmed) {}

    /** 운영자 [해지 처리](1021 설계서 1-4 · 어드민 01 C7) — 성립 뒤 · 공구 오픈 전. 체크 2개 필수. */
    @Schema(description = "계약 해지 처리 — 생성된 공구도 함께 종결되고 되돌릴 수 없다")
    public record TerminateRequest(
            @Schema(description = "해지 사유 — TERMINATE_CANNOT_PROCEED · TERMINATE_RENEGOTIATION · TERMINATE_CONTENT_ERROR · TERMINATE_OTHER",
                    example = "TERMINATE_CANNOT_PROCEED", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ContractTerminationReasonCode reasonCode,
            @Schema(description = "양측에 전달할 설명 — 필수 · 1000자 · 양측 운영팀 채널 카드에 그대로 나간다",
                    example = "브랜드 공급 일정 차질로 양측 합의 하에 해지합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000) String explanation,
            @Schema(description = "상대방 동의를 운영팀 채널에서 확인함 — true 필수", example = "true") Boolean counterpartyConsentConfirmed,
            @Schema(description = "공구가 아직 오픈되지 않았음을 확인함 — true 필수", example = "true") Boolean groupBuyNotOpenedConfirmed,
            @Schema(description = "처리할 해지 요청 ID — 요청 없이 운영자가 처리하면 null", nullable = true) Long requestId) {}

    /** 운영자가 상대방 채널에 「계약 취소 동의 확인」 카드를 보낸다(1021 설계서 1-5 · 20a A8 ②). */
    @Schema(description = "계약 취소 동의 확인 카드 발송")
    public record CancelConsentCardRequest(
            @Schema(description = "취소를 요청한 쪽 — SELLER · CREATOR. 카드는 반대쪽 채널에 붙는다", example = "SELLER",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ContractActorType requesterType,
            @Schema(description = "요청 요지 — 상대에게 그대로 보인다", example = "공구 일정이 맞지 않아 취소를 요청했습니다.",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 1000) String reasonSummary) {}

    @Schema(description = "카드 발송 결과")
    public record CardResponse(Long threadId, Long messageId) {}

    @Schema(description = "처리 대기 중인 해지 요청 — 없으면 null")
    public record TerminationRequestInfo(
            Long requestId,
            @Schema(description = "SELLER · CREATOR", example = "SELLER") ContractActorType requesterType,
            @Schema(example = "무드코스메틱") String requesterName,
            @Schema(description = "요청 사유 코드(공구 중단 요청과 같은 4종)", example = "QUALITY_ISSUE") String reasonCode,
            @Schema(example = "상품 품질 이슈") String reasonLabel,
            @Schema(nullable = true) String memo,
            LocalDateTime requestedAt,
            @Schema(description = "상대방 동의 확인 카드가 붙은 운영팀 채널 메시지", nullable = true) Long consentCardMessageId) {}

    @Schema(description = "해지 결과(B7) — 해지된 계약만")
    public record TerminationInfo(
            LocalDateTime terminatedAt,
            @Schema(example = "TERMINATE_CANNOT_PROCEED") String reasonCode,
            @Schema(example = "당사자 요청 — 공구 진행 불가") String reasonLabel,
            @Schema(description = "양측에 전달한 설명") String explanation,
            @Schema(description = "요청자 — 요청 없이 처리했으면 null", nullable = true) ContractActorType requesterType,
            @Schema(nullable = true) String requesterName,
            @Schema(description = "요청 사유 라벨(브랜드 요청) — B9 「사유 구분」", nullable = true) String requestReasonLabel,
            @Schema(description = "요청 메모", nullable = true) String requestMemo,
            @Schema(description = "처리 운영자 — 어드민에만", nullable = true) String operatorName,
            @Schema(description = "생성된 공구가 함께 종결됐는가") boolean groupBuyClosed) {}
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
    @Schema(description = "S3에 업로드한 체결 문서 등록 또는 교체")
    public record RegisterDocumentRequest(
            @Schema(description = "문서 종류 — 생성본 제외", allowableValues = {"SIGNED_PDF", "AUDIT_TRAIL"}, example = "SIGNED_PDF", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ContractDocumentType documentType,
            @Schema(description = "같은 운영자가 발급받은 presign 응답의 s3Key", example = "contracts/12/uploads/3/SIGNED_PDF/0b6f2c1e-8a4d-4c1b-9d3e-5f7a2b8c9d10.pdf", maxLength = 512, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 512) String s3Key,
            @Schema(description = "원본 PDF 파일명 — .pdf로 끝나고 200자 이하", example = "서명완료_계약서.pdf", maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String fileName,
            @Schema(description = "실제 업로드한 파일 크기(바이트) — 최소 5바이트", example = "1258291", minimum = "5", requiredMode = Schema.RequiredMode.REQUIRED)
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
    @Schema(description = "임시 다운로드 URL 및 문서 정보")
    public record DownloadResponse(
            @Schema(description = "S3 임시 다운로드 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/contracts/12/document.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&...") String downloadUrl,
            @Schema(description = "계약번호가 앞에 붙은 저장 파일명", example = "CTR-20260920-012_서명완료_계약서.pdf") String fileName,
            @Schema(description = "파일 크기(바이트)", example = "1258291") Long sizeBytes,
            @Schema(description = "다운로드 URL 유효 시간(초)", example = "300") long expiresInSeconds,
            @Schema(description = "계약서 생성본이 기준으로 삼은 검토 요청 시각. 체결 문서는 null", nullable = true) LocalDateTime sourceReviewRequestedAt) {}
    /** @param groupBuyNumber 체결 처리 응답에만 — 체결 트랜잭션이 함께 만든 공구의 번호(공구 설계서 2-4). 그 외 null */
    @Schema(description = "관리자 조치 결과 — groupBuyNumber는 체결 완료 응답에서만 값이 있음")
    public record ProcessResponse(
            @Schema(description = "계약 ID", example = "12") Long contractId,
            @Schema(description = "조치 후 계약 상태", example = "SIGNING") ContractStatus status,
            @Schema(description = "조치 후 버전 — 다음 서명 수정 요청에 사용", example = "2") Long version,
            @Schema(description = "체결 시 같은 트랜잭션에서 생성한 공구 번호. 다른 조치에서는 null", nullable = true) String groupBuyNumber) {}
    @Schema(description = "관리자 계약 목록 행")
    public record ListItem(Long contractId, String contractNumber, String title, String brandName, String creatorName,
                           @Schema(description = "계약 상품 수", example = "2") int itemCount,
                           LocalDateTime startAt, LocalDateTime endAt, LocalDateTime reviewRequestedAt,
                           @Schema(description = "계약 상태", example = "REVIEW_PENDING") ContractStatus status,
                           @Schema(description = "화면 표시용 상태명", example = "검토 대기") String statusLabel,
                           @Schema(description = "상태 배지 색", example = "INFO") ContractStatusTone statusTone,
            @Schema(description = "정지 보류 — 당사자 정지면 운영자가 검토 · 체결을 진행하지 않는다(1017 설계서 8-4)", nullable = true) SanctionHoldView sanctionHold) {}
    @Schema(description = "검색 조건과 무관한 관리자 계약 현황")
    public record Summary(
            @Schema(description = "조치 큐별 건수: REVIEW·CONCLUSION·EXPIRY·RESEND") Map<AdminContractQueue, Long> queues,
            @Schema(description = "상태 탭별 건수: ALL·REVIEW_PENDING·SIGNING·CONCLUSION_PENDING·CONCLUDED·CLOSED") Map<AdminContractTab, Long> tabCounts,
            @Schema(description = "네 조치 큐 건수의 합. 계약 중복 가능", example = "7") long actionRequiredCount) {}
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
                               LocalDateTime endAt, Integer days, Brand brand, Creator creator, Long threadId,
            @Schema(description = "정지 보류 — party 가 정지 당사자", nullable = true) SanctionHoldView sanctionHold,
            @Schema(description = "서명 기한이 멈춰 있는가") boolean signatureDeadlinePaused) {}
    public record Stepper(LocalDateTime reviewApprovedAt, String reviewApprovedActorName,
                          LocalDateTime signatureRequestedAt, int signedCount,
                          @Schema(description = "공구 생성 처리 시각(운영자) — 1021 이후 「체결」은 공구 생성을 뜻한다", nullable = true)
                          LocalDateTime concludedAt,
                          String concludedActorName,
                          @Schema(description = "계약 성립 = 양측 서명 완료 시각(1021 기-05) — 서명이 하나라도 비면 null", nullable = true)
                          LocalDateTime establishedAt) {}
    public record Review(LocalDateTime requestedAt, String waitingElapsed, LocalDateTime approvedAt,
                         LocalDateTime rejectedAt, ContractDetailResponse.Review.RejectReason rejectReason) {}
    public record Signature(LocalDateTime requestedAt, LocalDateTime deadlineAt, LocalDateTime brandSignedAt,
                            LocalDateTime creatorSignedAt, LocalDateTime asOf, String asOfActorName,
                            long deadlinePassedDays,
                            @Schema(description = "C1 서명 기한 제안값 — 공구 시작 3일 전 23:55(1021 기-07 · 설정값). 운영자가 확인한 뒤 모두싸인에 "
                                    + "같은 값을 넣는다. 검토 대기에서만 · 제안값이 지금보다 이전이면 null", nullable = true)
                            LocalDateTime deadlineSuggestedAt,
                            @Schema(description = "B3-s — 서명 진행중 ∧ 당사자 정지로 보류 중 ∧ 서명 기한 경과. 「모두싸인에서 기한 연장 · 재발송 뒤 "
                                    + "서명 현황을 갱신하세요」 안내 근거(시스템은 모두싸인 쪽 기한을 모른다)")
                            boolean deadlineOverdueWhileHeld) {}
    @Schema(description = "문서 종류별 등록 상태 — 미등록 문서도 exists=false로 포함")
    public record Document(ContractDocumentType type, boolean exists, String fileName, String downloadUrl,
                           @Schema(description = "파일 크기(바이트) — 표시 단위는 FE가 계산한다. 문서가 없으면 null",
                                   example = "1258291", nullable = true)
                           Long sizeBytes,
                           LocalDateTime uploadedAt, LocalDateTime sourceReviewRequestedAt) {}
    /**
     * 운영자 계약 취소의 요청 · 처리(C6 모달 · 취소 계약 우측 레일). 운영자 취소가 아니면 블록 자체가 null이다.
     * 공유 {@code closure}에 두지 않는다 — 파트너·스튜디오 상세도 쓰는 값이라 요청자가 상대에게 보이게 된다(28-1 미결 ①).
     */
    @Schema(description = "운영자 계약 취소의 요청·처리 — 운영자 취소가 아니면 null")
    public record CancelRequestInfo(
            @Schema(description = "SELLER · CREATOR · ADMIN(직권). 기록 도입 이전 취소 건은 null", nullable = true)
            ContractActorType requesterType,
            @Schema(description = "브랜드면 마켓명, 인플루언서면 쇼룸명 — 현재 이름. 직권이면 null", example = "부트코스스테틱", nullable = true)
            String requesterName,
            @Schema(nullable = true) ContractCancelRequestChannel requestChannel,
            @Schema(example = "소통 스레드", nullable = true) String requestChannelLabel,
            @Schema(nullable = true) LocalDateTime requestedAt,
            @Schema(description = "처리 시각 — closure.closedAt과 같다") LocalDateTime processedAt,
            @Schema(description = "처리 운영자 — CANCELED 이력의 표시 이름", example = "김윤영") String processedByName) {}
    public record Resend(@Schema(description = "서명 진행중일 때의 미처리 요청 건수. 다른 상태에서는 0") long pendingCount,
                         LocalDateTime lastRequestedAt, ContractActorType lastRequesterType) {}
    public record GroupBuy(@Schema(description = "체결과 함께 생성한 공구 ID. 체결 전 null", nullable = true) Long groupBuyId,
                           @Schema(description = "공구 번호. 체결 전 null", nullable = true) String groupBuyNumber,
                           @Schema(description = "공구 상태. 체결 전 null", nullable = true) String status) {}
    @Schema(description = "상세 화면 조치 활성화 여부 — 최종 실행 시 서버에서 다시 상태를 검사")
    public record Permissions(boolean canApprove, boolean canReject, boolean canUpdateSignature,
                               @Schema(description = "**폐지 예정** — canCreateGroupBuy 와 같은 값", deprecated = true)
                               boolean canConclude,
                               boolean canExpire, boolean canHandleResend,
                               boolean canUploadDocument, boolean canCancel,
                               @Schema(description = "[공구 생성](C4 · 1021 기-05) — 공구 생성 대기 ∧ 양측 서명 ∧ 체결 문서 2종")
                               boolean canCreateGroupBuy,
                               @Schema(description = "[해지 처리](C7) — 성립 뒤(공구 생성 대기 · 체결완료) ∧ 공구 오픈 전")
                               boolean canTerminate) {}
    public record History(ContractEventType eventType, ContractActorType actorType, Long actorId,
                          String actorDisplayName, String detail, LocalDateTime occurredAt) {}
    @Schema(description = "관리자 계약 상세 — 계약 조건과 이력은 읽기 전용")
    public record Detail(ContractInfo contract, Stepper stepper, Review review, Signature signature,
                         List<ContractDetailResponse.Item> items, ContractDetailResponse.Content content,
                         ContractDetailResponse.FixedFee fixedFee, ContractDetailResponse.Settlement settlement,
                         ContractDetailResponse.Closure closure,
                         @Schema(description = "운영자 취소 요청·처리 정보. 다른 종결 상태에서는 null", nullable = true) CancelRequestInfo cancelRequest,
                         @Schema(description = "처리 대기 중인 해지 요청(1021 기-05) — 없으면 null", nullable = true) TerminationRequestInfo terminationRequest,
                         @Schema(description = "해지 결과 — 해지된 계약만", nullable = true) TerminationInfo termination,
                         List<Document> documents, Resend resend,
                         GroupBuy groupBuy, Permissions permissions,
                         @Schema(description = "발생 시각·ID 오름차순의 전체 이력") List<History> history,
                         @Schema(description = "서명 갱신 시 전달할 현재 버전", example = "2") Long version) {}
}

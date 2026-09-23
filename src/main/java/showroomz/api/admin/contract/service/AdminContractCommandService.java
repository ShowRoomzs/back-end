package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.*;
import showroomz.domain.contract.service.*;
import showroomz.domain.contract.type.*;
import showroomz.global.error.exception.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AdminContractCommandService {
    private final AdminContractAccess access;
    private final ContractRepository contracts;
    private final ContractDocumentRepository documents;
    private final ContractResendRequestRepository resends;
    private final ContractHistoryRecorder history;
    private final ContractNotifier notifier;

    public ProcessResponse approve(Long id, Long operator, ApproveRequest request) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        requireReview(c);
        if (!Boolean.TRUE.equals(request.recipientsRegistered()) || !Boolean.TRUE.equals(request.documentUploaded())
                || !Boolean.TRUE.equals(request.requestSent())) fail(ErrorCode.CONTRACT_CHECKLIST_REQUIRED);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sent = request.signatureRequestedAt(), deadline = request.signatureDeadlineAt();
        if (sent == null || deadline == null || c.getReviewRequestedAt() == null
                || sent.isBefore(c.getReviewRequestedAt()) || sent.isAfter(now) || !sent.isBefore(deadline)) {
            fail(ErrorCode.CONTRACT_SIGNATURE_TIME_INVALID);
        }
        transition(c, ContractStatus.SIGNING);
        c.approveReview(sent, deadline, now);
        record(c, operator, name, ContractEventType.REVIEW_APPROVED, null, now);
        record(c, operator, name, ContractEventType.SIGNATURE_SENT, null, sent);
        notifier.notifyBothParties(c, "REVIEW_APPROVED");
        return response(c);
    }

    public ProcessResponse reject(Long id, Long operator, RejectRequest request) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        requireReview(c);
        if (request.reasonDetail() == null || request.reasonDetail().isBlank()) fail(ErrorCode.CONTRACT_REJECT_DETAIL_REQUIRED);
        if (request.reasonCode() == null || request.reasonDetail().length() > 1000) fail(ErrorCode.INVALID_INPUT_VALUE);
        LocalDateTime now = LocalDateTime.now();
        transition(c, ContractStatus.REVIEW_REJECTED);
        c.rejectReview(request.reasonCode().name(), request.reasonDetail(), now);
        record(c, operator, name, ContractEventType.REVIEW_REJECTED,
                request.reasonCode().name() + " · " + request.reasonDetail(), now);
        notifier.notifySeller(c, "REVIEW_REJECTED");
        return response(c);
    }

    public ProcessResponse updateSignatures(Long id, Long operator, SignatureRequest request) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (c.getStatus() == ContractStatus.CONCLUDED) fail(ErrorCode.CONTRACT_SIGNATURE_UPDATE_LOCKED);
        if (c.getStatus() != ContractStatus.SIGNING && c.getStatus() != ContractStatus.CONCLUSION_PENDING) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        if (!Objects.equals(c.getVersion(), request.version())) fail(ErrorCode.CONTRACT_MODIFIED_ELSEWHERE);
        LocalDateTime now = LocalDateTime.now();
        validateSignedAt(c, request.brandSignedAt(), now);
        validateSignedAt(c, request.creatorSignedAt(), now);
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(c.getBrandSignedAt(), request.brandSignedAt())) changes.add("브랜드 서명: " + c.getBrandSignedAt() + " → " + request.brandSignedAt());
        if (!Objects.equals(c.getCreatorSignedAt(), request.creatorSignedAt())) changes.add("인플루언서 서명: " + c.getCreatorSignedAt() + " → " + request.creatorSignedAt());
        ContractStatus target = request.brandSignedAt() != null && request.creatorSignedAt() != null
                ? ContractStatus.CONCLUSION_PENDING : ContractStatus.SIGNING;
        boolean brandNewlySigned = c.getBrandSignedAt() == null && request.brandSignedAt() != null;
        boolean creatorNewlySigned = c.getCreatorSignedAt() == null && request.creatorSignedAt() != null;
        boolean bothNewlyConfirmed = c.getStatus() == ContractStatus.SIGNING && target == ContractStatus.CONCLUSION_PENDING;
        transition(c, target);
        c.updateSignatures(request.brandSignedAt(), request.creatorSignedAt(), now);
        // 각자의 서명은 서명한 당사자·시각으로 남긴다 — 스튜디오 이력 「브랜드 서명 완료」·「내 서명 완료」(시안 S3a·S3b).
        // 입력한 운영자와 변경 내역은 아래 SIGNATURE_UPDATED가 따로 기록한다.
        if (brandNewlySigned) history.recordBySeller(c, ContractEventType.BRAND_SIGNED, null, request.brandSignedAt());
        if (creatorNewlySigned) history.recordByCreator(c, ContractEventType.CREATOR_SIGNED, null, request.creatorSignedAt());
        if (bothNewlyConfirmed) record(c, operator, name, ContractEventType.BOTH_SIGNED_CONFIRMED, null, now);
        record(c, operator, name, ContractEventType.SIGNATURE_UPDATED,
                changes.isEmpty() ? "변경 없음 · 기준 시각만 갱신" : String.join(" / ", changes), now);
        return response(c);
    }

    public ProcessResponse conclude(Long id, Long operator) {
        access.requireConclusionPermission(operator);
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (c.getStatus() != ContractStatus.CONCLUSION_PENDING || c.getBrandSignedAt() == null || c.getCreatorSignedAt() == null) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        Set<ContractDocumentType> types = new HashSet<>();
        documents.findByContractIdInTypeOrder(id).forEach(d -> types.add(d.getDocumentType()));
        if (!types.containsAll(List.of(ContractDocumentType.SIGNED_PDF, ContractDocumentType.AUDIT_TRAIL))) {
            throw new ContractDocumentRequiredException();
        }
        LocalDateTime now = LocalDateTime.now();
        transition(c, ContractStatus.CONCLUDED);
        c.conclude(now);
        record(c, operator, name, ContractEventType.CONCLUDED, null, now);
        // 공구 모듈이 assignGroupBuy 조건부 게이트로 연결한다. 여기서 공구를 생성했다고 응답하지 않는다.
        notifier.notifyBothParties(c, "CONCLUDED");
        return response(c);
    }

    public ProcessResponse expire(Long id, Long operator, ExpireRequest request) {
        access.requireExpiryPermission(operator);
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (c.getStatus() != ContractStatus.SIGNING) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        if (!Boolean.TRUE.equals(request.dashboardRechecked())) fail(ErrorCode.CONTRACT_CHECKLIST_REQUIRED);
        LocalDateTime now = LocalDateTime.now();
        if (c.getSignatureDeadlineAt() == null || !c.getSignatureDeadlineAt().isBefore(now)) fail(ErrorCode.CONTRACT_EXPIRE_NOT_DUE);
        transition(c, ContractStatus.EXPIRED);
        c.expire(now);
        record(c, operator, name, ContractEventType.EXPIRED, null, now);
        notifier.notifyBothParties(c, "EXPIRED");
        return response(c);
    }

    public ProcessResponse handleResend(Long id, Long operator) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (c.getStatus() != ContractStatus.SIGNING) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        LocalDateTime now = LocalDateTime.now();
        int handled = resends.handleAll(id, operator, now);
        record(c, operator, name, ContractEventType.RESEND_HANDLED, "서명 안내 재발송 · 요청 " + handled + "건 처리", now);
        return response(c);
    }

    private void requireReview(Contract c) {
        if (c.getStatus() != ContractStatus.REVIEW_PENDING) fail(ErrorCode.CONTRACT_REVIEW_NOT_PENDING);
    }
    private void validateSignedAt(Contract c, LocalDateTime signedAt, LocalDateTime now) {
        if (signedAt != null && (c.getSignatureRequestedAt() == null || signedAt.isAfter(now)
                || signedAt.isBefore(c.getSignatureRequestedAt()))) fail(ErrorCode.CONTRACT_SIGNATURE_TIME_INVALID);
    }
    private void transition(Contract c, ContractStatus target) {
        if (contracts.transitionStatus(c.getId(), c.getStatus(), target) != 1) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
    }
    private void record(Contract c, Long operator, String name, ContractEventType event, String detail, LocalDateTime now) {
        history.record(c, event, ContractActorType.ADMIN, operator, name, detail, now);
    }
    private ProcessResponse response(Contract c) {
        contracts.flush();
        return new ProcessResponse(c.getId(), c.getStatus(), c.getVersion());
    }
    private static void fail(ErrorCode code) { throw new BusinessException(code); }
}

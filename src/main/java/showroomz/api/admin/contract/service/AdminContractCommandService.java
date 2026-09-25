package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.*;
import showroomz.domain.contract.service.*;
import showroomz.domain.contract.type.*;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyFactory;
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
    private final GroupBuyFactory groupBuyFactory;

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
        // 공구는 체결 트랜잭션 안에서 생긴다 — 생성 실패 = 체결 실패(공구 설계서 0-2 · 2-1).
        // 「체결완료인데 공구가 없는 계약」이 구조적으로 생기지 않는다.
        GroupBuy groupBuy = groupBuyFactory.createFromConcludedContract(c, now);
        notifier.notifyBothParties(c, "CONCLUDED");
        contracts.flush();
        return new ProcessResponse(c.getId(), c.getStatus(), c.getVersion(), groupBuy.getGroupBuyNumber());
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

    /**
     * 운영자 [계약 취소] — 서명 요청 발송 이후 체결 전({@code SIGNING}·{@code CONCLUSION_PENDING}).
     *
     * <p>이 구간은 브랜드가 취소할 수 없다. 모두싸인에 서명 요청이 나가 있어 우리 상태만 종결하면 양측이
     * 여전히 서명 링크를 들고 있기 때문이다 — API가 없으니 운영자가 모두싸인에서 요청을 거뒀다는 확인을
     * 체크리스트로 받는다. 사유는 브랜드 취소와 같은 5종이고 {@code ETC}면 메모가 필수다.
     */
    public ProcessResponse cancel(Long id, Long operator, CancelRequest request) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (!ContractStatus.ADMIN_CANCELABLE.contains(c.getStatus())) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        if (!Boolean.TRUE.equals(request.signatureRequestWithdrawn())) fail(ErrorCode.CONTRACT_CHECKLIST_REQUIRED);
        String memo = request.memo() == null || request.memo().isBlank() ? null : request.memo().trim();
        if (request.reasonCode().requiresMemo() && memo == null) fail(ErrorCode.CONTRACT_CANCEL_REASON_MEMO_REQUIRED);
        LocalDateTime now = LocalDateTime.now();
        transition(c, ContractStatus.CANCELED);
        c.applyCanceledByAdmin(request.reasonCode().name(), memo, now);
        record(c, operator, name, ContractEventType.CANCELED, request.reasonCode().getLabel(), now);
        notifier.notifyBothParties(c, "CANCELED");
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
        return new ProcessResponse(c.getId(), c.getStatus(), c.getVersion(), null);
    }
    private static void fail(ErrorCode code) { throw new BusinessException(code); }
}

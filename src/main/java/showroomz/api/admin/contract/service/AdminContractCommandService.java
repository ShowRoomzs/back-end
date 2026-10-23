package showroomz.api.admin.contract.service;

import showroomz.domain.contract.service.ContractSanctionHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractTerminationRequest;
import showroomz.domain.groupbuy.service.GroupBuyTerminator;
import showroomz.domain.member.creator.service.CreatorSanctionGuard;
import showroomz.domain.contract.repository.*;
import showroomz.domain.contract.service.*;
import showroomz.domain.contract.service.port.ContractChannelGateway;
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
    private final ContractChannelGateway channelGateway;
    private final ContractHistoryRecorder history;
    private final ContractNotifier notifier;
    private final GroupBuyFactory groupBuyFactory;
    private final CreatorSanctionGuard creatorSanctionGuard;
    private final ContractSanctionHoldService sanctionHolds;
    private final ContractTerminationRequestRepository terminationRequests;
    private final showroomz.domain.groupbuy.repository.GroupBuyRepository groupBuyRepository;
    private final showroomz.domain.groupbuy.service.GroupBuyTerminator groupBuyTerminator;

    public ProcessResponse approve(Long id, Long operator, ApproveRequest request) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        requireReview(c);
        // 정지 중인 브랜드의 계약은 운영자가 검토를 진행하지 않는다 — 「검토 보류 · 계정 정지」(ui-partner-15 S4 · 1017 설계서 8-3).
        if (ContractSanctionHoldService.isMarketSuspended(c.getMarket())) fail(ErrorCode.CONTRACT_HELD_BY_SANCTION);
        // 검토 통과 = 서명 요청 발송 — 정지 · 탈퇴 심사 중인 인플루언서면 보내지 않는다(53 설계서 4-2).
        creatorSanctionGuard.requireCanReceiveContract(c.getCreator());
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
        // 상대가 서명했다는 알림 — 파트너 「서명 완료」 · 스튜디오 「이제 내 서명만 남았어요」(1017 설계서 1-4 · 1-5).
        if (brandNewlySigned) notifier.notifyCounterparty(c, "BRAND_SIGNED");
        if (creatorNewlySigned) notifier.notifySeller(c, "CREATOR_SIGNED");
        if (bothNewlyConfirmed) {
            record(c, operator, name, ContractEventType.BOTH_SIGNED_CONFIRMED, null, now);
            // 계약 성립 = 양측 서명 완료(1021 기-05) — 시각은 운영자가 저장한 시각이 아니라 나중 서명 시각이다.
            history.record(c, ContractEventType.ESTABLISHED, ContractActorType.SYSTEM, null, null, null,
                    c.getEstablishedAt());
            notifier.notifyBothParties(c, "ESTABLISHED");
        }
        record(c, operator, name, ContractEventType.SIGNATURE_UPDATED,
                changes.isEmpty() ? "변경 없음 · 기준 시각만 갱신" : String.join(" / ", changes), now);
        return response(c);
    }

    /** @deprecated 1021 기-05 — 「체결 처리」는 「공구 생성」이다. {@link #createGroupBuy} 와 같다. */
    @Deprecated
    public ProcessResponse conclude(Long id, Long operator) {
        return createGroupBuy(id, operator);
    }

    /**
     * [공구 생성](C4 · 1021 결정 노트 기-05) — 계약은 양측 서명 완료로 이미 성립했다. 운영자는 체결 문서 2종을 확인하고 공구를 만든다.
     * {@code concluded_at} 은 이 처리 시각이다(계약 성립 시각은 {@code established_at}).
     */
    public ProcessResponse createGroupBuy(Long id, Long operator) {
        access.requireConclusionPermission(operator);
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (c.getStatus() != ContractStatus.CONCLUSION_PENDING || c.getBrandSignedAt() == null || c.getCreatorSignedAt() == null) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        // 막는 지점은 체결이다 — 서명은 모두싸인이 받아 막을 수 없다. 어느 당사자든 정지면 체결(= 공구 생성)을 하지 않는다(8-3).
        sanctionHolds.requireNoPartySuspended(c);
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
        // 보류 중에는 서명 기한이 멈춰 있다 — 기한이 지난 것처럼 보여도 만료시키지 않는다(8-4).
        if (c.isHeldBySanction()) fail(ErrorCode.CONTRACT_EXPIRE_NOT_DUE);
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
        ContractCancelRequester requester = requireRequester(c, request, now);
        // 1021 기-06 【잠정】 — 당사자 요청이면 요청하지 않은 쪽의 동의를 운영팀 채널에서 확인해야 취소한다. 직권은 동의 대상이 없다.
        if (requester.type() != ContractActorType.ADMIN && !Boolean.TRUE.equals(request.counterpartyConsentConfirmed())) {
            fail(ErrorCode.CONTRACT_CHECKLIST_REQUIRED);
        }
        transition(c, ContractStatus.CANCELED);
        c.applyCanceledByAdmin(request.reasonCode().name(), memo, requester, now);
        // 결과 카드는 양측 운영팀 채널에 붙는다(1021 기-06 · 36 설계 §36-9 A-5 해소). 채널은 회원당 하나라 당사자에서 유도한다.
        // 요청 채널 기록은 스레드로 들어온 요청의 요청자 쪽 채널이다 — 전화 · 이메일 · 직권이면 NULL(V167 정의 그대로).
        for (ContractActorType party : List.of(ContractActorType.SELLER, ContractActorType.CREATOR)) {
            if (party == ContractActorType.CREATOR && c.getCreator() == null) continue;
            Long threadId = channelGateway.postAdminCanceledCard(c, party, operator).threadId();
            if (party == requester.type() && requester.channel() == ContractCancelRequestChannel.THREAD) {
                c.recordCancelRequestThread(threadId);
            }
        }
        record(c, operator, name, ContractEventType.CANCELED, request.reasonCode().getLabel(), now);
        notifier.notifyBothParties(c, "CANCELED");
        return response(c);
    }

    /**
     * 「계약 취소 동의 확인」 카드 발송(1021 설계서 1-5 · 어드민 20a A8 ②) — 요청하지 않은 쪽 운영팀 채널. 서명 진행중에만(취소 가능 구간).
     * 운영자가 요지를 고쳐 다시 보낼 수 있다(카드가 한 장 더 붙는다).
     */
    public CardResponse sendCancelConsentCard(Long id, Long operator, CancelConsentCardRequest request) {
        access.operatorName(operator);
        Contract c = access.lock(id);
        if (!ContractStatus.ADMIN_CANCELABLE.contains(c.getStatus())) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        if (request.requesterType() != ContractActorType.SELLER && request.requesterType() != ContractActorType.CREATOR) {
            fail(ErrorCode.CONTRACT_CANCEL_REQUESTER_INVALID);
        }
        ContractChannelGateway.ChannelCard card = channelGateway.postCancelConsentCard(c, request.requesterType(),
                request.reasonSummary().trim(), operator);
        return new CardResponse(card.threadId(), card.messageId());
    }

    /**
     * [해지 처리](1021 설계서 1-4 · 어드민 01 C7 · 결정 노트 기-05 【잠정】) — 성립 뒤 · 공구 오픈 전. 생성된 공구도 함께 종결되고 되돌릴 수 없다.
     *
     * <p>당사자 정지 중이어도 해지한다(종결은 새로 벌이는 일이 아니다 · 14절 Q3). 주문이 없는 공구라 환불 · 정산이 없다.
     */
    public ProcessResponse terminate(Long id, Long operator, TerminateRequest request) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (!Boolean.TRUE.equals(request.counterpartyConsentConfirmed())
                || !Boolean.TRUE.equals(request.groupBuyNotOpenedConfirmed())) fail(ErrorCode.CONTRACT_CHECKLIST_REQUIRED);
        GroupBuy groupBuy = c.getGroupBuyId() == null ? null
                : groupBuyRepository.findForUpdate(c.getGroupBuyId()).orElse(null);
        if (!ContractTerminationPolicy.isTerminable(c, groupBuy)) fail(ErrorCode.CONTRACT_TERMINATION_NOT_ALLOWED);
        ContractTerminationRequest pending = terminationRequests.findPending(c.getId()).orElse(null);
        if (request.requestId() != null && (pending == null || !pending.getId().equals(request.requestId()))) {
            fail(ErrorCode.CONTRACT_TERMINATION_REQUEST_NOT_PENDING);
        }
        LocalDateTime now = LocalDateTime.now();
        String explanation = request.explanation().trim();
        transition(c, ContractStatus.TERMINATED);
        c.terminate(request.reasonCode().name(), explanation, now);
        if (pending != null) pending.close(ContractTerminationRequestStatus.HANDLED, operator, now);
        // 미처리 재발송 요청도 함께 닫는다 — 종결된 계약의 큐가 남지 않게.
        resends.handleAll(id, operator, now);
        record(c, operator, name, ContractEventType.TERMINATED, request.reasonCode().getLabel(), now);
        boolean groupBuyClosed = false;
        if (groupBuy != null) {
            groupBuyTerminator.terminate(groupBuy, GroupBuyTerminator.Termination.terminatedByContract(c.getId(),
                    showroomz.domain.groupbuy.service.GroupBuyActor.admin(operator, name),
                    request.reasonCode().getLabel(), now), now);
            groupBuyClosed = true;
        }
        channelGateway.postTerminatedCards(c, request.reasonCode().getLabel(), explanation, groupBuyClosed, operator);
        notifier.notifyBothParties(c, "TERMINATED");
        return response(c);
    }

    public ProcessResponse handleResend(Long id, Long operator) {
        String name = access.operatorName(operator);
        Contract c = access.lock(id);
        if (c.getStatus() != ContractStatus.SIGNING) fail(ErrorCode.CONTRACT_STATUS_CONFLICT);
        LocalDateTime now = LocalDateTime.now();
        int handled = resends.handleAll(id, operator, now);
        notifier.notifyBothParties(c, "RESEND_HANDLED");
        record(c, operator, name, ContractEventType.RESEND_HANDLED, "서명 안내 재발송 · 요청 " + handled + "건 처리", now);
        return response(c);
    }

    /**
     * 취소 요청자(28-1 수정계획 1-3) — 서버가 스레드에서 요청을 특정할 수 없어 운영자가 기록한다. 브랜드·인플루언서 요청이면
     * 경로·시각이 모두 있어야 하고, 직권이면 둘 다 없어야 한다 — 한쪽만 온 값을 조용히 버리면 운영자가 입력한 기록이 사라진다.
     */
    private ContractCancelRequester requireRequester(Contract c, CancelRequest request, LocalDateTime now) {
        ContractActorType type = request.requesterType();
        if (type == null || type == ContractActorType.SYSTEM) {
            invalidRequester("요청자를 선택해 주세요.");
        }
        if (type == ContractActorType.ADMIN) {
            if (request.requestChannel() != null || request.requestedAt() != null) {
                invalidRequester("운영자 직권 취소에는 요청 경로·시각을 입력하지 않습니다.");
            }
            return ContractCancelRequester.byAdmin();
        }
        if (request.requestChannel() == null || request.requestedAt() == null) {
            invalidRequester("요청 경로와 요청 시각을 입력해 주세요.");
        }
        if (request.requestedAt().isAfter(now)
                || (c.getCreatedAt() != null && request.requestedAt().isBefore(c.getCreatedAt()))) {
            invalidRequester("요청 시각은 계약 작성 이후 현재 이전이어야 합니다.");
        }
        return new ContractCancelRequester(type, request.requestChannel(), request.requestedAt());
    }
    private static void invalidRequester(String message) {
        throw new BusinessException(ErrorCode.CONTRACT_CANCEL_REQUESTER_INVALID, message);
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

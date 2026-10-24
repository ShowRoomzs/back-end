package showroomz.api.admin.changerequest.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.changerequest.ChangeRequestElapsedFormatter;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.admin.changerequest.type.AdminChangeRequestStatusFilter;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 어드민 변경 요청 큐(§16 · 16 설계서 rev.7) — 목록 · 건수 · 상세 · 승인 · 반려. 브랜드(M1 · M2) · 인플루언서(사업자 구분 변경) 요청이 한
 * 큐다. 요청자마다 다른 일(상세 조립 · 승인 반영 · 결과 통지)은 전략({@link ChangeRequestDetailAssembler} · {@link ChangeRequestApplier})이
 * 맡고, 여기는 공통부(상태 검사 · SLA · 경과 · 이전/다음 · 처리 이력 · 동시 처리 충돌)만 든다(정합 계획서 R3-1 · R3-2).
 */
@Service
@Transactional(readOnly = true)
public class AdminChangeRequestService {

    private static final long SLA_HOURS = 48;

    private final ChangeRequestRepository changeRequestRepository;
    private final SellerRepository sellerRepository;
    private final Map<ChangeRequesterType, ChangeRequestDetailAssembler> assemblers = new EnumMap<>(ChangeRequesterType.class);
    private final Map<ChangeRequesterType, ChangeRequestApplier> appliers = new EnumMap<>(ChangeRequesterType.class);

    public AdminChangeRequestService(ChangeRequestRepository changeRequestRepository, SellerRepository sellerRepository,
                                     List<ChangeRequestDetailAssembler> assemblers, List<ChangeRequestApplier> appliers) {
        this.changeRequestRepository = changeRequestRepository;
        this.sellerRepository = sellerRepository;
        assemblers.forEach(a -> this.assemblers.put(a.requesterType(), a));
        appliers.forEach(a -> this.appliers.put(a.requesterType(), a));
    }

    public AdminChangeRequestDto.ListResponse getList(AdminChangeRequestStatusFilter statusFilter, String keyword, Pageable pageable) {
        return getList(statusFilter, keyword, null, null, pageable);
    }

    /** 목록 — 요청자(브랜드 · 인플루언서) · 유형 필터(1021 기-22 · 05 목록 「요청자 · 요청 유형」 열). */
    public AdminChangeRequestDto.ListResponse getList(AdminChangeRequestStatusFilter statusFilter, String keyword,
                                                      ChangeRequesterType requesterType, ChangeRequestType type,
                                                      Pageable pageable) {
        Page<ChangeRequest> page = changeRequestRepository.search(statusFilter.getStatusNames(), normalize(keyword),
                requesterType == null ? null : requesterType.name(), type == null ? null : type.name(), pageable);

        List<AdminChangeRequestDto.ListItem> content = page.getContent().stream()
                .map(this::toListItem)
                .toList();

        return AdminChangeRequestDto.ListResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .statusCounts(buildStatusCounts(normalize(keyword)))
                .build();
    }

    public AdminChangeRequestDto.SummaryResponse getSummary() {
        return AdminChangeRequestDto.SummaryResponse.builder()
                .pendingCount(changeRequestRepository.countByStatus(ChangeRequestStatus.PENDING))
                .brandPendingCount(changeRequestRepository.countByStatusAndRequesterType(ChangeRequestStatus.PENDING,
                        ChangeRequesterType.BRAND))
                .creatorPendingCount(changeRequestRepository.countByStatusAndRequesterType(ChangeRequestStatus.PENDING,
                        ChangeRequesterType.CREATOR))
                .build();
    }

    public List<AdminChangeRequestDto.RejectReasonOption> getRejectReasons(ChangeRequestType type) {
        return Arrays.stream(ChangeRequestRejectReason.values())
                .filter(reason -> reason.supports(type))
                .map(reason -> AdminChangeRequestDto.RejectReasonOption.builder()
                        .code(reason)
                        .label(reason.descriptionFor(type))
                        .detailRequired(reason.isDetailRequired())
                        .build())
                .toList();
    }

    /** 공통부(코드 · 상태 · SLA · 경과 · 이전/다음 · 이력) + 요청자별 조립 — 이전/다음은 요청자 구분 없이 현재 탭 순서다(§16-2). */
    public AdminChangeRequestDto.DetailResponse getDetail(Long requestId, AdminChangeRequestStatusFilter statusFilter) {
        ChangeRequest request = getRequest(requestId);

        List<Long> orderedIds = changeRequestRepository.findOrderedIds(statusFilter.getStatusNames());
        int index = orderedIds.indexOf(requestId);
        Long prevId = index > 0 ? orderedIds.get(index - 1) : null;
        Long nextId = (index >= 0 && index < orderedIds.size() - 1) ? orderedIds.get(index + 1) : null;

        ChangeRequestDetailParts parts = assemblerOf(request).assemble(request);
        return AdminChangeRequestDto.DetailResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .brandName(parts.brandName())
                .marketId(parts.marketId())
                .type(request.getType())
                .status(request.getStatus())
                .slaExceeded(isSlaExceeded(request))
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .requesterName(parts.requesterName())
                .elapsedText(elapsedText(request))
                .reason(request.getReason())
                .diff(parts.diff())
                .changedFieldLabels(parts.changedFieldLabels())
                .evidence(parts.evidence())
                .referenceItems(parts.referenceItems())
                .holderCheck(parts.holderCheck())
                .compareItems(parts.compareItems())
                .approvalImpact(parts.approvalImpact())
                .history(buildHistory(request))
                .prevRequestId(prevId)
                .nextRequestId(nextId)
                .build();
    }

    /**
     * 승인 — 반영(요청자별 전제 재검사 포함) → 승인 기록(인플루언서 건은 암호문을 비운다) → 동시 처리 충돌 확인 → 결과 통지. 정산 행은 건드리지
     * 않는다 — 인플루언서 구분의 「다음 정산부터」는 생성 시점 스냅샷이 만든다.
     */
    @Transactional
    public AdminChangeRequestDto.ProcessResponse approve(Long requestId, Long processorId) {
        ChangeRequest request = getRequest(requestId);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }
        ChangeRequestApplier applier = applierOf(request);
        applier.apply(request, LocalDateTime.now());
        request.approve(processorId);
        flushOrConflict(request);
        applier.notifyApproved(request);
        return processResponse(request, null, null);
    }

    @Transactional
    public AdminChangeRequestDto.ProcessResponse reject(Long requestId, Long processorId, AdminChangeRequestDto.RejectRequest rejectRequest) {
        ChangeRequest request = getRequest(requestId);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }

        ChangeRequestRejectReason reasonType = rejectRequest.getReasonType();
        if (!reasonType.supports(request.getType())) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH);
        }
        String detail = rejectRequest.getReasonDetail();
        if (reasonType.isDetailRequired() && (detail == null || detail.isBlank())) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_REJECT_DETAIL_REQUIRED);
        }

        request.reject(processorId, reasonType.name(), detail);
        flushOrConflict(request);
        applierOf(request).notifyRejected(request, reasonType, detail);
        return processResponse(request, reasonType.descriptionFor(request.getType()), detail);
    }

    // ------------------------------------------------------------------ 공통

    private ChangeRequestDetailAssembler assemblerOf(ChangeRequest request) {
        return strategy(assemblers, request);
    }

    private ChangeRequestApplier applierOf(ChangeRequest request) {
        return strategy(appliers, request);
    }

    private static <T> T strategy(Map<ChangeRequesterType, T> strategies, ChangeRequest request) {
        T strategy = strategies.get(request.getRequesterType());
        if (strategy == null) {
            throw new IllegalStateException("요청자 유형 전략 없음: " + request.getRequesterType());
        }
        return strategy;
    }

    private AdminChangeRequestDto.ProcessResponse processResponse(ChangeRequest request, String rejectReason,
                                                                  String rejectReasonDetail) {
        return AdminChangeRequestDto.ProcessResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .brandName(request.isCreatorRequest() ? null : request.getMarket().getMarketName())
                .type(request.getType())
                .status(request.getStatus())
                .processedAt(request.getProcessedAt())
                .rejectReason(rejectReason)
                .rejectReasonDetail(rejectReasonDetail)
                .build();
    }

    /** 승인 · 반려 동시 클릭 — 두 번째는 버전 충돌로 「검토 대기 상태인 요청만」(16 설계서 3-8). 알림 · 메일보다 먼저 확인한다. */
    private void flushOrConflict(ChangeRequest request) {
        try {
            changeRequestRepository.saveAndFlush(request);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }
    }

    private AdminChangeRequestDto.Requester requester(ChangeRequest request) {
        if (request.isCreatorRequest()) {
            Creator creator = request.getCreator();
            return AdminChangeRequestDto.Requester.builder()
                    .type(ChangeRequesterType.CREATOR).id(creator.getId()).name(creator.getShowroomName()).build();
        }
        Market market = request.getMarket();
        return AdminChangeRequestDto.Requester.builder()
                .type(ChangeRequesterType.BRAND).id(market.getId()).name(market.getMarketName()).build();
    }

    private AdminChangeRequestDto.ListItem toListItem(ChangeRequest request) {
        return AdminChangeRequestDto.ListItem.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .requester(requester(request))
                .brandName(request.isCreatorRequest() ? null : request.getMarket().getMarketName())
                .type(request.getType())
                .typeLabel(request.getType().label())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .elapsedText(elapsedText(request))
                .slaExceeded(isSlaExceeded(request))
                .status(request.getStatus())
                .build();
    }

    private List<AdminChangeRequestDto.HistoryEvent> buildHistory(ChangeRequest request) {
        List<AdminChangeRequestDto.HistoryEvent> history = new ArrayList<>();
        String requesterSurface = request.isCreatorRequest() ? "쇼룸 스튜디오" : "브랜드 파트너센터";
        history.add(AdminChangeRequestDto.HistoryEvent.builder()
                .event("REQUESTED")
                .occurredAt(request.getRequestedAt())
                .actorLabel(requesterSurface)
                .build());

        if (request.getProcessedAt() != null) {
            String actorLabel = request.getStatus() == ChangeRequestStatus.CANCELED
                    ? requesterSurface
                    : resolveAdminName(request.getProcessedBy());
            history.add(AdminChangeRequestDto.HistoryEvent.builder()
                    .event(request.getStatus().name())
                    .occurredAt(request.getProcessedAt())
                    .actorLabel(actorLabel)
                    .build());
        }
        return history;
    }

    private String resolveAdminName(Long processorId) {
        if (processorId == null) {
            return "운영자";
        }
        return sellerRepository.findById(processorId).map(Seller::getName).orElse("운영자");
    }

    private AdminChangeRequestDto.StatusCounts buildStatusCounts(String keyword) {
        long pending = 0L;
        long approved = 0L;
        long rejected = 0L;
        long canceled = 0L;

        for (Object[] row : changeRequestRepository.countByStatusGroup(keyword)) {
            ChangeRequestStatus status = (ChangeRequestStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case PENDING -> pending = count;
                case APPROVED -> approved = count;
                case REJECTED -> rejected = count;
                case CANCELED -> canceled = count;
            }
        }

        return AdminChangeRequestDto.StatusCounts.builder()
                .pending(pending).approved(approved).rejected(rejected).canceled(canceled)
                .all(pending + approved + rejected + canceled)
                .build();
    }

    private String elapsedText(ChangeRequest request) {
        if (request.getProcessedAt() != null) {
            return null;
        }
        return ChangeRequestElapsedFormatter.format(request.getRequestedAt());
    }

    private boolean isSlaExceeded(ChangeRequest request) {
        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            return false;
        }
        return Duration.between(request.getRequestedAt(), LocalDateTime.now()).toHours() > SLA_HOURS;
    }

    private String normalize(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    private ChangeRequest getRequest(Long requestId) {
        return changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_FOUND));
    }
}

package showroomz.api.seller.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.groupbuy.dto.GroupBuyDetailResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyDetailResponse.*;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyAppealAttachmentRepository;
import showroomz.domain.groupbuy.repository.GroupBuyHistoryRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.GroupBuyFactsLoader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.EarlyCloseReasonCode;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyAttachmentStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.SuspensionReasonCode;
import showroomz.global.utils.RewardCalculator;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공구 상세 응답 조립 — B1~B7a 22종이 이 응답 하나를 쓴다(설계서 4-4).
 *
 * <p>실행 API도 처리 후 이 조립기로 상세를 돌려준다. 버튼 판정은 {@link GroupBuyPermissionPolicy}와
 * 같은 사실 스냅샷({@link GroupBuyFacts})을 본다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBuyDetailAssembler {

    /** 어드민 정산 지연 감시 기준(§29-11) — 종료 +30일. 알림만 하고 강제 처리는 없다. */
    private static final int SETTLEMENT_WATCH_DAYS = 30;

    private final GroupBuyFactsLoader factsLoader;
    private final GroupBuyHistoryRepository historyRepository;
    private final GroupBuyAppealAttachmentRepository appealAttachmentRepository;
    private final GroupBuySalesReader salesReader;
    private final GroupBuyThreadGateway threadGateway;
    private final GroupBuyPermissionPolicy permissionPolicy;

    public GroupBuyDetailResponse assemble(GroupBuy groupBuy) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        Contract contract = groupBuy.getContract();
        Long pairThreadId = threadGateway.findPairThreadId(groupBuy).orElse(null);

        return new GroupBuyDetailResponse(
                summary(groupBuy, contract),
                timeline(groupBuy, contract, now),
                new Counterparty(groupBuy.getCreator().getId(), groupBuy.getCreator().getShowroomName(), pairThreadId),
                new ContractRef(contract.getId(), contract.getContractNumber(), contract.getConcludedAt()),
                items(contract),
                fixedFee(contract),
                new ContentDuty(contract.getContentFeedCount(), contract.getContentReelsCount(),
                        contract.getContentStoryCount(), contract.getContentDueDate()),
                readiness(groupBuy, facts.post()),
                post(groupBuy, facts.post(), now),
                sales(groupBuy, contract),
                orderClosure(groupBuy),
                extension(groupBuy, facts.extension()),
                facts.pendingChangeRequest().map(request -> activeRequest(groupBuy, request)).orElse(null),
                facts.lastDecidedChangeRequest().map(this::lastDecision).orElse(null),
                adminSuspension(facts),
                closure(groupBuy, facts),
                afterEnd(groupBuy, facts),
                permissionPolicy.evaluate(facts, pairThreadId != null, now),
                history(groupBuy));
    }

    private Summary summary(GroupBuy groupBuy, Contract contract) {
        GroupBuyStatus status = groupBuy.getStatus();
        return new Summary(
                groupBuy.getId(),
                groupBuy.getGroupBuyNumber(),
                contract.getTitle(),
                status,
                status.getLabel(),
                status.getTone(),
                groupBuy.getCreatedAt(),
                groupBuy.getReadyAt(),
                groupBuy.getOpenedAt(),
                groupBuy.getEndedAt(),
                groupBuy.getCloseType(),
                groupBuy.getCloseType() == null ? null : groupBuy.getCloseType().getLabel());
    }

    /**
     * 「시작까지 8일」 · 「진행 3일차 · 종료까지 5일」 · 「기간 진행 3일 / 8일」.
     * 일수는 {@code Contract.periodDays()}와 같은 양끝 포함 일자 계산이고 서버 now 기준이다.
     */
    private Timeline timeline(GroupBuy groupBuy, Contract contract, LocalDateTime now) {
        LocalDateTime startAt = groupBuy.getStartAt();
        LocalDateTime endAt = groupBuy.getEndAt();
        LocalDateTime cap = groupBuy.getEndedAt() != null ? groupBuy.getEndedAt() : endAt;
        int totalDays = groupBuy.totalDays();

        int elapsedDays = now.isBefore(startAt) ? 0
                : Math.min(GroupBuy.daysInclusive(startAt, now.isBefore(cap) ? now : cap), totalDays);
        Integer daysUntilStart = now.isBefore(startAt)
                ? (int) ChronoUnit.DAYS.between(now.toLocalDate(), startAt.toLocalDate()) : null;
        Integer daysUntilEnd = groupBuy.getStatus().isSelling() && now.isBefore(endAt)
                ? (int) ChronoUnit.DAYS.between(now.toLocalDate(), endAt.toLocalDate()) : null;
        boolean startOverdue = groupBuy.getStatus() == GroupBuyStatus.PREPARING && !now.isBefore(startAt);

        return new Timeline(startAt, endAt, contract.getGroupBuyEndAt(), totalDays, elapsedDays,
                daysUntilStart, daysUntilEnd, startOverdue);
    }

    private List<Item> items(Contract contract) {
        return contract.getItems().stream()
                .map(item -> new Item(
                        item.getProductId(),
                        item.getProductName(),
                        item.getRegularPrice(),
                        item.getGroupBuyPrice(),
                        item.getRewardRate(),
                        RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate()),
                        item.getMinQuantity()))
                .toList();
    }

    /**
     * 표준 표기 — 3서피스 문자 단위 동일(§29-9). FE 3개가 각자 조립하면 한 곳의 가운뎃점·띄어쓰기가 반드시
     * 어긋나므로 이 문자열 하나는 예외적으로 서버가 짓는다.
     */
    private FixedFee fixedFee(Contract contract) {
        String displayText = null;
        if (contract.hasFixedFee() && contract.getFixedFeeTrigger() != null) {
            displayText = "고정 지급비 %s원 · 지급 시점: %s · 브랜드 직접 지급".formatted(
                    NumberFormat.getNumberInstance(Locale.KOREA).format(contract.getFixedFeeAmount()),
                    contract.getFixedFeeTrigger().getLabel());
        }
        return new FixedFee(
                contract.getFixedFeeAmount(),
                contract.getFixedFeeTrigger(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel(),
                displayText);
    }

    private Readiness readiness(GroupBuy groupBuy, GroupBuyPost post) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (status != GroupBuyStatus.PREPARING && status != GroupBuyStatus.READY) {
            return null;
        }
        boolean stockDone = groupBuy.isStockConfirmed();
        boolean submitted = post != null && post.isSubmitted();
        boolean approved = post != null && post.isApproved();
        boolean rejected = post != null && post.getReviewStatus() == GroupBuyPostReviewStatus.REJECTED;

        return new Readiness(List.of(
                // 「내 차례인 줄만 경고 톤」(B1) — 브랜드가 채울 게이트만 ACTION_REQUIRED가 된다.
                new Gate(GateKey.STOCK_CONFIRMED, GroupBuyActorType.SELLER, stockDone, groupBuy.getStockConfirmedAt(),
                        stockDone ? GateState.DONE : GateState.ACTION_REQUIRED),
                new Gate(GateKey.POST_SUBMITTED, GroupBuyActorType.CREATOR, submitted,
                        submitted ? post.getSubmittedAt() : null,
                        submitted ? GateState.DONE : GateState.WAITING),
                new Gate(GateKey.OPEN_APPROVED, GroupBuyActorType.ADMIN, approved,
                        approved ? post.getReviewedAt() : null,
                        approved ? GateState.DONE : rejected ? GateState.REJECTED : GateState.WAITING)));
    }

    private Post post(GroupBuy groupBuy, GroupBuyPost post, LocalDateTime now) {
        GroupBuyPostStatus status = GroupBuyPostStatus.of(post, groupBuy);
        if (post == null) {
            return new Post(status, status.getLabel(), status.getTone(),
                    null, null, null, null, null, null, null, null, null, null);
        }
        boolean terminal = groupBuy.getStatus().isTerminal();
        boolean hidden = post.isHidden();
        return new Post(
                status,
                status.getLabel(),
                status.getTone(),
                post.getTitle(),
                post.getContent(),
                post.getSubmittedAt(),
                groupBuy.getOpenedAt(),
                terminal ? groupBuy.getEndedAt() : null,
                terminal ? groupBuy.getCloseType() : null,
                post.getRejectReasonCode() == null ? null
                        : new Reason(post.getRejectReasonCode(), post.getRejectReasonDetail()),
                hidden ? new Reason(post.getHiddenReasonCode(), post.getHiddenReasonDetail()) : null,
                hidden ? post.getHiddenAt() : null,
                hidden ? (int) ChronoUnit.DAYS.between(post.getHiddenAt().toLocalDate(), now.toLocalDate()) : null);
    }

    /**
     * 상태별로 서버가 내려주지 않는다(설계서 4-4) — 종료(ENDED) 화면에 KPI를 두지 않는다(§30-4 「잠정치가
     * 지급액으로 오해된다」). 판매 포트가 비어 있으면 모든 상태에서 null이다 — 0이 아니다.
     */
    private Sales sales(GroupBuy groupBuy, Contract contract) {
        SalesBasis basis = switch (groupBuy.getStatus()) {
            case IN_PROGRESS, SUSPENSION_SCHEDULED -> SalesBasis.LIVE;
            case SETTLED -> SalesBasis.SETTLED;
            case SUSPENDED -> SalesBasis.AT_SUSPENSION;
            default -> null;
        };
        if (basis == null) {
            return null;
        }
        Map<Long, ContractItem> itemsByProduct = contract.getItems().stream()
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.toMap(ContractItem::getProductId, Function.identity(), (a, b) -> a));

        return salesReader.readSales(groupBuy.getId())
                .map(sales -> {
                    long rewardAmount = sales.itemQuantities().stream()
                            .mapToLong(quantity -> {
                                ContractItem item = itemsByProduct.get(quantity.productId());
                                Long unit = item == null ? null
                                        : RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate());
                                return unit == null ? 0L : unit * quantity.quantity();
                            })
                            .sum();
                    List<ItemQuantity> quantities = sales.itemQuantities().stream()
                            .map(quantity -> new ItemQuantity(quantity.productId(), quantity.quantity()))
                            .toList();
                    return new Sales(basis, sales.orderCount(), sales.amount(), rewardAmount, quantities);
                })
                .orElse(null);
    }

    private OrderClosure orderClosure(GroupBuy groupBuy) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (!status.isSelling() && status != GroupBuyStatus.ENDED && status != GroupBuyStatus.SUSPENDED) {
            return null;
        }
        return salesReader.readClosure(groupBuy.getId())
                .map(closure -> new OrderClosure(closure.totalCount(), closure.closedCount(), closure.unclosedCount()))
                .orElse(null);
    }

    private Extension extension(GroupBuy groupBuy, GroupBuyExtensionRequest request) {
        int maxDays = permissionPolicy.maxExtensionDays(groupBuy);
        LocalDateTime cutoffAt = permissionPolicy.extensionCutoffAt(groupBuy);
        if (request == null) {
            return new Extension(null, null, null, null, null, null, null, null, null, null, maxDays, cutoffAt);
        }
        return new Extension(
                request.getStatus(),
                request.getExtensionDays(),
                request.getReason(),
                request.getBeforeEndAt(),
                request.getAfterEndAt(),
                request.getRequestedAt(),
                request.getRespondedAt(),
                request.getResponseActorType(),
                request.getRejectReasonCode(),
                request.getRejectMemo(),
                maxDays,
                cutoffAt);
    }

    private ActiveRequest activeRequest(GroupBuy groupBuy, GroupBuyChangeRequest request) {
        return new ActiveRequest(
                request.getId(),
                request.getRequestType(),
                request.getRequesterType(),
                requesterName(groupBuy, request.getRequesterType()),
                request.getReasonCode(),
                reasonLabel(request),
                request.getMemo(),
                request.getStatusAtRequest(),
                request.getRequestedAt());
    }

    private LastDecision lastDecision(GroupBuyChangeRequest request) {
        return new LastDecision(
                request.getId(),
                request.getRequestType(),
                request.getRequesterType(),
                request.getStatus(),
                request.getDecisionReason(),
                request.getDecidedAt());
    }

    private AdminSuspension adminSuspension(GroupBuyFacts facts) {
        GroupBuyAdminSuspension suspension = facts.activeNotice()
                .or(facts::closingAdminSuspension)
                .or(() -> facts.adminSuspensions().stream().findFirst())
                .orElse(null);
        if (suspension == null) {
            return null;
        }
        Appeal appeal = null;
        if (suspension.isAppealSubmitted()) {
            List<AppealAttachment> attachments = appealAttachmentRepository
                    .findByAdminSuspensionIdAndStatusOrderByIdAsc(suspension.getId(), GroupBuyAttachmentStatus.UPLOADED)
                    .stream()
                    .map(a -> new AppealAttachment(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes()))
                    .toList();
            appeal = new Appeal(suspension.getAppealContent(), suspension.getAppealSubmittedAt(), attachments);
        }
        return new AdminSuspension(
                suspension.getId(),
                suspension.getKind(),
                suspension.getStatus(),
                suspension.getReasonClause(),
                suspension.getEmergencyReason(),
                suspension.getNoticeBody(),
                suspension.getNoticedAt(),
                suspension.getExecuteScheduledAt(),
                suspension.getAppealDeadlineAt(),
                suspension.getWithdrawnAt(),
                suspension.getWithdrawReason(),
                suspension.getExecutedAt(),
                appeal);
    }

    private Closure closure(GroupBuy groupBuy, GroupBuyFacts facts) {
        if (!groupBuy.getStatus().isTerminal()) {
            return null;
        }
        GroupBuyChangeRequest request = facts.closingChangeRequest().orElse(null);
        ClosureSource source = null;
        if (request != null) {
            source = ClosureSource.REQUEST;
        } else if (groupBuy.getClosingAdminSuspensionId() != null) {
            source = facts.closingAdminSuspension()
                    .map(s -> s.getKind() == AdminSuspensionKind.EMERGENCY
                            ? ClosureSource.ADMIN_EMERGENCY : ClosureSource.ADMIN_NOTICE)
                    .orElse(null);
        }
        Requester requester = request == null ? null : new Requester(
                request.getRequesterType(),
                requesterName(groupBuy, request.getRequesterType()),
                request.getReasonCode(),
                reasonLabel(request),
                request.getMemo(),
                request.getRequestedAt());
        return new Closure(
                groupBuy.getCloseType(),
                groupBuy.getEndedAt(),
                source,
                requester,
                request == null ? null : request.getDecisionReason());
    }

    private AfterEnd afterEnd(GroupBuy groupBuy, GroupBuyFacts facts) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (!status.isTerminal()) {
            return null;
        }
        Fulfillment fulfillment = null;
        if (status == GroupBuyStatus.ENDED || status == GroupBuyStatus.SETTLED) {
            List<GroupBuyFulfillmentCheck> checks = facts.fulfillmentChecks();
            Long threadId = checks.stream()
                    .map(GroupBuyFulfillmentCheck::getThreadId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            fulfillment = new Fulfillment(
                    facts.fulfillmentCheck(FulfillmentSide.SELLER).map(this::toCheck).orElse(null),
                    facts.fulfillmentCheck(FulfillmentSide.CREATOR).map(this::toCheck).orElse(null),
                    groupBuy.getFulfillmentDueAt(),
                    GroupBuyCommandService.isSettlementOnHold(groupBuy, checks),
                    threadId,
                    groupBuy.getFulfillmentResolvedAt());
        }
        OpenIssue openIssue = facts.openIssue() == null ? null : new OpenIssue(
                facts.openIssue().getId(),
                facts.openIssue().getIssueType(),
                facts.openIssue().getOpenedAt(),
                facts.openIssue().getThreadId());
        LocalDateTime watchAt = status == GroupBuyStatus.ENDED && groupBuy.getEndedAt() != null
                ? groupBuy.getEndedAt().plusDays(SETTLEMENT_WATCH_DAYS) : null;
        return new AfterEnd(fulfillment, openIssue, watchAt, groupBuy.getSettledAt());
    }

    private FulfillmentCheck toCheck(GroupBuyFulfillmentCheck check) {
        return new FulfillmentCheck(check.getResult(), check.getReason(), check.getCheckedAt(), check.isAutoConfirmed());
    }

    private List<HistoryEntry> history(GroupBuy groupBuy) {
        return historyRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()).stream()
                .map(entry -> new HistoryEntry(
                        entry.getEventType(),
                        entry.getActorType(),
                        // 운영자 실명은 어드민 화면에만 나간다 — 호칭은 FE가 고른다(§29-13).
                        entry.getActorType() == GroupBuyActorType.ADMIN ? null : entry.getActorDisplayName(),
                        entry.getDetail(),
                        entry.getOccurredAt()))
                .toList();
    }

    private static String requesterName(GroupBuy groupBuy, GroupBuyActorType requesterType) {
        return requesterType == GroupBuyActorType.CREATOR
                ? groupBuy.getCreator().getShowroomName()
                : groupBuy.getMarket().getMarketName();
    }

    /** 브랜드 사유 코드만 라벨을 안다. 인플루언서 사유는 미정(§33-1 #9)이라 그럴듯한 문구를 지어내지 않고 null. */
    private static String reasonLabel(GroupBuyChangeRequest request) {
        if (request.getRequesterType() != GroupBuyActorType.SELLER) {
            return null;
        }
        try {
            return request.getRequestType() == ChangeRequestType.SUSPEND
                    ? SuspensionReasonCode.valueOf(request.getReasonCode()).getLabel()
                    : EarlyCloseReasonCode.valueOf(request.getReasonCode()).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

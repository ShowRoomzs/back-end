package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.api.seller.contract.service.ContractDetailAssembler;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.repository.*;
import showroomz.domain.contract.type.*;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.global.dto.PageResponse;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContractQueryService {
    private final AdminContractAccess access;
    private final AdminContractRepository adminContracts;
    private final ContractRepository contracts;
    private final ContractItemRepository items;
    private final ContractHistoryRepository histories;
    private final ContractDocumentRepository documents;
    private final ContractResendRequestRepository resends;
    private final ContractDetailAssembler assembler;
    private final ContractDocumentStorage storage;
    private final ConnectionRepository connections;
    private final MessageThreadRepository threads;
    private final showroomz.domain.groupbuy.repository.GroupBuyRepository groupBuys;

    public PageResponse<ListItem> list(AdminContractTab tab, AdminContractQueue queue, String keyword,
                                       AdminContractSort sort, Pageable page) {
        var result = adminContracts.search(tab, queue, keyword, sort, page, LocalDateTime.now());
        Map<Long, Integer> itemCounts = new HashMap<>();
        if (!result.isEmpty()) items.countByContractIds(result.map(Contract::getId).getContent())
                .forEach(row -> itemCounts.put((Long) row[0], ((Long) row[1]).intValue()));
        return PageResponse.of(result.map(c ->
                new ListItem(c.getId(), c.getContractNumber(), c.getTitle(), c.getMarket().getMarketName(),
                        c.getCreator() == null ? null : c.getCreator().getShowroomName(), itemCounts.getOrDefault(c.getId(), 0),
                        c.getGroupBuyStartAt(), c.getGroupBuyEndAt(), c.getReviewRequestedAt(), c.getStatus(),
                        c.getStatus().getLabel(), c.getStatus().getTone())));
    }

    public Summary summary() {
        LocalDateTime now = LocalDateTime.now();
        Map<ContractStatus, Long> counts = new EnumMap<>(ContractStatus.class);
        contracts.countAdminStatuses().forEach(row -> counts.put((ContractStatus) row[0], (Long) row[1]));
        Map<AdminContractTab, Long> tabs = new EnumMap<>(AdminContractTab.class);
        for (AdminContractTab tab : AdminContractTab.values()) {
            long count = tab == AdminContractTab.ALL ? counts.values().stream().mapToLong(Long::longValue).sum()
                    : tab == AdminContractTab.CLOSED ? List.of(ContractStatus.DECLINED, ContractStatus.EXPIRED, ContractStatus.CANCELED)
                        .stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum()
                    : counts.getOrDefault(ContractStatus.valueOf(tab.name()), 0L);
            tabs.put(tab, count);
        }
        Map<AdminContractQueue, Long> queues = new EnumMap<>(AdminContractQueue.class);
        for (AdminContractQueue queue : AdminContractQueue.values()) queues.put(queue, adminContracts.countQueue(queue, now));
        return new Summary(queues, tabs, queues.values().stream().mapToLong(Long::longValue).sum());
    }

    public Detail detail(Long id) {
        Contract c = access.read(id);
        var shared = assembler.assemble(c);
        var history = histories.findByContractIdOrderByOccurredAtAscIdAsc(id);
        var docs = documents.findByContractIdInTypeOrder(id);
        var requests = resends.findByContractIdOrderByRequestedAtDescIdDesc(id);
        LocalDateTime now = LocalDateTime.now();
        ContractStatus status = c.getStatus();
        boolean signing = status == ContractStatus.SIGNING;
        boolean pending = status == ContractStatus.CONCLUSION_PENDING;
        boolean due = c.getSignatureDeadlineAt() != null && c.getSignatureDeadlineAt().isBefore(now);
        boolean signed = c.getBrandSignedAt() != null && c.getCreatorSignedAt() != null;
        boolean completeDocs = docs.stream().anyMatch(d -> d.getDocumentType() == ContractDocumentType.SIGNED_PDF)
                && docs.stream().anyMatch(d -> d.getDocumentType() == ContractDocumentType.AUDIT_TRAIL);
        var connection = c.getConnection() != null ? c.getConnection() : c.getCreator() == null ? null
                : connections.findConnectedPair(c.getMarket().getId(), c.getCreator().getId()).orElse(null);
        Long threadId = connection == null ? null : threads.findByConnection(connection).map(t -> t.getId()).orElse(null);
        long minutes = c.getReviewRequestedAt() == null ? 0 : Math.max(0, Duration.between(c.getReviewRequestedAt(),
                c.getReviewApprovedAt() != null ? c.getReviewApprovedAt() : c.getReviewRejectedAt() != null ? c.getReviewRejectedAt() : now).toMinutes());
        List<Document> documentResponses = Arrays.stream(ContractDocumentType.values()).map(type -> {
            var d = docs.stream().filter(x -> x.getDocumentType() == type)
                    .filter(x -> type != ContractDocumentType.GENERATED_DRAFT || Objects.equals(x.getSourceReviewRequestedAt(), c.getReviewRequestedAt()))
                    .findFirst().orElse(null);
            return d == null ? new Document(type, false, null, null, null, null)
                    : new Document(type, true, d.getOriginalName(), storage.download(d).downloadUrl(), d.getUploadedAt(), d.getSourceReviewRequestedAt());
        }).toList();
        var last = requests.isEmpty() ? null : requests.getFirst();
        return new Detail(new ContractInfo(id, c.getContractNumber(), c.getTitle(), status, status.getLabel(), status.getTone(),
                c.getGroupBuyStartAt(), c.getGroupBuyEndAt(), c.periodDays(),
                new Brand(c.getMarket().getId(), c.getMarket().getMarketName(), "/admin/brands/" + c.getMarket().getId(),
                        c.getMarket().getSeller() == null ? null : c.getMarket().getSeller().getEmail()),
                c.getCreator() == null ? null : new Creator(c.getCreator().getId(), c.getCreator().getShowroomName(),
                        "/admin/creators/" + c.getCreator().getId(), c.getCreator().getBusinessEmail()), threadId),
                new Stepper(c.getReviewApprovedAt(), actor(history, ContractEventType.REVIEW_APPROVED), c.getSignatureRequestedAt(),
                        (c.getBrandSignedAt() == null ? 0 : 1) + (c.getCreatorSignedAt() == null ? 0 : 1), c.getConcludedAt(), actor(history, ContractEventType.CONCLUDED)),
                new Review(c.getReviewRequestedAt(), (minutes / 1440) + "일 " + (minutes % 1440 / 60) + "시간", c.getReviewApprovedAt(), c.getReviewRejectedAt(), shared.review().rejectReason()),
                new Signature(c.getSignatureRequestedAt(), c.getSignatureDeadlineAt(), c.getBrandSignedAt(), c.getCreatorSignedAt(), c.getSignatureAsOf(),
                        actor(history, ContractEventType.SIGNATURE_UPDATED), due ? Duration.between(c.getSignatureDeadlineAt(), now).toDays() : 0),
                shared.items(), shared.content(), shared.fixedFee(), shared.settlement(), shared.closure(), documentResponses,
                // 미처리 건수는 처리할 수 있을 때(서명 진행 중)만 센다 — 목록의 재발송 큐와 같은 정의다.
                new Resend(signing ? requests.stream().filter(r -> !r.isHandled()).count() : 0,
                        last == null ? null : last.getRequestedAt(), last == null ? null : last.getRequesterType()),
                groupBuy(c),
                new Permissions(status == ContractStatus.REVIEW_PENDING, status == ContractStatus.REVIEW_PENDING,
                        signing || pending, pending && signed && completeDocs, signing && due, signing, pending,
                        ContractStatus.ADMIN_CANCELABLE.contains(status)),
                history.stream().map(h -> new History(h.getEventType(), h.getActorType(), h.getActorId(), h.getActorDisplayName(), h.getDetail(), h.getOccurredAt())).toList(), c.getVersion());
    }

    /** 체결 트랜잭션이 만든 공구(공구 설계서 2-1). 체결 전에는 id·번호·상태 모두 null이다. */
    private GroupBuy groupBuy(Contract c) {
        if (c.getGroupBuyId() == null) return new GroupBuy(null, null, null);
        return groupBuys.findById(c.getGroupBuyId())
                .map(g -> new GroupBuy(g.getId(), g.getGroupBuyNumber(), g.getStatus().name()))
                .orElse(new GroupBuy(c.getGroupBuyId(), null, null));
    }

    private String actor(List<ContractHistory> entries, ContractEventType type) {
        return entries.reversed().stream().filter(h -> h.getEventType() == type).map(ContractHistory::getActorDisplayName)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }
}

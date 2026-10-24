package showroomz.api.creator.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.home.dto.CreatorHomeDto;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.global.config.properties.MemberProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 스튜디오 홈 할 일(1017 설계서 5-1) — 할 일 한 건 = 카드 한 장 · 버튼 하나. 정렬 · 문구 · 정지 중 막힘을 서버가 정한다
 * (H1 · H2 · H4 · H5 에서 같은 카드가 같은 말을 해야 한다).
 *
 * <p>정렬 — 세금계산서 「지급 대기」 맨 위 → 기한(게시물 작성 · 재등록은 오픈일) 이른 순 → 기한 없는 일 → 연결 요청 맨 아래.
 * 정지 중에도 할 일을 숨기지 않는다 — 막히는 카드만 {@code blocked}(연결 수락 · 게시물 등록 · 재등록).
 * 연장 응답 카드는 거절이 허용되므로 {@code blocked} 를 두지 않고 {@code extra.acceptBlocked} 로 수락만 막는다(55 계획서 S9 · 1017 H5).
 * 계약 서명 · 세금계산서 · 숨김 게시물 수정은 그대로 할 수 있다(진행 중 공구를 지키는 일).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorHomeTodoBuilder {

    static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM.dd");
    static final String TAX_INVOICE_ISSUE = "TAX_INVOICE_ISSUE";
    static final String CONTRACT_SIGN = "CONTRACT_SIGN";
    static final String EXTENSION_RESPOND = "EXTENSION_RESPOND";
    static final String GROUP_BUY_POST_WRITE = "GROUP_BUY_POST_WRITE";
    static final String GROUP_BUY_POST_RESUBMIT = "GROUP_BUY_POST_RESUBMIT";
    static final String GROUP_BUY_POST_HIDDEN_FIX = "GROUP_BUY_POST_HIDDEN_FIX";
    static final String CONNECTION_REQUESTS = "CONNECTION_REQUESTS";
    static final String EXTENSION_ACCEPT_BLOCKED = "연장 수락은 정지 해제 후 할 수 있어요";
    /** 게시물 할 일을 볼 공구 — 작성 · 재등록은 준비중, 숨김 수정은 준비완료 · 진행중(공구 GNB 배지 판정과 같다). */
    static final EnumSet<GroupBuyStatus> POST_TODO_STATUSES =
            EnumSet.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY, GroupBuyStatus.IN_PROGRESS);

    private final SettlementTaxDocumentRepository taxDocumentRepository;
    private final ContractRepository contractRepository;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository groupBuyPostRepository;
    private final ConnectionRepository connectionRepository;
    private final MemberProperties memberProperties;

    public List<CreatorHomeDto.Todo> build(Creator creator, LocalDateTime now) {
        boolean suspended = creator.isSuspended();
        List<CreatorHomeDto.Todo> todos = new ArrayList<>(taxInvoices(creator.getId()));

        List<Dated> dated = new ArrayList<>();
        List<CreatorHomeDto.Todo> undated = new ArrayList<>();
        for (Contract contract : contractRepository.findCreatorSignatureTodos(creator.getId())) {
            dated.add(new Dated(contract.getSignatureDeadlineAt(), contractSign(contract)));
        }
        for (GroupBuyExtensionRequest request : extensionRequestRepository.findPendingForCreator(creator.getId(), now)) {
            dated.add(new Dated(request.getGroupBuy().getEndAt(), extensionRespond(request, suspended)));
        }
        postTodos(creator.getId(), suspended && memberProperties.getSanction().isBlockGroupBuyPost(), dated, undated);

        dated.sort(Comparator.comparing(Dated::sortKey, Comparator.nullsLast(Comparator.naturalOrder())));
        dated.forEach(d -> todos.add(d.todo()));
        todos.addAll(undated);
        CreatorHomeDto.Todo connections = connectionRequests(creator, suspended);
        if (connections != null) {
            todos.add(connections);
        }
        return todos;
    }

    // ------------------------------------------------------------------ 세금계산서(H4)

    private List<CreatorHomeDto.Todo> taxInvoices(Long creatorId) {
        List<CreatorHomeDto.Todo> todos = new ArrayList<>();
        for (Object[] row : taxDocumentRepository.findCreatorInvoicesToInput(creatorId)) {
            SettlementTaxDocument document = (SettlementTaxDocument) row[0];
            Settlement settlement = (Settlement) row[1];
            String groupBuyName = (String) row[2];
            boolean rejected = document.getStatus() == TaxDocumentStatus.REJECTED;
            LocalDateTime confirmedAt = settlement.getConfirmedAt();
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("settlementId", settlement.getId());
            extra.put("groupBuyName", groupBuyName);
            extra.put("confirmedAt", confirmedAt);
            extra.put("amount", settlement.getCreatorPayoutAmount());
            extra.put("rejected", rejected);
            String description = rejected
                    ? "승인번호가 반려됐어요 · 세금계산서를 확인하고 다시 입력해 주세요"
                    : (confirmedAt == null ? "" : confirmedAt.format(MONTH_DAY) + " 정산 자동 확정 · ")
                    + "세금계산서를 발행하고 승인번호를 입력하면 지급돼요";
            todos.add(new CreatorHomeDto.Todo(TAX_INVOICE_ISSUE, null, "지급 대기", "세금계산서 발행 · " + groupBuyName,
                    description, rejected ? "다시 입력" : "승인번호 입력",
                    new CreatorHomeDto.Target("SETTLEMENT", settlement.getId()), null, extra));
        }
        return todos;
    }

    // ------------------------------------------------------------------ 계약 서명 · 연장 응답

    /** 서명은 정지 중에도 허용 — 체결 보류 중이면 기한이 멈춰 있어 「보류 중」으로 적는다. */
    private static CreatorHomeDto.Todo contractSign(Contract contract) {
        boolean brandSigned = contract.getBrandSignedAt() != null;
        String brandName = contract.getMarket().getMarketName();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("brandSigned", brandSigned);
        extra.put("brandName", brandName);
        String dueLabel = contract.isHeldBySanction() ? "보류 중"
                : contract.getSignatureDeadlineAt() == null ? null
                : "서명 기한 " + contract.getSignatureDeadlineAt().format(MONTH_DAY);
        String description = brandSigned
                ? brandName + " 브랜드가 서명했어요 · 메일 · 문자로 받은 링크에서 서명해 주세요"
                : "메일 · 문자로 받은 링크에서 서명해 주세요";
        return new CreatorHomeDto.Todo(CONTRACT_SIGN, contract.getSignatureDeadlineAt(), dueLabel,
                "계약 서명 · " + contract.getTitle(), description, "계약 보기",
                new CreatorHomeDto.Target("CONTRACT", contract.getId()), null, extra);
    }

    /**
     * 응답 기한 = 공구 종료 전. 정지 중에는 수락만 막힌다 — 거절은 허용되므로(SanctionScopeIntegrationTest) 카드 버튼은 막지 않는다
     * ({@code blocked = null}). 수락 버튼만 {@code extra.acceptBlocked} · {@code acceptBlockedMessage} 로 끈다(55 계획서 S9).
     */
    private static CreatorHomeDto.Todo extensionRespond(GroupBuyExtensionRequest request, boolean suspended) {
        GroupBuy groupBuy = request.getGroupBuy();
        String brandName = groupBuy.getMarket().getMarketName();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("extensionDays", request.getExtensionDays());
        extra.put("brandName", brandName);
        extra.put("acceptBlocked", suspended);
        if (suspended) {
            extra.put("acceptBlockedMessage", EXTENSION_ACCEPT_BLOCKED);
        }
        return new CreatorHomeDto.Todo(EXTENSION_RESPOND, groupBuy.getEndAt(),
                "응답 기한 " + groupBuy.getEndAt().format(MONTH_DAY), "연장 요청 응답 · " + groupBuy.getContract().getTitle(),
                brandName + " 브랜드가 " + request.getExtensionDays() + "일 연장을 요청했어요 · 공구 종료 전에 응답해 주세요",
                "응답하기", new CreatorHomeDto.Target("GROUP_BUY", groupBuy.getId()), null, extra);
    }

    // ------------------------------------------------------------------ 공구 게시물

    private void postTodos(Long creatorId, boolean postBlocked, List<Dated> dated, List<CreatorHomeDto.Todo> undated) {
        List<GroupBuy> groupBuys = groupBuyRepository.findByCreatorAndStatuses(creatorId, POST_TODO_STATUSES);
        if (groupBuys.isEmpty()) {
            return;
        }
        Map<Long, GroupBuyPost> posts = groupBuyPostRepository
                .findByGroupBuyIds(groupBuys.stream().map(GroupBuy::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getGroupBuy().getId(), Function.identity(), (a, b) -> a));
        for (GroupBuy groupBuy : groupBuys) {
            GroupBuyPostStatus postStatus = GroupBuyPostStatus.of(posts.get(groupBuy.getId()), groupBuy);
            String name = groupBuy.getContract().getTitle();
            CreatorHomeDto.Target target = new CreatorHomeDto.Target("GROUP_BUY", groupBuy.getId());
            String openLabel = "오픈 " + groupBuy.getStartAt().format(MONTH_DAY);
            if (groupBuy.getStatus() == GroupBuyStatus.PREPARING
                    && (postStatus == GroupBuyPostStatus.NOT_WRITTEN || postStatus == GroupBuyPostStatus.WRITING)) {
                // dueAt = 오픈일 — 재등록 카드와 같다. 제출 마감 규칙은 없어 달력에는 넣지 않는다(55 계획서 S10 · 시안 「카드에는 오픈일」).
                dated.add(new Dated(groupBuy.getStartAt(), new CreatorHomeDto.Todo(GROUP_BUY_POST_WRITE,
                        groupBuy.getStartAt(), openLabel,
                        "공구 게시물 작성 · " + name, "오픈 전에 게시물을 등록하면 운영팀 검토 후 공개돼요", "작성하기", target,
                        postBlocked ? new CreatorHomeDto.Blocked("정지 해제 후 등록할 수 있어요") : null, null)));
            } else if (groupBuy.getStatus() == GroupBuyStatus.PREPARING && postStatus == GroupBuyPostStatus.REJECTED) {
                dated.add(new Dated(groupBuy.getStartAt(), new CreatorHomeDto.Todo(GROUP_BUY_POST_RESUBMIT,
                        groupBuy.getStartAt(), openLabel, "게시물 재등록 · " + name,
                        "운영팀이 게시물을 반려했어요 · 사유를 확인하고 다시 등록해 주세요", "수정하기", target,
                        postBlocked ? new CreatorHomeDto.Blocked("정지 해제 후 재등록할 수 있어요") : null, null)));
            } else if (groupBuy.getStatus() != GroupBuyStatus.PREPARING && postStatus == GroupBuyPostStatus.EXPOSURE_SUSPENDED) {
                undated.add(new CreatorHomeDto.Todo(GROUP_BUY_POST_HIDDEN_FIX, null, null, "노출 중지 게시물 수정 · " + name,
                        "운영팀이 게시물을 숨겼어요 · 사유를 확인하고 수정해 주세요", "수정하기", target, null, null));
            }
        }
    }

    // ------------------------------------------------------------------ 연결 요청(맨 아래 · 한 장)

    private CreatorHomeDto.Todo connectionRequests(Creator creator, boolean suspended) {
        Page<Connection> page = connectionRepository.findRequestsByCreator(creator, List.of(ConnectionStatus.REQUESTED),
                null, PageRequest.of(0, 2));
        long count = page.getTotalElements();
        if (count == 0) {
            return null;
        }
        String names = page.getContent().stream().map(c -> c.getMarket().getMarketName())
                .collect(Collectors.joining(" · "));
        String title = "연결 요청 · " + names + (count > 2 ? " 외 " + (count - 2) + "곳" : "");
        return new CreatorHomeDto.Todo(CONNECTION_REQUESTS, null, null, title,
                "브랜드가 연결을 요청했어요 · 수락하면 계약을 받을 수 있어요", "요청 보기",
                new CreatorHomeDto.Target("CONNECTION_REQUESTS", null),
                suspended ? new CreatorHomeDto.Blocked("연결 수락은 정지 해제 후 할 수 있어요") : null,
                Map.of("count", count));
    }

    /** @param sortKey 정렬 기준 — 기한(게시물 작성은 오픈일) */
    private record Dated(LocalDateTime sortKey, CreatorHomeDto.Todo todo) {
    }
}

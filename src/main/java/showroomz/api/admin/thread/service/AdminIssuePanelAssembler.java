package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelInfo;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueBrand;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueContract;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueGroupBuy;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueInfluencer;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueLinks;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssuePanel;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueProposal;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueSettlement;
import showroomz.api.admin.thread.dto.AdminThreadDto.IssueStep;
import showroomz.api.admin.thread.dto.AdminThreadDto.PairInfo;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.adjustment.port.SettlementAdjustmentPort;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.AdjustmentSummary;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader.ProposalView;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 어드민 20b 이슈 패널(44 이슈 스레드 설계서 4-3) — 정산 영향 · 이슈 정보 · 진행 단계 · 참고 링크.
 *
 * <p>배지 · 보류 문구는 <b>운영자 기준</b>이다(§42 「목록 배지」) — 당사자 화면의 차례(내 응답 필요 · 상대 응답 대기)와 다르다.
 * 운영자는 참가자가 아니므로 차례가 없다.
 */
@Component
@RequiredArgsConstructor
public class AdminIssuePanelAssembler {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");

    private final SettlementAdjustmentReader reader;
    private final SettlementAdjustmentPort settlementPort;
    private final GroupBuyRepository groupBuys;
    private final MessageThreadRepository threads;

    /** 목록 · 패널 공통 배지 — {@code [라벨, 톤]}. */
    public static String[] badgeOf(AdjustmentStatus status) {
        return switch (status) {
            case OPEN -> new String[]{"응답 대기", "INFO"};
            case AGREED -> new String[]{"합의 · 금액 변경", "SUCCESS"};
            case EXPIRED -> new String[]{"기한 만료 · 원래 금액", "NEUTRAL"};
        };
    }

    public static String subtitleOf(String brandName, String showroomName) {
        return "%s × %s · 정산 조정 요청".formatted(brandName, showroomName);
    }

    /** 이슈 스레드의 헤더 + 패널. 상대가 한 명이 아니라 회원 칸(profile · progress · openIssueThreads)은 비운다. */
    public ChannelInfo info(MessageThread thread) {
        AdjustmentSummary a = reader.findByThreadId(thread.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_FOUND));
        Connection connection = thread.getConnection();
        String brandName = connection.getMarket().getMarketName();
        String showroomName = connection.getCreator().getShowroomName();
        Optional<GroupBuy> groupBuy = groupBuys.findDetailById(a.groupBuyId());
        String title = groupBuy.map(g -> g.getContract().getTitle()).orElse(null);
        return new ChannelInfo(thread.getId(), AdminChannelTab.ISSUE, title, null, null, null, null, false,
                null, null, null, panel(a, connection, groupBuy, brandName, showroomName), null);
    }

    /** 브랜드–인플루언서 1:1 스레드 열람(4-5) — 헤더만. 읽기 전용이라 {@code writable = false}. */
    public ChannelInfo pairInfo(MessageThread thread) {
        Connection connection = thread.getConnection();
        String brandName = connection.getMarket().getMarketName();
        String showroomName = connection.getCreator().getShowroomName();
        return new ChannelInfo(thread.getId(), null, "%s × %s".formatted(brandName, showroomName), null, null, null,
                null, false, null, null, null, null,
                new PairInfo(connection.getMarket().getId(), brandName, connection.getCreator().getId(), showroomName));
    }

    private IssuePanel panel(AdjustmentSummary a, Connection connection, Optional<GroupBuy> groupBuy,
                             String brandName, String showroomName) {
        String[] badge = badgeOf(a.status());
        ProposalView latest = a.latestProposal();
        IssueProposal current = latest == null ? null
                : new IssueProposal(latest.proposalId(), latest.seq(), latest.rewardAmount(),
                latest.proposerType().name(), nameOf(latest.proposerType(), brandName, showroomName),
                nameOf(latest.proposerType().counterpart(), brandName, showroomName), latest.status().name());
        Long finalCreatorNet = a.finalRewardAmount() == null ? null
                : settlementPort.preview(a.settlementId(), a.finalRewardAmount()).creatorNetAmount();
        IssueSettlement settlement = new IssueSettlement(a.settlementId(), a.settlementNumber(), holdLabelOf(a.status()),
                a.status() == AdjustmentStatus.OPEN ? "WARNING" : "SUCCESS", a.originalRewardAmount(), current,
                a.agreedRewardAmount(), a.finalRewardAmount(), finalCreatorNet);
        String requesterName = nameOf(a.requesterType(), brandName, showroomName);
        return new IssuePanel(a.adjustmentId(), a.status().name(), a.status().getLabel(), badge[0], badge[1],
                settlement,
                new IssueGroupBuy(a.groupBuyId(), groupBuy.map(GroupBuy::getGroupBuyNumber).orElse(null),
                        groupBuy.map(g -> g.getContract().getTitle()).orElse(null)),
                groupBuy.map(g -> new IssueContract(g.getContract().getId(), g.getContract().getContractNumber()))
                        .orElse(null),
                new IssueBrand(a.marketId(), brandName), new IssueInfluencer(a.creatorId(), showroomName),
                a.requesterType().name(), a.openedAt(), a.deadlineAt(), a.remainingBusinessDays(), a.closedAt(),
                steps(a, requesterName),
                new IssueLinks(pairThreadIdOf(connection), a.settlementId(), a.marketId(), a.creatorId()));
    }

    private static String holdLabelOf(AdjustmentStatus status) {
        return switch (status) {
            case OPEN -> "정산 보류 · 전액";
            case AGREED -> "보류 해제 · 금액 변경";
            case EXPIRED -> "보류 해제 · 금액 변경 없음";
        };
    }

    /**
     * 진행 단계 4칸 — OPEN: ① DONE ② CURRENT ③④ TODO / AGREED: 전부 DONE / EXPIRED: ①② DONE · ③ SKIPPED · ④ DONE.
     * 만료는 「합의 · 반영」이 일어나지 않았으므로 ③을 SKIPPED 로 내린다(10-1 #4).
     */
    static List<IssueStep> steps(AdjustmentSummary a, String requesterName) {
        IssueStep requested = new IssueStep("REQUESTED", "요청", "DONE",
                "%s · %s".formatted(DAY.format(a.openedAt()), requesterName));
        return switch (a.status()) {
            case OPEN -> List.of(requested,
                    new IssueStep("OPEN", "협의", "CURRENT", "양측 직접"),
                    new IssueStep("AGREED", "합의 · 반영", "TODO", "동의 시 자동"),
                    new IssueStep("CLOSED", "종결", "TODO", "합의 또는 " + DAY.format(a.deadlineAt())));
            case AGREED -> List.of(requested,
                    new IssueStep("OPEN", "협의", "DONE", "양측 직접"),
                    new IssueStep("AGREED", "합의 · 반영", "DONE", dayOf(a.closedAt()) + " · 금액 변경"),
                    new IssueStep("CLOSED", "종결", "DONE", dayOf(a.closedAt()) + " · 합의"));
            case EXPIRED -> List.of(requested,
                    new IssueStep("OPEN", "협의", "DONE", "양측 직접"),
                    new IssueStep("AGREED", "합의 · 반영", "SKIPPED", "합의 없음"),
                    new IssueStep("CLOSED", "종결", "DONE", dayOf(a.closedAt()) + " · 기한 만료"));
        };
    }

    /** 그 쌍의 1:1 스레드 — 연결이 끊겼으면 null(열람 링크를 그리지 않는다). */
    private Long pairThreadIdOf(Connection connection) {
        if (connection.getStatus() != ConnectionStatus.CONNECTED) {
            return null;
        }
        return threads.findByConnectionAndKind(connection, ThreadKind.CONNECTION).map(MessageThread::getId)
                .orElse(null);
    }

    private static String nameOf(SettlementParty party, String brandName, String showroomName) {
        return party == SettlementParty.SELLER ? brandName : showroomName;
    }

    private static String dayOf(LocalDateTime at) {
        return at == null ? "" : DAY.format(at);
    }
}

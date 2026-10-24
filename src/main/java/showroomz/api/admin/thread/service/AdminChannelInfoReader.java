package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.thread.dto.AdminThreadDto.OpenIssueThread;
import showroomz.api.admin.thread.dto.AdminThreadDto.OpenSanction;
import showroomz.api.admin.thread.dto.AdminThreadDto.Profile;
import showroomz.api.admin.thread.dto.AdminThreadDto.Progress;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.brand.service.BrandSanctionReader;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorMemberQueryRepository;
import showroomz.domain.member.creator.service.CreatorSanctionReader;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 접이식 정보 바의 값(36 설계 3-4) — <b>이 화면에서 계산하지 않는다.</b> 각 수치는 그 도메인 리포지토리의 집계를 읽는다.
 *
 * <p>집계할 모듈이 없으면 0이 아니라 {@code null}이다 — 0은 「없음」이라는 사실이고 null은 「모름」이다.
 * 과세 유형은 저장 값이 없어 null이다. 미정산 건수는 회원 상세의 탈퇴 조건과 같은 숫자를 읽는다(55 계획서 A9).
 */
@Component
@RequiredArgsConstructor
public class AdminChannelInfoReader {

    /** 「서명 진행중」 — 서명 요청이 나가 있고 아직 체결 전인 계약. 운영자가 취소할 수 있는 구간과 같다(11-2 #4 · 잠정). */
    private static final Set<ContractStatus> SIGNING = Set.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING);
    private static final Set<GroupBuyStatus> ENDED = Set.of(GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED);

    private final ContractRepository contracts;
    private final GroupBuyRepository groupBuys;
    private final GroupBuyAdminSuspensionRepository adminSuspensions;
    private final ConnectionRepository connections;
    private final MessageThreadRepository threads;
    private final SettlementPayoutRepository payouts;
    private final SettlementClawbackRepository clawbacks;
    private final CreatorMemberQueryRepository creatorQueries;
    private final BrandSanctionReader brandSanctions;
    private final CreatorSanctionReader creatorSanctions;

    public Profile brandProfile(Market market) {
        Seller seller = market.getSeller();
        return new Profile(seller.getName(), seller.getPhoneNumber(), null, null, null, null, seller.getCreatedAt());
    }

    public Profile creatorProfile(Creator creator) {
        return new Profile(null, null, creator.getBusinessType(), null, creator.getBusinessEmail(),
                creator.getInstagramUrl(), creator.getCreatedAt());
    }

    public Progress brandProgress(Market market) {
        Map<Object, Long> contractCounts = toMap(contracts.countByStatus(market.getId()));
        Map<Object, Long> groupBuyCounts = toMap(groupBuys.countByStatus(market.getId()));
        return new Progress(sum(contractCounts, SIGNING), sum(contractCounts, Set.of(ContractStatus.CONCLUDED)),
                sum(groupBuyCounts, GroupBuyStatus.SELLING), null,
                adminSuspensions.countNoticedByMarketId(market.getId()), brandUnsettled(market.getId()), null);
    }

    public Progress creatorProgress(Creator creator) {
        // 스튜디오에 도착한 계약만 센다 — 브랜드가 작성 중이거나 검토 중인 계약은 인플루언서에게 없는 계약이다.
        Map<Object, Long> contractCounts = toMap(contracts.countByStatusForCreator(creator.getId(),
                List.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING, ContractStatus.CONCLUDED)));
        Map<Object, Long> groupBuyCounts = toMap(groupBuys.countByStatusForCreator(creator.getId()));
        long connectedBrands = connections.countByTypeAndCreatorAndStatus(
                ConnectionType.PAIR, creator, ConnectionStatus.CONNECTED);
        return new Progress(sum(contractCounts, SIGNING), sum(contractCounts, Set.of(ContractStatus.CONCLUDED)),
                sum(groupBuyCounts, GroupBuyStatus.SELLING), sum(groupBuyCounts, ENDED),
                adminSuspensions.countNoticedByCreatorId(creator.getId()), creatorUnsettled(creator.getId()),
                connectedBrands);
    }

    // ------------------------------------------------------------------ 미정산 — 탈퇴 조건과 같은 숫자(55 계획서 A9 · D22)

    /**
     * 두 화면(정보 바 · 회원 상세 탈퇴 조건)의 숫자가 같아야 하므로 미정산 판정식은 이 메서드와 {@link #creatorUnsettled}에만 둔다.
     * 브랜드는 탈퇴 조건 ④와 같은 <b>브랜드 몫 기준</b>(51 설계서 1-5 · D-1) — 브랜드 지급 행이 지급 완료가 아닌 정산(분배 실패 · 지급 보류
     * 포함 · D22) + 회수 대기 차감.
     */
    private long brandUnsettled(Long marketId) {
        return payouts.countBrandOpenForMarket(marketId) + clawbacks.countPendingForMarket(marketId);
    }

    /** 인플루언서 탈퇴 조건(미정산)과 같은 쿼리. */
    private long creatorUnsettled(Long creatorId) {
        return creatorQueries.countUnsettled(creatorId);
    }

    // ------------------------------------------------------------------ 정지 사유 · 일시(55 계획서 A11)

    /** 열린 정지가 없으면 null — 상태가 정지여도 정지 행이 없으면(옛 상태 변경) 사유를 모른다. */
    public OpenSanction brandSanction(Long marketId) {
        return brandSanctions.openSanction(marketId)
                .map(s -> new OpenSanction(s.getReason().name(), s.getReason().getLabel(), s.getReasonDetail(),
                        s.getSuspendedAt(), s.getEndsOn(), s.getAppealDeadlineAt()))
                .orElse(null);
    }

    public OpenSanction creatorSanction(Long creatorId) {
        return creatorSanctions.openSanction(creatorId)
                .map(s -> new OpenSanction(s.getReason().name(), s.getReason().getLabel(), s.getReasonDetail(),
                        s.getSuspendedAt(), s.getEndsOn(), s.getAppealDeadlineAt()))
                .orElse(null);
    }

    /** 그 회원이 당사자인 열린 공구 3자 스레드 — 이슈 스레드 기획 전까지는 목록만 내린다(여는 화면이 없다). */
    public List<OpenIssueThread> openIssueThreads(Long marketId, Long creatorId) {
        List<MessageThread> open = threads.findOpenGroupBuyThreadsOf(marketId, creatorId);
        if (open.isEmpty()) {
            return List.of();
        }
        Map<Long, String> titles = groupBuys.findTitleMapByIds(
                open.stream().map(MessageThread::getSubjectId).filter(Objects::nonNull).distinct().toList());
        return open.stream()
                .map(t -> new OpenIssueThread(t.getId(), t.getKind(), t.getSubjectId(),
                        t.getSubjectId() == null ? null : titles.get(t.getSubjectId())))
                .toList();
    }

    private static Map<Object, Long> toMap(List<Object[]> rows) {
        Map<Object, Long> counts = new java.util.HashMap<>();
        for (Object[] row : rows) {
            counts.put(row[0], (Long) row[1]);
        }
        return counts;
    }

    private static long sum(Map<Object, Long> counts, Set<?> keys) {
        long total = 0;
        for (Object key : keys) {
            total += counts.getOrDefault(key, 0L);
        }
        return total;
    }
}

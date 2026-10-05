package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.thread.dto.AdminThreadDto.OpenIssueThread;
import showroomz.api.admin.thread.dto.AdminThreadDto.Profile;
import showroomz.api.admin.thread.dto.AdminThreadDto.Progress;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 접이식 정보 바의 값(36 설계 3-4) — <b>이 화면에서 계산하지 않는다.</b> 각 수치는 그 도메인 리포지토리의 집계를 읽는다.
 *
 * <p>집계할 모듈이 없으면 0이 아니라 {@code null}이다 — 0은 「없음」이라는 사실이고 null은 「모름」이다.
 * 미정산 건수는 정산 모듈이 없어 null이고, 과세 유형은 저장 값이 없어 null이다.
 */
@Component
@RequiredArgsConstructor
public class AdminChannelInfoReader {

    /** 「서명 진행중」 — 서명 요청이 나가 있고 아직 체결 전인 계약. 운영자가 취소할 수 있는 구간과 같다(11-2 #4 · 잠정). */
    private static final Set<ContractStatus> SIGNING = Set.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING);
    private static final Set<GroupBuyStatus> ENDED = Set.of(GroupBuyStatus.ENDED, GroupBuyStatus.SETTLED);

    private final ContractRepository contracts;
    private final GroupBuyRepository groupBuys;
    private final ConnectionRepository connections;
    private final MessageThreadRepository threads;

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
                sum(groupBuyCounts, GroupBuyStatus.SELLING), null, null, null);
    }

    public Progress creatorProgress(Creator creator) {
        // 스튜디오에 도착한 계약만 센다 — 브랜드가 작성 중이거나 검토 중인 계약은 인플루언서에게 없는 계약이다.
        Map<Object, Long> contractCounts = toMap(contracts.countByStatusForCreator(creator.getId(),
                List.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING, ContractStatus.CONCLUDED)));
        Map<Object, Long> groupBuyCounts = toMap(groupBuys.countByStatusForCreator(creator.getId()));
        long connectedBrands = connections.countByTypeAndCreatorAndStatus(
                ConnectionType.PAIR, creator, ConnectionStatus.CONNECTED);
        return new Progress(sum(contractCounts, SIGNING), sum(contractCounts, Set.of(ContractStatus.CONCLUDED)),
                sum(groupBuyCounts, GroupBuyStatus.SELLING), sum(groupBuyCounts, ENDED), null, connectedBrands);
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

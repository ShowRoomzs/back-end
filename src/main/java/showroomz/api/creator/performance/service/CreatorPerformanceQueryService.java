package showroomz.api.creator.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.groupbuy.service.CreatorGroupBuyReader;
import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.api.creator.performance.type.CreatorPerformanceSort;
import showroomz.api.creator.performance.type.CreatorPerformanceStatus;
import showroomz.api.creator.performance.type.RewardStage;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.performance.service.GroupBuyPerformanceReader;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 쇼룸 스튜디오 성과 관리(10) 조회(49 스튜디오 성과 설계서 3절 · 0-A) — 수치는 전부 공통 리더({@link GroupBuyPerformanceReader})의
 * {@link PerformanceFacts} 다. 이 서비스는 <b>스튜디오의 창</b>만 만든다 — 상태 3종 접기 · 리워드 단계 · KPI 모집단 · 정렬 · 페이징.
 *
 * <p>목록 · KPI 는 기간 안 종료 공구를 한 번 읽어 메모리에서 거르고 정렬한다 — 상태가 정산 행에서 파생되고(저장하지 않는다) 정렬 키
 * (리워드 · 전환율)도 리더가 계산한 값이라 SQL 로 내릴 수 없다. 한 인플루언서의 기간 안 종료 공구는 많아야 수십 건이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorPerformanceQueryService {

    private static final int DEFAULT_MONTHS = 6;
    private static final Set<Integer> ALLOWED_MONTHS = Set.of(3, 6, 12);
    private static final int MAX_PAGE_SIZE = 100;

    private final CreatorGroupBuyReader groupBuyReader;
    private final CreatorPerformanceGroupBuys groupBuys;
    private final GroupBuyPerformanceReader performanceReader;
    private final CreatorPerformanceDetailAssembler detailAssembler;

    // ------------------------------------------------------------------ 목록(3-2)

    public PageResponse<CreatorPerformanceDto.ListItem> getList(String creatorEmail, Integer months,
                                                                Set<CreatorPerformanceStatus> statuses, String keyword,
                                                                CreatorPerformanceSort sort, PagingRequest paging) {
        Creator creator = groupBuyReader.resolveCreator(creatorEmail);
        List<GroupBuy> listed = groupBuys.findListed(creator.getId(), LocalDate.now()
                .minusMonths(months(months)).atStartOfDay());
        Map<Long, PerformanceFacts> facts = performanceReader.readRows(listed);

        String needle = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<CreatorPerformanceDto.ListItem> rows = new ArrayList<>();
        for (GroupBuy groupBuy : listed) {
            CreatorPerformanceDto.ListItem row = toListItem(groupBuy, facts.get(groupBuy.getId()));
            if (statuses != null && !statuses.isEmpty() && !statuses.contains(row.status())) {
                continue;
            }
            if (needle != null && !contains(row.groupBuyTitle(), needle) && !contains(row.brandName(), needle)) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(comparator(sort == null ? CreatorPerformanceSort.ENDED_DESC : sort));

        Pageable pageable = paging.toPageable(Sort.unsorted());
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE);
        }
        int start = (int) Math.min(pageable.getOffset(), rows.size());
        int end = Math.min(start + pageable.getPageSize(), rows.size());
        List<CreatorPerformanceDto.ListItem> content = rows.subList(start, end);
        return new PageResponse<>(content, new PageImpl<>(content, pageable, rows.size()));
    }

    // ------------------------------------------------------------------ 요약 · KPI(3-3)

    public CreatorPerformanceDto.Summary getSummary(String creatorEmail, Integer months) {
        Creator creator = groupBuyReader.resolveCreator(creatorEmail);
        int span = months(months);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(span);
        List<GroupBuy> listed = groupBuys.findListed(creator.getId(), from.atStartOfDay());
        Map<Long, PerformanceFacts> facts = performanceReader.readRows(listed);

        Map<CreatorPerformanceStatus, Long> counts = new EnumMap<>(CreatorPerformanceStatus.class);
        for (CreatorPerformanceStatus status : CreatorPerformanceStatus.values()) {
            counts.put(status, 0L);
        }
        List<PerformanceFacts> settled = new ArrayList<>();
        for (PerformanceFacts f : facts.values()) {
            CreatorPerformanceStatus status = CreatorPerformanceStatus.of(f.status());
            counts.merge(status, 1L, Long::sum);
            if (status == CreatorPerformanceStatus.SETTLED) {
                settled.add(f);
            }
        }
        return new CreatorPerformanceDto.Summary(from, to, "최근 %d개월".formatted(span), kpi(settled), counts,
                !listed.isEmpty());
    }

    /** KPI 모집단 = 정산 완료 공구만(§49-1) — 집계 중 · 조정 협의는 리워드가 확정되지 않아 평균을 왜곡한다. */
    static CreatorPerformanceDto.Kpi kpi(List<PerformanceFacts> settled) {
        int n = settled.size();
        if (n == 0) {
            return new CreatorPerformanceDto.Kpi(null, null, 0, null, null, null, null, null);
        }
        long totalReward = settled.stream().mapToLong(CreatorPerformanceQueryService::myReward).sum();
        List<Double> conversions = new ArrayList<>();
        List<Double> repeats = new ArrayList<>();
        List<Double> followerBuyers = new ArrayList<>();
        long impressions = 0;
        int withPost = 0;
        long female = 0;
        long male = 0;
        for (PerformanceFacts f : settled) {
            conversions.add(CreatorPerformanceRates.percent(f.orders().confirmedCount(), f.traffic().visitorCount()));
            long buyers = f.buyers().size();
            repeats.add(CreatorPerformanceRates.percent(
                    f.buyers().stream().filter(PerformanceFacts.Buyer::repeatWithCreator).count(), buyers));
            followerBuyers.add(CreatorPerformanceRates.percent(
                    f.buyers().stream().filter(PerformanceFacts.Buyer::followerAtPurchase).count(), buyers));
            if (f.post() != null) {
                impressions += f.post().impressionCount();
                withPost++;
            }
            for (PerformanceFacts.Buyer buyer : f.buyers()) {
                if ("FEMALE".equalsIgnoreCase(buyer.gender())) {
                    female++;
                } else if ("MALE".equalsIgnoreCase(buyer.gender())) {
                    male++;
                }
            }
        }
        return new CreatorPerformanceDto.Kpi(totalReward, totalReward / n, n,
                CreatorPerformanceRates.average(conversions), CreatorPerformanceRates.average(repeats),
                CreatorPerformanceRates.average(followerBuyers), withPost == 0 ? null : impressions / withPost,
                CreatorPerformanceDistribution.genderRatio(female, male));
    }

    // ------------------------------------------------------------------ 상세(3-4)

    public CreatorPerformanceDto.Detail getDetail(String creatorEmail, Long groupBuyId) {
        Creator creator = groupBuyReader.resolveCreator(creatorEmail);
        GroupBuy groupBuy = groupBuyReader.requireMine(creator.getId(), groupBuyId);   // 남의 공구 404(31 설계 0-4)
        if (groupBuy.getStatus() != GroupBuyStatus.ENDED && groupBuy.getStatus() != GroupBuyStatus.SETTLED) {
            throw new BusinessException(ErrorCode.PERFORMANCE_NOT_READY);              // 진행 중 · 중단 — 공구 관리에서 본다
        }
        if (!performanceReader.isListed(groupBuy)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND);                // 판매 0건 — 성과 모집단 밖(48 0-3)
        }
        return detailAssembler.assemble(groupBuy, creator, performanceReader.read(groupBuy));
    }

    // ------------------------------------------------------------------ 내부

    private static CreatorPerformanceDto.ListItem toListItem(GroupBuy groupBuy, PerformanceFacts f) {
        CreatorPerformanceStatus status = CreatorPerformanceStatus.of(f.status());
        boolean salesFinal = status != CreatorPerformanceStatus.COLLECTING;   // 목록은 정산 완료 전 판매 지표를 「—」로 둔다(0-6)
        RewardStage stage = RewardStage.of(status);
        return new CreatorPerformanceDto.ListItem(groupBuy.getId(), groupBuy.getGroupBuyNumber(),
                groupBuy.getContract().getTitle(), groupBuy.getMarket().getMarketName(), groupBuy.getMarket().getId(),
                f.periodStart(), f.periodEnd(), status, status.getLabel(), status.getTone(),
                salesFinal ? f.orders().confirmedSalesAmount() : null,
                salesFinal ? f.orders().confirmedQuantity() : null,
                salesFinal ? CreatorPerformanceRates.percent(f.orders().confirmedCount(), f.traffic().visitorCount()) : null,
                stage, stage == RewardStage.CONFIRMED ? myReward(f) : null);
    }

    /** 내 리워드 = 판매 리워드(합의 반영 · 정산 행) + 고정 지급비 · 세전(§49-2 · §55 A-3). */
    static long myReward(PerformanceFacts f) {
        long reward = f.settlement() == null ? 0 : f.settlement().getRewardAmount();
        return reward + f.fixedFeeAmount();
    }

    private static Comparator<CreatorPerformanceDto.ListItem> comparator(CreatorPerformanceSort sort) {
        Comparator<CreatorPerformanceDto.ListItem> byEnded = Comparator.comparing(
                CreatorPerformanceDto.ListItem::periodEndAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sort) {
            case ENDED_DESC -> byEnded;
            case REWARD_DESC -> nullsLastDesc(CreatorPerformanceDto.ListItem::myRewardAmount).thenComparing(byEnded);
            case CONVERSION_DESC -> nullsLastDesc(CreatorPerformanceDto.ListItem::conversionRate).thenComparing(byEnded);
        };
    }

    private static <T extends Comparable<? super T>> Comparator<CreatorPerformanceDto.ListItem> nullsLastDesc(
            Function<CreatorPerformanceDto.ListItem, T> key) {
        return Comparator.comparing(key, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static int months(Integer months) {
        if (months == null) {
            return DEFAULT_MONTHS;
        }
        if (!ALLOWED_MONTHS.contains(months)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "months 는 3 · 6 · 12 중 하나입니다.");
        }
        return months;
    }
}

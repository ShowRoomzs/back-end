package showroomz.api.creator.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.home.dto.CreatorHomeDto;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.service.OrderGroupBuyLiveReader;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 스튜디오 진행 중 공구 실시간 띠(1017 설계서 5-2) — 값은 <b>공구 관리 잠정 실적과 같다</b>(취소 · 반품 제외). 파트너 홈 live 의
 * 「결제 기준 · 취소 전 포함」과 정의가 다르다 — 두 시안이 각자 자기 메뉴 값을 쓴다. 방문은 쇼룸 방문 횟수(공구 기간 · 오늘)다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorHomeLiveService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuySalesReader salesReader;
    private final OrderGroupBuyLiveReader liveReader;
    private final ShowroomVisitRepository visitRepository;

    /** 진행 중 공구가 없으면 null(H3). 겹치면 먼저 시작한 공구 하나. */
    public CreatorHomeDto.Live read(Long creatorId) {
        List<GroupBuy> selling = groupBuyRepository.findByCreatorAndStatuses(creatorId, GroupBuyStatus.SELLING);
        if (selling.isEmpty()) {
            return null;
        }
        GroupBuy groupBuy = selling.get(0);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = KstDates.startOfDay(KstDates.today(now));
        LocalDateTime todayFrom = todayStart.isAfter(groupBuy.getStartAt()) ? todayStart : groupBuy.getStartAt();
        GroupBuySalesReader.GroupBuySales sales = salesReader.readSales(groupBuy.getId())
                .orElse(new GroupBuySalesReader.GroupBuySales(0, 0L, List.of()));
        OrderGroupBuyLiveReader.ConfirmedSales today = liveReader.readConfirmedSince(groupBuy.getId(), todayStart);
        return new CreatorHomeDto.Live(now, groupBuy.getId(), groupBuy.getContract().getTitle(),
                groupBuy.getMarket().getMarketName(), groupBuy.getStartAt(), groupBuy.getEndAt(),
                sales.amount(), today.amount(), sales.orderCount(), today.orders(),
                visitRepository.countVisits(creatorId, groupBuy.getStartAt(), now),
                visitRepository.countVisits(creatorId, todayFrom, now));
    }
}

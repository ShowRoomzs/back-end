package showroomz.api.admin.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.home.dto.AdminHomeDto;
import showroomz.domain.metrics.repository.AdminOperationsQueryRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 홈 오늘 지표 4줄(1017 설계서 6-4) — 오늘 00:00 ~ 지금 vs 어제 00:00 ~ 어제 이 시각. 주문 · 거래액은 결제 시각 기준 · 취소 포함
 * (운영 지표 7절의 「오늘」과 같은 정의). 증감 색은 FE 몫이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTodayMetricsReader {

    private final AdminOperationsQueryRepository opsRepository;

    public AdminHomeDto.MetricsToday read(LocalDateTime now) {
        LocalDateTime todayStart = KstDates.startOfDay(KstDates.today(now));
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime yesterdayNow = now.minusDays(1);
        return new AdminHomeDto.MetricsToday(now, List.of(
                new AdminHomeDto.MetricItem("ORDERS", "주문", opsRepository.countPaidOrders(todayStart, now),
                        opsRepository.countPaidOrders(yesterdayStart, yesterdayNow)),
                new AdminHomeDto.MetricItem("PAID_AMOUNT", "결제 거래액", opsRepository.sumPaidAmount(todayStart, now),
                        opsRepository.sumPaidAmount(yesterdayStart, yesterdayNow)),
                new AdminHomeDto.MetricItem("SIGNUPS", "신규 가입", opsRepository.countSignups(todayStart, now),
                        opsRepository.countSignups(yesterdayStart, yesterdayNow)),
                new AdminHomeDto.MetricItem("CLAIMS", "반품 · 교환 접수", opsRepository.countClaims(todayStart, now),
                        opsRepository.countClaims(yesterdayStart, yesterdayNow))));
    }
}

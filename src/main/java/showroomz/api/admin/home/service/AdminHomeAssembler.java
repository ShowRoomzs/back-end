package showroomz.api.admin.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.home.dto.AdminHomeDto;
import showroomz.api.admin.home.type.TodoUrgency;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.metrics.repository.AdminOperationsQueryRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 홈 1회 조립 · 사이드바 배지(1017 설계서 6-1) — 둘 다 {@link AdminHomeQueueReader} 한 번의 계산에서 나온다.
 * 홈 전용 숫자를 만들지 않는다 — 큐 건수는 각 모듈 요약과 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHomeAssembler {

    static final String PROCESSED_NOTE = "처리 시각이 기록되는 큐만 합산 — 배송 예외 · 차감 미회수는 빠진다";
    private static final List<ConnectionType> OPERATOR_CHANNEL_TYPES =
            List.of(ConnectionType.OPERATOR_MARKET, ConnectionType.OPERATOR_CREATOR);

    private final AdminHomeQueueReader queueReader;
    private final AdminTodayScheduleReader scheduleReader;
    private final AdminTodayMetricsReader metricsReader;
    private final AdminIntegrationStatusReader integrationReader;
    private final AdminOperationsQueryRepository opsRepository;
    private final MarketRepository marketRepository;
    private final MessageRepository messageRepository;

    public AdminHomeDto.Home getHome() {
        LocalDateTime now = LocalDateTime.now();
        AdminHomeQueueReader.Snapshot snapshot = queueReader.read(now);
        long total = snapshot.total();
        long failed = snapshot.rowCount(TodoUrgency.FAILED);
        long overdue = snapshot.rowCount(TodoUrgency.OVERDUE);
        long dueSoon = snapshot.rowCount(TodoUrgency.EXECUTION_PENDING) + snapshot.rowCount(TodoUrgency.DUE_SOON);
        AdminHomeDto.Todo todo = new AdminHomeDto.Todo(total, failed, overdue, dueSoon,
                Math.max(0, total - failed - overdue - dueSoon),
                opsRepository.countProcessed(KstDates.startOfDay(KstDates.today(now)), now), PROCESSED_NOTE, snapshot.rows());
        return new AdminHomeDto.Home(now, integrationReader.read(now), todo, scheduleReader.read(now),
                metricsReader.read(now));
    }

    public AdminHomeDto.Badges getBadges() {
        AdminHomeQueueReader.Snapshot snapshot = queueReader.read(LocalDateTime.now());
        long brand = snapshot.count("BRAND_APPLICATION");
        long creator = snapshot.count("CREATOR_APPLICATION");
        long changeRequests = snapshot.count("CHANGE_REQUEST");
        long inquiries = snapshot.count("INQUIRY");
        long posts = snapshot.menuTotal(AdminHomeQueueReader.POSTS);
        // 소통 스레드는 할 일이 아니라 posts.total 에 더하지 않는다 — 더하면 「할 일 합계 = 배지 합」이 깨진다(55 계획서 A12 · D24).
        return new AdminHomeDto.Badges(
                new AdminHomeDto.Onboarding(brand + creator + changeRequests, brand, creator, changeRequests),
                new AdminHomeDto.Members(snapshot.menuTotal(AdminHomeQueueReader.MEMBERS), brandMembers(),
                        opsRepository.countCreatorMembers(), opsRepository.countUserMembers()),
                snapshot.menuTotal(AdminHomeQueueReader.CONTRACTS),
                snapshot.menuTotal(AdminHomeQueueReader.GROUP_BUYS),
                snapshot.menuTotal(AdminHomeQueueReader.TRANSACTIONS),
                snapshot.menuTotal(AdminHomeQueueReader.SETTLEMENTS),
                new AdminHomeDto.Cs(snapshot.menuTotal(AdminHomeQueueReader.CS), inquiries),
                new AdminHomeDto.Posts(posts, posts, operatorThreadUnread()));
    }

    /**
     * 사이드바 소통 스레드 배지(55 계획서 A12 · D24 · 36 설계서 B-7 기본값) — 브랜드 · 인플루언서 탭 운영팀 채널의 운영팀 기준 안 읽은 메시지
     * 합(채널 수가 아니다 — 시안 20a 「브랜드 2」는 한 채널의 안 읽은 2건). 소통 스레드 탭 배지와 같은 쿼리이고, 이슈 탭은 뺀다.
     */
    private long operatorThreadUnread() {
        long sum = 0;
        for (Object[] row : messageRepository.sumUnreadForOperatorTeamByConnectionType(OPERATOR_CHANNEL_TYPES,
                MessageThreadService.OPERATOR_TEAM_PARTICIPANT_ID)) {
            sum += ((Number) row[1]).longValue();
        }
        return sum;
    }

    /** 브랜드 회원 — 어드민 브랜드 목록과 같은 범위(승인된 판매자) · 탈퇴 제외. */
    private long brandMembers() {
        long count = 0;
        for (Object[] row : marketRepository.countBrandMembersByStatus(null, null)) {
            if (row[0] != MarketStatus.WITHDRAWN) {
                count += ((Number) row[1]).longValue();
            }
        }
        return count;
    }
}

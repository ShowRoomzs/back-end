package showroomz.api.creator.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.account.service.CreatorAccountStatusService;
import showroomz.api.creator.common.CreatorResolver;
import showroomz.api.creator.home.dto.CreatorHomeDto;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.api.creator.settlement.service.CreatorSettlementQueryService;
import showroomz.api.creator.showroom.service.Demographics;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.type.CreatorAccountVerificationStatus;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쇼룸 스튜디오 홈 1회 조립(1017 설계서 5-1). 실시간 띠 갱신은 {@code /home/live}, 달력 월 이동은 {@code /home/calendar}.
 * 정지 상태(H5)는 {@code account} · 할 일 카드의 {@code blocked} 로만 보인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorHomeAssembler {

    static final int UPCOMING_LIMIT = 3;
    static final String SETTLEMENT_INFO = "SETTLEMENT_INFO";
    static final String PROFILE = "PROFILE";
    static final String FIRST_POST = "FIRST_POST";
    static final String SHOWROOM_LINK = "SHOWROOM_LINK";

    private final CreatorResolver creatorResolver;
    private final CreatorAccountStatusService accountStatusService;
    private final CreatorHomeTodoBuilder todoBuilder;
    private final CreatorHomeLiveService liveService;
    private final CreatorHomeCalendarService calendarService;
    private final CreatorSettlementQueryService settlementQueryService;
    private final ConnectionRepository connectionRepository;
    private final PostRepository postRepository;
    private final CreatorFollowRepository followRepository;
    private final ShowroomVisitRepository visitRepository;

    /** 쇼룸 주소 규칙 — 내 쇼룸(프로필) 화면과 같은 {@code app.base-url + "/@" + 주소}. */
    @Value("${app.base-url:https://showroomz.shop}")
    private String baseUrl;

    public CreatorHomeDto.Home getHome(Long userId) {
        Creator creator = creatorResolver.resolve(userId);
        LocalDateTime now = LocalDateTime.now();
        return new CreatorHomeDto.Home(
                accountStatusService.of(creator),
                guide(creator),
                liveService.read(creator.getId()),
                todoBuilder.build(creator, now),
                calendarService.upcoming(creator.getId(), now, UPCOMING_LIMIT),
                reward(creator),
                showroom7d(creator.getId(), now));
    }

    /** 시작 가이드(H3) — 연결 요청을 한 번이라도 받으면 사라진다. 링크 복사는 판정할 수 없어 {@code done = null}. */
    CreatorHomeDto.Guide guide(Creator creator) {
        boolean settlementInfo = creator.getAccountVerificationStatus() == CreatorAccountVerificationStatus.VERIFIED
                || creator.getAccountVerificationStatus() == CreatorAccountVerificationStatus.VERIFYING
                || (notBlank(creator.getBankName()) && notBlank(creator.getAccountNumber())
                && notBlank(creator.getBankbookImageUrl()));
        boolean profile = notBlank(creator.getShowroomName()) && notBlank(creator.getIntroduction())
                && notBlank(creator.getProfileImageUrl());
        boolean firstPost = postRepository.countByCreator_IdAndStatus(creator.getId(), PostStatus.PUBLISHED) >= 1;
        String showroomUrl = creator.getShowroomAddress() == null ? null : baseUrl + "/@" + creator.getShowroomAddress();
        List<CreatorHomeDto.ChecklistItem> checklist = List.of(
                new CreatorHomeDto.ChecklistItem(SETTLEMENT_INFO, settlementInfo, null),
                new CreatorHomeDto.ChecklistItem(PROFILE, profile, null),
                new CreatorHomeDto.ChecklistItem(FIRST_POST, firstPost, null),
                new CreatorHomeDto.ChecklistItem(SHOWROOM_LINK, null, showroomUrl));
        int completed = (settlementInfo ? 1 : 0) + (profile ? 1 : 0) + (firstPost ? 1 : 0);
        return new CreatorHomeDto.Guide(!connectionRepository.existsPairForCreator(creator.getId()), checklist,
                completed, checklist.size());
    }

    private CreatorHomeDto.Reward reward(Creator creator) {
        CreatorSettlementDto.Summary summary = settlementQueryService.summarize(creator);
        return new CreatorHomeDto.Reward(summary.payoutScheduled().amount(), summary.payoutScheduled().nearestDate(),
                summary.payoutCondition(), summary.totalPaid().amount());
    }

    /** 쇼룸 통계(DAYS_7)와 같은 공식 — 통계 화면 전체(인구 통계 · 상위 콘텐츠)를 계산하지 않으려고 숫자 셋만 센다. */
    private CreatorHomeDto.Showroom7d showroom7d(Long creatorId, LocalDateTime now) {
        LocalDateTime from = StatsPeriod.DAYS_7.startOf(now);
        long visitors = visitRepository.countVisitors(creatorId, from, now);
        long newFollowers = followRepository.countNewFollowers(creatorId, from, now);
        return new CreatorHomeDto.Showroom7d(visitors, newFollowers,
                visitors == 0 ? null : Demographics.round(newFollowers * 100.0 / visitors));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

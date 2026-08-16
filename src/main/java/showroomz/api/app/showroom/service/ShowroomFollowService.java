package showroomz.api.app.showroom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.showroom.DTO.FollowingShowroomResponse;
import showroomz.api.app.showroom.type.FollowingShowroomSort;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.service.PostAttributionService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 쇼룸(크리에이터) 팔로우 서비스.
 * 팔로우 대상은 쇼룸뿐이다 — 마켓(브랜드) 팔로우는 폐지되었다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ShowroomFollowService {

    private final CreatorFollowRepository creatorFollowRepository;
    private final CreatorRepository creatorRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ConnectionRepository connectionRepository;
    private final PostAttributionService postAttributionService;

    /**
     * 쇼룸 팔로우 — 이미 팔로우 중이면 아무 동작도 하지 않는다.
     */
    public void followShowroom(String username, Long showroomId) {
        Users user = getUser(username);
        Creator creator = getCreator(showroomId);

        if (!creatorFollowRepository.existsByUserAndCreator(user, creator)) {
            CreatorFollow follow = new CreatorFollow(user, creator);

            // §24-7 — 이 팔로우가 어떤 게시물을 보고 누른 것인지 지금 정해 태그로 굳힌다.
            // 팔로우는 로그인 행동이므로 조회 키는 언제나 사용자 기준이다.
            follow.attributeTo(postAttributionService.resolveAttributedPostId(
                    PostAttributionService.viewerKeyOf(user.getId(), null),
                    creator.getId(),
                    LocalDateTime.now()));

            creatorFollowRepository.save(follow);
        }
    }

    /**
     * 쇼룸 팔로우 취소 — 팔로우 중이 아니면 아무 동작도 하지 않는다.
     */
    public void unfollowShowroom(String username, Long showroomId) {
        Users user = getUser(username);
        Creator creator = getCreator(showroomId);

        if (creatorFollowRepository.existsByUserAndCreator(user, creator)) {
            creatorFollowRepository.deleteByUserAndCreator(user, creator);
        }
    }

    /**
     * 특정 유저가 특정 쇼룸을 팔로우했는지 확인 (조회용)
     */
    @Transactional(readOnly = true)
    public boolean isFollowing(String username, Long showroomId) {
        Users user = getUser(username);
        Creator creator = creatorRepository.getReferenceById(showroomId);
        return creatorFollowRepository.existsByUserAndCreator(user, creator);
    }

    /**
     * 팔로우한 쇼룸 목록 조회.
     * 정렬 기준이 팔로우 시각뿐 아니라 "최근 게시물"까지 걸쳐 있어 전체 목록을 모아 정렬한 뒤 수동 페이징한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<FollowingShowroomResponse> getFollowedShowrooms(
            String username, FollowingShowroomSort sort, PagingRequest pagingRequest) {

        Users user = getUser(username);
        List<CreatorFollow> follows = creatorFollowRepository.findAllByUserWithCreator(user);
        List<CreatorFollow> sortedFollows = sortFollows(follows, sort);

        // 수동 페이징 (정렬이 DB 밖에서 끝나므로 슬라이싱도 여기서 한다)
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), sortedFollows.size());
        List<CreatorFollow> pageContent = start >= sortedFollows.size()
                ? List.of()
                : sortedFollows.subList(start, end);

        Set<Long> ongoingGroupBuyShowroomIds = findOngoingGroupBuyShowroomIds(pageContent);

        List<FollowingShowroomResponse> content = pageContent.stream()
                .map(follow -> toResponse(follow, ongoingGroupBuyShowroomIds))
                .toList();

        return new PageResponse<>(content, new PageImpl<>(content, pageable, sortedFollows.size()));
    }

    private List<CreatorFollow> sortFollows(List<CreatorFollow> follows, FollowingShowroomSort sort) {
        FollowingShowroomSort target = sort != null ? sort : FollowingShowroomSort.DEFAULT;

        return switch (target) {
            case FOLLOW_LATEST -> follows.stream()
                    .sorted(Comparator.comparing(CreatorFollow::getCreatedAt).reversed())
                    .toList();
            case FOLLOW_OLDEST -> follows.stream()
                    .sorted(Comparator.comparing(CreatorFollow::getCreatedAt))
                    .toList();
            case DEFAULT -> sortByLatestPost(follows);
        };
    }

    /** 기본 정렬 — 최근에 게시물을 올린 쇼룸 순, 게시물이 없으면 팔로우 최신순으로 뒤에 붙인다. */
    private List<CreatorFollow> sortByLatestPost(List<CreatorFollow> follows) {
        if (follows.isEmpty()) {
            return List.of();
        }

        List<Long> showroomIds = follows.stream()
                .map(follow -> follow.getCreator().getId())
                .toList();

        Map<Long, LocalDateTime> latestPostMap = new HashMap<>();
        for (Object[] row : postRepository.findLatestPostCreatedAtByCreatorIds(showroomIds)) {
            latestPostMap.put((Long) row[0], (LocalDateTime) row[1]);
        }

        Comparator<CreatorFollow> byLatestPost = Comparator.comparing(
                (CreatorFollow follow) -> latestPostMap.get(follow.getCreator().getId()),
                Comparator.nullsLast(Comparator.reverseOrder()));

        return follows.stream()
                .sorted(byLatestPost.thenComparing(CreatorFollow::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    /** 진행 중 공구 보유 쇼룸 — 연결된 브랜드의 상품 중 공구 상태가 진행중(IN_PROGRESS)인 건이 있는 쇼룸 */
    private Set<Long> findOngoingGroupBuyShowroomIds(List<CreatorFollow> follows) {
        if (follows.isEmpty()) {
            return Set.of();
        }

        List<Long> showroomIds = follows.stream()
                .map(follow -> follow.getCreator().getId())
                .toList();

        return new HashSet<>(connectionRepository.findCreatorIdsWithOngoingGroupBuy(showroomIds));
    }

    private FollowingShowroomResponse toResponse(CreatorFollow follow, Set<Long> ongoingGroupBuyShowroomIds) {
        Creator creator = follow.getCreator();
        Users creatorUser = creator.getUser();

        return FollowingShowroomResponse.builder()
                .showroomId(creator.getId())
                .showroomName(creator.getShowroomName() != null
                        ? creator.getShowroomName()
                        : creatorUser.getNickname())
                // §22-1 — 쇼룸 아바타는 쇼룸 프로필 이미지다. 앱 계정 프로필과는 별개 값이다.
                .showroomImageUrl(creator.getProfileImageUrl())
                .hasOngoingGroupBuy(ongoingGroupBuyShowroomIds.contains(creator.getId()))
                .followedAt(follow.getCreatedAt())
                .build();
    }

    private Users getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Creator getCreator(Long showroomId) {
        return creatorRepository.findById(showroomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}

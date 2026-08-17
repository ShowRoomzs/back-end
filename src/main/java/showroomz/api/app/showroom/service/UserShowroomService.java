package showroomz.api.app.showroom.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.showroom.DTO.ShowroomDetailResponse;
import showroomz.api.app.showroom.DTO.ShowroomListItem;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.PublicShowrooms;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.type.PostStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;

/**
 * 소비자에게 쇼룸을 보여주는 서비스.
 *
 * <p>구 샵 API({@code /v1/user/shops})를 대체한다. 조회 대상이 <b>마켓에서 쇼룸으로</b> 바뀌었다 —
 * 소비자 앱에서 브랜드(마켓)는 더 이상 조회되지 않고, 판매 채널의 얼굴은 쇼룸 하나다. 그래서
 * 조회 뿌리도 {@code Market}이 아니라 {@link Creator}이며, 마켓에만 있던 개념(대표 카테고리,
 * {@code shopType} 판별자, SNS 링크 배열)은 응답에서 사라졌다.
 *
 * <p>노출 조건은 {@link PublicShowrooms#visible()} 하나로 모아 검색·자동완성과 같은 기준을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserShowroomService {

    private final JPAQueryFactory queryFactory;
    private final UserRepository userRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final ConnectionRepository connectionRepository;
    private final PostRepository postRepository;

    /**
     * 쇼룸 목록 — 키워드가 있으면 이름·아이디 부분 일치로 좁힌다.
     *
     * <p>정렬은 구 샵 목록과 같은 <b>신규 등록순</b>이다. 검색 랭킹(왜 걸렸는지 순)은 C14 전용이라
     * {@code GET /v1/user/search/showrooms}가 맡는다 — 두 API가 같은 순서를 흉내 내면 한쪽만
     * 고쳐졌을 때 어긋난다.
     */
    public PageResponse<ShowroomListItem> getShowrooms(String username, String keyword, PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        BooleanExpression where = PublicShowrooms.visible().and(matches(keyword));

        List<Creator> content = queryFactory
                .selectFrom(creator)
                .join(creator.user, users)
                .where(where)
                .orderBy(creator.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(creator.count())
                .from(creator)
                .join(creator.user, users)
                .where(where)
                .fetchOne();

        List<Long> showroomIds = content.stream().map(Creator::getId).toList();
        Set<Long> ongoing = ongoingGroupBuyIds(showroomIds);
        Set<Long> followed = followedIds(username, showroomIds);

        List<ShowroomListItem> items = content.stream()
                .map(found -> ShowroomListItem.builder()
                        .showroomId(found.getId())
                        .showroomName(showroomName(found))
                        .showroomAddress(found.getShowroomAddress())
                        // §22-1 — 쇼룸 아바타는 쇼룸 프로필 이미지다. 앱 계정 프로필과는 별개 값이다.
                        .showroomImageUrl(found.getProfileImageUrl())
                        .introduction(found.getIntroduction())
                        .hasOngoingGroupBuy(ongoing.contains(found.getId()))
                        .isFollowing(followed.contains(found.getId()))
                        .build())
                .toList();

        return new PageResponse<>(items, new PageImpl<>(items, pageable, total == null ? 0 : total));
    }

    /**
     * C4 쇼룸 프로필.
     *
     * <p>노출할 수 없는 쇼룸(탈퇴·정지·등록 미완료)은 <b>없는 쇼룸</b>과 같이 404다 — 상태를 구분해
     * 알려주면 조치 사실이 드러난다. 비로그인도 열 수 있고, 이때 {@code isFollowing}은 false다.
     */
    public ShowroomDetailResponse getShowroom(String username, Long showroomId) {
        Creator found = queryFactory
                .selectFrom(creator)
                .join(creator.user, users)
                .where(PublicShowrooms.visible().and(creator.id.eq(showroomId)))
                .fetchOne();

        if (found == null) {
            throw new BusinessException(ErrorCode.SHOWROOM_NOT_FOUND);
        }

        List<Long> showroomIds = List.of(found.getId());
        return ShowroomDetailResponse.builder()
                .showroomId(found.getId())
                .showroomName(showroomName(found))
                .showroomAddress(found.getShowroomAddress())
                .showroomImageUrl(found.getProfileImageUrl())
                .introduction(found.getIntroduction())
                .instagramUrl(found.getInstagramUrl())
                .postCount(postRepository.countByCreator_IdAndStatus(found.getId(), PostStatus.PUBLISHED))
                .followerCount(creatorFollowRepository.countByCreator_Id(found.getId()))
                .hasOngoingGroupBuy(ongoingGroupBuyIds(showroomIds).contains(found.getId()))
                .isFollowing(followedIds(username, showroomIds).contains(found.getId()))
                .build();
    }

    // ------------------------------------------------------------------ 내부

    /** 검색어는 이름과 아이디에만 걸린다 — 소개글까지 걸리면 결과가 설명 없이 늘어난다(C14와 같은 규칙). */
    private BooleanExpression matches(String keyword) {
        String name = keyword == null ? "" : keyword.trim();
        if (name.isEmpty()) {
            return null;
        }

        // 사용자는 화면에 보이는 대로 "@handle"을 붙여 넣는다 — 앞의 @는 떼고 맞춘다.
        String handle = name.startsWith("@") ? name.substring(1) : name;
        return handle.isEmpty()
                ? creator.showroomName.containsIgnoreCase(name)
                : creator.showroomName.containsIgnoreCase(name)
                        .or(creator.showroomAddress.containsIgnoreCase(handle));
    }

    private Set<Long> ongoingGroupBuyIds(List<Long> showroomIds) {
        if (showroomIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(connectionRepository.findCreatorIdsWithOngoingGroupBuy(showroomIds));
    }

    /** 페이지에 실린 쇼룸만 대조한다 — 팔로잉이 많은 사용자에게 전체 목록을 읽게 하지 않는다. */
    private Set<Long> followedIds(String username, List<Long> showroomIds) {
        if (username == null || showroomIds.isEmpty()) {
            return Set.of();
        }
        Users user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Set.of();
        }
        return Set.copyOf(creatorFollowRepository.findFollowedCreatorIds(user.getId(), showroomIds));
    }

    /**
     * §22-1 — 소비자에게 보이는 이름은 <b>쇼룸명</b>이다. 노출 조건이 쇼룸명을 요구하므로 실제로는
     * 언제나 쇼룸명이 있지만, 계약상 nullable인 값이라 방어적으로 닉네임으로 떨어뜨린다.
     */
    private static String showroomName(Creator creator) {
        return creator.getShowroomName() != null
                ? creator.getShowroomName()
                : creator.getUser().getNickname();
    }
}

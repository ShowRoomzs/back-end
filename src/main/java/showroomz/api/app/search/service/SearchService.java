package showroomz.api.app.search.service;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.search.dto.AutoCompleteResponse;
import showroomz.api.app.search.dto.ShowroomSearchItem;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;

import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static showroomz.domain.market.entity.QMarket.market;
import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;
import static showroomz.domain.product.entity.QProduct.product;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    /** 결과 없음 화면의 "이런 쇼룸은 어떠세요" 목록 크기 — 탐색 시스템이 아니라 마중물이라 짧게 둔다. */
    private static final int DEFAULT_ACTIVE_SHOWROOM_SIZE = 10;

    private final JPAQueryFactory queryFactory;
    private final PostRepository postRepository;
    private final ConnectionRepository connectionRepository;

    /**
     * C14 쇼룸 검색 — 검색 대상은 <b>쇼룸 이름과 아이디(@handle)</b>뿐이다(상품·카테고리는 범위 밖).
     *
     * <p>둘 다 부분 일치로 걸리며, 아이디는 쇼룸 고유값이라 같은 이름의 쇼룸을 구별하는 정확한 키다.
     * 사용자가 "@brai"처럼 @를 붙여 입력해도 핸들에 걸리도록 앞의 @는 떼고 맞춘다.
     *
     * <p>정렬은 "왜 걸렸는지"가 위로 오도록 이름 앞부분 일치 → 이름 부분 일치 → 아이디 앞부분 일치 →
     * 아이디 부분 일치 순이고, 같은 등급 안에서는 이름이 짧은 순이다.
     */
    public PageResponse<ShowroomSearchItem> searchShowrooms(String keyword, PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());

        String name = keyword == null ? "" : keyword.trim();
        String handle = name.startsWith("@") ? name.substring(1) : name;

        if (name.isEmpty()) {
            return emptyPage(pageable);
        }

        BooleanExpression match = handle.isEmpty()
                ? creator.showroomName.containsIgnoreCase(name)
                : creator.showroomName.containsIgnoreCase(name)
                        .or(creator.showroomAddress.containsIgnoreCase(handle));

        BooleanExpression where = publicShowroom().and(match);

        NumberExpression<Integer> matchRank = new CaseBuilder()
                .when(creator.showroomName.startsWithIgnoreCase(name)).then(0)
                .when(creator.showroomName.containsIgnoreCase(name)).then(1)
                .when(creator.showroomAddress.startsWithIgnoreCase(handle)).then(2)
                .otherwise(3);

        List<Creator> content = queryFactory
                .selectFrom(creator)
                .join(creator.user, users)
                .where(where)
                .orderBy(matchRank.asc(), creator.showroomName.length().asc(), creator.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(creator.count())
                .from(creator)
                .join(creator.user, users)
                .where(where)
                .fetchOne();

        List<ShowroomSearchItem> items = toItems(content);
        return new PageResponse<>(items, new PageImpl<>(items, pageable, total == null ? 0 : total));
    }

    /**
     * 활동 중인 쇼룸 — 검색 결과가 없을 때 다음 행동을 만들어 주는 목록이다.
     *
     * <p>랭킹을 표기하지 않으므로 점수 체계를 세우지 않고, "최근에 게시물을 올린 쇼룸 순"이라는
     * C2 팔로잉 기본 정렬과 같은 기준을 쓴다. 게시물이 아직 없는 쇼룸은 신규 등록순으로 뒤를 채운다.
     */
    public List<ShowroomSearchItem> getActiveShowrooms(Integer size) {
        int limit = (size == null || size <= 0) ? DEFAULT_ACTIVE_SHOWROOM_SIZE : size;

        // 게시물이 있는 쇼룸이라도 탈퇴·정지 등으로 걸러질 수 있어 넉넉히 뽑아 두고 자른다.
        List<Long> postedCreatorIds = postRepository.findCreatorIdsOrderByLatestPost(
                PageRequest.of(0, limit * 2));

        List<Creator> picked = new ArrayList<>(orderByGivenIds(postedCreatorIds, limit));

        if (picked.size() < limit) {
            Set<Long> excluded = picked.stream().map(Creator::getId).collect(Collectors.toSet());
            picked.addAll(queryFactory
                    .selectFrom(creator)
                    .join(creator.user, users)
                    .where(publicShowroom(), excluded.isEmpty() ? null : creator.id.notIn(excluded))
                    .orderBy(creator.id.desc())
                    .limit(limit - picked.size())
                    .fetch());
        }

        return toItems(picked);
    }

    /**
     * 검색어 자동완성
     * - 상품: 이름 포함, 전시 중, 이름 짧은 순 5개
     * - 마켓: 이름 포함, 마켓 타입, 승인된(APPROVED) 판매자만, 이름 짧은 순 3개
     * - 쇼룸: 쇼룸명 또는 아이디(@handle) 포함, 공개 중인 쇼룸만, 이름 짧은 순 3개
     */
    public AutoCompleteResponse getAutocomplete(String keyword) {

        // 1. 상품 검색
        List<AutoCompleteResponse.SearchDto> products = queryFactory
                .select(Projections.constructor(AutoCompleteResponse.SearchDto.class,
                        product.productId,
                        product.name
                ))
                .from(product)
                .where(product.name.contains(keyword)
                        .and(product.displayStatus.eq(ProductDisplayStatus.DISPLAY)))
                .orderBy(product.name.length().asc())
                .limit(5)
                .fetch();

        // 2. 마켓 검색 (승인된 판매자만)
        List<AutoCompleteResponse.SearchDto> markets = queryFactory
                .select(Projections.constructor(AutoCompleteResponse.SearchDto.class,
                        market.id,
                        market.marketName
                ))
                .from(market)
                .where(market.marketName.contains(keyword)
                        .and(market.seller.roleType.eq(RoleType.SELLER))
                        .and(market.seller.status.eq(SellerStatus.APPROVED)))
                .orderBy(market.marketName.length().asc())
                .limit(3)
                .fetch();

        // 3. 쇼룸 검색 — 쇼룸은 마켓이 아니라 크리에이터다. 이름과 아이디(@handle) 모두에 걸린다.
        String handle = keyword.startsWith("@") ? keyword.substring(1) : keyword;
        BooleanExpression showroomMatch = handle.isEmpty()
                ? creator.showroomName.containsIgnoreCase(keyword)
                : creator.showroomName.containsIgnoreCase(keyword)
                        .or(creator.showroomAddress.containsIgnoreCase(handle));

        List<AutoCompleteResponse.SearchDto> showrooms = queryFactory
                .select(Projections.constructor(AutoCompleteResponse.SearchDto.class,
                        creator.id,
                        creator.showroomName
                ))
                .from(creator)
                .join(creator.user, users)
                .where(publicShowroom().and(showroomMatch))
                .orderBy(creator.showroomName.length().asc())
                .limit(3)
                .fetch();

        return AutoCompleteResponse.builder()
                .products(products)
                .markets(markets)
                .showrooms(showrooms)
                .build();
    }

    /**
     * 검색·추천에 노출할 수 있는 쇼룸 — 등록을 마쳐 쇼룸명과 아이디가 확정됐고(둘 다 등록 완료 시점에 정해진다),
     * 계정이 정상인 크리에이터다.
     */
    private BooleanExpression publicShowroom() {
        return creator.showroomName.isNotNull()
                .and(creator.showroomName.ne(""))
                .and(creator.showroomAddress.isNotNull())
                .and(users.status.eq(UserStatus.NORMAL))
                .and(users.roleType.eq(RoleType.CREATOR));
    }

    /** 주어진 ID 순서를 유지한 채 공개 가능한 쇼룸만 남긴다. */
    private List<Creator> orderByGivenIds(List<Long> creatorIds, int limit) {
        if (creatorIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Creator> found = queryFactory
                .selectFrom(creator)
                .join(creator.user, users)
                .where(publicShowroom().and(creator.id.in(creatorIds)))
                .fetch()
                .stream()
                .collect(Collectors.toMap(Creator::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        return creatorIds.stream()
                .map(found::get)
                .filter(java.util.Objects::nonNull)
                .limit(limit)
                .toList();
    }

    private List<ShowroomSearchItem> toItems(List<Creator> creators) {
        if (creators.isEmpty()) {
            return List.of();
        }

        Set<Long> ongoing = new HashSet<>(connectionRepository.findCreatorIdsWithOngoingGroupBuy(
                creators.stream().map(Creator::getId).toList()));

        return creators.stream()
                .map(c -> ShowroomSearchItem.builder()
                        .showroomId(c.getId())
                        .showroomName(c.getShowroomName())
                        .showroomAddress(c.getShowroomAddress())
                        // §22-1 — 쇼룸 아바타는 쇼룸 프로필 이미지다. 앱 계정 프로필과는 별개 값이다.
                        .showroomImageUrl(c.getProfileImageUrl())
                        .hasOngoingGroupBuy(ongoing.contains(c.getId()))
                        .build())
                .toList();
    }

    private PageResponse<ShowroomSearchItem> emptyPage(Pageable pageable) {
        List<ShowroomSearchItem> empty = List.of();
        return new PageResponse<>(empty, new PageImpl<>(empty, pageable, 0));
    }
}

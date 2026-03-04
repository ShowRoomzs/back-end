package showroomz.api.app.search.service;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.search.dto.AutoCompleteResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;

import java.util.List;

import static showroomz.domain.market.entity.QMarket.market;
import static showroomz.domain.product.entity.QProduct.product;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final JPAQueryFactory queryFactory;

    /**
     * 검색어 자동완성
     * - 상품: 이름 포함, 전시 중, 이름 짧은 순 5개
     * - 마켓: 이름 포함, 마켓 타입, 승인된(APPROVED) 판매자만, 이름 짧은 순 3개
     * - 쇼룸: 이름 포함, 쇼룸 타입, 승인된(APPROVED) 판매자만, 이름 짧은 순 3개
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
                        .and(product.isDisplay.isTrue()))
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

        // 3. 쇼룸 검색 (승인된 판매자만)
        List<AutoCompleteResponse.SearchDto> showrooms = queryFactory
                .select(Projections.constructor(AutoCompleteResponse.SearchDto.class,
                        market.id,
                        market.marketName
                ))
                .from(market)
                .where(market.marketName.contains(keyword)
                        .and(market.seller.roleType.eq(RoleType.CREATOR))
                        .and(market.seller.status.eq(SellerStatus.APPROVED)))
                .orderBy(market.marketName.length().asc())
                .limit(3)
                .fetch();

        return AutoCompleteResponse.builder()
                .products(products)
                .markets(markets)
                .showrooms(showrooms)
                .build();
    }
}

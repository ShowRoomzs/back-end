package showroomz.api.app.product.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import showroomz.api.app.product.DTO.ProductDetailViewRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyPriceResolver;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.service.PostAttributionService;
import showroomz.domain.product.entity.ProductDetailView;
import showroomz.domain.product.repository.ProductDetailViewRepository;
import showroomz.domain.product.type.ProductDetailViewSource;
import showroomz.global.error.exception.BusinessException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 상품 상세 조회 적재(49 스튜디오 성과 설계서 1-4) — 30분 세션 · 공구 판정(상세와 같은 규칙) · 라스트 터치 귀속 · 비로그인 식별자.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("상품 상세 조회 적재")
class ProductDetailViewServiceTest {

    private static final long PRODUCT = 101L;

    @Mock
    private ProductDetailViewRepository viewRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupBuyPriceResolver priceResolver;
    @Mock
    private PostAttributionService postAttributionService;

    @InjectMocks
    private ProductDetailViewService service;

    private static ProductDetailViewRequest request(String source, Long groupBuyId, String visitorId) {
        ProductDetailViewRequest request = new ProductDetailViewRequest();
        request.setSource(source);
        request.setGroupBuyId(groupBuyId);
        request.setVisitorId(visitorId);
        return request;
    }

    private GroupBuy sellingGroupBuy(long groupBuyId, long creatorId) {
        Creator creator = Creator.builder().id(creatorId).build();
        GroupBuy groupBuy = mock(GroupBuy.class);
        given(groupBuy.getId()).willReturn(groupBuyId);
        given(groupBuy.getCreator()).willReturn(creator);
        given(priceResolver.resolveProductOffer(eq(groupBuyId), eq(PRODUCT), any()))
                .willReturn(Optional.of(new GroupBuyPriceResolver.ProductOffer(groupBuy, Map.of())));
        return groupBuy;
    }

    @Test
    @DisplayName("판매 중 공구가 있으면 공구 · 인플루언서 · 라스트 터치 게시물을 붙여 적재한다")
    void recordsWithGroupBuyAndAttribution() {
        sellingGroupBuy(12L, 7L);
        given(postAttributionService.resolveAttributedPostId(eq("d:device-1"), eq(7L), any())).willReturn(301L);

        service.recordView(null, PRODUCT, request("post", 12L, "device-1"));

        ArgumentCaptor<ProductDetailView> captor = ArgumentCaptor.forClass(ProductDetailView.class);
        verify(viewRepository).save(captor.capture());
        ProductDetailView view = captor.getValue();
        assertThat(view.getGroupBuyId()).isEqualTo(12L);
        assertThat(view.getCreatorId()).isEqualTo(7L);
        assertThat(view.getAttributedPostId()).isEqualTo(301L);
        assertThat(view.getSource()).isEqualTo(ProductDetailViewSource.POST);
        assertThat(view.getViewerKey()).isEqualTo("d:device-1");
        assertThat(view.getUserId()).isNull();
    }

    @Test
    @DisplayName("공구 밖 조회도 남긴다(공구 · 귀속 null · 모르는 소스는 OTHER) — 귀속은 부르지 않는다")
    void recordsOutsideGroupBuy() {
        given(priceResolver.resolveProductOffer(any(), eq(PRODUCT), any())).willReturn(Optional.empty());

        service.recordView(null, PRODUCT, request("banner", null, "device-1"));

        ArgumentCaptor<ProductDetailView> captor = ArgumentCaptor.forClass(ProductDetailView.class);
        verify(viewRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupBuyId()).isNull();
        assertThat(captor.getValue().getAttributedPostId()).isNull();
        assertThat(captor.getValue().getSource()).isEqualTo(ProductDetailViewSource.OTHER);
        verify(postAttributionService, never()).resolveAttributedPostId(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("30분 안 재조회는 적재하지 않는다")
    void skipsWithinSession() {
        given(viewRepository.existsByProductIdAndViewerKeyAndViewedAtAfter(eq(PRODUCT), eq("d:device-1"), any()))
                .willReturn(true);

        service.recordView(null, PRODUCT, request(null, null, "device-1"));

        verify(viewRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그인 조회는 사용자 키 · 비로그인인데 visitorId 가 없으면 400")
    void viewerKey() {
        showroomz.domain.member.user.entity.Users user = mock(showroomz.domain.member.user.entity.Users.class);
        given(user.getId()).willReturn(5L);
        given(userRepository.findByUsername("mia")).willReturn(Optional.of(user));
        given(priceResolver.resolveProductOffer(any(), eq(PRODUCT), any())).willReturn(Optional.empty());

        service.recordView("mia", PRODUCT, null);

        ArgumentCaptor<ProductDetailView> captor = ArgumentCaptor.forClass(ProductDetailView.class);
        verify(viewRepository).save(captor.capture());
        assertThat(captor.getValue().getViewerKey()).isEqualTo("u:5");
        assertThat(captor.getValue().getUserId()).isEqualTo(5L);

        assertThatThrownBy(() -> service.recordView(null, PRODUCT, request(null, null, null)))
                .isInstanceOf(BusinessException.class);
    }
}

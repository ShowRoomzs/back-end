package showroomz.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.post.service.PostAttributionService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 결제 완료 하위주문의 게시물 귀속(49 스튜디오 성과 설계서 1-5) — 하위주문마다 <b>그 공구 인플루언서의</b> 라스트 터치를 붙인다.
 * 멀티브랜드 결제에서 공구가 갈리므로 주문이 아니라 하위주문 단위다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("결제 완료 게시물 귀속")
class OrderPostAttributionServiceTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 8, 3, 14, 0);

    @Mock
    private OrderDeliveryGroupRepository deliveryGroupRepository;
    @Mock
    private PostAttributionService postAttributionService;

    @InjectMocks
    private OrderPostAttributionService service;

    @Test
    @DisplayName("하위주문마다 그 공구 인플루언서 기준으로 귀속 — 귀속 대상이 없으면 그 하위주문은 건너뛴다")
    void attributesPerDeliveryGroup() {
        given(deliveryGroupRepository.findCreatorIdsByOrderId(9L)).willReturn(List.of(
                new Object[]{100L, 7L}, new Object[]{101L, 8L}));
        given(postAttributionService.resolveAttributedPostId("u:5", 7L, PAID_AT)).willReturn(301L);
        given(postAttributionService.resolveAttributedPostId("u:5", 8L, PAID_AT)).willReturn(null);

        service.attributeOnPaid(9L, 5L, PAID_AT);

        verify(deliveryGroupRepository).attributePost(100L, 301L);
        verify(deliveryGroupRepository, never()).attributePost(eq(101L), anyLong());
    }

    @Test
    @DisplayName("사용자가 없으면 아무것도 하지 않는다")
    void noUser() {
        service.attributeOnPaid(9L, null, PAID_AT);

        verifyNoInteractions(deliveryGroupRepository, postAttributionService);
    }
}

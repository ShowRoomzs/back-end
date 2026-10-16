package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.post.service.PostAttributionService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 결제 완료 하위주문의 게시물 귀속(49 스튜디오 성과 설계서 1-5) — 성과 「게시물 → 주문」의 원천.
 *
 * <p>하위주문마다 <b>그 공구 인플루언서의</b> 게시물 중 결제 직전 24시간 안에 마지막으로 본 것을 붙인다(§24-7 라스트 터치 · 쇼룸 방문 ·
 * 팔로우와 같은 규칙). 멀티브랜드 결제는 하위주문마다 인플루언서가 다를 수 있어 주문이 아니라 하위주문 단위다.
 *
 * <p>호출자의 결제 확정 트랜잭션 안에서 돈다. 값을 먼저 다 계산한 뒤 UPDATE 한다 — 조건부 UPDATE 뒤에는 지연 로딩이 막힐 수 있다.
 */
@Service
@RequiredArgsConstructor
public class OrderPostAttributionService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final PostAttributionService postAttributionService;

    @Transactional
    public void attributeOnPaid(Long orderId, Long userId, LocalDateTime paidAt) {
        if (orderId == null || userId == null) {
            return;
        }
        String viewerKey = PostAttributionService.viewerKeyOf(userId, null);
        List<long[]> plan = new ArrayList<>();
        for (Object[] row : deliveryGroupRepository.findCreatorIdsByOrderId(orderId)) {
            if (!(row[0] instanceof Long groupId) || !(row[1] instanceof Long creatorId)) {
                continue;
            }
            Long postId = postAttributionService.resolveAttributedPostId(viewerKey, creatorId, paidAt);
            if (postId != null) {
                plan.add(new long[]{groupId, postId});
            }
        }
        for (long[] entry : plan) {
            deliveryGroupRepository.attributePost(entry[0], entry[1]);
        }
    }
}

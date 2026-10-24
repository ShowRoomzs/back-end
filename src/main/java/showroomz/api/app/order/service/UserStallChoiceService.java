package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.service.OrderStallChoiceService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 소비자 추적 정지 선택(C10-2 1d · 1021 결정 노트 기-15 【잠정】) — 배송 조회에서 들어온다. 선택은 한 번이고, 처리는 브랜드가 한다.
 */
@Service
@RequiredArgsConstructor
public class UserStallChoiceService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderStallChoiceService stallChoiceService;

    @Transactional(readOnly = true)
    public UserOrderDto.StallChoiceResponse get(Long userId, Long deliveryGroupId) {
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId)
                .filter(found -> found.getOrder().getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        return UserOrderDto.StallChoiceResponse.of(group.getId(), OrderStallChoiceService.view(group, now));
    }

    @Transactional
    public UserOrderDto.StallChoiceResponse choose(Long userId, Long deliveryGroupId,
                                                   UserOrderDto.StallChoiceRequest request) {
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup group = stallChoiceService.choose(userId, deliveryGroupId, request.getChoice(), now);
        return UserOrderDto.StallChoiceResponse.of(group.getId(), OrderStallChoiceService.view(group, now));
    }
}

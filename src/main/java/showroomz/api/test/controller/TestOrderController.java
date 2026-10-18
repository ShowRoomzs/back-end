package showroomz.api.test.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.test.docs.TestOrderControllerDocs;
import showroomz.api.test.dto.TestOrderDto;
import showroomz.api.test.service.TestOrderService;

/**
 * 테스트 전용: 주문 상태 강제 전이. 인증 불필요.
 */
@RestController
@RequestMapping("/test/orders")
@RequiredArgsConstructor
public class TestOrderController implements TestOrderControllerDocs {

    private final TestOrderService testOrderService;

    @Override
    @PostMapping("/{orderNumber}/delivered")
    public ResponseEntity<TestOrderDto.DeliveredResponse> markDelivered(
            @PathVariable("orderNumber") String orderNumber) {
        return ResponseEntity.ok(testOrderService.markDelivered(orderNumber));
    }
}

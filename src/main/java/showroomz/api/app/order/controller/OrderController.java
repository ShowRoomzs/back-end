package showroomz.api.app.order.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.order.docs.OrderControllerDocs;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.api.app.order.service.CheckoutService;
import showroomz.api.app.order.service.OrderCommandService;
import showroomz.api.app.order.service.OrderCommandService.CancelResult;
import showroomz.api.app.order.service.UserDeliveryTrackingService;
import showroomz.api.app.order.service.UserOrderQueryService;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/user/orders")
@RequiredArgsConstructor
@Tag(name = "User - Order", description = "주문서 · 주문 생성 · 주문 내역 · 주문 상세 · 취소 (C9 · C10)")
public class OrderController implements OrderControllerDocs {

    private final CheckoutService checkoutService;
    private final OrderCommandService orderCommandService;
    private final UserOrderQueryService userOrderQueryService;
    private final UserDeliveryTrackingService userDeliveryTrackingService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<UserOrderDto.OrderCard>> getOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(userOrderQueryService.getOrders(principal.getUserId(), page, size));
    }

    @Override
    @PostMapping("/checkout")
    public ResponseEntity<OrderDto.CheckoutResponse> checkout(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Valid @RequestBody OrderDto.CheckoutRequest request) {
        return ResponseEntity.ok(checkoutService.checkout(principal.getUserId(), request));
    }

    @Override
    @PostMapping
    public ResponseEntity<OrderDto.CreateOrderResponse> createOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @Valid @RequestBody OrderDto.CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderCommandService.createOrder(principal.getUserId(), request));
    }

    @Override
    @PostMapping("/{orderId}/payments")
    public ResponseEntity<OrderDto.CreateOrderResponse> retryPayment(@AuthenticationPrincipal UserPrincipal principal,
                                                                     @PathVariable("orderId") Long orderId,
                                                                     @Valid @RequestBody OrderDto.RetryPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderCommandService.retryPayment(principal.getUserId(), orderId, request.getPayment()));
    }

    @Override
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto.OrderDetailResponse> getOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(checkoutService.getOrder(principal.getUserId(), orderId));
    }

    @Override
    @GetMapping("/{orderId}/items/{orderProductId}/tracking")
    public ResponseEntity<UserOrderDto.TrackingResponse> getItemTracking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId,
            @PathVariable("orderProductId") Long orderProductId) {
        return ResponseEntity.ok(
                userDeliveryTrackingService.trackOrderItem(principal.getUserId(), orderId, orderProductId));
    }

    @Override
    @PatchMapping("/{orderId}/delivery-address")
    public ResponseEntity<OrderDto.ChangeAddressResponse> changeDeliveryAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody OrderDto.ChangeAddressRequest request) {
        return ResponseEntity.ok(
                orderCommandService.changeDeliveryAddress(principal.getUserId(), orderId, request.getAddressId()));
    }

    @Override
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDto.CancelResponse> cancelOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable("orderId") Long orderId,
                                                               @Valid @RequestBody(required = false) OrderDto.CancelRequest request) {
        String reason = request != null ? request.getReason() : null;
        CancelResult result = orderCommandService.cancelOrder(principal.getUserId(), orderId, reason);
        OrderDto.CancelResponse body = OrderDto.CancelResponse.builder()
                .orderId(orderId)
                .status(result.orderStatus())
                .paymentStatus(result.paymentStatus())
                .message(result.pending() ? "취소 처리 중입니다. 잠시 후 주문 상태를 확인해 주세요." : "주문이 취소되었습니다.")
                .build();
        return ResponseEntity.status(result.pending() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(body);
    }
}

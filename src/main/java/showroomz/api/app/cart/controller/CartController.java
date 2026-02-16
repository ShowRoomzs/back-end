package showroomz.api.app.cart.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.entity.UserPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.cart.dto.CartDto;
import showroomz.api.app.cart.service.CartService;
import showroomz.api.app.docs.CartControllerDocs;

import java.util.List;

@RestController
@RequestMapping("/v1/user/cart")
@RequiredArgsConstructor
@Tag(name = "User - Cart", description = "장바구니 관리 API")
public class CartController implements CartControllerDocs {

    private final CartService cartService;

    @Override
    @PostMapping
    public ResponseEntity<CartDto.BulkAddCartResponse> addCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody List<CartDto.AddCartRequest> requests
    ) {
        CartDto.BulkAddCartResponse response = cartService.addCartBulk(userPrincipal.getUsername(), requests);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<CartDto.CartListResponse> getCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        CartDto.CartListResponse response = cartService.getCart(userPrincipal.getUsername());
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{cartItemId}")
    public ResponseEntity<CartDto.UpdateCartResponse> updateCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long cartItemId,
            @RequestBody CartDto.UpdateCartRequest request
    ) {
        CartDto.UpdateCartResponse response = cartService.updateCart(userPrincipal.getUsername(), cartItemId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping
    public ResponseEntity<CartDto.DeleteCartResponse> deleteCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "cartItemIds", required = false) List<Long> cartItemIds
    ) {
        CartDto.DeleteCartResponse response = cartService.deleteCart(userPrincipal.getUsername(), cartItemIds);
        return ResponseEntity.ok(response);
    }
}

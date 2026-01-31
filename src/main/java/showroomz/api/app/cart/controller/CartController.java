package showroomz.api.app.cart.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import showroomz.api.app.cart.dto.CartDto;
import showroomz.api.app.cart.service.CartService;
import showroomz.api.app.docs.CartControllerDocs;

@RestController
@RequestMapping("/v1/user/cart")
@RequiredArgsConstructor
public class CartController implements CartControllerDocs {

    private final CartService cartService;

    @Override
    @PostMapping
    public ResponseEntity<CartDto.AddCartResponse> addCart(
            @AuthenticationPrincipal User principal,
            @Valid @RequestBody CartDto.AddCartRequest request
    ) {
        CartDto.AddCartResponse response = cartService.addCart(principal.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<CartDto.CartListResponse> getCart(
            @AuthenticationPrincipal User principal,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        CartDto.CartListResponse response = cartService.getCart(principal.getUsername(), pageable);
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{cartItemId}")
    public ResponseEntity<CartDto.UpdateCartResponse> updateCart(
            @AuthenticationPrincipal User principal,
            @PathVariable Long cartItemId,
            @RequestBody CartDto.UpdateCartRequest request
    ) {
        CartDto.UpdateCartResponse response = cartService.updateCart(principal.getUsername(), cartItemId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<CartDto.DeleteCartResponse> deleteCart(
            @AuthenticationPrincipal User principal,
            @PathVariable Long cartItemId
    ) {
        CartDto.DeleteCartResponse response = cartService.deleteCart(principal.getUsername(), cartItemId);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping
    public ResponseEntity<CartDto.ClearCartResponse> clearCart(
            @AuthenticationPrincipal User principal
    ) {
        CartDto.ClearCartResponse response = cartService.clearCart(principal.getUsername());
        return ResponseEntity.ok(response);
    }
}

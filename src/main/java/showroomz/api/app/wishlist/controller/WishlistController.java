package showroomz.api.app.wishlist.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.entity.UserPrincipal;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.wishlist.docs.WishlistControllerDocs;
import showroomz.api.app.wishlist.service.WishlistService;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/user/wishlist")
@RequiredArgsConstructor
public class WishlistController implements WishlistControllerDocs {

    private final WishlistService wishlistService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<ProductDto.ProductItem>> getWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {

        PageResponse<ProductDto.ProductItem> response = wishlistService.getWishlist(
                userPrincipal.getUsername(), page, limit, categoryId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{productId}")
    public ResponseEntity<Void> addWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("productId") Long productId) {

        wishlistService.addWishlist(userPrincipal.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteWishlist(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("productId") Long productId) {

        wishlistService.deleteWishlist(userPrincipal.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }
}

package showroomz.api.app.wishlist.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.docs.WishlistControllerDocs;
import showroomz.api.app.wishlist.service.WishlistService;

@RestController
@RequestMapping("/v1/user/wishlist")
@RequiredArgsConstructor
public class WishlistController implements WishlistControllerDocs {

    private final WishlistService wishlistService;

    @Override
    @PostMapping("/{productId}")
    public ResponseEntity<Void> addWishlist(
            @AuthenticationPrincipal User principal,
            @PathVariable("productId") Long productId) {

        wishlistService.addWishlist(principal.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteWishlist(
            @AuthenticationPrincipal User principal,
            @PathVariable("productId") Long productId) {

        wishlistService.deleteWishlist(principal.getUsername(), productId);
        return ResponseEntity.noContent().build();
    }
}

package showroomz.api.app.wishlist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.wishlist.entitiy.Wishlist;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * 위시리스트 추가 (멱등성 보장)
     * 이미 존재하면 저장하지 않고 성공(void) 리턴
     */
    @Transactional
    public void addWishlist(String username, Long productId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 이미 위시리스트에 존재하면 아무것도 하지 않고 종료 (200 OK)
        if (wishlistRepository.existsByUserAndProduct(user, product)) {
            return;
        }

        Wishlist wishlist = new Wishlist(user, product);
        wishlistRepository.save(wishlist);
    }

    /**
     * 위시리스트 삭제 (멱등성 보장)
     * 존재하지 않으면 아무것도 하지 않고 성공(void) 리턴
     */
    @Transactional
    public void deleteWishlist(String username, Long productId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 위시리스트에 존재할 때만 삭제 수행
        wishlistRepository.findByUserAndProduct(user, product)
                .ifPresent(wishlistRepository::delete);
    }

    /**
     * 위시리스트 여부 확인
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return 위시리스트에 존재하면 true, 아니면 false
     */
    public boolean isWished(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return false;
        }

        Users user = userRepository.findById(userId).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        if (user == null || product == null) {
            return false;
        }
        return wishlistRepository.existsByUserAndProduct(user, product);
    }
}

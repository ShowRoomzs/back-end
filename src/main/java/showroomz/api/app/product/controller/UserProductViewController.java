package showroomz.api.app.product.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.product.DTO.ProductDetailViewRequest;
import showroomz.api.app.product.docs.UserProductViewControllerDocs;
import showroomz.api.app.product.service.ProductDetailViewService;

/**
 * 상품 상세 조회 적재(49 스튜디오 성과 설계서 1-4). 상세 조회(GET)에 부수 효과로 붙이지 않고 따로 둔다 — 게시물 노출 · 쇼룸 방문과
 * 같은 꼴이고, GET 은 캐시 · 재시도로 몇 번 불릴지 앱이 통제할 수 없다.
 */
@RestController
@RequestMapping("/v1/user/products")
@RequiredArgsConstructor
public class UserProductViewController implements UserProductViewControllerDocs {

    private final ProductDetailViewService productDetailViewService;

    @Override
    @PostMapping("/{productId}/views")
    public ResponseEntity<Void> recordView(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                           @PathVariable Long productId,
                                           @RequestBody(required = false) ProductDetailViewRequest request) {
        String username = userPrincipal == null ? null : userPrincipal.getUsername();
        productDetailViewService.recordView(username, productId, request);
        return ResponseEntity.noContent().build();
    }
}

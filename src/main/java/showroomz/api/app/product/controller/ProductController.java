package showroomz.api.app.product.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.app.docs.UserProductControllerDocs;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.product.service.ProductService;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.ErrorCode;

@RestController("appProductController")
@RequestMapping("/v1/common/products")
@RequiredArgsConstructor
public class ProductController implements UserProductControllerDocs {

    private final ProductService productService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<ProductDto.ProductItem>> searchProducts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long marketId,
            @RequestParam(required = false) String filters,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        java.util.List<ProductDto.FilterRequest> filterRequests = null;
        if (filters != null && !filters.isBlank()) {
            try {
                filterRequests = objectMapper.readValue(filters, new TypeReference<>() {});
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        // Request DTO 생성
        ProductDto.ProductSearchRequest request = ProductDto.ProductSearchRequest.builder()
                .q(q)
                .categoryId(categoryId)
                .marketId(marketId)
                .filters(filterRequests)
                .build();

        Users currentUser = resolveCurrentUser();

        PageResponse<ProductDto.ProductItem> response = productService.searchProducts(
                request,
                page,
                limit,
                currentUser
        );

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDto.ProductDetailResponse> getProductDetail(
            @PathVariable Long productId
    ) {
        ProductDto.ProductDetailResponse response = productService.getProductDetail(productId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{productId}/related")
    public ResponseEntity<ProductDto.ProductSearchResponse> getRelatedProducts(
            @PathVariable Long productId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        Users currentUser = resolveCurrentUser();
        ProductDto.ProductSearchResponse response = productService.getRelatedProducts(
                productId,
                page,
                limit,
                currentUser
        );
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<ProductDto.ProductVariantStockResponse> getVariantStock(
            @PathVariable Long productId,
            @PathVariable Long variantId
    ) {
        ProductDto.ProductVariantStockResponse response = productService.getVariantStock(productId, variantId);
        return ResponseEntity.ok(response);
    }

    private Users resolveCurrentUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof User userPrincipal) {
                return userRepository.findByUsername(userPrincipal.getUsername()).orElse(null);
            }
        } catch (Exception ignored) {
            // guest user
        }
        return null;
    }
}

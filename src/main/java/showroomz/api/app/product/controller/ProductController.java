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
    public ResponseEntity<ProductDto.ProductSearchResponse> searchProducts(
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

        // 현재 로그인한 사용자 ID 확인 (선택사항)
        Long userId = null;
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof User) {
                String username = ((User) principal).getUsername();
                Users user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    userId = user.getId();
                }
            }
        } catch (Exception e) {
            // 인증되지 않은 사용자(게스트)인 경우 userId는 null로 유지
        }

        ProductDto.ProductSearchResponse response = productService.searchProducts(
                request,
                page,
                limit,
                userId
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
}

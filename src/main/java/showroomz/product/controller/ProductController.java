package showroomz.product.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.product.DTO.ProductDto;
import showroomz.product.service.ProductService;
import showroomz.swaggerDocs.product.ProductControllerDocs;

@RestController
@RequestMapping("/v1/seller/products")
@RequiredArgsConstructor
public class ProductController implements ProductControllerDocs {

    private final ProductService productService;

    // 현재 로그인한 Admin의 Email 가져오기 (SecurityContext의 username은 email)
    private String getCurrentAdminEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((User) principal).getUsername();
    }

    @Override
    @PostMapping
    public ResponseEntity<ProductDto.CreateProductResponse> createProduct(
            @Valid @RequestBody ProductDto.CreateProductRequest request) {
        String adminEmail = getCurrentAdminEmail();
        ProductDto.CreateProductResponse response = productService.createProduct(adminEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDto.ProductListItem> getProductById(
            @PathVariable Long productId) {
        String adminEmail = getCurrentAdminEmail();
        ProductDto.ProductListItem response = productService.getProductById(adminEmail, productId);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<ProductDto.ProductListResponse> getProductList() {
        String adminEmail = getCurrentAdminEmail();
        ProductDto.ProductListResponse response = productService.getProductList(adminEmail);
        return ResponseEntity.ok(response);
    }
}




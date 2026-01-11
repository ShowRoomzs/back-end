package showroomz.api.seller.product.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.docs.ProductControllerDocs;
import showroomz.api.seller.product.DTO.ProductDto;
import showroomz.api.seller.product.service.ProductService;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

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
    public ResponseEntity<PageResponse<ProductDto.ProductListItem>> getProductList(
            ProductDto.ProductListRequest request,
            PagingRequest pagingRequest) {
        String adminEmail = getCurrentAdminEmail();
        PageResponse<ProductDto.ProductListItem> response = productService.getProductList(adminEmail, request, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/{productId}")
    public ResponseEntity<ProductDto.UpdateProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductDto.UpdateProductRequest request) {
        String adminEmail = getCurrentAdminEmail();
        ProductDto.UpdateProductResponse response = productService.updateProduct(adminEmail, productId, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{productId}")
    public ResponseEntity<ProductDto.DeleteProductResponse> deleteProduct(
            @PathVariable Long productId) {
        String adminEmail = getCurrentAdminEmail();
        ProductDto.DeleteProductResponse response = productService.deleteProduct(adminEmail, productId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/batch/stock-status")
    public ResponseEntity<ProductDto.BatchUpdateResponse> batchToggleStockStatus(
            @Valid @RequestBody ProductDto.BatchUpdateRequest request) {
        String adminEmail = getCurrentAdminEmail();
        ProductDto.BatchUpdateResponse response = productService.batchToggleStockStatus(adminEmail, request);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/batch/display-status")
    public ResponseEntity<ProductDto.BatchUpdateResponse> batchToggleDisplayStatus(
            @Valid @RequestBody ProductDto.BatchUpdateRequest request) {
        String adminEmail = getCurrentAdminEmail();
        ProductDto.BatchUpdateResponse response = productService.batchToggleDisplayStatus(adminEmail, request);
        return ResponseEntity.ok(response);
    }
}




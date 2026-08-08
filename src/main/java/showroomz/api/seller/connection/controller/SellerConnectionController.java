package showroomz.api.seller.connection.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.connection.dto.ConnectRequest;
import showroomz.api.seller.connection.dto.ConnectResponse;
import showroomz.api.seller.connection.dto.ConnectionCodeCheckResponse;
import showroomz.api.seller.connection.dto.ConnectionCreatorSearchItem;
import showroomz.api.seller.connection.docs.SellerConnectionControllerDocs;
import showroomz.api.seller.connection.service.SellerConnectionService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/connections")
@RequiredArgsConstructor
public class SellerConnectionController implements SellerConnectionControllerDocs {

    private final SellerConnectionService sellerConnectionService;

    @Override
    @GetMapping("/creators")
    public ResponseEntity<PageResponse<ConnectionCreatorSearchItem>> searchCreators(
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        PageResponse<ConnectionCreatorSearchItem> response =
                sellerConnectionService.searchCreators(getCurrentSellerEmail(), keyword, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/code")
    public ResponseEntity<ConnectionCodeCheckResponse> checkConnectionCode(
            @RequestParam("code") String code) {
        ConnectionCodeCheckResponse response =
                sellerConnectionService.checkConnectionCode(getCurrentSellerEmail(), code);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping
    public ResponseEntity<ConnectResponse> requestConnection(
            @Valid @RequestBody ConnectRequest request) {
        ConnectResponse response =
                sellerConnectionService.requestConnection(getCurrentSellerEmail(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}

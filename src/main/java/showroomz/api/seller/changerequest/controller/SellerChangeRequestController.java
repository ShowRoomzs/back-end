package showroomz.api.seller.changerequest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.changerequest.docs.SellerChangeRequestControllerDocs;
import showroomz.api.seller.changerequest.dto.ChangeRequestBannerResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestCreateResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestFieldOption;
import showroomz.api.seller.changerequest.dto.CreateChangeRequestRequest;
import showroomz.api.seller.changerequest.service.BrandChangeRequestService;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@RestController
@RequestMapping("/v1/seller/change-requests")
@RequiredArgsConstructor
public class SellerChangeRequestController implements SellerChangeRequestControllerDocs {

    private final BrandChangeRequestService brandChangeRequestService;

    @Override
    @PostMapping
    public ResponseEntity<ChangeRequestCreateResponse> create(@Valid @RequestBody CreateChangeRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(brandChangeRequestService.create(getCurrentSellerEmail(), request));
    }

    @Override
    @GetMapping("/latest")
    public ResponseEntity<ChangeRequestBannerResponse> getLatest(@RequestParam("type") ChangeRequestType type) {
        return ResponseEntity.ok(brandChangeRequestService.getBanner(getCurrentSellerEmail(), type));
    }

    @Override
    @GetMapping("/fields")
    public ResponseEntity<List<ChangeRequestFieldOption>> getFields(@RequestParam("type") ChangeRequestType type) {
        return ResponseEntity.ok(brandChangeRequestService.getFields(getCurrentSellerEmail(), type));
    }

    @Override
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("requestId") Long requestId) {
        brandChangeRequestService.cancel(getCurrentSellerEmail(), requestId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{requestId}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable("requestId") Long requestId) {
        brandChangeRequestService.acknowledge(getCurrentSellerEmail(), requestId);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}

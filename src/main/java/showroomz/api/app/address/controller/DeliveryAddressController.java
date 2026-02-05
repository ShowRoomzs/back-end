package showroomz.api.app.address.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.address.dto.DeliveryAddressDto;
import showroomz.api.app.address.service.DeliveryAddressService;
import showroomz.api.app.docs.DeliveryAddressControllerDocs;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@RestController
@RequestMapping("/v1/user/delivery-addresses")
@RequiredArgsConstructor
public class DeliveryAddressController implements DeliveryAddressControllerDocs {

    private final DeliveryAddressService deliveryAddressService;

    @Override
    // 배송지 목록 조회
    @GetMapping
    public ResponseEntity<List<DeliveryAddressDto.Response>> getAddressList() {
        // 1. 현재 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;

        return ResponseEntity.ok(deliveryAddressService.getAddressList(springUser.getUsername()));
    }

    @Override
    public ResponseEntity<DeliveryAddressDto.Response> getAddressDetail(@PathVariable("addressId") Long addressId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;
        return ResponseEntity.ok(deliveryAddressService.getAddressDetail(springUser.getUsername(), addressId));
    }

    @Override
    // 1. 배송지 추가
    @PostMapping
    public ResponseEntity<Void> addAddress(@Valid @RequestBody DeliveryAddressDto.Request request) {
        // 1. 현재 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;

        deliveryAddressService.addAddress(springUser.getUsername(), request);
        return ResponseEntity.ok().build();
    }

    @Override
    // 2. 배송지 삭제
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable("addressId") Long addressId) {
        // 1. 현재 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;

        deliveryAddressService.deleteAddress(springUser.getUsername(), addressId);
        return ResponseEntity.ok().build();
    }

    @Override
    // 3. 배송지 수정
    @PutMapping("/{addressId}")
    public ResponseEntity<Void> updateAddress(@PathVariable("addressId") Long addressId,
                                              @Valid @RequestBody DeliveryAddressDto.Request request) {
        // 1. 현재 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;

        deliveryAddressService.updateAddress(springUser.getUsername(), addressId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    // 4. 기본 배송지로 지정
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<Void> setDefaultAddress(@PathVariable("addressId") Long addressId) {
        // 1. 현재 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof User)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        User springUser = (User) principal;

        deliveryAddressService.setDefaultAddress(springUser.getUsername(), addressId);
        return ResponseEntity.ok().build();
    }
}

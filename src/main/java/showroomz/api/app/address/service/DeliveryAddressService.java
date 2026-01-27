package showroomz.api.app.address.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.address.dto.DeliveryAddressDto;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryAddressService {

    private final DeliveryAddressRepository deliveryAddressRepository;
    private final UserRepository userRepository;

    // 배송지 목록 조회
    public List<DeliveryAddressDto.Response> getAddressList(String username) {
        Users user = getUserByUsername(username);
        return deliveryAddressRepository.findAllByUserOrderByIsDefaultDescModifiedAtDesc(user)
                .stream()
                .map(DeliveryAddressDto.Response::from)
                .collect(Collectors.toList());
    }

    // 1. 배송지 추가
    @Transactional
    public void addAddress(String username, DeliveryAddressDto.Request request) {
        Users user = getUserByUsername(username);

        // 최대 배송지 개수 제한 로직이 필요하다면 여기에 추가 (예: 10개)
        if (deliveryAddressRepository.countByUser(user) >= 10) {
            throw new BusinessException(ErrorCode.MAX_ADDRESS_LIMIT_EXCEEDED);
        }

        // 기본 배송지로 설정하는 경우, 기존 기본 배송지 해제
        if (request.isDefault()) {
            resetDefaultAddress(user);
        }

        // 첫 배송지인 경우 강제로 기본 배송지로 설정 (선택 사항)
        boolean isFirst = deliveryAddressRepository.countByUser(user) == 0;
        
        DeliveryAddress address = DeliveryAddress.builder()
                .user(user)
                .recipientName(request.getRecipientName())
                .zipCode(request.getZipCode())
                .address(request.getAddress())
                .detailAddress(request.getDetailAddress())
                .phoneNumber(request.getPhoneNumber())
                .isDefault(request.isDefault() || isFirst)
                .build();

        deliveryAddressRepository.save(address);
    }

    // 2. 배송지 삭제
    @Transactional
    public void deleteAddress(String username, Long addressId) {
        Users user = getUserByUsername(username);
        DeliveryAddress address = getAddress(addressId, user);

        // // 기본 배송지 삭제 방지 (배송지가 1개만 남았을 때는 삭제 허용)
        // if (address.isDefault()) {
        //     if (deliveryAddressRepository.countByUser(user) > 1) {
        //         throw new BusinessException(ErrorCode.DEFAULT_ADDRESS_DELETE_NOT_ALLOWED);
        //     }
        // }

        deliveryAddressRepository.delete(address);
    }

    // 3. 배송지 수정
    @Transactional
    public void updateAddress(String username, Long addressId, DeliveryAddressDto.Request request) {
        Users user = getUserByUsername(username);
        DeliveryAddress address = getAddress(addressId, user);

        // 기본 배송지로 변경 요청 시 기존 설정 해제
        if (request.isDefault() && !address.isDefault()) {
            resetDefaultAddress(user);
        }

        address.updateAddress(
                request.getRecipientName(),
                request.getZipCode(),
                request.getAddress(),
                request.getDetailAddress(),
                request.getPhoneNumber(),
                request.isDefault()
        );
    }

    // 4. 기본 배송지로 지정 (단독 API)
    @Transactional
    public void setDefaultAddress(String username, Long addressId) {
        Users user = getUserByUsername(username);
        DeliveryAddress address = getAddress(addressId, user);

        if (address.isDefault()) return; // 이미 기본이면 패스

        resetDefaultAddress(user);
        address.updateDefaultStatus(true);
    }

    // Helper: 기존 기본 배송지 해제
    private void resetDefaultAddress(Users user) {
        deliveryAddressRepository.findByUserAndIsDefaultTrue(user)
                .ifPresent(addr -> addr.updateDefaultStatus(false));
    }

    // Helper: 검증 및 조회
    private DeliveryAddress getAddress(Long addressId, Users user) {
        DeliveryAddress address = deliveryAddressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        
        if (!address.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.ADDRESS_ACCESS_DENIED);
        }
        return address;
    }

    private Users getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}

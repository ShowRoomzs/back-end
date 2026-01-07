package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.auth.entity.Seller;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final SellerRepository sellerRepository;

    /**
     * 판매자(관리자) 계정 승인/반려 처리
     */
    @Transactional
    public void updateAdminStatus(Long adminId, SellerStatus status) {
        Seller admin = sellerRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        admin.setStatus(status);
        admin.setModifiedAt(LocalDateTime.now());
    }
}


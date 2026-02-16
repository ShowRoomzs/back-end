package showroomz.api.app.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.coupon.dto.UserCouponDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.entity.UserCoupon;
import showroomz.domain.coupon.repository.CouponRepository;
import showroomz.domain.coupon.repository.UserCouponRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserCouponService {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserCouponDto> getMyCoupons(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = pagingRequest.toPageable(Sort.by(Sort.Direction.DESC, "registeredAt"));
        Page<UserCoupon> page = userCouponRepository.findByUserOrderByRegisteredAtDesc(user, pageable);
        List<UserCouponDto> content = page.getContent().stream()
                .map(UserCouponDto::from)
                .collect(Collectors.toList());
        return new PageResponse<>(content, page);
    }

    @Transactional
    public void registerCoupon(String username, String code) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }

        if (userCouponRepository.existsByUserAndCoupon(user, coupon)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_REGISTERED);
        }

        userCouponRepository.save(new UserCoupon(user, coupon));
    }
}

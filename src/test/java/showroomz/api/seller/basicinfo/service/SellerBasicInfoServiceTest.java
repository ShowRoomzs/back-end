package showroomz.api.seller.basicinfo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import showroomz.api.seller.auth.refreshToken.SellerRefreshToken;
import showroomz.api.seller.auth.refreshToken.SellerRefreshTokenRepository;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.basicinfo.dto.SellerBasicInfoDto;
import showroomz.api.seller.changerequest.service.BrandChangeRequestService;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerBasicInfoServiceTest {

    private static final String SELLER_EMAIL = "brand@showroomz.co.kr";

    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private BrandChangeRequestService brandChangeRequestService;
    @Mock
    private MailService mailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SellerRefreshTokenRepository sellerRefreshTokenRepository;

    @InjectMocks
    private SellerBasicInfoService sellerBasicInfoService;

    private Seller seller;

    @BeforeEach
    void setUp() {
        seller = new Seller(SELLER_EMAIL, "encoded-password", "김담당", "010-1234-5678", LocalDateTime.now());
        given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.of(seller));
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하지 않으면 비밀번호 변경을 거부한다")
    void changePasswordRejectsWhenCurrentPasswordMismatches() {
        given(passwordEncoder.matches("wrong", seller.getPassword())).willReturn(false);

        SellerBasicInfoDto.ChangePasswordRequest request =
                new SellerBasicInfoDto.ChangePasswordRequest("wrong", "NewPass123!", "NewPass123!");

        assertThatThrownBy(() -> sellerBasicInfoService.changePassword(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("새 비밀번호와 확인값이 다르면 거부한다")
    void changePasswordRejectsWhenConfirmMismatches() {
        given(passwordEncoder.matches("current", seller.getPassword())).willReturn(true);

        SellerBasicInfoDto.ChangePasswordRequest request =
                new SellerBasicInfoDto.ChangePasswordRequest("current", "NewPass123!", "Different123!");

        assertThatThrownBy(() -> sellerBasicInfoService.changePassword(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NEW_PASSWORD_CONFIRM_MISMATCH);
    }

    @Test
    @DisplayName("이메일 변경 1개월 이내(29일 경과)면 롤링 제한에 걸린다")
    void changeEmailBlockedWithin29Days() {
        seller.setEmailChangedAt(LocalDateTime.now().minusDays(29));
        given(passwordEncoder.matches("current", seller.getPassword())).willReturn(true);

        SellerBasicInfoDto.ChangeEmailRequest request =
                new SellerBasicInfoDto.ChangeEmailRequest("current", "new@showroomz.co.kr");

        assertThatThrownBy(() -> sellerBasicInfoService.changeEmail(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_CHANGE_LIMIT_EXCEEDED);
        verify(mailService, never()).sendLoginEmailChangedNotice(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("1개월 하고 하루가 지나면 다시 변경할 수 있다")
    void changeEmailAllowedAfterOneMonthAndOneDay() {
        seller.setEmailChangedAt(LocalDateTime.now().minusMonths(1).minusDays(1));
        given(passwordEncoder.matches("current", seller.getPassword())).willReturn(true);
        given(sellerRepository.existsByEmailAndStatusNotRejected(any(), any())).willReturn(false);

        SellerBasicInfoDto.ChangeEmailRequest request =
                new SellerBasicInfoDto.ChangeEmailRequest("current", "new@showroomz.co.kr");

        SellerBasicInfoDto.AccountInfoResponse response = sellerBasicInfoService.changeEmail(SELLER_EMAIL, request);

        assertThat(response.getLoginEmail()).isEqualTo("new@showroomz.co.kr");
        assertThat(seller.getEmail()).isEqualTo("new@showroomz.co.kr");
    }

    @Test
    @DisplayName("중복 이메일이면 변경을 거부한다")
    void changeEmailRejectsDuplicateEmail() {
        given(passwordEncoder.matches("current", seller.getPassword())).willReturn(true);
        given(sellerRepository.existsByEmailAndStatusNotRejected(any(), any())).willReturn(true);

        SellerBasicInfoDto.ChangeEmailRequest request =
                new SellerBasicInfoDto.ChangeEmailRequest("current", "taken@showroomz.co.kr");

        assertThatThrownBy(() -> sellerBasicInfoService.changeEmail(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL_SIGNUP);
    }

    @Test
    @DisplayName("이메일 변경 성공 시 구 이메일로 통지 메일을 보내고 기존 리프레시 토큰을 삭제한다")
    void changeEmailSendsNoticeAndClearsRefreshToken() {
        given(passwordEncoder.matches("current", seller.getPassword())).willReturn(true);
        given(sellerRepository.existsByEmailAndStatusNotRejected(any(), any())).willReturn(false);
        SellerRefreshToken existingToken = new SellerRefreshToken(SELLER_EMAIL, "old-refresh-token");
        given(sellerRefreshTokenRepository.findByAdminEmail(SELLER_EMAIL)).willReturn(existingToken);

        SellerBasicInfoDto.ChangeEmailRequest request =
                new SellerBasicInfoDto.ChangeEmailRequest("current", "new@showroomz.co.kr");

        sellerBasicInfoService.changeEmail(SELLER_EMAIL, request);

        verify(mailService, times(1)).sendLoginEmailChangedNotice(
                org.mockito.ArgumentMatchers.eq(SELLER_EMAIL),
                org.mockito.ArgumentMatchers.eq("new@showroomz.co.kr"),
                any());
        verify(sellerRefreshTokenRepository).delete(existingToken);
    }
}

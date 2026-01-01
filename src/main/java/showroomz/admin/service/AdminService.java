package showroomz.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.Market.entity.Market;
import showroomz.user.repository.MarketRepository;
import showroomz.auth.DTO.AdminSignUpRequest;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.user.entity.Users;
import showroomz.user.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final MarketRepository marketRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void registerAdmin(AdminSignUpRequest request) {
        // 1. 비밀번호 일치 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. 이메일(ID) 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL_SIGNUP);
        }
        
        // 3. 마켓명 중복 체크
        if (marketRepository.existsByMarketName(request.getMarketName())) {
             throw new BusinessException(ErrorCode.DUPLICATE_MARKET_NAME);
        }

        // 4. Users 엔티티 생성 (계정 + 개인 정보)
        Users user = new Users(
                request.getEmail(), // username을 이메일로 사용
                request.getMarketName(), // nickname에 마켓명 저장
                request.getEmail(),
                "N", // 이메일 인증 여부
                null, // 프로필 이미지
                ProviderType.LOCAL,
                RoleType.ADMIN, // 권한을 ADMIN으로 설정
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getSellerName()); // 판매자 이름(실명)을 name 필드에 저장
        user.setPhoneNumber(request.getSellerContact()); // 판매자 연락처를 유저 정보로 저장
        
        Users savedUser = userRepository.save(user);

        // 5. Market 엔티티 생성 (가게 정보)
        Market market = new Market(
                savedUser,
                request.getMarketName(),
                request.getCsNumber() // 고객센터 번호
        );

        marketRepository.save(market);
    }

    // 읽기 전용 트랜잭션으로 설정하여 성능 최적화
    @Transactional(readOnly = true)
    public boolean checkEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean checkMarketNameDuplicate(String marketName) {
        return marketRepository.existsByMarketName(marketName);
    }
}
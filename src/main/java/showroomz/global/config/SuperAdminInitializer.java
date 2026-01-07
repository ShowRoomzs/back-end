package showroomz.global.config; // 패키지 위치는 프로젝트 구조에 맞게 조정하세요

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.entity.Seller;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SuperAdminInitializer implements CommandLineRunner {

    private final SellerRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 1. 생성할 슈퍼 어드민 계정 정보 설정
        String email = "super"; // 원하는 이메일
        String password = "super"; // 원하는 비밀번호

        // 2. 이미 존재하는지 확인 (중복 생성 방지)
        if (!adminRepository.existsByEmail(email)) {
            
            // 3. Admin 엔티티 생성 (기본 생성자와 Setter 사용)
            // 주의: 기존 Admin 생성자(public Admin(...))는 RoleType.SELLER로 고정되므로 사용하지 않습니다.
            Seller superAdmin = new Seller();
            superAdmin.setEmail(email);
            superAdmin.setPassword(passwordEncoder.encode(password)); // 비밀번호 암호화 필수
            superAdmin.setName("Super Master");
            superAdmin.setPhoneNumber("010-0000-0000");
            superAdmin.setRoleType(RoleType.ADMIN); // 핵심: 권한을 ADMIN으로 설정
            superAdmin.setStatus(SellerStatus.APPROVED); // 슈퍼 어드민은 자동 승인
            superAdmin.setCreatedAt(LocalDateTime.now());
            superAdmin.setModifiedAt(LocalDateTime.now());

            // 4. DB 저장
            adminRepository.save(superAdmin);
            System.out.println(">>> [INIT] 슈퍼 어드민 계정이 자동으로 생성되었습니다: " + email);
        }
    }
}
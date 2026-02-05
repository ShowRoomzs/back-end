package showroomz.api.app.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.user.DTO.NicknameCheckResponse;
import showroomz.api.app.user.DTO.RefundAccountRequest;
import showroomz.api.app.user.DTO.RefundAccountResponse;
import showroomz.api.app.user.DTO.UpdateUserProfileRequest;
import showroomz.api.app.user.DTO.UserProfileResponse;
import showroomz.api.app.user.DTO.WithdrawalRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.history.entity.WithdrawalHistory;
import showroomz.domain.history.repository.WithdrawalHistoryRepository;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.vo.RefundAccount;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final MarketFollowRepository marketFollowRepository;
    private final WithdrawalHistoryRepository withdrawalHistoryRepository;

    public Optional<Users> getUser(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 유저 프로필 조회 (팔로잉 수 포함)
     * Controller에서 이 메서드를 호출하여 응답을 생성합니다.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 탈퇴 회원 체크
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }

        // 유저가 팔로우한 마켓 수 조회
        long followingCount = marketFollowRepository.countByUser(user);

        // DTO 생성 및 반환 (더미 데이터 추가)
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getPhoneNumber(),
                user.getBirthday(),
                user.getGender(),
                user.getProviderType(),
                user.getRoleType(),
                user.getCreatedAt(),
                user.getModifiedAt(),
                user.isMarketingAgree(),
                followingCount, // 실제 팔로잉 수
                3L,             // couponCount (더미 값: 3장)
                5000L,          // point (더미 값: 5000포인트)
                12L             // reviewCount (더미 값: 12개)
        );
    }

    /**
     * 사용자 프로필 업데이트
     * @param username 사용자 이름
     * @param request 업데이트 요청
     * @return 업데이트된 사용자
     */
    @Transactional
    public Users updateProfile(String username, UpdateUserProfileRequest request) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 탈퇴 회원 체크
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }

        // 닉네임 업데이트
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            user.setNickname(request.getNickname());
        }

        // 휴대폰 번호 업데이트
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().isEmpty() ? null : request.getPhoneNumber());
        }

        // 생년월일 업데이트
        if (request.getBirthday() != null && !request.getBirthday().isEmpty()) {
            user.setBirthday(request.getBirthday());
        }

        // 성별 업데이트
        if (request.getGender() != null && !request.getGender().isEmpty()) {
            user.setGender(request.getGender());
        }

        // 프로필 이미지 업데이트
        if (request.getProfileImageUrl() != null && !request.getProfileImageUrl().isEmpty()) {
            user.setProfileImageUrl(request.getProfileImageUrl());
        }

        // 마케팅 동의 업데이트
        if (request.getMarketingAgree() != null) {
            user.setMarketingAgree(request.getMarketingAgree().booleanValue());
        }

        // 수정 시간 업데이트
        user.setModifiedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    /**
     * 닉네임 검증 및 체크
     * @param nickname 검증할 닉네임
     * @return NicknameCheckResponse 검증 결과
     */
    public NicknameCheckResponse checkNickname(String nickname) {
        // 1. 길이 검증 (2자 이상 10자 이하)
        if (!isValidNicknameLength(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "INVALID_LENGTH",
                    "닉네임은 2자 이상 10자 이하이어야 합니다."
            );
        }
        
        // 2. 닉네임 형식 검증 (한글, 영문, 숫자만 허용)
        if (!isValidNicknameFormat(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "INVALID_FORMAT",
                    "닉네임에 특수문자나 이모티콘을 사용할 수 없습니다."
            );
        }

        // 3. 금칙어 체크
        if (containsInappropriateWord(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "PROFANITY",
                    "부적절한 단어가 포함되어 있습니다."
            );
        }

        // 4. 중복 체크
        if (userRepository.existsByNickname(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "DUPLICATE",
                    "이미 사용 중인 닉네임입니다."
            );
        }

        // 4. 사용 가능
        return new NicknameCheckResponse(
                true,
                "AVAILABLE",
                "사용 가능한 닉네임입니다."
        );
    }

    /**
     * 닉네임 형식 검증 (한글, 영문, 숫자만 허용)
     * 주의: 길이 검증은 별도로 수행해야 함
     */
    public boolean isValidNicknameFormat(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return false;
        }
        // 한글(완성형 + 자모), 영문(대소문자), 숫자만 허용
        // 완성형 한글(가-힣), 한글 자모(ㄱ-ㅎ, ㅏ-ㅣ)
        return nickname.matches("^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+$");
    }

    /**
     * 닉네임 길이 검증
     */
    public boolean isValidNicknameLength(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return false;
        }
        return nickname.length() >= 2 && nickname.length() <= 10;
    }

    /**
     * 닉네임 부적절한 단어 체크
     */
    public boolean containsInappropriateWord(String nickname) {
        // 부적절한 단어 목록 (실제로는 DB나 설정 파일에서 관리하는 것이 좋습니다)
        String[] inappropriateWords = {
            "관리자", "admin", "administrator", "운영자", "operator",
            "시스템", "system", "서버", "server", "테스트", "test",
            "욕설", "비속어", "fuck", "shit", "damn", "hell"
        };
        
        String lowerNickname = nickname.toLowerCase();
        for (String word : inappropriateWords) {
            if (lowerNickname.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 회원 탈퇴 (논리 삭제)
     * 실제 DB 삭제 대신 상태를 WITHDRAWN으로 변경하여 외래 키 문제를 방지합니다.
     */
    @Transactional
    public void withdrawUser(String username, WithdrawalRequest request) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이미 탈퇴한 회원인지 확인
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }

        // 1. 탈퇴 동의 체크 확인 (백엔드에서도 한 번 더 검증)
        if (!request.isAgreeConsent()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴 유의사항에 동의해야 합니다.");
        }

        // 2. 탈퇴 히스토리 저장
        withdrawalHistoryRepository.save(WithdrawalHistory.builder()
                .userId(user.getId())
                .agreeConsent(request.isAgreeConsent())
                .reason(request.getReason())
                .customReason(request.getCustomReason())
                .build());

        // 3. 회원 상태 변경 (논리 삭제)
        user.updateStatus(UserStatus.WITHDRAWN);

        // (선택 사항) 개인정보 보호를 위한 중요 정보 마스킹/삭제 처리
        // 탈퇴한 회원의 개인정보를 즉시 파기해야 한다면 아래와 같이 처리합니다.
        // user.setPassword(""); // 비밀번호 삭제
        user.setNickname("탈퇴한 회원" + user.getId());
        // user.setPhoneNumber("");
        // user.setEmail(user.getId() + "@withdrawn.user"); // 유니크 제약조건 유지를 위해 ID 활용
        
        // Dirty Checking(변경 감지)에 의해 트랜잭션 종료 시 자동으로 Update 쿼리가 실행됩니다.
    }

    /**
     * 내 환불 계좌 정보 조회
     * 등록된 계좌가 없으면 null을 반환합니다.
     */
    @Transactional(readOnly = true)
    public RefundAccountResponse getRefundAccount(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        RefundAccount account = user.getRefundAccount();
        if (account == null) {
            return null;
        }

        return RefundAccountResponse.of(account, account.getBank().getName());
    }

    /**
     * 환불 계좌 등록 및 수정
     */
    @Transactional
    public void updateRefundAccount(Long userId, RefundAccountRequest request) {
        // 1. 유저 조회
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 탈퇴 회원 체크
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }

        Bank bank = bankRepository.findById(request.getBankCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_NOT_FOUND));

        // 4. 계좌 정보 업데이트
        user.updateRefundAccount(
                bank,
                request.getAccountNumber(),
                request.getAccountHolder()
        );
    }
}

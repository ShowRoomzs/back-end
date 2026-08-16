package showroomz.api.app.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.user.DTO.NicknameCheckResponse;
import showroomz.api.app.user.DTO.RefundAccountRequest;
import showroomz.api.app.user.DTO.RefundAccountResponse;
import showroomz.api.app.user.DTO.UpdateUserProfileRequest;
import showroomz.api.app.user.DTO.UserProfileResponse;
import showroomz.api.app.user.DTO.WithdrawalInfoResponse;
import showroomz.api.app.user.DTO.WithdrawalRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.history.entity.UserConsentHistory;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.entity.WithdrawalHistory;
import showroomz.domain.history.repository.UserConsentHistoryRepository;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.history.repository.WithdrawalHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.type.WithdrawalReason;
import showroomz.domain.member.user.vo.RefundAccount;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final WithdrawalHistoryRepository withdrawalHistoryRepository;
    private final UserStatusHistoryRepository userStatusHistoryRepository;
    private final UserConsentHistoryRepository userConsentHistoryRepository;
    private final WishlistRepository wishlistRepository;
    private final CartRepository cartRepository;
    private final OrderProductRepository orderProductRepository;

    /** 더 이상 진행 중이 아닌 주문 상태 — 이 둘을 뺀 나머지가 탈퇴를 막는다 */
    private static final Set<OrderProductStatus> FINISHED_ORDER_STATUSES =
            EnumSet.of(OrderProductStatus.PURCHASE_CONFIRMED, OrderProductStatus.CANCELLED);

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

        // 유저가 팔로우한 쇼룸 수 조회
        long followingCount = creatorFollowRepository.countByUser(user);

        // DTO 생성 및 반환 (더미 데이터 추가)
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getName(),
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

        // 프로필 이미지 업데이트 — 빈 문자열이면 기본 이미지로 되돌린다
        if (request.getProfileImageUrl() != null) {
            user.setProfileImageUrl(request.getProfileImageUrl().isEmpty() ? null : request.getProfileImageUrl());
        }

        // 수정 시간 업데이트
        user.setModifiedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    /**
     * C15-1 / C0-1 닉네임 검증. 두 화면이 같은 규칙·문구를 쓰므로 코드와 메시지를 여기서 한 벌만 정한다.
     *
     * <p>{@code currentNickname}이 주어지고 값이 같으면 {@code UNCHANGED}를 돌려준다 —
     * 자기 닉네임을 다시 입력한 것은 오류가 아니지만 [저장]도 켜지면 안 되기 때문이다.
     *
     * @param nickname        검증할 닉네임
     * @param currentNickname 호출자의 현재 닉네임 (가입 화면처럼 없을 수 있음)
     */
    public NicknameCheckResponse checkNickname(String nickname, String currentNickname) {
        // 0. 현재 닉네임 그대로 — 오류는 아니지만 저장할 것도 없다
        if (currentNickname != null && currentNickname.equals(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "UNCHANGED",
                    "현재 사용 중인 닉네임이에요"
            );
        }

        // 1. 길이 검증 (2자 이상 10자 이하)
        if (nickname == null || nickname.length() < 2) {
            return new NicknameCheckResponse(
                    false,
                    "INVALID_LENGTH",
                    "2자 이상 입력해 주세요"
            );
        }
        if (nickname.length() > 10) {
            return new NicknameCheckResponse(
                    false,
                    "INVALID_LENGTH",
                    "10자 이하로 입력해 주세요"
            );
        }

        // 2. 닉네임 형식 검증 (한글, 영문, 숫자만 허용)
        if (!isValidNicknameFormat(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "INVALID_FORMAT",
                    "한글·영문·숫자만 사용할 수 있어요"
            );
        }

        // 3. 금칙어 체크
        if (containsInappropriateWord(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "PROFANITY",
                    "사용할 수 없는 단어가 포함되어 있어요"
            );
        }

        // 4. 중복 체크
        if (userRepository.existsByNickname(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "DUPLICATE",
                    "이미 사용 중인 닉네임이에요"
            );
        }

        // 5. 사용 가능
        return new NicknameCheckResponse(
                true,
                "AVAILABLE",
                "사용할 수 있는 닉네임이에요"
        );
    }

    /** 현재 닉네임을 모르는 호출부(가입 화면 등)용 */
    public NicknameCheckResponse checkNickname(String nickname) {
        return checkNickname(nickname, null);
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
            "공식", "official", "고객센터",
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
     * C15-4 회원 탈퇴 2단계 진입 데이터.
     * 차단 사유(진행 중 주문)와 최종 확인 모달에 넣을 실제 개수를 함께 내려준다.
     */
    @Transactional(readOnly = true)
    public WithdrawalInfoResponse getWithdrawalInfo(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }

        long ongoingOrderCount = orderProductRepository.countOngoingByUserId(user.getId(), FINISHED_ORDER_STATUSES);

        List<WithdrawalInfoResponse.WithdrawalReasonOption> reasons = Arrays.stream(WithdrawalReason.values())
                .map(r -> new WithdrawalInfoResponse.WithdrawalReasonOption(r.name(), r.getDescription()))
                .toList();

        return new WithdrawalInfoResponse(
                ongoingOrderCount == 0,
                ongoingOrderCount,
                creatorFollowRepository.countByUser(user),
                wishlistRepository.countByUser_Id(user.getId()),
                cartRepository.countByUser(user),
                reasons
        );
    }

    /**
     * C15-4 회원 탈퇴 (논리 삭제).
     *
     * <p>주문·결제 기록은 전자상거래법상 법정 기간 동안 분리 보관해야 하므로 회원 행을 지우지 않고
     * 상태만 WITHDRAWN으로 바꾼다. 대신 화면에서 "삭제된다"고 고지한 것 — 닉네임·프로필 사진·
     * 팔로잉·좋아요·장바구니 — 은 실제로 지운다.
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
            throw new BusinessException(ErrorCode.WITHDRAWAL_CONSENT_REQUIRED);
        }

        // 2. 진행 중 주문이 있으면 배송·교환·환불이 끝날 때까지 탈퇴를 막는다.
        //    화면에서 이미 비활성이지만, 조회 후 주문이 생겼거나 API를 직접 호출한 경우를 여기서 거른다.
        if (orderProductRepository.countOngoingByUserId(user.getId(), FINISHED_ORDER_STATUSES) > 0) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_BLOCKED_BY_ORDER);
        }

        // 기존 상태 임시 저장 (히스토리 기록용)
        UserStatus previousStatus = user.getStatus();

        // 3. 탈퇴 히스토리 저장 (이유는 선택이라 null일 수 있다)
        withdrawalHistoryRepository.save(WithdrawalHistory.builder()
                .userId(user.getId())
                .agreeConsent(request.isAgreeConsent())
                .reason(request.getReason())
                .customReason(request.getCustomReason())
                .build());

        // 4. 회원 상태 변경 (논리 삭제)
        user.updateStatus(UserStatus.WITHDRAWN);

        // 5. 유저 상태 변경 히스토리 저장
        userStatusHistoryRepository.save(UserStatusHistory.builder()
                .user(user)
                .previousStatus(previousStatus)
                .newStatus(UserStatus.WITHDRAWN)
                .reason(request.getCustomReason() != null ? request.getCustomReason() : (request.getReason() != null ? request.getReason().name() : null))
                .build());

        // 6. 활동 기록 파기 — 팔로잉·좋아요·장바구니
        creatorFollowRepository.deleteByUser(user);
        wishlistRepository.deleteByUser(user);
        cartRepository.deleteByUser(user);

        // 7. 광고성 정보 수신에 동의한 상태였다면 탈퇴로 철회되므로 철회 일시를 남긴다
        if (user.isMarketingAgree()) {
            userConsentHistoryRepository.save(UserConsentHistory.builder()
                    .user(user)
                    .consentType(ConsentType.MARKETING)
                    .agreed(false)
                    .build());
        }

        // 8. 회원 행에 남은 개인정보 파기 (닉네임·프로필 사진·본인인증 정보·환불 계좌)
        user.purgeOnWithdrawal();

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

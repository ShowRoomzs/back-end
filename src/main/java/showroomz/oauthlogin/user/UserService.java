package showroomz.oauthlogin.user;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

import org.springframework.stereotype.Service;

import showroomz.oauthlogin.auth.UserRepository;
import showroomz.oauthlogin.user.DTO.NicknameCheckResponse;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public Optional<User> getUser(String userId) {
        return userRepository.findByUserId(userId);
    }

    /**
     * 닉네임 검증 및 체크
     * @param nickname 검증할 닉네임
     * @return NicknameCheckResponse 검증 결과
     */
    public NicknameCheckResponse checkNickname(String nickname) {
        // 1. 닉네임 형식 검증 (한글, 영문, 숫자만 허용)
        if (!isValidNicknameFormat(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "INVALID_FORMAT",
                    "닉네임에 특수문자나 이모티콘을 사용할 수 없습니다."
            );
        }

        // 2. 금칙어 체크
        if (containsInappropriateWord(nickname)) {
            return new NicknameCheckResponse(
                    false,
                    "PROFANITY",
                    "부적절한 단어가 포함되어 있습니다."
            );
        }

        // 3. 중복 체크
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
     */
    public boolean isValidNicknameFormat(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return false;
        }
        // 한글, 영문(대소문자), 숫자만 허용
        return nickname.matches("^[가-힣a-zA-Z0-9]+$");
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
}

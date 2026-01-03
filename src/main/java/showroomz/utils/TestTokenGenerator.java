package showroomz.utils;

import java.util.Date;

import showroomz.auth.entity.RoleType;
import showroomz.auth.token.AuthToken;
import showroomz.auth.token.AuthTokenProvider;

/**
 * 테스트용 액세스 토큰 생성 유틸리티
 * 
 * 사용 예시:
 * - userId만 있는 경우: TestTokenGenerator.generateToken("user123", "your-secret-key")
 * - userId와 role이 있는 경우: TestTokenGenerator.generateToken("user123", RoleType.USER, "your-secret-key")
 */
public class TestTokenGenerator {

    /**
     * 테스트용 액세스 토큰 생성 (유효기간: 2개월)
     * 
     * @param userId 사용자 ID
     * @param tokenSecret JWT 시크릿 키 (application.yml의 app.auth.tokenSecret 값)
     * @return 생성된 액세스 토큰 문자열
     */
    public static String generateToken(String userId, String tokenSecret) {
        AuthTokenProvider tokenProvider = new AuthTokenProvider(tokenSecret);
        
        // 2개월 = 60일 = 60 * 24 * 60 * 60 * 1000 밀리초
        long twoMonthsInMillis = 60L * 24 * 60 * 60 * 1000;
        Date expiry = new Date(System.currentTimeMillis() + twoMonthsInMillis);
        
        AuthToken authToken = tokenProvider.createAuthToken(userId, expiry);
        return authToken.getToken();
    }

    /**
     * 테스트용 액세스 토큰 생성 (유효기간: 2개월, 역할 포함)
     * 
     * @param userId 사용자 ID
     * @param roleType 역할 타입 (USER, ADMIN, SUPER_ADMIN)
     * @param tokenSecret JWT 시크릿 키 (application.yml의 app.auth.tokenSecret 값)
     * @return 생성된 액세스 토큰 문자열
     */
    public static String generateToken(String userId, RoleType roleType, String tokenSecret) {
        AuthTokenProvider tokenProvider = new AuthTokenProvider(tokenSecret);
        
        // 2개월 = 60일 = 60 * 24 * 60 * 60 * 1000 밀리초
        long twoMonthsInMillis = 60L * 24 * 60 * 60 * 1000;
        Date expiry = new Date(System.currentTimeMillis() + twoMonthsInMillis);
        
        AuthToken authToken = tokenProvider.createAuthToken(userId, roleType.getCode(), expiry);
        return authToken.getToken();
    }

    /**
     * 테스트용 액세스 토큰 생성 및 출력 (메인 메서드)
     * 
     * 실행 방법:
     * 1. application.yml의 app.auth.tokenSecret 값을 확인
     * 2. 이 클래스의 main 메서드를 실행
     * 3. 생성된 토큰을 콘솔에서 복사하여 사용
     */
    public static void main(String[] args) {
        // application.yml의 app.auth.tokenSecret 값을 여기에 입력하세요
        String tokenSecret = System.getenv("TOKEN_SECRET");
        if (tokenSecret == null || tokenSecret.isEmpty()) {
            tokenSecret = "your-token-secret-key-minimum-32-characters-long"; // 기본값 (실제로는 환경변수나 설정에서 가져와야 함)
            System.out.println("⚠️  TOKEN_SECRET 환경변수가 설정되지 않았습니다. 기본값을 사용합니다.");
            System.out.println("   환경변수 설정: export TOKEN_SECRET='your-secret-key'\n");
        }

        // 테스트용 사용자 ID
        String testUserId = "test-user-123";
        
        // USER 역할로 토큰 생성
        String userToken = generateToken(testUserId, RoleType.USER, tokenSecret);
        System.out.println("=".repeat(80));
        System.out.println("테스트용 액세스 토큰 (USER 역할, 유효기간: 2개월)");
        System.out.println("=".repeat(80));
        System.out.println("User ID: " + testUserId);
        System.out.println("Role: USER");
        System.out.println("Token:");
        System.out.println(userToken);
        System.out.println("=".repeat(80));
        System.out.println("\ncurl 예시:");
        System.out.println("curl -X POST 'https://api.showroomz.shop/v1/images?type=PROFILE' \\");
        System.out.println("  -H 'Authorization: Bearer " + userToken + "' \\");
        System.out.println("  -F 'file=@/path/to/image.jpg'\n");

        // ADMIN 역할로 토큰 생성
        String adminToken = generateToken(testUserId, RoleType.ADMIN, tokenSecret);
        System.out.println("=".repeat(80));
        System.out.println("테스트용 액세스 토큰 (ADMIN 역할, 유효기간: 2개월)");
        System.out.println("=".repeat(80));
        System.out.println("User ID: " + testUserId);
        System.out.println("Role: ADMIN");
        System.out.println("Token:");
        System.out.println(adminToken);
        System.out.println("=".repeat(80));
        System.out.println("\ncurl 예시:");
        System.out.println("curl -X GET 'https://api.showroomz.shop/v1/markets/me' \\");
        System.out.println("  -H 'Authorization: Bearer " + adminToken + "'\n");

        // SUPER_ADMIN 역할로 토큰 생성
        String superAdminToken = generateToken(testUserId, RoleType.SUPER_ADMIN, tokenSecret);
        System.out.println("=".repeat(80));
        System.out.println("테스트용 액세스 토큰 (SUPER_ADMIN 역할, 유효기간: 2개월)");
        System.out.println("=".repeat(80));
        System.out.println("User ID: " + testUserId);
        System.out.println("Role: SUPER_ADMIN");
        System.out.println("Token:");
        System.out.println(superAdminToken);
        System.out.println("=".repeat(80));
        System.out.println("\ncurl 예시:");
        System.out.println("curl -X PATCH 'https://api.showroomz.shop/v1/markets/1/image-status' \\");
        System.out.println("  -H 'Authorization: Bearer " + superAdminToken + "' \\");
        System.out.println("  -H 'Content-Type: application/json' \\");
        System.out.println("  -d '{\"status\":\"APPROVED\"}'\n");
    }
}


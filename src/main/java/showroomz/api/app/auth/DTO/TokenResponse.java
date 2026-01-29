package showroomz.api.app.auth.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponse {
    private String tokenType = "Bearer";
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
    private Long refreshTokenExpiresIn;
    private Boolean isNewMember;
    private String registerToken; // 신규 회원일 때만 제공 (5분 유효)
    private String role; // 권한 정보 (예: "SELLER", "ADMIN")
    private String shopType; // 판매자 로그인 시 샵 타입 (예: "MARKET", "SHOWROOM"), 어드민/일반 사용자 등은 null

    // 기존 회원용 생성자
    public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn, long refreshTokenExpiresIn, boolean isNewMember, String role) {
        this.tokenType = "Bearer";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
        this.isNewMember = isNewMember;
        this.registerToken = null;
        this.role = role;
        this.shopType = null;
    }

    // 기존 회원용 생성자 + shopType 포함 (판매자 로그인 전용)
    public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn, long refreshTokenExpiresIn, boolean isNewMember, String role, String shopType) {
        this(accessToken, refreshToken, accessTokenExpiresIn, refreshTokenExpiresIn, isNewMember, role);
        this.shopType = shopType;
    }

    // 신규 회원용 생성자
    public TokenResponse(String registerToken) {
        this.isNewMember = true;
        this.registerToken = registerToken;
        this.tokenType = null;
        this.accessToken = null;
        this.refreshToken = null;
        this.accessTokenExpiresIn = null;
        this.refreshTokenExpiresIn = null;
        this.role = "GUEST"; // 필요하다면 기본값 설정
    }
}
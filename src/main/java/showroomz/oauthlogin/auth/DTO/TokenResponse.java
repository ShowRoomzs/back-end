package showroomz.oauthlogin.auth.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TokenResponse {
    private String tokenType = "Bearer";
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpiresIn;
    private Long refreshTokenExpiresIn;
    private Boolean isNewMember;
    private String registerToken; // 신규 회원일 때만 제공 (5분 유효)

    // 기존 회원용 생성자
    public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn, long refreshTokenExpiresIn, boolean isNewMember) {
        this.tokenType = "Bearer";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
        this.isNewMember = isNewMember;
        this.registerToken = null;
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
    }
}
package showroomz.oauthlogin.auth.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private String tokenType = "Bearer";
    private String accessToken;
    private String refreshToken;
    private long accessTokenExpiresIn;
    private boolean isNewMember;

    public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn, boolean isNewMember) {
        this.tokenType = "Bearer";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.isNewMember = isNewMember;
    }
}
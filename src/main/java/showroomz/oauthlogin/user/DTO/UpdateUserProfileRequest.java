package showroomz.oauthlogin.user.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {
    private String nickname; // optional
    private String birthday; // optional (YYYY-MM-DD)
    private String gender; // optional (MALE, FEMALE)
    private String profileImageUrl; // optional
    private Boolean marketingAgree; // optional
}


package showroomz.user.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.auth.entity.ProviderType;
import showroomz.auth.entity.RoleType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String birthday;
    private String gender;
    private ProviderType providerType;
    private RoleType roleType;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private Boolean marketingAgree;
    // 팔로워/팔로잉 카운트
    private Long followerCount = 0L;  // 나를 팔로우하는 유저 수
    private Long followingCount = 0L; // 내가 팔로우하는 유저(또는 마켓) 수
}


package showroomz.api.admin.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.social.entity.SocialLoginPolicy;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "소셜 로그인 제공자별 활성 상태")
public class AdminSocialProviderStatusResponse {

    @Schema(description = "활성 여부", example = "true")
    private boolean active;

    @Schema(description = "상태 마지막 변경 시각 (정책 행이 없거나 아직 변경된 적이 없으면 null)")
    private LocalDateTime statusChangedAt;

    public static AdminSocialProviderStatusResponse from(SocialLoginPolicy policy) {
        return AdminSocialProviderStatusResponse.builder()
                .active(policy.isActive())
                .statusChangedAt(policy.getStatusChangedAt())
                .build();
    }
}

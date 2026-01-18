package showroomz.api.admin.social.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.docs.AdminSocialControllerDocs;
import showroomz.api.admin.social.service.SocialPolicyService;
import showroomz.api.app.auth.entity.ProviderType;

@RestController
@RequestMapping("/api/admin/social")
@RequiredArgsConstructor
public class AdminSocialController implements AdminSocialControllerDocs {

    private final SocialPolicyService socialPolicyService;

    @Override
    @PatchMapping("/{provider}/status")
    public ResponseEntity<String> updateSocialStatus(
            @PathVariable("provider") ProviderType providerType,
            @RequestParam boolean active) {
        
        socialPolicyService.updateProviderStatus(providerType, active);
        
        String statusText = active ? "활성화" : "일시 중단";
        return ResponseEntity.ok(providerType + " 로그인이 " + statusText + " 되었습니다.");
    }
}

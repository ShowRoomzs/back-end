package showroomz.api.admin.social.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import showroomz.api.admin.social.docs.AdminSocialControllerDocs;
import showroomz.api.admin.social.dto.AdminSocialProviderStatusResponse;
import showroomz.api.admin.social.service.SocialPolicyService;
import showroomz.api.app.auth.entity.ProviderType;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin/social")
@RequiredArgsConstructor
// [기획 제외] 보류된 기능 — 신규 작업/리팩터링 대상 아님. 상세: CLAUDE.md
@Hidden
public class AdminSocialController implements AdminSocialControllerDocs {

    private final SocialPolicyService socialPolicyService;

    @Override
    @GetMapping("/status")
    public ResponseEntity<Map<String, AdminSocialProviderStatusResponse>> getAllSocialStatuses() {
        Map<String, AdminSocialProviderStatusResponse> statuses = socialPolicyService.getAllProviderStatuses();
        return ResponseEntity.ok(statuses);
    }

    @Override
    @PatchMapping("/{provider}/status")
    public ResponseEntity<Map<String, String>> updateSocialStatus(
            @PathVariable("provider") ProviderType providerType,
            @RequestParam("active") boolean active) {
        
        socialPolicyService.updateProviderStatus(providerType, active);
        
        String statusText = active ? "활성화" : "일시 중단";
        String message = providerType + " 로그인이 " + statusText + " 되었습니다.";
        return ResponseEntity.ok(Map.of("message", message));
    }
}

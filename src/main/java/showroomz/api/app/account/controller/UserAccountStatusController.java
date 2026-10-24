package showroomz.api.app.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.account.docs.UserAccountStatusControllerDocs;
import showroomz.api.app.account.dto.AccountStatusResponse;
import showroomz.api.app.account.service.UserAccountStatusService;
import showroomz.api.app.auth.entity.UserPrincipal;

@RestController
@RequestMapping("/v1/user/account-status")
@RequiredArgsConstructor
public class UserAccountStatusController implements UserAccountStatusControllerDocs {

    private final UserAccountStatusService accountStatusService;

    @Override
    @GetMapping
    public ResponseEntity<AccountStatusResponse> getAccountStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(accountStatusService.getStatus(userId(principal)));
    }

    @Override
    @PostMapping("/suspension-notice/ack")
    public ResponseEntity<Void> ackSuspensionNotice(@AuthenticationPrincipal UserPrincipal principal) {
        accountStatusService.ackSuspensionNotice(userId(principal));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/release-notice/ack")
    public ResponseEntity<Void> ackReleaseNotice(@AuthenticationPrincipal UserPrincipal principal) {
        accountStatusService.ackReleaseNotice(userId(principal));
        return ResponseEntity.noContent().build();
    }

    private static Long userId(UserPrincipal principal) {
        return principal == null ? null : principal.getUserId();
    }
}

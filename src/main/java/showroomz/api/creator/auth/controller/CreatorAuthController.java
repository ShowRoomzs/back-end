package showroomz.api.creator.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;
import showroomz.api.creator.auth.docs.CreatorAuthControllerDocs;
import showroomz.api.creator.auth.service.CreatorAuthService;

@RestController
@RequestMapping("/v1/creator/auth")
@RequiredArgsConstructor
public class CreatorAuthController implements CreatorAuthControllerDocs {

    private final CreatorAuthService creatorAuthService;

    @Override
    @PostMapping("/complete-registration")
    public ResponseEntity<TokenResponse> completeRegistration(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreatorCompleteRegistrationRequest registrationRequest) {

        TokenResponse tokenResponse = creatorAuthService.completeRegistration(
                userPrincipal.getUserId(),
                registrationRequest
        );
        return ResponseEntity.ok(tokenResponse);
    }
}

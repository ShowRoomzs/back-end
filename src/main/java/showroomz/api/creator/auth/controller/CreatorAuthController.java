package showroomz.api.creator.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.DTO.SocialLoginRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;
import showroomz.api.creator.auth.docs.CreatorAuthControllerDocs;
import showroomz.api.creator.auth.service.CreatorAuthService;
import showroomz.global.utils.HeaderUtil;

@RestController
@RequestMapping("/v1/creator/auth")
@RequiredArgsConstructor
public class CreatorAuthController implements CreatorAuthControllerDocs {

    private final CreatorAuthService creatorAuthService;

    @Override
    @PostMapping("/social/login")
    public ResponseEntity<TokenResponse> socialLogin(
            HttpServletRequest request,
            @Valid @RequestBody SocialLoginRequest socialLoginRequest) {

        TokenResponse tokenResponse = creatorAuthService.socialLogin(request, socialLoginRequest);
        return ResponseEntity.ok(tokenResponse);
    }

    @Override
    @PostMapping("/complete-registration")
    public ResponseEntity<TokenResponse> completeRegistration(
            HttpServletRequest request,
            @Valid @RequestBody CreatorCompleteRegistrationRequest registrationRequest) {

        String registerToken = HeaderUtil.getAccessToken(request);
        TokenResponse tokenResponse = creatorAuthService.completeRegistration(registerToken, registrationRequest);
        return ResponseEntity.ok(tokenResponse);
    }
}

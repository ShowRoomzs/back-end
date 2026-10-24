package showroomz.api.app.auth.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.DTO.IdentityVerificationCompleteRequest;
import showroomz.api.app.auth.DTO.IdentityVerificationCompleteResponse;
import showroomz.api.app.auth.DTO.IdentityVerificationStartRequest;
import showroomz.api.app.auth.DTO.IdentityVerificationStartResponse;
import showroomz.api.app.auth.docs.IdentityVerificationControllerDocs;
import showroomz.api.app.auth.service.IdentityVerificationService;
import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.HeaderUtil;

import java.time.LocalDateTime;

/**
 * 포트원 KCP 본인인증 시작 · 완료(57 설계서 3절). 가입(GUEST · registerToken)과 재인증(USER · accessToken)이 같이 쓴다.
 *
 * <p>registerToken 은 role 클레임이 없어 인증 필터가 SecurityContext 를 채우지 않는다 — 그래서 경로는 {@code SecurityConfig}
 * 화이트리스트에 있고, 여기서 {@code AuthController.register()} 와 같은 식으로 토큰을 직접 검증해 username 을 꺼낸다(13절 #5).
 * 토큰이 없거나 만료면 401 {@code REGISTER_EXPIRED}(가입) 가 아니라 공통 {@code UNAUTHORIZED_ACCESS} 다 — 어느 토큰인지 모른다.
 */
@RestController
@RequestMapping("/v1/user/identity-verifications")
@RequiredArgsConstructor
public class IdentityVerificationController implements IdentityVerificationControllerDocs {

    private final IdentityVerificationService identityVerificationService;
    private final AuthTokenProvider tokenProvider;

    @Override
    @PostMapping
    public ResponseEntity<IdentityVerificationStartResponse> start(HttpServletRequest request,
                                                                   @RequestBody @Valid IdentityVerificationStartRequest body) {
        String username = usernameOf(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(identityVerificationService.start(username, body.getPurpose(), LocalDateTime.now()));
    }

    @Override
    @PostMapping("/{identityVerificationId}/complete")
    public ResponseEntity<IdentityVerificationCompleteResponse> complete(HttpServletRequest request,
                                                                         @PathVariable String identityVerificationId,
                                                                         @RequestBody(required = false) @Valid IdentityVerificationCompleteRequest body) {
        String username = usernameOf(request);
        return ResponseEntity.ok(identityVerificationService.complete(username, identityVerificationId, body, LocalDateTime.now()));
    }

    private String usernameOf(HttpServletRequest request) {
        String tokenStr = HeaderUtil.getAccessToken(request);
        if (tokenStr == null || tokenStr.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        AuthToken token = tokenProvider.convertAuthToken(tokenStr);
        if (!token.validate()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        Claims claims = token.getTokenClaims();
        if (claims == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return claims.getSubject();
    }
}

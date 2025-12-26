package showroomz.oauthlogin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import showroomz.oauthlogin.auth.DTO.ErrorResponse;
import showroomz.oauthlogin.user.DTO.NicknameCheckResponse;
import showroomz.oauthlogin.user.DTO.UserProfileResponse;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 정보 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(
            summary = "현재 로그인한 사용자 정보 조회",
            description = "프로필 카드에 표시될 현재 로그인한 사용자의 정보(닉네임, 이메일, 프로필 이미지 등)를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음, 만료, 위조)"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    public ResponseEntity<?> getCurrentUser() {
        try {
            // 1. SecurityContext에서 현재 인증된 사용자 정보 가져오기
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            
            if (principal == null || !(principal instanceof User)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "인증 정보가 유효하지 않습니다. 다시 로그인해주세요."));
            }

            User springUser = (User) principal;
            String username = springUser.getUsername();

            // 2. 사용자 정보 조회
            Users user = userService.getUser(username)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "존재하지 않는 회원입니다."
                    ));

            // 3. UserProfileResponse로 변환
            UserProfileResponse response = new UserProfileResponse(
                    user.getUserId(), // id
                    user.getEmail(),
                    user.getNickname(),
                    user.getProfileImageUrl(),
                    user.getBirthday(),
                    user.getGender(),
                    user.getProviderType(),
                    user.getRoleType(),
                    user.getCreatedAt(),
                    user.getModifiedAt(),
                    null // marketingAgree 필드가 Users 엔티티에 없음 (추가 필요 시 Users 엔티티에 필드 추가)
            );

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("USER_NOT_FOUND", e.getReason()));
            }
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("UNAUTHORIZED", "인증 정보가 유효하지 않습니다. 다시 로그인해주세요."));
        }
    }

    @GetMapping
    public Users getUser() {
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Users user = userService.getUser(principal.getUsername())
        		.orElseThrow(() -> new RuntimeException("User not found"));

        return user;
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam String nickname) {
        NicknameCheckResponse response = userService.checkNickname(nickname);
        return ResponseEntity.ok(response);
    }
}

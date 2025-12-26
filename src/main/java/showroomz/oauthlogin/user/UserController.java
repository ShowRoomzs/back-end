package showroomz.oauthlogin.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.oauthlogin.user.DTO.NicknameCheckResponse;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public User getUser() {
        org.springframework.security.core.userdetails.User principal = (org.springframework.security.core.userdetails.User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User user = userService.getUser(principal.getUsername())
        		.orElseThrow(() -> new RuntimeException("User not found"));

        return user;
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam String nickname) {
        NicknameCheckResponse response = userService.checkNickname(nickname);
        return ResponseEntity.ok(response);
    }
}

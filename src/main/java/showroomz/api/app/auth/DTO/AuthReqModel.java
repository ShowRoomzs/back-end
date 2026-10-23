package showroomz.api.app.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthReqModel {
    @Schema(description = "로그인 ID — 로컬 회원가입의 `username`", example = "testuser01")
    private String id;
    @Schema(description = "비밀번호", example = "password123!")
    private String password;
}

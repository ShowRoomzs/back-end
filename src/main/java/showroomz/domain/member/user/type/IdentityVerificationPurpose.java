package showroomz.domain.member.user.type;

/** 본인인증 용도(57 설계서 0-4) — 가입(C0-2)과 재인증(C15-2)은 같은 API 를 쓰고 소비 지점이 다르다. */
public enum IdentityVerificationPurpose {
    SIGNUP,
    REVERIFY
}

package showroomz.api.app.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import showroomz.domain.member.user.entity.Users;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
@Setter
@RequiredArgsConstructor
// [변경] OAuth2User, OidcUser 인터페이스 제거
public class UserPrincipal implements UserDetails {
    private final Long userId;
    private final String username;
    private final String password;
    private final ProviderType providerType;
    private final RoleType roleType;
    // GrantedAuthority를 구현한 모든 타입 허용
    private final Collection<? extends GrantedAuthority> authorities;
    // [삭제] private Map<String, Object> attributes;

    // [삭제] getAttributes(), getClaims(), getUserInfo(), getIdToken() 등 OAuth2 관련 메서드 전체 삭제

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }

    public static UserPrincipal create(Users user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getProviderType(),
                RoleType.USER,
                Collections.singletonList(new SimpleGrantedAuthority(RoleType.USER.getCode()))
        );
    }

    /**
     * 토큰 기반 생성을 위한 편의 생성자.
     * - password, providerType은 토큰 인증 시 사용하지 않으므로 기본값으로 설정합니다.
     */
    public UserPrincipal(Long userId, String username, RoleType roleType, Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, "", null, roleType, authorities);
    }

    // [삭제] create(User user, Map<String, Object> attributes) 메서드 삭제
}
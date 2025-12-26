package showroomz.oauthlogin.oauth.entity;

import showroomz.oauthlogin.user.Users;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
    private final String username;
    private final String password;
    private final ProviderType providerType;
    private final RoleType roleType;
    private final Collection<GrantedAuthority> authorities;
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
                user.getUsername(),
                user.getPassword(),
                user.getProviderType(),
                RoleType.USER,
                Collections.singletonList(new SimpleGrantedAuthority(RoleType.USER.getCode()))
        );
    }

    // [삭제] create(User user, Map<String, Object> attributes) 메서드 삭제
}
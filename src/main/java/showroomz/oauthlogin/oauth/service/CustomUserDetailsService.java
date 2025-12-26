package showroomz.oauthlogin.oauth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import showroomz.oauthlogin.auth.UserRepository;
import showroomz.oauthlogin.oauth.entity.UserPrincipal;
import showroomz.oauthlogin.user.Users;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userRepository.findByUsername(username)
        		.orElseThrow(() -> new RuntimeException("User not found"));
        if (user == null) {
            throw new UsernameNotFoundException("Can not find username.");
        }
        return UserPrincipal.create(user);
    }
}

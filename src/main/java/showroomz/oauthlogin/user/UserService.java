package showroomz.oauthlogin.user;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

import org.springframework.stereotype.Service;

import showroomz.oauthlogin.auth.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public Optional<User> getUser(String userId) {
        return userRepository.findByUserId(userId);
    }
}

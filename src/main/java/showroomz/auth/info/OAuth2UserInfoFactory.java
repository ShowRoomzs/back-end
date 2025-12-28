package showroomz.auth.info;

import java.util.Map;

import showroomz.auth.entity.ProviderType;
import showroomz.auth.info.impl.AppleOAuth2UserInfo;
import showroomz.auth.info.impl.FacebookOAuth2UserInfo;
import showroomz.auth.info.impl.GoogleOAuth2UserInfo;
import showroomz.auth.info.impl.KakaoOAuth2UserInfo;
import showroomz.auth.info.impl.NaverOAuth2UserInfo;

public class OAuth2UserInfoFactory {
    public static OAuth2UserInfo getOAuth2UserInfo(ProviderType providerType, Map<String, Object> attributes) {
        switch (providerType) {
            case GOOGLE: return new GoogleOAuth2UserInfo(attributes);
            case FACEBOOK: return new FacebookOAuth2UserInfo(attributes);
            case NAVER: return new NaverOAuth2UserInfo(attributes);
            case KAKAO: return new KakaoOAuth2UserInfo(attributes);
            case APPLE: return new AppleOAuth2UserInfo(attributes);
            default: throw new IllegalArgumentException("Invalid Provider Type.");
        }
    }
}

package showroomz.oauthlogin.oauth.info;

import java.util.Map;

import showroomz.oauthlogin.oauth.entity.ProviderType;
import showroomz.oauthlogin.oauth.info.impl.FacebookOAuth2UserInfo;
import showroomz.oauthlogin.oauth.info.impl.GoogleOAuth2UserInfo;
import showroomz.oauthlogin.oauth.info.impl.KakaoOAuth2UserInfo;
import showroomz.oauthlogin.oauth.info.impl.NaverOAuth2UserInfo;
import showroomz.oauthlogin.oauth.info.impl.AppleOAuth2UserInfo;

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

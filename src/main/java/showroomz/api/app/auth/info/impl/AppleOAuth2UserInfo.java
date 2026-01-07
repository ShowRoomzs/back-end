package showroomz.api.app.auth.info.impl;

import java.util.Map;

import showroomz.api.app.auth.info.OAuth2UserInfo;

public class AppleOAuth2UserInfo extends OAuth2UserInfo {

    public AppleOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getName() {
        // Apple은 최초 동의 시에만 name을 내려주므로 없을 수 있음
        Object name = attributes.get("name");
        return name != null ? name.toString() : null;
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getImageUrl() {
        // 기본적으로 프로필 이미지를 제공하지 않음
        return null;
    }
}


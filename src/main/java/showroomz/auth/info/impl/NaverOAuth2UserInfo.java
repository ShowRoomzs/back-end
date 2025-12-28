package showroomz.auth.info.impl;

import java.util.Map;

import showroomz.auth.info.OAuth2UserInfo;

public class NaverOAuth2UserInfo extends OAuth2UserInfo {
    
    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getId() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            return null;
        }

        return (String) response.get("id");
    }

    @Override
    public String getName() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            return null;
        }

        return (String) response.get("nickname");
    }

    @Override
    public String getEmail() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            return null;
        }

        return (String) response.get("email");
    }

    @Override
    public String getImageUrl() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        if (response == null) {
            return null;
        }

        String profileImage = (String) response.get("profile_image");
        
        // 네이버 기본 프로필 이미지는 null로 저장 (URL이 정확히 일치하거나 포함되는 경우)
        if (profileImage != null) {
            // 정확한 URL 매칭 또는 경로 부분 매칭
            if (profileImage.equals("https://ssl.pstatic.net/static/pwe/address/img_profile.png") ||
                profileImage.equals("http://ssl.pstatic.net/static/pwe/address/img_profile.png") ||
                profileImage.contains("/static/pwe/address/img_profile.png")) {
                return null;
            }
        }

        return profileImage;
    }
}

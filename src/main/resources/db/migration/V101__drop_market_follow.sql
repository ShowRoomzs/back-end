/*
  마켓(브랜드) 팔로우 폐지 — 팔로우 대상은 쇼룸(creator_follow)뿐이다.

  - 마켓 팔로우/언팔로우/팔로우 목록 API가 삭제되어 market_follow는 더 이상 쓰이지 않는다.
  - 쇼룸 팔로우는 creator_follow 테이블(V6x 계열에서 생성)로 일원화된다.
  - V71에서 쇼룸(CREATOR) 소유 market과 그 팔로우 행은 이미 정리되었으므로
    여기 남은 데이터는 모두 브랜드(SELLER) 마켓 팔로우이며 이관 대상이 아니다.
*/

DROP TABLE IF EXISTS market_follow;

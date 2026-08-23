package showroomz.domain.notification.repository;

/**
 * 발송 대상 한 줄 — 토큰 문자열과 그 행의 id.
 *
 * <p>엔티티를 통째로 꺼내지 않는 이유는 팔로워가 수만 명인 쇼룸 때문이다. 발송에 필요한 것은
 * 토큰 문자열뿐인데 엔티티를 로드하면 {@code Users}까지 영속성 컨텍스트에 쌓인다.
 *
 * <p>id를 함께 꺼내는 것은 <b>키셋 페이징</b> 때문이다. 발송 도중 만료 토큰을 지우므로
 * OFFSET 페이징을 쓰면 삭제된 만큼 뒤 페이지가 앞으로 당겨져 <b>대상이 건너뛰어진다.</b>
 */
public record DeviceTokenRow(Long id, String token) {
}

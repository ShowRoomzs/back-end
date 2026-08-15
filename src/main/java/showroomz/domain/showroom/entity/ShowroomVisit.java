package showroomz.domain.showroom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.showroom.type.ShowroomVisitSource;

import java.time.LocalDateTime;

/**
 * §22-4 쇼룸 방문 로그 — 행 1건이 순방문 1회다.
 *
 * <p>"같은 소비자의 재방문은 30분 세션 기준 1회"라는 규칙을 집계가 아니라 <b>적재 시점</b>에 적용한다.
 * 30분 안에 같은 {@code visitorKey}가 다시 들어오면 행을 만들지 않으므로, 이후 집계는 단순해진다 —
 * 행 수가 순방문이고, {@code visitorKey}의 중복 제거 개수가 방문자 수다.
 *
 * <p>개인 단위 정보는 어떤 지표 카드에도 노출하지 않는다(§22-4). {@code user}를 들고 있는 이유는
 * 팔로워 행동(재방문율·방문자 중 팔로워 비중)을 <b>집계</b>하기 위해서지 방문자를 나열하기 위해서가 아니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "SHOWROOM_VISIT")
public class ShowroomVisit {

    /** §22-4 순방문 판정 세션 길이. */
    public static final int SESSION_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VISIT_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATOR_ID", nullable = false)
    private Creator creator;

    /** 로그인 방문에만 채워진다. 비로그인 방문은 사람 수만 세고 누구인지는 알지 못한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID")
    private Users user;

    @Column(name = "VISITOR_KEY", nullable = false, length = 64)
    private String visitorKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE", nullable = false, length = 20)
    private ShowroomVisitSource source;

    @Column(name = "VISITED_AT", nullable = false)
    private LocalDateTime visitedAt;

    public ShowroomVisit(Creator creator, Users user, String visitorKey,
                         ShowroomVisitSource source, LocalDateTime visitedAt) {
        this.creator = creator;
        this.user = user;
        this.visitorKey = visitorKey;
        this.source = source;
        this.visitedAt = visitedAt;
    }
}

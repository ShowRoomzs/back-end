package showroomz.global.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.global.utils.ConnectionCodeGenerator;

import java.util.List;

/**
 * §7 리스크 3(연결코드 열거) 대응 — V91 마이그레이션이 기존 크리에이터의 연결코드를
 * `CONCAT('SZ', LPAD(CREATOR_ID, 8, '0'))`로 채웠다. 이 값은 `SZ00000001`, `SZ00000002` …로
 * <b>순차 열거가 가능</b>해서, 크리에이터 수 N에 대해 N번 시도만으로 전수 조사가 된다.
 *
 * <p>신규 발급 경로(`ConnectionCodeGenerator`, 32자 알파벳 10자리 ≈ 1.1×10^15)는 안전하므로
 * <b>백필분만</b> 같은 생성기로 다시 발급한다.
 *
 * <p>Flyway SQL이 아니라 기동 훅으로 처리하는 이유: MySQL만으로 32진 랜덤 문자열을 만들고
 * 유니크 충돌까지 재시도하려면 복잡해지는데, 이미 검증된 생성기가 애플리케이션에 있다.
 *
 * <p>멱등하다 — 1회 실행 후에는 대상이 0건이고, `CONNECTION_CODE` 유니크 인덱스 덕분에
 * 이후 기동에서의 비용은 인덱스 스캔 한 번이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyConnectionCodeReissuer implements ApplicationRunner {

    private final CreatorRepository creatorRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Long> targetIds = creatorRepository.findIdsWithLegacyConnectionCode();
        if (targetIds.isEmpty()) {
            return;
        }

        for (Creator creator : creatorRepository.findAllById(targetIds)) {
            creator.reissueConnectionCode(
                    ConnectionCodeGenerator.generateUnique(creatorRepository::existsByConnectionCode));
        }
        log.info("열거 가능한 연결코드(V91 백필분) {}건을 랜덤 코드로 재발급했습니다.", targetIds.size());
    }
}

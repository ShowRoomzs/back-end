package showroomz.domain.changerequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;

import java.time.Year;

/**
 * 변경 요청 코드 {@code CHG-{YYYY}-{4자리}} — 브랜드(파트너센터) · 인플루언서(스튜디오) 요청이 같은 순번을 쓴다(§16-1 · 16 설계서 2-4).
 * 이미 쓰인 번호면 다음 번호로 넘어간다. 완전한 동시 접수 경합은 {@code uk_brand_change_request_code} 유니크가 마지막으로 막는다.
 */
@Component
@RequiredArgsConstructor
public class ChangeRequestCodeGenerator {

    private final ChangeRequestRepository repository;

    public String next() {
        String prefix = "CHG-" + Year.now().getValue() + "-";
        long sequence = repository.countByRequestCodeStartingWith(prefix) + 1;
        String code = format(prefix, sequence);
        while (repository.countByRequestCode(code) > 0) {
            code = format(prefix, ++sequence);
        }
        return code;
    }

    private static String format(String prefix, long sequence) {
        return prefix + String.format("%04d", sequence);
    }
}

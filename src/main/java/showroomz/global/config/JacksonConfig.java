package showroomz.global.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.TimeZone;

/**
 * 시각 입출력 규약(58 계획 3-2) — 서버 시각({@code LocalDateTime} · 운영 UTC)을 그대로 저장 · 비교하고, 응답은 전부 {@code …Z} 다.
 * 입력은 {@code +09:00} 같은 오프셋이 있으면 그 순간을 서버 시각으로 환산하고, {@code Z} 이거나 오프셋이 없으면 서버 시각 그대로 받는다.
 */
@Configuration
public class JacksonConfig {

    // 원하시는 포맷 (맨 뒤에 'Z' 문자 포함)
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // 1. LocalDateTime을 직렬화(JSON 변환)할 때 사용할 포맷 지정
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
            builder.serializers(new LocalDateTimeSerializer(formatter));

            // 2. 입력 시각 — 오프셋이 있으면 서버 시각으로 환산(기본 역직렬화는 Z 를 떼고 읽고 +09:00 은 거부한다)
            builder.deserializerByType(LocalDateTime.class, new OffsetAwareLocalDateTimeDeserializer());

            // 3. 기본 TimeZone을 UTC로 명시 (Date, ZonedDateTime 등의 타입에 영향)
            builder.timeZone(TimeZone.getTimeZone("UTC"));

            // 4. 날짜를 타임스탬프(숫자)가 아닌 문자열(String)로 출력하도록 설정
            builder.featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    /**
     * {@code 2026-09-01T10:00:00+09:00} → 같은 순간의 서버 시각. {@code 2026-09-01T01:00:00Z} · {@code 2026-09-01T01:00:00} → 그대로.
     * 문자열이 아닌 입력(숫자 배열 등)은 기본 역직렬화에 맡긴다.
     *
     * <p>{@code Z} 는 응답 직렬화와 대칭으로 떼기만 한다 — 응답의 {@code Z} 는 「서버 벽시계」 표시라 운영(UTC JVM)에서는 곧 UTC 이고,
     * KST 로 도는 로컬에서도 내려받은 값을 그대로 다시 보내면 같은 시각이 된다.
     */
    static final class OffsetAwareLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return LocalDateTimeDeserializer.INSTANCE.deserialize(parser, context);
            }
            String text = parser.getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            if (text.endsWith("Z") || text.endsWith("z")) {
                text = text.substring(0, text.length() - 1);
            }
            try {
                TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(text, OffsetDateTime::from,
                        LocalDateTime::from);
                if (parsed instanceof OffsetDateTime offset) {
                    return LocalDateTime.ofInstant(offset.toInstant(), ZoneId.systemDefault());
                }
                return (LocalDateTime) parsed;
            } catch (DateTimeParseException e) {
                return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, text,
                        "ISO 시각이 아닙니다 — 예: 2026-09-01T01:00:00Z");
            }
        }

        @Override
        public Class<?> handledType() {
            return LocalDateTime.class;
        }
    }
}

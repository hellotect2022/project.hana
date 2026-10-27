package com.mpole.hana.digitaltwin.global.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 응답 시각 형식 — 예 2026-10-21 11:26:21 (KST, 오프셋 표기 없음) */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        // OffsetDateTime 응답을 한국 시간으로 내보낸다.
        // DB(timestamptz)에서 읽은 값은 UTC(+00:00) 오프셋을 달고 오기 때문에, 포맷만 하면 UTC 시각이 찍힌다.
        // 같은 순간(instant)을 KST 로 옮긴 뒤 포맷한다(2026-10-21).
        return builder -> builder.serializerByType(OffsetDateTime.class, new KstOffsetDateTimeSerializer());
    }

    /** OffsetDateTime → 같은 순간의 KST 시각 문자열 */
    static final class KstOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.atZoneSameInstant(KST).format(FORMATTER));
        }

        @Override
        public Class<OffsetDateTime> handledType() {
            return OffsetDateTime.class;
        }
    }
}

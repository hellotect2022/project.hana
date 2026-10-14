package com.mpole.hana.digitaltwin.global.config;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * enum 컬럼에 CHECK 제약을 생성하지 않는 PostgreSQL 방언.
 *
 * <p>Hibernate 는 {@code @Enumerated(EnumType.STRING)} 컬럼에 대해
 * {@code check (col in ('A','B',...))} 제약을 자동 생성한다.
 * 이후 enum 에 값을 추가해도 {@code ddl-auto: update} 는 기존 제약을 갱신하지 않아
 * INSERT 가 제약 위반으로 실패한다. (예: property_type='TOILET_BOOTH')
 * 제약 생성 자체를 막아 이 문제를 원천 차단한다.
 *
 * <p>차단 지점은 {@code EnumJavaType#getCheckCondition} 이 호출하는
 * {@code Dialect#getCheckCondition(String, Class)} 이며, 이 메서드가 null 을 반환하면
 * 제약이 생성되지 않는다. (동일 메서드가 converter 사용 시에도 null 을 반환하므로
 * 호출부는 null 을 정상 케이스로 처리한다.)
 *
 * <p><b>주의:</b> Java 는 생성자를 상속하지 않으므로 아래 두 생성자를 반드시 명시한다.
 * {@link DialectResolutionInfo} 생성자가 없으면 실제 서버 버전 감지와 키워드 등록이
 * 생략되고 최소 지원 버전(PostgreSQL 12)으로 고정된다.
 *
 * <p>Spring 빈이 아니다. {@code spring.jpa.properties.hibernate.dialect} 에 FQCN 으로
 * 지정하면 Hibernate 가 리플렉션으로 직접 생성한다.
 */
public class NoEnumCheckPostgreSQLDialect extends PostgreSQLDialect {

    public NoEnumCheckPostgreSQLDialect() {
        super();
    }

    public NoEnumCheckPostgreSQLDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    public String getCheckCondition(String columnName, Class<? extends Enum<?>> enumType) {
        return null;
    }
}

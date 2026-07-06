# database-migration

## 목적
멀티 DB(Postgres/MSSQL/H2) 환경에서 스키마/마이그레이션/시드를 안전하게 관리한다.

## 사용 시점
테이블/인덱스/제약 변경, 마이그레이션·시드 작성 시. database-agent 전용.

## 적용 대상
`**/src/main/resources/db/`, `hana-digitaltwin/scripts/`, SQL 스크립트.

## 절차
1. 영향 받는 테이블과 이를 사용하는 모듈(JPA: digitaltwin / R2DBC: gateway·event)을 식별.
2. 전/후 호환을 고려해 마이그레이션을 단계적으로 설계(추가 → 백필 → 전환 → 정리).
3. DB별 문법 차이(Postgres/MSSQL)와 H2(테스트) 호환을 점검.
4. 롤백 스크립트/절차를 함께 작성.
5. 엔티티/R2DBC 매핑 변경이 필요하면 spring-backend-agent에 변경 요청서 전달.

## 체크리스트
- [ ] 사용 모듈 모두 식별(JPA/R2DBC)
- [ ] DB별 호환(Postgres/MSSQL/H2) 확인
- [ ] 파괴적 변경은 HIGH 위험도 + 롤백 방안
- [ ] 시드/참조 데이터 멱등성
- [ ] 접속정보는 yml(devops-agent) 영역 — 침범 금지

## 금지 사항
- 엔티티(Java) 직접 수정
- application.yml 수정
- 승인 없는 파괴적 스키마 변경

## 출력 형식
스키마 변경 요약 + 영향 테이블/모듈 + 마이그레이션·롤백 절차 + 위험도.

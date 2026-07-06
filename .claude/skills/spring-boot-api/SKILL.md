# spring-boot-api

## 목적
Hana 백엔드의 Spring Boot API를 일관된 구조로 작성/수정한다.

## 사용 시점
Controller/Service/Repository/DTO 추가·수정, 도메인 로직 작업 시. spring-backend-agent 전용.

## 적용 대상
- 블로킹: `hana-digitaltwin` (Spring MVC + JPA, Security/JWT, Kafka, AMQP)
- 리액티브: `hana-gateway`, `hana-event` (WebFlux + R2DBC, reactor-kafka)
- 공통: `hana-common`

## 절차
1. 도메인 패키지(device/location/sop/iam/event/realtime/... )를 확인하고 기존 구조를 따른다.
2. 계층: Controller(API) → Service(비즈니스) → Repository(영속). DTO로 경계 분리.
3. 입력 검증(`@Valid`/Bean Validation), 예외는 도메인 예외 + 핸들러로 처리.
4. springdoc/Swagger 어노테이션으로 API 문서화.
5. 프론트 `services/<domain>Service.ts` 계약과 일치하는지 확인.
6. 검증: `gradlew :hana-digitaltwin:test` 등.

## 체크리스트
- [ ] 리액티브 모듈에서 블로킹(JPA/RestTemplate/Thread.sleep) 호출 없음
- [ ] 트랜잭션 경계(@Transactional) 적절
- [ ] DTO ↔ Entity 변환 분리, Entity 직접 노출 금지
- [ ] 검증/예외 처리 존재
- [ ] 공통 로직은 hana-common
- [ ] 프론트 API 계약 일치
- [ ] Swagger 어노테이션 작성

## 금지 사항
- WebFlux 모듈에 블로킹 코드 혼용
- 빌드 파일/yml 직접 수정(→ devops-agent + 승인)
- 엔티티 외 DB 스키마 직접 변경(→ database-agent)
- 승인 없이 기존 코드 수정

## 출력 형식
변경 파일 목록 + 계층별 요약 + 프론트 계약 영향 + QA 요청 항목.

# approval-gate

## 목적
모든 코드/설정/의존성/빌드 변경 전 사용자 승인을 강제한다.

## 사용 시점
어떤 AGENT든 기존 코드·설정·DB·빌드 파일을 수정하려 할 때, 수정 직전.

## 적용 대상
전체 프로젝트의 기존 파일 수정. (운영 체계 파일 `.claude/`·`docs/`·`qa/`·`CLAUDE.md`
신규 생성은 게이트 면제.)

## 절차
1. 수정 필요성을 분석한다.
2. `report-writing`의 "코드 수정 승인 요청서" 양식으로 작성해 `qa/change-requests/`에 저장.
3. 위험도(LOW/MEDIUM/HIGH)와 미수정 시 문제를 명시한다.
4. 사용자에게 승인/보류/방향변경을 요청한다.
5. **명확한 승인**을 받은 후에만 담당 AGENT가 자기 소유 영역만 수정한다.
6. 수정 후 작업 보고서 작성 → QA 재검증.

## 수정 금지 파일 (승인 필수)
```
.env / .env.*
build.gradle / settings.gradle / gradle.properties / gradle/ / gradlew*
**/application*.yml
Dockerfile / docker-compose.yml / .github/
package.json / package-lock.json / tsconfig.json / vite.config.ts (프론트)
```

## 체크리스트
- [ ] 승인 요청서를 작성했는가
- [ ] 위험도를 표기했는가
- [ ] 사용자 승인을 명확히 받았는가
- [ ] 담당 AGENT 소유 영역만 수정하는가

## 금지 사항
- 승인 전 코드/설정 수정
- "사소하니까" 승인 생략
- 한 영역 승인을 다른 영역에 확대 적용

## 출력 형식
코드 수정 승인 요청서 → (승인 후) 수정 → 작업 보고서.

# 코드 수정 승인 요청서

## 1. 수정 필요 여부
필요 (백엔드 + 프론트)

## 2. 문제 요약
사용자 관리 페이지에 (1) 권한그룹별 사용자 조회 필터, (2) 권한그룹·최종로그인 컬럼 표시,
(3) 표준 Pagination 적용이 요구됨. 그러나:
- 백엔드 `UserResponse`에 role 정보 없음.
- `/api/users`는 active/loginId/username만 필터, **roleId 필터 미지원**.
- User↔Role은 **다대다(tbl_user_role)** — 한 사용자가 여러 권한 가능(목업은 1개 표시).
- 최종로그인(`lastLoginDate`)·Pagination은 이미 지원/연결됨.

## 3. 영향 범위
- server(iam/user: DTO·service·repository·controller)
- client(05-06: UserManagePage, userService)
- DB 변경 없음(기존 tbl_user_role 조인만)

## 4. 수정 대상 파일
### 백엔드 (spring-backend-agent)
- `iam/user/dto/UserResponse.java` — `List<RoleBrief> roles`(roleId, roleName) 추가
- `iam/user/repository/UserRepository.java` — roleId 필터(UserRole 조인) + 페이지 사용자들의
  역할 일괄 조회 쿼리(N+1 방지)
- `iam/user/service/UserService.java` — roleId 파라미터, 역할 배치 로딩 후 매핑
- `iam/user/api/UserController.java` — `@RequestParam(required=false) Long roleId`
### 프론트 (react-frontend-agent)
- `pages/users/UserManagePage.tsx` — 권한그룹 필터 Select(전체+roles), 컬럼(이름/권한그룹/
  최종로그인) 추가, 디자인시스템 프리미티브(Toolbar/Select/Input/Badge) 적용
- `services/userService.ts` — fetchUsersAPI 주석에 roleId 추가
- roles 드롭다운은 기존 `roleService.fetchRolesList()` 재사용

## 5. 수정 방향
- UserResponse.roles = 사용자의 전체 역할 목록(다대다 반영).
- 필터: roleId 지정 시 해당 역할을 가진 사용자만(UserRole 조인, DISTINCT).
- N+1 방지: 페이지 사용자 id 목록으로 UserRole을 한 번에 조회해 메모리 매핑.
- 프론트: 권한그룹 컬럼은 역할 배지(Badge tone="info") 1개 이상 표시, 없으면 "—".
- Pagination: 이미 표준 `components/common/Pagination` 사용 중 → 유지(요구사항 충족).

## 6. 위험도
- LOW. DB 스키마 변경 없음(기존 조인). 응답에 roles 필드 가산.
- 단, getUsers 쿼리에 조인 추가 → 페이징 DISTINCT 동작 확인 필요.

## 7. 미수정 시 문제
- 권한별 조회·권한그룹 표시 불가(요구 기능 미충족).

## 8. 승인 요청 (+ 설계 결정)
- 백엔드 변경 진행: 승인/보류
- 권한그룹 표시: (A) 사용자의 **모든 역할 배지 표시**(다대다 정확, 권장) /
  (B) 첫 역할 1개만 표시(목업과 동일하나 정보 손실)

@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ==========================================
:: 1. 고정할 기본 파일 이름 정의
:: ==========================================
set "JAR_NAME=hana-event-0.0.1-SNAPSHOT.jar"
set "LOG_CONFIG=logback-spring.xml"
set "CONFIG_NAME=event-application"
set "SERVICE_NAME=이벤트 서버"

echo ===================================================
echo  [정보] 서버 실행 환경 검증 및 준비 중...
echo ===================================================

:: 자바 실행 옵션을 누적할 변수 초기화
set "JAVA_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8"
set "APP_ARGS="

:: 1. JAR 파일 체크 (없으면 즉시 에러 종료하여 NSSM에 알림)
if exist "%JAR_NAME%" (
    echo [확인] JAR 파일 발견: %JAR_NAME%
) else (
    echo [에러] 실행할 필수 JAR 파일이 없습니다: %JAR_NAME%
    exit /b 1
)

:: 2. Logback 파일 체크
if exist "%LOG_CONFIG%" (
    echo [확인] Logback 설정 파일 발견: %LOG_CONFIG% (적용 완료)
    set "JAVA_OPTS=%JAVA_OPTS% -Dlogging.config=./%LOG_CONFIG%"
) else (
    echo [알림] 외부 Logback 파일이 없습니다. (JAR 내부 설정 사용)
)

:: 3. 스프링 설정 파일 체크 (properties, yml, yaml 순차 확인)
set "CONFIG_FOUND=fallback"
if exist "%CONFIG_NAME%.properties" set "CONFIG_FOUND=%CONFIG_NAME%.properties"
if exist "%CONFIG_NAME%.yml" set "CONFIG_FOUND=%CONFIG_NAME%.yml"
if exist "%CONFIG_NAME%.yaml" set "CONFIG_FOUND=%CONFIG_NAME%.yaml"

if not "!CONFIG_FOUND!"=="fallback" (
    echo [확인] 스프링 설정 파일 발견: !CONFIG_FOUND! (적용 완료)
    set "APP_ARGS=%APP_ARGS% --spring.config.name=%CONFIG_NAME%"
) else (
    echo [알림] 외부 스프링 설정 파일이 없습니다. (JAR 내부 설정 사용)
)

echo ===================================================
echo [정보] %SERVICE_NAME%를 시작합니다...
echo [명령] java %JAVA_OPTS% -jar "%JAR_NAME%"%APP_ARGS%
echo ===================================================

:: 최종 완성된 명령어 동적 실행 (call 제거하고 바로 java 실행)
java %JAVA_OPTS% -jar "%JAR_NAME%"%APP_ARGS%

:: 자바가 종료되면 그 에러코드를 그대로 윈도우(NSSM)에 반환합니다.
:: 만약 에러(비정상 종료)가 나면 NSSM이 이를 감지하고 설정된 주기에 따라 안전하게 재시작해 줍니다.
exit /b %ERRORLEVEL%
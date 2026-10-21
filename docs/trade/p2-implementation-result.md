# P2 A단계 구현 결과

## 기준

- 시작 브랜치: `feat/chat-api-namespace`
- 시작 HEAD: `48097bc`
- 정식 게시글·내부 이전 운영 모듈: 시작 시점 미구현
- 구현 범위: 구매 요청 생성·목록·상세·취소·거절·수락, 예약 원자성, pending DDL, 테스트 전용 PostgreSQL 연동

## 구현 상태

| 항목 | 상태 |
|---|---|
| 업무 모델·Service·여섯 HTTP API | 구현 |
| JDBC 구매 요청 저장·조회·잠금 | 구현 |
| 동일 방 PENDING·게시글 RESERVED DB 제약 | 구현 |
| 기본 기능 플래그 off | 구현 |
| 실제 PostgreSQL DDL·커밋·롤백·동시성 | 전용 fixture로 검증 |
| 실제 Spring 서버·Bearer JWT·HTTP 생성 | 전용 fixture로 검증 |
| 정식 게시글 운영 연동 | 미연결, B단계 대기 |
| 정식 내부 이전 운영 Writer | 미연결, B단계 대기 |
| pending Migration 공유 DB 적용 | 미적용 |
| 쿠키 인증·CSRF 최종 계약 | 미검증, B단계 대기 |

## PostgreSQL 검증

- 제품: PostgreSQL 17.11 (`postgres:17-alpine` 로컬 임시 컨테이너)
- 전용 DB: 합성 데이터만 사용
- 스키마: 테스트마다 `p2_trade_<random>`, `p2_trade_http_<random>` 생성 후 제거
- 기본 Flyway 6개 적용 후 외부 fixture를 만들고 pending 거래 Migration 1개를 추가 적용
- 실제 저장소: `JdbcPurchaseRequestRepository`
- 게시글·내부 이전: 같은 PostgreSQL·같은 트랜잭션의 테스트 전용 JDBC 어댑터

확인한 결과:

- 수락 시 요청 ACCEPTED/RESERVED, 게시글 RESERVED, INTERNAL/PENDING 이전 1건이 함께 커밋됨
- 내부 이전 삽입 뒤 강제 예외 시 요청 PENDING, 게시글 ACTIVE로 복원되고 이전 행이 남지 않음
- 서로 다른 두 요청의 동시 수락은 성공 1건·충돌 1건이며 예약과 내부 이전이 각각 1건만 남음
- 잘못된 상태 조합과 동일 방 중복 PENDING 직접 삽입을 PostgreSQL 제약이 거부함
- 실제 임의 포트 Spring 서버에 Bearer JWT로 POST 요청 시 201과 PENDING 응답을 받고 DB 커밋을 재조회함
- 거래 신규 테스트 15건이 실행되어 실패·오류·skipped 없이 통과함
- `spotlessCheck test build --rerun-tasks --no-build-cache` 전체 검사가 PostgreSQL 전용 테스트를 포함해 성공함

## 실행 방법

일반 검사는 PostgreSQL 전용 테스트를 조건부 건너뛴다.

```powershell
.\gradlew.bat spotlessCheck test build --rerun-tasks --no-build-cache
```

PostgreSQL 전용 검사는 다음 환경변수를 설정하고 실행한다.

```powershell
$env:P2_TRADE_TEST_ENABLED='true'
$env:P2_TRADE_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55432/p2_trade_test'
$env:P2_TRADE_TEST_DB_USER='p2_trade'
$env:P2_TRADE_TEST_DB_PASSWORD='<local-only password>'
.\gradlew.bat test --tests 'org.example.springtestci.trade.*' --rerun-tasks --no-build-cache
```

비밀번호와 토큰 값은 문서·로그에 기록하지 않는다. PostgreSQL 설정이 없으면 전용 테스트는 일반 검사에서 skipped이며 H2로 대체 성공하지 않는다.

## B단계 대기

- 최신 전체 API 계약의 URI·DTO·성공 코드 재합의
- 정식 게시글 조회·잠금·예약 변경 연결과 채팅 `listing_id` 정합성
- 정식 내부 이전 Writer 및 진행 중 이전 중복 제약
- pending Migration 버전·선행 Migration·공유 DB 편입 승인
- HttpOnly 쿠키, 로그아웃·갱신, CSRF 최종 인증 경로
- 예약·완료 시각 저장 원천과 후속 P3~P5 상태 전이

UI, 실제 소유자 변경, 거래 완료, 72시간 만료, 결제·배송, 배포는 이번 A단계 범위가 아니다.

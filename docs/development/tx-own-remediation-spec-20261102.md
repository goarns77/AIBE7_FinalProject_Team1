# 채팅 거래와 소유권 이전 보강 작업 명세서

작성일: 2026-11-02 KST

실행 연결: `fix/tx-own-remediation-20261102`에서 통합 기준 B에 구현했다. 최신 API·배포 계약은 [구현 계약](tx-own-remediation-api.md), 실제 실행·미실행 범위는 [검수 보고서](../reports/tx-own-remediation-implementation-20261102.md)를 따른다. 아래 설계안과 최초 기준 기록은 검수 추적을 위해 보존한다.

이 명세서는 [누락 항목 재검토 보고서](../reports/tx-own-gap-review-20261102.md)와 추가 전달된 작업 2·3·4·7을 구현 가능한 작업으로 구체화한다. 현재 MVP의 **수신자 수락 후 소유자 최종 승인**을 기본안으로 사용한다. 이미 연결된 내부 이전·시스템 이력·증빙 보호·초안 정리·감사는 유지하며, 누락된 권한·화면·승인 단계와 목록 제한을 보강한다.

상태: 구현 전 작업 명세. 이 문서 작성으로 팀의 미확정 정책이나 운영 환경 선택이 확정되는 것은 아니다. API·DTO·신규 컬럼 이름은 아래 기본 설계안이며, 구현 시 기존 명명 규칙에 맞춰 변경한다면 계약표와 시험을 함께 갱신한다.

## 1 기준 소스와 착수 조건

| 구분 | 경로와 고정 커밋 | 적용 |
| --- | --- | --- |
| 명세 저장 위치 | `C:/Workspace/AIBE7_FinalProject_Team1`, `0da56160a901f920d9f5469b0b41112b5c22dd1a` | 기존 보고서와 이 명세의 저장 위치. 이전 단계 코드여서 이곳에 바로 기능을 덧붙이지 않는다. |
| 구현 설계 기준 | `C:/Workspace/AIBE7_FinalProject_Team1-p5-main-20261102`, `b3e20f8188e112843a55704fdcbbe1098d6e68ad` | 실제 ownership/history·거래 UI가 연결된 통합본. 이하 B로 표기한다. |

작성 시 두 HEAD가 기존 보고서와 같은 것을 확인했다. B에는 추적 파일 변경이 없다. 원 검수자가 사용한 커밋과 실제 배포 커밋은 아직 확인되지 않았다.

착수자는 `git status --short`, `git rev-parse HEAD`, 대상 브랜치의 차이를 기록한다. 팀의 실제 통합 대상이 B와 다르면 관련 파일·요구사항·테스트를 먼저 대조해 이미 반영된 항목을 제외한다. 이 명세만으로 브랜치 생성·병합·push를 수행하지 않는다.

근거 문서는 B의 `docs/REQUIREMENTS.md`, `docs/MVP_SCOPE.md`, `docs/ERD.md`, `docs/INTERNAL_TRADE_OWNERSHIP_INTEGRATION.md`, `docs/HISTORY_PUBLIC_TEXT_CONTRACT.md`다. 루트 `REQUIREMENTS_SPECIFICATION.md`에는 일부 옛 표현이 남아 있으므로 동기화 대상이다.

이하 Java 경로는 `src/main/java/org/example/springtestci/`, JS 경로는 `src/main/resources/static/js/` 아래를 뜻한다. 테스트는 각각 `src/test/java/org/example/springtestci/`, `src/test/js/` 아래에 둔다.

## 2 작업 구성과 의존성

| ID | 원 작업 | 결과물 | 선행 조건 |
| --- | --- | --- | --- |
| G0 | 공통 | 대상 커밋·정책 결정 기록·DB 적용 계획 | 전체 착수 전 |
| W2A | 작업 2 | 인증된 수신자만 이전 요약 조회, 로그인 복귀 유지 | G0 소스 확정 |
| W2B | 작업 2 | 수신자 수락 저장 및 소유자 최종 승인 | G0 승인 정책·대기 상태 정책 확정, W2A 계약 |
| W3 | 작업 3 | 실제 문서 다운로드의 소유권·파일 버전 검사 | G0 소스 확정. 운영 어댑터는 저장소 결정 필요 |
| W4 | 작업 4 | 구매자 PENDING 취소와 당사자 취소 내역 | G0 소스 확정 |
| W7A | 작업 7 | 제품 목록 서버 페이지 구분·조회 상한 및 모든 소비 화면 수정 | G0 소스 확정 |
| W7B | 작업 7 | 요구사항 사본·상태 용어·설정과 migration 계약 정리 | 정책·API·DDL 결정 반영 |
| W7C | 작업 7 | PostgreSQL·브라우저·운영·성능 인수 결과 | 관련 구현과 환경 준비 완료 |

권장 실행 순서는 G0 → W2A/W3/W4/W7A → W2B → W7B → W7C다. 정책 결정 대기 중에도 W2A·W3의 공통 경로·W4·W7A는 진행할 수 있다. 배포 단위는 서버 API·DTO와 이를 사용하는 화면을 함께 묶는다.

## 3 결정 사항과 추가 검수 지점

| ID | 현재 기준과 기본 제안 | 확정 또는 추가 검수 | 영향 |
| --- | --- | --- | --- |
| D1 외부 승인 순서 | 현행 MVP를 따라 수신자 수락 후 소유자 최종 승인 | 팀이 발급을 승인으로 간주하기로 이미 합의했다면 근거와 요구사항 변경을 기록 | W2B의 업무 흐름. 대안 합의가 확인되지 않으면 현행 MVP대로 별도 최종 승인 전까지 이전을 완료하지 않는다. |
| D2 승인 대기 중 종료 | 제안: 발급 시점의 24시간 만료를 유지하고 최종 완료 전 소유자 취소 허용 | 현행 ‘수락 전 취소’ 문구와 새 승인 대기의 관계, 수락 후 재발급 허용 여부를 확정 | W2B의 만료·취소·재발급과 문서. 이 제안을 확정 정책으로 간주하지 않는다. |
| D3 상태 용어 | 제안: DB/API의 최종 `COMPLETED`, 외부 유형 `DIRECT` 유지, 문서에 대응 명시 | `ACCEPTED`는 구매 요청 수락과 혼동하지 않도록 문서 의미를 정리 | 이름 변경만을 위한 데이터 변환은 기본 범위에서 제외. DB값 변경 선택 시 별도 신규 migration 필요. |
| D4 운영 저장소 | 인증된 앱 다운로드 경로를 공통으로 사용 | 현재 선택된 운영 저장소·배포 방식·서비스 자격 증명 공급 방식 확인 | W3 운영 연결. `.env`에 Supabase 관련 키가 있다는 사실만으로 파일 저장소를 Supabase로 정하지 않는다. |
| D5 배포 DB 이력 | 일반 migration 후 pending P2/P3 적용 순서가 필요 | 실제 대상 DB의 적용 버전·checksum·테이블을 읽기 검수 | W2B migration 및 W7C. 공유 DB repair/clean으로 해결하지 않는다. |
| D6 운영 인수 조건 | 실제 브라우저·환경·부하 조건을 결과에 고정 | 실행할 시험 환경과 운영 담당자의 로그/AI 데이터 정책 확인 | 환경 인수 완료 여부. 단위 테스트 통과로 대체하지 않는다. |

보고서만으로 운영 저장소·승인 대기 정책·배포 DB 상태를 확정할 근거는 부족하다. 이 부분은 구현 전 또는 해당 인수 단계에서 추가 검수가 필요하다. 명세 작성과 독립적인 보강 작업을 막는 사유는 아니다.

## 4 공통 구현 규칙

- Controller → Service → Repository를 유지하고 응답은 DTO로 제공한다. 인증 사용자 ID를 서버에서 해석하며 클라이언트의 수행자 ID를 신뢰하지 않는다.
- 기존 잠금 순서는 제품 → 판매글 → 구매 요청 → 이전 요청을 기준으로 한다. 경로에 없는 테이블은 생략한다. 새 흐름이 기존 경로와 역순 잠금을 만들지 않는지 확인한다.
- 이전 확정은 소유자·facts 증가·TRANSFER·PRIVATE·증빙 보호·초안·감사까지 같은 트랜잭션이다. 동기 초안 정리를 AFTER_COMMIT으로 옮기지 않는다.
- 내부 거래의 양측 최신 확인·미확인 변경 차단·기존 완료 재요청 동작을 유지한다. 외부 최종 승인 절차를 내부 거래에 중복 추가하지 않는다.
- 모든 변경은 요청 단위의 권한·상태 재검사와 실패 응답을 포함한다. 중복·경합·응답 유실을 UI와 서버에서 각각 다룬다.
- 새 상태·컬럼·제약은 신규 Flyway migration으로 추가한다. 기존 적용 migration을 수정하지 않는다. H2 통과만으로 PostgreSQL의 잠금·커밋·DDL 검증을 대신하지 않는다.
- chat/trade 변경 클래스와 주요 메소드에는 AGENTS.md의 주석 기준을 적용하고 `docs/development/chat-trade-code-guide.md`를 갱신한다.

## 5 W2A 수신자 요약 권한

관련 요구사항: OWN-04, SEC-02. 기존 진입점은 `ownership/controller/OwnershipTransferController.java:54`, `ownership/service/OwnershipTransferService.java:158`, `common/security/SecurityConfig.java:122`, `receive.js`다.

### 조회 계약

기본안은 기존 GET `/api/v1/ownership/transfers/verify?token=...`를 인증 필수로 바꾸고 Service에 인증 사용자 ID를 전달하는 것이다. 비회원은 토큰을 서버에서 조회하기 전에 로그인 안내를 보며, 로그인 후 동일 토큰을 재검증한다.

| 요청자 또는 상태 | 결과 |
| --- | --- |
| 비회원 | 401. 제품 제목·발신자·제품/이전 식별자를 응답하지 않는다. |
| 지정 수신자와 다른 회원 | 기존 오류 체계에 따른 권한 거부. 제품 요약 없음. |
| 지정 수신자 | 유효성·현재 소유권·판매/예약 상태가 허용되면 제한 요약 제공. |
| 수신자 미지정 링크의 로그인 회원 | 유효 토큰과 기존 자격 조건을 만족하면 제한 요약 제공. 발신자 본인은 수락 불가. |
| 만료·취소·완료 또는 INTERNAL 토큰 | 수신자용 요약과 수락 불가. 상태 안내는 식별 정보 없는 최소 응답으로 제공. |
| W2B에서 이미 수락자가 확정된 요청 | 다른 회원의 조회·수락 차단. 확정 수락자는 승인 대기 상태를 재조회 가능. |

발신자의 제품·수신자 확인은 인증된 소유자용 API를 사용한다. `canAccept`는 요청자별 권한과 상태를 반영해야 한다. 조회는 소유권 변경이나 암묵적 수락을 수행하지 않는다.

### 화면과 개인정보

`receive.js`의 URL 토큰 제거, 메모리 내 로그인 복귀, `no-store`·`no-referrer`, 로그아웃/화면 종료 정리를 유지한다. 초기 로딩에서 인증 복원이 끝나기 전 잘못된 401 상태를 고정하지 않는다. 401이면 로그인 후 같은 요청을 다시 검사하며, 로그인 자체로 accept를 호출하지 않는다.

토큰·서명은 로그·오류·분석 이벤트에 기록하지 않는다. 브라우저 새로고침까지 토큰 보존을 확장할 필요는 없으며, 현재 복귀 보존시간을 넘으면 원 링크 재진입 안내를 제공한다. 다른 계정으로 로그인하면 새 계정 기준으로 검증한다.

### 완료 기준

W2A-01 익명 응답에 요약 필드가 없다. W2A-02 지정/미지정·발신자·제삼자 권한 표가 HTTP 시험에서 일치한다. W2A-03 만료·취소·완료 토큰으로 요약이나 수락을 우회하지 못한다. W2A-04 실제 브라우저에서 QR→비회원→로그인→동일 요청 복귀가 되고 자동 수락하지 않는다.

## 6 W2B 외부 이전 최종 승인

관련 요구사항: OWN-02·03·05·06. 기존 `OwnershipTransferService.acceptTransfer()`의 원자적 완료 효과를 소유자 최종 승인으로 옮긴다. 생성·verify·ownerStatus·취소·재발급과 `ownership.js`, `receive.js`를 함께 변경한다.

### 데이터와 상태 기본 설계

추가 상태 enum을 도입하기보다 **기존 PENDING 안에서 수락 전/후를 구분**하는 기본안을 사용한다. 기존 제품별 PENDING 슬롯과 이를 조회하는 내부 예약·판매 게시 제약을 유지하기 위한 선택이다.

| 논리 단계 | 저장 상태 | 수신자와 동의 정보 | 소유권 효과 |
| --- | --- | --- | --- |
| 수락 대기 | PENDING, pending_slot=1 | `to_user_id`는 기존 지정 수신자 또는 null, `recipient_accepted_at`은 null | 없음 |
| 소유자 승인 대기 | PENDING, pending_slot=1 | `to_user_id`를 실제 수락자로 고정, `recipient_accepted_at` 저장 | 없음 |
| 완료 | COMPLETED, pending_slot=null | 수락자·수락 시각 보존, completed_at 저장 | 기존 완료 효과 일괄 확정 |
| 취소/만료 | CANCELLED/EXPIRED, pending_slot=null | 이미 받은 동의 정보는 감사 목적으로 보존 | 없음 |

신규 nullable 컬럼 `recipient_accepted_at`을 migration으로 추가한다. 신규 승인 대기는 수신자 ID가 반드시 있어야 한다. INTERNAL은 기존 처리대로 이 컬럼 없이 완료 가능하다. 기존 완료 행에 가상의 수락 시각을 채우지 않는다. DTO에는 논리 단계와 실제 수락 시각을 명확히 제공한다.

모든 `status=PENDING` 조회를 점검한다. `OwnershipTransferService`, `InternalTradeOwnershipService`, `JdbcListingPublishConstraints`가 승인 대기도 유효 요청으로 취급해야 한다. 별도 `AWAITING_APPROVAL` enum을 선택한다면 슬롯 CHECK와 모든 조회 조건의 변경 목록부터 작성해야 하며 enum 추가만으로 마무리하지 않는다.

### API 기본 계약

| API | 변경 내용 |
| --- | --- |
| POST `/api/v1/ownership/transfers/accept` | 인증 수신자의 명시 동의만 저장. `newOwnerId/completedAt`을 성공처럼 반환하지 않고 `transferId`, 단계, `recipientAcceptedAt`, `expiresAt` 반환. |
| GET `/api/v1/ownership/transfers/product/{productId}` | 현재 소유자의 복구 조회에 승인 대기 여부·수락자 최소 식별 정보·제품 확인 정보 추가. 원문 토큰/해시 반환 금지. |
| POST `/api/v1/ownership/transfers/{id}/approve` | 신규. 현재 소유자가 선택한 요청·수락자·현재 제품을 확인한 뒤 최종 이전 확정. |
| GET `/api/v1/ownership/transfers/{id}` | 신규 상태 복구 경로. 원 발신자/확정 수락자에게 최소 처리 상태만 반환. 완료 후 전 소유자에게 제품 관리 정보·증빙을 제공하지 않음. |
| POST `/{id}/cancel` 및 재발급 | D2에서 확정한 승인 대기 취소/만료 규칙 적용. 내부 이전 ID의 외부 조작은 계속 거부. |

승인 본문에는 화면에서 확인한 `expectedRecipientId`와 `expectedFactsVersion`을 전달하는 기본안을 사용한다. 서버는 저장된 수락자·현재 제품 버전과 비교하며 이 값으로 대상자를 변경하지 않는다. 제품 정보가 바뀌면 재조회·재확인을 요구한다. 기존 완료 재요청은 최소 결과만 반환하고 후속 제품 변경을 이유로 이전을 반복하지 않는다.

### 수락과 승인 처리

수락: 토큰으로 제품 ID 식별 → 제품/판매글/이전 잠금 → DIRECT·유효기간·소유자·수신자·판매/예약 차단 재검사 → 실제 수락자와 시각 및 동의 감사 저장. 소유자/facts·TRANSFER·PRIVATE·증빙·초안은 변경하지 않는다. 같은 수신자의 반복 동의는 동일 결과를 반환하고 감사도 중복 생성하지 않는다. 다른 회원이 먼저 수락자를 바꿀 수 없다.

최종 승인: 인증된 현재 소유자와 발신자 일치 → 제품/판매글/이전 잠금 → DIRECT·PENDING·수락 존재·유효기간·저장 수신자·판매/예약 상태·확인 버전 재검사 → 소유자와 facts 변경 → 이전 COMPLETED → 실제 TRANSFER/PRIVATE → 증빙 보호 → 동기 초안 정리 → 감사 → 커밋. 모든 효과가 성공해야 최종 확정한다. 현재 소유자 검사는 잠금 이후 최신 값을 사용한다.

`DirectTransferGuard`는 현재 ACTIVE/RESERVED 판매글을 조회한다. 별도 예약 거래와 불일치한 데이터도 차단해야 하는지 G0에서 검수하고, 예약 존재 검사가 필요한 경우 실제 purchase_requests와 연결해 보강한다. 없는 테이블이나 포트 오류를 ‘예약 없음’으로 처리하지 않는다.

### 화면과 배포 호환성

수신자 화면은 ‘수락 완료·소유자 승인 대기’를 표시한다. 발신자 화면은 수락자와 제품 확인 후 승인 버튼을 제공한다. 발신자 발급 화면·수신자 화면·재조회 DTO·기존 성공 문구를 같은 배포에서 변경한다. 현재 `AcceptTransferResponse`의 즉시 완료 계약을 그대로 두면 화면이 잘못 완료 안내하므로 반드시 수정한다.

이미 완료된 외부 이전은 다시 열지 않는다. 기존 PENDING 링크는 새 정책하에서 수락→승인으로 처리한다. 이전 버전 앱과 새 앱을 동시에 운영하면 구 accept가 즉시 이전할 수 있으므로 외부 변경 API의 구 버전 트래픽을 종료한 뒤 새 경로를 활성화하는 전환 계획을 포함한다.

### 필수 시험

| ID | 사례와 기대 결과 |
| --- | --- |
| W2B-01 | 수락만 수행: 수락 정보/동의 감사만 추가되고 기존 소유자·facts·이력·증빙·초안은 유지. |
| W2B-02 | 실제 소유자 승인: 완료 효과가 정확히 한 번 발생. 다른 사용자·수신자·구 소유자의 승인 거부. |
| W2B-03 | 수락자/제품 버전이 확인값과 다름, 소유자 변경, 판매/예약 시작, 만료 정각: 승인 거부·전체 상태 보존. |
| W2B-04 | 같은 수신자 반복 수락, 다른 회원 동시 수락: 하나의 수락자만 확정·동의 감사 중복 없음. |
| W2B-05 | 승인 대기에서 새 DIRECT 발급·판매 게시·내부 예약 시도: 기존 유효 슬롯을 침범하지 않음. |
| W2B-06 | 승인/취소, 승인/만료 후 재발급, 승인/다른 예약 경합: 하나만 확정. D2 정책에 맞게 종료. |
| W2B-07 | 소유자 갱신·이력·증빙·초안·감사 및 지연 커밋 실패 주입: 승인 전 상태로 전체 롤백하고 재시도 가능. |
| W2B-08 | 완료 응답 유실과 반복 승인, 이후 재이전: 원 완료 결과만 복구·후속 데이터 변경 없음. |
| W2B-09 | 기존 INTERNAL 양측 완료·취소·버전 무효화 회귀: 기존 동작 유지. |

시험은 `OwnershipTransferServiceTest`, `OwnershipTransferControllerTest`, `DirectTransferPostgresTest`, `InternalTradeOwnershipHttpPostgresTest`에 우선 확장한다. HTTP 시험에는 실제 SecurityFilterChain을 포함해 인증/CSRF를 검증한다.

## 7 W3 문서 다운로드 권한과 사본 버전

관련 요구사항: DOC-07, HIST-08, OWN-06, SEC-02·03. 변경 중심은 `document/controller/DocumentController.java`, `document/service/DocumentService.java`, `document/repository/DocumentRepository.java`, `common/storage/LocalFileController.java`, `common/security/SecurityConfig.java`다.

### 제공 경로 기본안

기존 GET `/api/v1/documents/{documentId}/file-url` 응답 형태 `{url, expiresAt}`를 유지하되 URL은 **인증된 앱 파일 API**로 발급한다. 신규 GET `/api/v1/documents/{documentId}/file`을 제공하고, URL에 문서 버전·만료·서명을 포함한다. 서명은 문서 ID·파일 버전·발급 사용자·만료에 묶는다. object key는 클라이언트가 선택하지 못한다.

실제 GET에서 인증 사용자, 서명, `now < expiresAt`, 현재 소유자, 현재 문서 버전, 마스킹 확인 및 재마스킹 차단을 검사한다. 통과한 문서의 **현재 object key**를 Service에서 FileStorage에 전달한다. 다른 사용자는 유효 서명만 가지고도 다운로드할 수 없어야 한다. 정각 만료는 거부한다.

사용자는 `documents.js`와 `products.js`의 현재 열람 흐름을 유지할 수 있다. 동일 출처 인증 쿠키를 사용하고 Content-Type·`nosniff`·`Cache-Control: no-store`와 안전한 파일 이름을 제공한다. 브라우저가 직접 접근하는 URL을 로그인 필수로 바꾼 만큼 새 탭 열기와 인증 만료 동작도 실제 브라우저에서 확인한다.

### 기존 경로의 우회 차단

`/local-files?key=...`는 문서 저장 키에 대한 구 서명을 더 이상 받아들이지 않아야 한다. 키를 정규화해 인코딩/경로 변형도 차단한다. 단순히 화면에서 새 URL을 쓰는 것으로 종료하지 않는다. `FileStorage.createTemporaryUrl()`은 판매글 이미지 등 다른 호출자도 사용할 수 있으므로 전체를 삭제하거나 모두 비공개 처리하지 말고 호출자를 조사해 문서와 허용 이미지 경로를 명확하게 제한한다.

운영 저장소는 비공개 object를 서버의 `FileStorage.load()`로 읽어 같은 인증 경로로 전달한다. 별도 저장소 URL로 redirect하면 이후 직접 접근을 즉시 회수할 수 있는지 별도로 증명해야 한다. D4 결정 전에는 공통 앱 경로와 local/test 시험까지 완료할 수 있지만 운영 다운로드 인수 완료로 표시할 수 없다.

### 경합과 보호 유지

다운로드의 권한 판정·문서 버전 확인·파일 선택은 하나의 일관된 경계에서 수행한다. 잠금을 추가한다면 제품→문서 순서를 기본으로 하고, 현재 replace가 문서를 먼저 잠그는 점과 추출/삭제 경로를 검수해 역순 잠금을 없앤다. 네트워크 전송 전체 동안 DB 잠금을 잡지 않는다. 권한이 있을 때 이미 열린 전송의 회수와 이전 커밋 뒤 시작한 새 요청의 거부를 구분한다.

파일을 짧은 잠금 안에서 열거나 크기 제한 내 사본으로 확보한 뒤 전송하는 방식 등 구체적인 일관성 방식을 구현 결과에 기록한다. 소유권 이전/교체가 먼저 커밋된 요청에는 과거 권한·버전을 사용하지 않는다.

기존 `Document.replaceFile()`의 버전 증가, 문서 ID·이력 연결·업로더·protectedAt 유지와 `delete()`의 영구 보호 검사를 보존한다. 구 파일 삭제가 실패해도 버전/키 검사로 구 URL을 막는다. 파일 정리 실패를 전체 업무 커밋 성공과 혼동하지 않으며 정리 재시도는 운영 후처리로 관리한다.

### 필수 시험

| ID | 사례와 기대 결과 |
| --- | --- |
| W3-01 | 현재 소유자 정상 다운로드, 익명·제삼자·이전 소유자·서명 변조·만료 정각 거부. 응답 본문에 파일 바이트 없음. |
| W3-02 | 내부/외부 이전 전 발급 URL로 이전 커밋 후 GET: 거부. 새 소유자의 새 URL은 성공. |
| W3-03 | 구 `/local-files` 문서 URL 및 인코딩/경로 변형으로 우회 불가. 허용된 제품 이미지 제공은 회귀 없음. |
| W3-04 | 사본 교체 성공 후 구 URL 거부. 구 파일 삭제 실패를 주입해도 동일. 새 버전은 성공. |
| W3-05 | 교체 저장/커밋 실패: 기존 사본·버전·이력 연결·보호 유지. 재마스킹 필요 상태에서는 다운로드 거부. |
| W3-06 | A→B→A 재이전: 보호된 승계 사본 직접 삭제 거부. 재마스킹 뒤에도 보호 유지. |
| W3-07 | 다운로드와 이전/교체 경합: 정한 권한 판정 경계 준수, 역순 잠금·다른 제품 파일 반환 없음. |
| W3-08 | 실제 운영 저장소와 새 탭 다운로드: 동일 권한 계약, 저장소 직접 공개 우회 없음. |

Service 시험만으로 W3-02·03을 통과 처리하지 않는다. 실제 파일 HTTP 응답과 저장소 실패 주입을 포함한다.

## 8 W4 대기 요청 취소와 과거 취소 내역

관련 요구사항: TX-02·03·05·07. 중심 파일은 `deals.js`, `deals-completion.js`, `pages/deals.html`, 필요한 DTO와 `src/test/js/deals.test.mjs`, `completion.test.mjs`다.

### 구매자 대기 요청 취소

본인의 PENDING 요청에 ‘구매 요청 취소’를 제공하고 기존 POST `/api/v1/purchase-requests/{requestId}/cancel`을 호출한다. 처리 중 중복 클릭을 차단하고 성공/실패를 표시한다. 취소 자체로 판매글 상태를 바꾸지 않는다.

응답 유실은 성공이나 실패로 단정하지 않고 해당 요청을 GET으로 다시 확인한다. CANCELLED면 취소 결과, ACCEPTED/RESERVED면 예약 상태와 허용되는 예약 취소 조작, 여전히 PENDING이면 재시도 안내를 표시한다. 새 구매 요청을 자동 생성하지 않는다. 기존 cancel API의 반복 취소 충돌을 UI 재조회로 복구할 수 있으며 멱등성을 위해 과거 상태를 임의로 다시 변경하지 않는다.

### 내역 조회와 표시

현재 요청 선택은 진행 상태(PENDING 또는 RESERVED)를 우선하고 과거 취소/완료와 분리한다. 최근 100건만 받아 필터링하지 말고 기존 상태 필터와 페이지 API로 필요한 범위를 조회한다. SENT/RECEIVED 양쪽 응답을 합칠 경우 각각의 페이지와 중복 `requestId`를 관리한다.

GET `/api/v1/purchase-requests?direction=...&roomId=...&status=CANCELLED&page=...&size=...` 및 단건 GET을 재사용한다. 판매글 CANCELLED로 조작을 숨기더라도 내역 조회는 계속 허용한다. 새 요청 생성 후에도 이전 취소 기록은 독립된 requestId로 남는다.

| 내역 | 표시 계약 |
| --- | --- |
| PENDING 취소 | ‘구매 요청 취소’. transactionStatus와 취소 메타데이터가 null인 기존 계약 유지. 존재하지 않는 사유·시각을 현재 시간 등으로 만들어 표시하지 않는다. |
| RESERVED 취소 | 서버의 사유 enum을 문구로 변환하고 취소자 역할·처리 시각 표시. 원 기록을 변경하지 않는다. |
| 역할 판별 불가 | ‘확인 불가’ 등 중립 표시. 필요한 경우 서버가 원 방 당사자를 기준으로 역할 DTO를 추가한다. 사용자가 입력한 닉네임으로 추정하지 않는다. |

원 방의 판매자/문의자 권한과 종료 후 대화 접근을 보존한다. account/room 변경 시 요청을 취소하거나 응답을 버리고, 폴링이 이전 방의 취소 내역·확인 입력을 덮지 않게 한다. 사용자 문자열은 textContent로 표시한다.

완료 기준: W4-01 구매자만 대기 요청 취소, W4-02 수락 경합·응답 유실 복구, W4-03 양측의 일반 예약 취소/변경 CANCEL/판매 중지 내역 표시, W4-04 취소 후 재요청과 100건 초과 페이지 접근, W4-05 비당사자 조회/조작 차단, W4-06 빠른 방/계정 전환·중복 클릭·새로고침 후 상태 일치. 저장/API의 기존 회귀 시험과 실제 JS 모듈 시험을 함께 수행한다.

## 9 W7A 제품 목록 페이지 구분

관련 요구사항: NFR-01. B의 `ProductController.listMine()` → `ProductService.listMine()` → `ProductRepository.findAllOwned()`는 전체 소유 제품을 반환한다.

기본 계약은 GET `/api/v1/products?page=0&size=20`, 기본 size=20, 최대 size=100, page≥0, size≥1이다. 범위를 벗어난 값은 기존 validation 규칙에 맞춰 400으로 처리한다. 응답은 ApiResponse 안에 `{items, page:{number,size,totalElements,totalPages,hasNext}}`를 제공한다. 정렬은 `created_at DESC, id DESC`, 현재 소유자·미보관 조건은 유지한다.

LIMIT/OFFSET과 count를 서버에서 적용하고 페이지 내부 제품의 이미지 결합도 조회 수가 제품 수에 비례해 불필요하게 증가하지 않도록 확인한다. 전체 제품을 읽은 뒤 메모리에서 자르는 방식은 불가하다. 빈 목록, 마지막 페이지, 페이지 사이 등록/삭제/이전 시의 새로고침 정책을 정의한다.

이 변경은 배열 응답을 쓰는 소비자에 영향을 준다. 최소 수정 대상은 `products.js:49`, `documents.js:75`, `doc-mask.js:491`이다. 내 제품 목록뿐 아니라 문서 보관함과 마스킹 화면의 제품 선택도 모든 페이지에 접근할 수 있어야 한다. 목록을 자동으로 끝까지 순회해 처음에 전부 읽는 방식으로 페이지 목적을 무력화하지 않는다. 별도 `/api/v1/listing-draft-products`를 같은 API로 오인해 변경하지 않는다.

필수 시험: W7A-01 0/20/21/100건 및 최대 초과 입력, W7A-02 동일 시각 정렬과 다른 소유자 제외, W7A-03 내 제품·문서 보관함·마스킹 선택의 다음 페이지, W7A-04 등록/삭제/이전 후 현재 페이지 갱신, W7A-05 응답 상한과 DB 조회 수 확인. 기존 API 소비 테스트의 배열 가정도 함께 갱신한다.

## 10 W7B 문서와 환경 계약

### 요구사항 정리

`docs/REQUIREMENTS.md`의 자동 공개 필드 검사와 `HISTORY_PUBLIC_TEXT_CONTRACT.md`를 기준으로 루트 요구사항 사본을 동기화한다. 이는 증빙 사본 공개 허용이나 문서 마스킹 확인 생략이 아니다. D1·D2·D3 결정은 MVP, 요구사항 두 사본, ERD, ownership/trade API 문서, 화면 문구에 함께 반영한다.

각 항목에 결정일·결정 근거·적용 커밋을 남긴다. 과거 검증 기록은 당시 결과로 유지하고 최신 결과를 별도 추가한다.

### 실제 설정과 migration

시험/배포별 프로필, `listing-history`, `trade-purchase-requests`, `trade-p3`, `trade-p4`의 최종 유효값, 실제 등록된 Bean과 API를 확인한다. 기본값만 읽고 런타임 상태를 확정하지 않는다. 이력 Reader의 fallback 구현 여부도 확인한다.

기존 `docs/INTERNAL_TRADE_OWNERSHIP_INTEGRATION.md:43`에 따르면 빈 DB는 일반 migration 후 pending P2/P3 적용이 필요하다. 세 locations를 처음부터 합치면 P3가 참조하는 history 테이블 생성보다 먼저 실행될 수 있다. 신규 migration은 실제 의존성을 기준으로 버전을 정하고 빈 DB 및 기존 보존 DB 복제본에서 모두 검증한다. pending 통합 방식은 적용 이력과 팀 DB 계약을 확인한 후 별도 계획으로 결정한다.

W2B의 nullable 동의 컬럼은 기존 행과 INTERNAL을 허용한다. 구 버전 서버의 공존 문제는 nullable DDL만으로 해결되지 않으므로 API 전환 계획과 함께 배포한다. 실패 시 새 데이터를 임의 삭제하는 down migration을 실행하지 않고, 요청 차단·호환 버전 복구 등 실행 가능한 복구 절차를 기록한다.

`.env`의 비밀값을 명세·로그·시험 산출물에 복사하지 않는다. 공유 DB 주소나 자격 증명이 있다는 이유로 파괴적 시험을 실행하지 않는다. 전용 DB·스키마·Redis임을 확인한 시험 설정을 별도로 공급한다.

## 11 W7C 인수 시험과 증거

### 실행 단계

1. 변경 유스케이스의 JUnit/Mockito 및 Node 시험으로 권한·상태·UI 복구를 검증한다.
2. `spotlessCheck`, `test`, `build`를 실행한다. Java 17을 사용하고 UP-TO-DATE와 실제 재실행을 구분한다.
3. 전용 PostgreSQL에서 정식 ownership/history 포트, JPA/JDBC 트랜잭션, SQL·지연 커밋 실패와 동시 요청을 검증한다. 테스트 대역만으로 완료 효과를 통과 처리하지 않는다.
4. 실제 브라우저의 두 사용자 세션으로 내부 거래 및 외부 이전을 끝까지 수행하고 DB 결과와 비교한다.
5. 운영 저장소·메일·외부 AI·HTTPS·Secure 쿠키·CSRF·로그와 성능 조건을 환경별로 검증한다.

기존 `DirectTransferPostgresTest`, `InternalTradeOwnershipHttpPostgresTest`, P2/P3/P4 PostgreSQL 클래스는 환경 변수 조건으로 제외될 수 있다. `DirectTransferPostgresTest.p5A09BrowserFlow`는 외부 스크립트도 필요하므로 재실행 가능한 브라우저 스크립트·버전·실행 명령을 시험 자원에 포함하거나 접근 가능한 증거 위치를 명시한다.

### 시나리오 묶음

| ID | 필수 확인 |
| --- | --- |
| E2E-I | 채팅→구매 요청→수락→변경 확인→양측 최신 완료→소유자/SOLD/COMPLETED/TRANSFER/PRIVATE/증빙/초안 일치. |
| E2E-D | 발급→실제 QR→로그인 복귀→제한 요약→수신자 수락→발신자 최종 승인→동일 완료 효과. D1 변경 시 확정 정책에 맞게 갱신. |
| E2E-C | PENDING 취소, 예약 취소, 변경 CANCEL, 판매 중지, 재요청 후 과거 기록을 양측에서 재조회. |
| E2E-R | 재발급 취소 성공/발급 실패, 발급 성공/응답 유실, 동시 발급: 유효 요청 하나와 UI 복구를 확인. 단일 API 신설은 필수 아님. |
| E2E-F | 이전 전 URL·교체 전 URL·직접 저장소 URL·제삼자 다운로드 차단. 새 소유자는 정상 열람, 승계 문서는 재승계 후에도 삭제 제한. |
| E2E-X | 승인/취소·내부 완료/취소·내부 예약/외부 발급 경합과 SQL/커밋 실패에서 전체 롤백·중복 방지. |
| E2E-B | Chrome·Edge·Safari 및 모바일 실제 기기/OS 버전별 주요 흐름. 자동화 엔진 대체 시험과 실제 브라우저 시험을 구분. |

### 성능과 운영

성능 시험 기본 제안은 제품 총 10,000건·목록 대상 사용자 보유 1,000건·동시 사용자 20명·예열 60초·측정 300초다. 실제 목표 환경에 맞게 확정한 뒤 변경 없이 실행하고, 제품/판매글 목록·상세 각각의 표본 수·p50/p95/p99·실패율·DB 조회 수를 기록한다. 목표는 API별 p95≤1초이며 AI 처리·파일 전송은 별도로 측정한다. 이 숫자는 기존 측정 결과나 합의된 운영 용량이 아니다.

인증 메일은 발송·재발송·유효기간/인증 결과, 외부 AI는 성공·시간 초과·직접 입력 대안과 제공사 데이터 조건, HTTPS/쿠키/CSRF는 실제 배포 응답과 변조 요청을 확인한다. 로그 접근자·보관 기간·삭제 방식 및 제공사의 보관/학습 정책은 담당자의 확정 근거가 있어야 완료로 표시한다.

### 결과 형식

결과마다 대상 commit, dirty 여부, 실행 시각, JDK/DB/브라우저 버전, 프로필·기능 플래그, migration 버전, 시험 데이터와 명령, PASS/FAIL/SKIPPED/NOT_RUN, XML/화면 증거 위치를 남긴다. 토큰·문서 내용·실제 계정 비밀을 산출물에 넣지 않는다. 미실행/제외 항목은 이유와 다음 확인 조건을 적는다.

## 12 변경 묶음과 완료 판정

| 묶음 | 주요 파일 | 필수 산출물 |
| --- | --- | --- |
| 수신자 권한 | OwnershipTransferController/Service, SecurityConfig, VerifyTransferResponse, receive.js | 권한 표·HTTP/JS 시험·로그인 복귀 증거 |
| 외부 승인 | ownership Entity/Repository/DTO/Service, 신규 migration, ownership.js/receive.js | 상태 전이·승인 API·동의/완료 감사·PG 롤백/경합 시험·배포 호환 계획 |
| 파일 접근 | DocumentController/Service/Repository, LocalFileController, 서명 처리와 운영 어댑터, 문서 UI | 구 URL 차단·버전·재마스킹·HTTP 파일 시험·운영 연결 결과 |
| 거래 UI | deals.js, deals-completion.js, deals.html, 필요한 DTO | 취소 버튼·과거 내역·페이지·응답 유실/계정 전환 시험 |
| 제품 목록 | ProductController/Service/Repository/DTO, products.js/documents.js/doc-mask.js | 서버 조회 상한·모든 소비자 전환·페이지/조회 수 시험 |
| 계약과 인수 | 요구사항 두 사본, MVP, ERD, 코드 가이드, 인수 결과 문서 | 결정 기록·환경별 실행 증거·잔여 항목 |

기능별 완료는 해당 API·화면·migration·시험이 함께 통과했을 때 판정한다. 전체 완료는 D1~D6의 필요한 결정과 W7C 증거까지 충족해야 한다. 운영 환경이 준비되지 않은 경우 ‘구현 완료·운영 인수 미완료’로 범위를 적고 전체 완료로 합치지 않는다.

## 13 작성 시 검증과 인계

이번에는 기존 보고서, 추가 전달 체크리스트, B의 실제 DTO·Repository·파일 제공·UI 소비자와 migration 안내를 대조했다. 제품 목록 변경의 문서/마스킹 화면 의존성, 승인 상태와 기존 PENDING 슬롯의 연계, 구 파일 URL의 직접 다운로드 경로를 추가 검수했다. 애플리케이션 코드·DB·`.env`는 변경하지 않았다.

기존 보고서의 Java 1,044건 통과/134건 제외 및 Node 129건 통과는 이전 실행 결과다. 이번 명세 작성 중 애플리케이션 테스트는 재실행하지 않았다. 다음 구현자는 정책 D1·D2, 대상 통합 브랜치, 운영 저장소 D4, DB 적용 이력 D5를 우선 확인하되, 이미 반영된 내부 이전 후처리를 새로 구현하지 않는다.

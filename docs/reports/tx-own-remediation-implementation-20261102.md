# TX·OWN 보강 구현 및 타 에이전트 검수 보고서

작성일: 2026-11-02 KST. 판정: **기능 보강 구현·로컬 자동 검증 완료, PostgreSQL·실제 브라우저·운영 환경 인수 미완료**.

## 1 검수할 소스

| 항목 | 값 |
| --- | --- |
| 기준 커밋 | `b3e20f8188e112843a55704fdcbbe1098d6e68ad` |
| 구현 커밋 | `b4bc3dfaac4d76ca136165349c2748c469254584` |
| 개인 브랜치 | `fix/tx-own-remediation-20261102` |
| 구현 작업 폴더 | `C:/Workspace/AIBE7_FinalProject_Team1-tx-own-remediation-20261102` |
| 원격 대상 | 개인 저장소 `origin` = `goarns77/AIBE7_FinalProject_Team1` |
| 검수 비교 | `git diff b3e20f8188e112843a55704fdcbbe1098d6e68ad..b4bc3dfaac4d76ca136165349c2748c469254584` |

사용자가 커밋·개인 브랜치 푸시를 요청한 뒤 별도 worktree를 만들었다. 기존 기본 작업 폴더의 `feature/trade-p4-completion`과 통합 기준 폴더의 브랜치는 변경하지 않았다. main/upstream 병합·푸시, 공유 DB 변경, `.env` 수정·복사·비밀값 기록은 수행하지 않았다. 보고서 커밋은 위 구현 커밋 뒤에 문서만 추가한다.

이전 [검수 보고서](tx-own-gap-review-20261102.md)와 [작업 명세서](../development/tx-own-remediation-spec-20261102.md)는 최초 발견·설계 기록으로 함께 보관했다. 최신 동작은 [보강 API·배포 계약](../development/tx-own-remediation-api.md)을 기준으로 검수한다.

## 2 명세 대비 결과

| 작업 | 구현 결과 | 검증 범위·남은 조건 |
| --- | --- | --- |
| W2A 요약 권한 | verify 인증 필수, 지정·확정 수신자 검사 선행, 종료 요청의 제품·발신자 식별/요약 숨김, 로그인 복귀 유지 | 실제 HTTP SecurityFilterChain, 서비스·Node 시험 통과. 실제 QR/기기 로그인 복귀 인수는 미실행 |
| W2B 최종 승인 | accept는 동의만 저장. owner approve에서 소유권·facts·실제 TRANSFER·PRIVATE·문서 보호·초안·감사를 원자 확정 | 실제 HTTP/H2 승인·초안·실패 롤백·쿠키 CSRF 시험 통과. PG 경합·지연 커밋 실패는 미실행 |
| W3 다운로드 | 인증된 실제 파일 GET에서 소유권·사본 버전·서명·만료·재마스킹 재검사. 구 local 문서 URL 차단 | 실제 local 파일 HTTP·외부 이전 후 구 URL·교체 후 구 URL, 서비스 실패 주입 시험 통과. 운영 저장소 인수는 미실행 |
| W4 구매 취소·내역 | 구매자 PENDING 취소, 중복 클릭 차단·응답 유실 재조회, 방향별 CANCELLED 페이지·사유·역할·시각 표시 | 실제 deals 모듈 Node 시험 및 기존 Java 거래 회귀 통과. 실제 양측 브라우저 흐름은 미실행 |
| W7A 제품 페이지 | SQL LIMIT/OFFSET/count, 크기 상한, 안정 정렬, 페이지 사진 일괄 조회. 제품·문서·마스킹 선택기 함께 연결 | Repository·Service·HTTP·세 소비 화면 Node 시험 통과. 운영 조회 p95는 미측정 |
| W7B 문서·계약 | 요구사항 사본·MVP·ERD·초안/코드 가이드·API·배포 절차 갱신. 기존 migration 보존 | D2는 명세 기본안을 구현한 정책으로 팀 확인 필요. 실제 배포 DB·플래그·Bean·스토리지 확인은 미실행 |
| W7C 환경 인수 | 자동 검증 결과와 PG/브라우저 재검증용 시험 소스 정리 | 운영/브라우저/성능 인수 완료로 판정하지 않음 |

## 3 핵심 변경과 검수 포인트

### 외부 이전

- `OwnershipTransferService`, Controller, DTO, Entity, `ownership.js`, `receive.js`를 함께 변경했다.
- 동의 대기와 소유자 승인 대기 모두 DB `PENDING/pending_slot=1`을 유지한다. `recipient_accepted_at`의 유무로 구분하고 수락 시 `to_user_id`를 고정한다. 새 DB enum은 추가하지 않았다.
- `POST /{id}/approve`는 인증 발신자, 현재 소유자, DIRECT/PENDING, 동의 존재, 발급 후 만료, 판매 상태, 확인한 수신자·제품 버전을 재검사한다. 제품→판매글→이전 잠금 순서를 유지한다.
- 기존 완료 효과를 accept에서 approve로 옮겼다. 동기 `OwnershipTransferred` 초안 정리와 같은 트랜잭션의 문서 보호·이력·감사를 보존했다. 기존 INTERNAL 완료에 별도 외부 승인을 추가하지 않았다.
- 동일 수신자의 승인 전 동의 재시도와 동일 발신자의 완료 승인 재시도는 중복 이력·감사를 만들지 않는다. 완료 재시도는 최소 기존 결과만 반환한다.
- `GET /{id}`는 최초 발신자·확정 수신자의 최소 상태 복구 API다. 이전 소유자의 제품 상세·문서 접근을 허용하지 않는다.
- DB 저장 후 시각이 반올림되어 반복 승인 응답이 달라지는 문제를 실제 HTTP 시험에서 발견했다. 기록 시각을 마이크로초로 맞춰 첫 응답과 재시도 결과를 일치시켰다.
- verify의 익명 공개 예외를 SecurityConfig에서 제거했다. 지정 수신자 불일치는 제목·닉네임 조회 전에 거부한다. 유효하지 않은 상태는 제품·발신자 ID도 반환하지 않는다.
- 로그인 전에 요약을 요청하지 않고 메모리 토큰으로 로그인 후 재검증한다. 자동 수락하지 않는다. 계정 전환의 늦은 응답·로그아웃·페이지 종료를 무효화한다.

추가 검수: `DirectTransferGuard`의 ACTIVE/RESERVED 판매글을 기준으로 한 예약 차단은 기존 불변식을 유지한다. 레거시 DB에 RESERVED 구매 요청과 종료된 판매글이 불일치하는 데이터가 있는지는 이번에 확인하지 않았다. 배포 전 데이터 정합성 확인과 PG 경합 검수에 포함해야 한다.

### 문서 파일

- `DocumentUrlSigner`는 문서 ID·발급 사용자·사본 버전·만료를 HMAC으로 묶는다. 실제 GET에서 인증 사용자로 서명을 재계산한다.
- `DocumentService.download()`가 제품 ID만 먼저 읽고 제품→문서를 잠근 뒤 현재 소유자·버전·마스킹·재마스킹 상태를 검사한다. 저장소의 현재 object key만 사용한다.
- 파일을 잠금 안에서 메모리 바이트로 확보하고 메서드 반환 시 트랜잭션을 끝낸다. 이전/교체가 먼저 커밋된 새 GET은 구 권한·버전으로 읽을 수 없고, 이미 허용되어 확보된 파일의 네트워크 전송은 소급 회수하지 않는다.
- 교체·삭제·추출 접수·검토의 잠금도 제품→문서로 정렬해 문서→제품 역순을 피했다. 비동기 분석 처리 자체는 제품 잠금을 요청하지 않는 기존 경로다.
- `/local-files`는 정규형 제품 이미지 키만 허용한다. 구 문서 서명이 유효해도 거부한다. 경로 이동·인코딩 변형·역슬래시·절대 경로도 허용 키 형식에 맞지 않아 차단된다.
- 교체 후 구 파일 삭제가 실패해도 버전은 새 버전으로 남고 이전 URL은 차단된다. 업로더·보호·문서 ID를 유지하며 제품이 원 업로더에게 돌아와도 보호된 사본을 직접 삭제할 수 없다.

검수 경계: 운영 `FileStorage` 공급자는 정해져 있지 않다. 공통 서버 게이트웨이는 `FileStorage.load()`를 사용하며 저장소 없는 프로필의 기존 503 동작을 유지한다. 별도 공개 object URL이나 과거 운영 presigned URL의 회수는 운영 저장소 설정 없이는 증명할 수 없다.

### 거래 UI와 목록

- 대기 취소는 기존 API를 사용하고 취소/수락 경합·응답 유실 때 단건 재조회 후 화면을 갱신한다. 사용자 입력으로 취소자·사유·시각을 만들지 않는다.
- 예약 우선, 다음 PENDING, 다음 완료 요청 순으로 현재 조작을 고르고 취소 내역은 별도 영역에 둔다. 최근 100건을 받아 필터링하는 방식을 상태별 제한 조회·페이지로 바꿨다.
- 취소 내역 조회 실패가 현재 거래 조작 전체를 막지 않게 오류를 별도로 표시한다. 방 이동 때 이전 내역을 즉시 비우고 응답 세대를 검사한다.
- 제품 목록은 기본 20·최대 100이며 페이지 내 사진을 한 번에 조회한다. 현재 페이지 검색이라는 범위를 화면에 표시하고 전체 개수는 서버 totalElements로 표시한다.
- 별도 판매 초안 제품 선택 API의 배열 계약은 `listForDrafts()`로 보존했다. 이 별도 API의 페이지 구분은 후속 범위다.

## 4 정책·migration·배포

| 결정 | 이번 구현 | 추가 확인 |
| --- | --- | --- |
| D1 승인 순서 | 현행 MVP의 수신자 동의 → 소유자 최종 승인 | 발급 자체를 승인으로 간주한다는 대안 합의는 확인되지 않았음 |
| D2 대기 종료 | 발급 후 24시간 유지, 동의 후에도 승인 전 취소·재발급 허용 | 명세 기본안을 구현. 팀이 다른 정책을 확정하면 코드·문서·시험 동시 변경 필요 |
| D3 용어 | DB/API COMPLETED, 외부 DIRECT 유지. 문서 ACCEPTED/EXTERNAL 표기 동기화 | 구매 요청 ACCEPTED와 혼동하지 않을 것 |
| D4 저장소 | 인증된 공통 다운로드 경로 준비 | 실제 운영 private 저장소·서비스 자격 증명 공급·직접 공개 우회 여부 확인 필요 |
| D5 DB 이력 | 신규 nullable 컬럼과 CHECK migration만 추가 | 실제 배포 DB의 기존 버전·checksum·pending 준비 확인 필요 |
| D6 운영 인수 | 로컬 H2/HTTP·Java·Node 시험 실행 | 아래 미실행 목록과 정책 담당자 확인 필요 |

신규 파일: `src/main/resources/db/migration/V2026110201__direct_transfer_recipient_consent.sql`.

H2 빈 DB 전체 migration, 기존 canonical ownership 데이터 보존·컬럼/제약 확장 시험은 통과했다. 이전 migration의 checksum·내용·버전을 수정하지 않았다. 과거 COMPLETED/INTERNAL 행에 가짜 동의 값을 채우지 않는다.

일반 migration → 기존 pending P2/P3 적용 순서가 필요하다. 세 경로를 빈 DB에 처음부터 합치지 않는다. 외부 mutation 트래픽 중지 후 migration·호환 서버·UI를 함께 배포해야 한다. 구 accept가 즉시 이전하므로 신구 서버 혼용은 금지한다. 상세 중지·복구 절차는 API 계약 문서를 따른다.

## 5 실행한 검증

실행 환경: Windows PowerShell, Java toolchain 17, 실행 JAVA_HOME `C:/Users/GNDriver/.jdks/temurin-17.0.19`, Node 24.13.0. `.env`와 공유 DB에 연결하지 않는 기본 테스트 설정을 사용했다.

최종 실행 명령:

```powershell
$env:JAVA_HOME='C:/Users/GNDriver/.jdks/temurin-17.0.19'
./gradlew.bat spotlessApply spotlessCheck test build --no-daemon
node --test --test-timeout=10000 src/test/js/*.test.mjs
node --check src/test/browser/direct-transfer.cjs
git diff --check
```

| 검사 | 결과 |
| --- | --- |
| Spotless | PASS |
| Java 전체 | XML 120개 suite, 총 1,191건 = PASS 1,057 / SKIPPED 134 / FAIL 0 / ERROR 0 |
| Gradle build | PASS, 실제 test 실행 포함. compile/bootJar/jar/assemble/check/build 성공 |
| Node 전체 | PASS 139 / FAIL 0 / SKIPPED 0 |
| 브라우저 스크립트 | 문법 PASS. 브라우저 실행 PASS를 의미하지 않음 |
| diff 공백 검사 | PASS. Windows의 LF→CRLF 안내만 발생 |

중간 실행에서 변경 전 즉시 완료 계약을 전제로 한 통합 시험과 신규 컬럼을 고려하지 않은 schema 보존 비교가 실패했다. 해당 시험을 동의→승인, 승인 시 실패 주입·CSRF·경합, 기존 데이터/기존 제약 보존과 신규 컬럼 확장 검증으로 갱신했다. 실패 assertion을 제거하거나 시험을 비활성화하지 않았으며 최종 전체 실행은 실패 0건이다.

새 `RemediationHttpTest`는 실제 HTTP/SecurityFilterChain/H2/Flyway/local 파일을 사용한다. 익명 401·다른 수신자 403, 동의만으로 소유권 유지, 최종 승인 단일 효과·반복 결과, 외부 이전 후 구 URL 차단, 사본 교체 후 구 URL 차단, 보호 문서 회귀, 페이지 입력 상한을 확인했다.

기존 `ListingDraftOwnershipHttpTest`는 승인 단계의 실제 초안 삭제·삭제 실패·감사 실패 전체 롤백, 이전 소유자의 늦은 저장과 제품 잠금, 쿠키 CSRF를 확인한다. H2의 시험 결과이며 PG의 지연 커밋·잠금 의미를 대신하지 않는다.

Node에서 새로 확인한 10개 시나리오는 구매 취소/응답 유실·취소 내역 페이지/역할·종료 판매글, 비회원 로그인 복귀·동의 대기/중복 클릭·계정 전환의 늦은 요약·소유자 확인 본문, 제품/문서/마스킹 세 페이지 이동이다. DOM 대역 시험으로 실제 레이아웃·기기 입력을 검증하지 않는다.

원시 결과 위치(구현 worktree, Git에는 포함하지 않음): `build/test-results/test/TEST-*.xml`, `build/reports/tests/test/index.html`, `build/reports/problems/problems-report.html`.

## 6 미실행·잔여 인수

| 항목 | 상태·이유 | 다음 검수 조건 |
| --- | --- | --- |
| PG 내부/외부 경합·지연 커밋·SQL 실패·이전 후 권한 | NOT_RUN. 이 세션에서 Docker 데몬 연결 실패, 전용 PG/Redis 자격 증명 미공급 | loopback 전용 `sale_ai_test` 및 전용 Redis로 opt-in 통합 시험 실행 |
| `DirectTransferPostgresTest`·내부 이전/P2/P3/P4 PG 시험 | SKIPPED. 기존 환경 조건 미충족 | 갱신한 시험 소스는 컴파일 통과. 실제 동시 요청 성공 증거는 아직 없음 |
| Chrome·Edge·Safari·모바일·실제 QR | NOT_RUN | 수신자 동의 후 소유자 승인·새 다운로드 쿠키 동작·방 전환·취소 내역을 실제 기기에서 확인 |
| `direct-transfer.cjs` 실제 실행 | NOT_RUN. 시험용 실제 서버·PG·Redis 및 브라우저 도구 설정 미공급 | 기존 P5 UI 환경변수·loopback 서버·playwright-core/jsqr·브라우저 버전을 준비. 이번 스크립트는 로그인 요약/소유자 승인 단계에 맞춰 갱신됨 |
| 재발급 부분 실패/응답 유실·동시 발급 | 환경 인수 NOT_RUN | 취소 성공/발급 실패, 원문 토큰 유실, 재조회·재시도와 단일 슬롯을 PG·브라우저에서 검증 |
| 운영 FileStorage·메일·AI·HTTPS·Secure 쿠키·CSRF | NOT_RUN | 실제 배포 응답·공급자 연결·토큰/문서 직접 공개 우회·실패 처리를 확인 |
| 운영 DB migration/history·실제 플래그·Bean | NOT_RUN | 배포 대상에서 읽기 점검. 소스 기본값만으로 활성화 완료 판정 금지 |
| API p95 1초 성능 | NOT_MEASURED | 명세의 데이터 수·동시 사용자·측정 시간 조건을 확정한 뒤 API별 측정 |
| 로그 접근·보관·삭제, 외부 AI 데이터 처리 정책 | 미확정 | 운영 담당자의 정책 근거와 실제 설정 필요 |

제외 134건에는 PG뿐 아니라 live AI·Redis·환경 조건 시험도 포함된다. 제외된 시험을 통과로 집계하지 않았다. `.env`에 접속 정보가 있다는 이유로 공유 DB·외부 서비스에 시험 요청을 보내지 않았다.

## 7 독립 에이전트 재검수 요청

1. 위 기준 커밋과 구현 커밋의 diff를 검수하고, 현재 기본 작업 폴더의 오래된 브랜치와 혼동하지 않는다.
2. accept에 완료 효과가 남아 있지 않은지, approve에서 현재 소유권·수신자·facts·만료·판매 차단 및 기존 원자적 효과가 모두 유지되는지 확인한다.
3. 제품→판매글→구매 요청→이전 잠금과 제품→문서 잠금의 상호 작용을 PG에서 검수한다. 파일 바이트 확보·추출·교체·삭제의 역순 잠금과 커밋 경계를 특히 확인한다.
4. 유효한 구 문서 URL·교체 전 URL·다른 계정·레거시 local 경로·운영 저장소 직접 경로로 문서 바이트를 우회할 수 없는지 실제 HTTP로 확인한다.
5. 승인·취소·재발급·응답 유실 뒤 상태 복구와 비회원 QR→로그인→재검증, 두 계정/방 전환의 늦은 응답을 실제 브라우저에서 확인한다.
6. D2 정책과 운영 저장소/DB 준비를 팀·운영 근거로 확정하고 W7 미실행 항목을 별도 결과로 추가한다. 이 보고서의 로컬 자동 검증을 운영 인수 완료로 확대 해석하지 않는다.

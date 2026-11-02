# 채팅 거래와 소유권 이전 누락 항목 재검토 보고서

작성일: 2026-11-02 KST. 대상: 제공된 TX-01~07 및 OWN-01~06 평가. 목적: 후속 구현과 다른 에이전트의 독립 재검토에 사용할 누락 판정 및 인수 기준 제공.

후속 구현 범위·API·상태 전이·시험·완료 기준은 [보강 작업 명세서](../development/tx-own-remediation-spec-20261102.md)를 따른다. 미확정 정책과 추가 검수 지점은 해당 명세의 3절에 정리했다.

**통합본 기준으로 구매자의 대기 요청 취소 UI, 당사자의 취소 내역 표시, 외부 이전의 소유자 최종 승인, 수신자 확인 전 요약 공개 제한, 기존 문서 URL의 다운로드 권한 재검사가 남아 있다.** 반면 내부 이전·시스템 이력·증빙 보호·초안 정리는 이미 운영 코드로 연결되어 있어 재구현 대상으로 분류하면 안 된다. PostgreSQL 검증도 일부 기존 실행 기록이 있으므로 전부 미실행으로 평가하면 부정확하다.

## 검토 기준과 소스 구분

이번 작업에서는 보고서만 추가한다. 애플리케이션 코드·기존 요구사항·마이그레이션·`.env`를 수정하지 않는다.

제공된 평가는 처음 열린 작업 폴더의 코드와 일치하지 않는다. 같은 저장소에 통합 구현이 있는 별도 worktree가 있으므로 다음 두 소스를 구분한다.

| 구분 | 작업 폴더 | 브랜치와 고정 커밋 | 해석 |
| --- | --- | --- | --- |
| A 현재 작업 폴더 | `C:/Workspace/AIBE7_FinalProject_Team1` | `feature/trade-p4-completion`, `0da56160a901f920d9f5469b0b41112b5c22dd1a` | ownership/history는 패키지 자리표시자이고 거래 포트의 운영 구현과 실제 거래 화면 연결이 없다. |
| B 통합본 | `C:/Workspace/AIBE7_FinalProject_Team1-p5-main-20261102` | `feat/p5-direct-main-convergence-20261102`, `b3e20f8188e112843a55704fdcbbe1098d6e68ad` | 제공된 평가에 언급된 내부·외부 이전과 실제 화면 구현이 존재한다. 아래 항목별 판정은 이 소스를 기준으로 한다. |

B를 원 평가의 실제 검수 커밋으로 단정하지 않는다. 다만 평가에 적힌 구현이 존재하는 비교 소스로 확인했다. A와 B 모두 조사 시작 시 추적 파일 변경이 없었다. 다른 worktree와 원격 브랜치의 최신 상태까지 통합 완료로 간주하지 않는다.

다른 검토자는 먼저 `git rev-parse HEAD`와 `git status --short`로 소스를 고정해야 한다. 아래 파일 경로와 줄 번호는 별도 표시가 없으면 **B의 고정 커밋 기준**이다. A에서 B의 코드는 다음처럼 읽을 수 있다.

Java 파일의 `chat/`, `trade/`, `ownership/`, `history/`, `document/`, `listing/`, `common/` 경로는 `src/main/java/org/example/springtestci/` 아래를 줄여 쓴 것이다. `db/`는 `src/main/resources/db/` 아래를 가리킨다.

```powershell
git show b3e20f8188e112843a55704fdcbbe1098d6e68ad:src/main/resources/static/js/deals.js
```

요구사항 기준은 B의 `docs/REQUIREMENTS.md:402`, `docs/REQUIREMENTS.md:506`, `docs/MVP_SCOPE.md:57` 및 `docs/MVP_SCOPE.md:182`이다. 후속으로 제공된 작업 2·3·4·7의 상세 체크리스트와 완료 기준도 반영했다. 원 작업 번호와 이 보고서의 보강 번호는 아래 대응표로 구분한다.

| 원 검수 작업 | 이 보고서의 대응 절 | 후속 자료로 명확해진 범위 |
| --- | --- | --- |
| 작업 2 외부 이전 승인과 수신자 확인 | 보강 3·4 | 최종 승인 추가 또는 팀의 확정 정책에 따른 요구사항 변경. 지정/미지정 수신자의 요약 자격과 로그인 복귀 유지. |
| 작업 3 실제 증빙 다운로드 권한 | 보강 5 | 기존 URL뿐 아니라 사본 교체 전 URL 차단, 재마스킹 보호 유지, 원 업로더에게 돌아온 승계 사본의 삭제 제한, 운영 제공 경로. |
| 작업 4 대기 요청 취소와 취소 내역 | 보강 1·2 | 처리 중·성공·실패, 응답 유실 뒤 재조회, 양측 당사자의 과거 취소 확인. |
| 작업 7 환경과 비기능 및 인수 | 보강 6 및 추가 작업 항목 검토 | 요구사항 사본 정합성, 실제 활성 설정, 운영 연동, 페이지 구분, 성능 측정, 환경별 인수와 운영 정책. |

## 항목별 재판정

‘반영’은 해당 코드 경로의 존재를 뜻한다. 실제 PostgreSQL·브라우저 인수 성공과 구분한다.

| 항목 | B 통합본 판정 | 원 평가에 대한 판단과 남은 범위 |
| --- | --- | --- |
| TX-01 채팅 시작 | 반영 | `DefaultChatService.openRoom()`의 재사용·생성과 `ChatAccessPolicy`의 ACTIVE·소유자·타인 검사가 있다. 별도 미구현 지적 없음은 타당하다. |
| TX-02 채팅과 거래 진행 | 부분 반영 | 대화·자동 갱신·본인 미확인·예약·완료 연결은 있다. 구매자 PENDING 취소와 당사자 취소 내역 UI 누락은 맞다. 보강 1·2 참조. |
| TX-03 구매 요청 | 부분 반영 | 본인 PENDING 취소 API와 권한·상태 검사는 이미 있다. 추가할 것은 구매자 화면의 버튼과 호출 연결이다. 보강 1 참조. |
| TX-04 예약 확정 | 반영 | 수락·거절, 제품/판매글/요청 잠금, 단일 예약, 나머지 PENDING 유지와 실제 INTERNAL/PENDING 생성 포트가 연결된다. |
| TX-05 예약 취소 | 부분 반영 | 취소 저장·메타데이터·이전 취소·ACTIVE 복귀·KEEP/CANCEL 처리는 있다. 취소 후 다시 조회해 당사자 화면에 표시하는 경로가 없다. 보강 2 참조. |
| TX-06 인수와 거래 완료 | 구현 반영 및 인수 잔여 | 최신 버전·양측 확인·미확인 변경 차단·실제 이전·이력·SOLD가 연결된다. PostgreSQL 검증은 기존 기록이 있으나 최신 B의 전체 브라우저 인수 완료로 확대할 수 없다. 보강 6 참조. |
| TX-07 내 거래 | 부분 반영 | 본인 방 목록·종료 후 원 당사자 접근은 있다. CANCELLED 내역 표시는 빠져 있다. 보강 2 참조. |
| OWN-01 내부 거래 이전 | 구현 반영 및 용어 정리 | 같은 트랜잭션의 실제 소유자 변경·시스템 TRANSFER·PRIVATE·증빙 보호·초안 정리가 있다. `ACCEPTED`와 `COMPLETED` 차이는 업무 단계 누락이 아닌 계약 불일치다. 보강 6 참조. |
| OWN-02 외부 이전 | 부분 반영 | 발급·링크·실제 QR·수신자 수락·실제 이전은 있다. 수신자 수락 후 현재 소유자의 최종 승인을 기다리는 단계는 없다. 보강 3 참조. |
| OWN-03 만료와 취소 및 재발급 | 반영 | 24시간·정각 만료·해시·단일 PENDING·취소·재발급 UI가 있다. 단일 재발급 API 부재 자체는 필수 누락이 아니다. 중간 실패·응답 유실은 인수 대상으로 유지한다. |
| OWN-04 수신자 확인과 수락 | 부분 반영 | 로그인 복귀·명시 수락·수락 시 권한/상태 재검사는 있다. verify는 익명이며 지정 수신자 확인 없이 유효 요청 요약을 반환한다. 보강 4 참조. |
| OWN-05 이전 처리 | 구현 반영 | 내부·외부의 잠금, 재검사, 원자적 소유자/이력/보호/초안/감사 처리와 중복 부작용 방지 코드가 있다. 최종 승인 정책 차이는 OWN-02에 귀속한다. |
| OWN-06 이전 후 권한 | 부분 반영 | 현재 소유자 권한, 이전 작성자 수정 제한, 증빙 보호와 초안 정리는 반영됐다. 이미 발급된 URL의 실제 다운로드에서 소유권을 다시 검사하지 않는다. 보강 5 참조. |

## 보강 1 구매자 대기 요청 취소 연결

**판정: 실제 UI 누락. 원 작업 4에 해당하며 기존 서버 API를 재사용한다.**

- 근거: `src/main/resources/static/js/deals.js:316`에서 PENDING 상태를 표시하지만 `renderPendingCard()`는 `deals.js:344`에서 구매자이면 바로 반환한다. 판매자의 수락·거절만 연결되어 있고 구매자용 `/purchase-requests/{requestId}/cancel` 호출은 없다.
- 이미 반영: `src/main/java/org/example/springtestci/trade/controller/PurchaseRequestController.java`의 POST `/api/v1/purchase-requests/{requestId}/cancel`, `trade/service/PurchaseRequestService.java:128`의 구매자·PENDING 검사와 저장. 취소 시 판매글 상태를 변경하지 않는다.
- 보강 내용: 본인 PENDING에만 취소 버튼을 제공하고 위 API를 호출한다. 중복 클릭을 막고 성공 후 같은 방의 요청·진행 상태를 다시 조회한다. 수락과 경합한 실패는 최신 예약 상태로 복구한다. 계정/방 전환 뒤 늦게 도착한 응답이 현재 화면에 반영되지 않게 기존 화면 범위 검사를 유지한다.
- 인수 기준: 구매자는 자신의 대기 요청만 취소 가능하고 판매자·제삼자는 불가하다. 취소 후 새 요청을 보낼 수 있으며 판매글은 기존 상태를 유지한다. 수락이 먼저 확정된 경우 PENDING 취소로 예약을 해제해서는 안 된다.

## 보강 2 취소된 요청과 예약의 당사자 내역

**판정: 실제 UI 누락. 저장·조회 API는 이미 반영되어 있다. 원 작업 4에 통합한다.**

`src/main/resources/static/js/deals.js:291`은 송신·수신 목록을 조회하지만 `deals.js:296`에서 PENDING 또는 ACCEPTED 요청만 선택한다. CANCELLED는 이 단계에서 사라진다. 판매글이 CANCELLED이면 `deals.js:283` 부근에서 조회 전에 반환하는 경로도 있어 판매 중지로 끝난 거래 내역도 놓칠 수 있다.

반면 `trade/service/PurchaseRequestService.java:120`의 단건 조회와 `:92`의 목록 조회는 당사자 검사를 수행하고, `:302`에서 취소 수행자·사유·시각을 응답한다. `trade/repository/JdbcPurchaseRequestRepository.java:168`은 선택적인 상태 필터를 제공한다. `TradeCancellationService.java:142`의 예약 취소 저장도 있다. `docs/REQUIREMENTS.md:682`는 취소 사유·수행자·처리 시각의 표시를 요구한다. 관리자 조회 화면이 있더라도 당사자 화면의 누락을 대신 충족하지 않는다.

보강할 화면은 현재 진행 요청과 과거 요청을 함께 다루되 각각의 `requestId`를 유지해야 한다. 새 요청을 보냈다고 이전 CANCELLED 기록을 덮거나 숨기면 안 된다. 목록은 처음 100건 이후도 조회할 수 있어야 하며 판매글 종료 후에는 거래 식별용 최소 정보와 당사자 내역을 보여준다.

다음 두 취소는 구분해야 한다.

| 종류 | 기존 응답 계약 | 화면에 필요한 처리 |
| --- | --- | --- |
| 수락 전 구매 요청 취소 | `requestStatus=CANCELLED`, `transactionStatus=null`, 취소 메타데이터는 모두 null | ‘구매 요청 취소’로 표시하고 예약 취소 사유를 만들어 표시하지 않는다. |
| 수락 후 예약 취소 | `requestStatus=CANCELLED`, `transactionStatus=CANCELLED`, `cancelledById/cancelReason/cancelledAt` 존재 | 사유·수행자·시각을 표시한다. 양측 ID를 안전하게 비교할 수 없다면 표시 역할을 DTO로 제공하고 닉네임 등을 임의 추정하지 않는다. |

이 구분은 `src/main/java/org/example/springtestci/trade/dto/PurchaseRequestResponse.java:11`에도 명시되어 있다. 현 요구사항의 사유·수행자·시각 기록 의무를 수락 전 취소까지 자동 확장해 서버·DDL 누락으로 판정하지 않는다.

인수 기준은 일반 예약 취소, 변경 확인에서 CANCEL, 판매글 중지에 따른 취소, 취소 후 재요청, 완료 후 기존 대화 접근을 각각 새로고침 후에도 양측이 확인하는 것이다. 다른 방 참여자의 내역은 노출하면 안 된다.

## 보강 3 외부 이전의 소유자 최종 승인

**판정: 명시 요구사항 대비 실제 업무 단계 누락. 원 작업 2에 해당한다.**

`docs/MVP_SCOPE.md:182`는 수신자 수락 후 현재 소유자가 최종 승인해야 하며, `:184`는 수락만으로 소유권이 바뀌지 않는다고 명시한다. `docs/REQUIREMENTS.md:524`의 OWN-02도 양측 승인과 판매자 미승인 시 이전 금지를 요구한다.

현재 `ownership/service/OwnershipTransferService.java:206`의 `acceptTransfer()`는 수신자의 요청에서 바로 `productRepository.updateOwner()`, `transfer.complete()`, 이력·증빙·초안·감사를 처리한다. `ownership/controller/OwnershipTransferController.java:62`의 accept 외에 최종 승인 API는 없다. 발신자의 최초 발급은 이 사후 최종 승인 요건을 충족하지 않는다.

보강 시 수신자 수락과 실제 이전 확정을 분리한다. 수락 단계에서는 수신자 ID·수락 시각을 기록하되 소유자·이력 공개 범위·증빙·초안을 변경하지 않는다. 이후 현재 소유자의 인증된 최종 승인에서 제품→판매글→이전 잠금과 권한·최신 소유권·판매 진행·요청 상태를 다시 검사하고 기존 원자적 이전 처리를 수행한다.

수락 이후 승인 대기 상태의 명칭, 유효기간, 취소 가능 시점, 제품별 유효 요청 슬롯은 요구사항·API·DDL·화면을 함께 확정해야 한다. 새 상태/컬럼이 필요하면 새 Flyway migration으로 추가하며 기존 migration을 수정하지 않는다. 내부 거래의 양측 완료 확인 흐름에는 외부 이전 승인 단계를 중복 삽입하지 않는다.

현행 문서를 유지할 때 인수 기준은 수신자 수락만으로 제품 소유자가 바뀌지 않는 것, 발신자만 최종 승인 가능한 것, 승인·취소·만료 경합에서 하나만 확정되는 것, 승인 후 재요청으로 TRANSFER/감사가 중복되지 않는 것이다. 후속 체크리스트가 제시한 다른 경로는 팀이 ‘소유자의 발급을 승인으로 간주하고 수신자 수락으로 완료’를 확정한 경우다. 이때는 합의 근거와 적용 범위를 기록하고 MVP·요구사항 사본·API·화면을 동기화한다. 이 대안이 제시됐다는 사실만으로 팀 정책이 이미 확정됐다고 간주하지 않는다.

## 보강 4 수신자 확인 전 제품 요약 공개 제한

**판정: 실제 조회 권한 누락. 원 작업 2에 포함되지만 최종 승인과는 별도 수정 지점이다.**

- `common/security/SecurityConfig.java:122`에서 `/api/v1/ownership/transfers/verify`를 익명 허용한다.
- `ownership/controller/OwnershipTransferController.java:54`의 verify는 인증 사용자 ID를 전달하지 않는다.
- `ownership/service/OwnershipTransferService.java:158`의 `verifyTransfer(String rawToken)`은 유효성·현재 소유자·판매 진행 여부로 `canAccept`를 정하고, 유효하면 제품 제목과 발신자 닉네임을 조회한다. 지정 수신자인지 검사할 입력 자체가 없다.
- 지정 수신자와 본인 수락 거부는 `acceptTransfer()`에 있다. 이는 이미 공개한 요약의 권한 검사를 대신하지 않는다.

발신자가 B를 지정한 요청의 토큰을 C나 익명 사용자가 가지고 verify하면, B 자격을 확인하기 전에 유효 요청의 요약이 반환되는 코드 경로다. 무효 요청의 제목·닉네임을 숨기는 처리는 이미 있으므로 그 부분을 미구현으로 적으면 안 된다.

보강안은 로그인 전 응답을 로그인 복귀에 필요한 최소 상태로 제한하고, 요약 제공 API에는 인증 사용자 식별자와 지정 수신자 검사를 적용하는 것이다. 추가 전달된 기준에 따라 지정 수신자가 있으면 로그인 회원 ID의 일치를 검사하고, 미지정 링크는 유효 토큰을 제시한 로그인 회원에게 허용하는 정책을 적용한다. 발신자 본인의 수락 금지는 유지한다. 단순히 verify를 로그인 필수로 바꾸는 것만으로 지정 수신자가 아닌 회원의 조회는 막히지 않는다. 무효 요청에서 남는 제품/발신자 식별자 필드도 해당 응답 목적에 필요한지 함께 확인한다.

인수 기준은 익명·발신자 본인·지정 수신자·다른 회원·수신자 미지정·만료·취소·완료 토큰별 필드 검증이다. 로그인 복귀는 유지하고 로그인 자체가 수락/승인을 실행해서는 안 된다.

## 보강 5 이미 발급된 문서 URL의 권한 회수

**판정: 로컬·테스트 저장소의 실제 다운로드 권한 재검사 누락. 원 작업 3에 해당한다.**

`document/service/DocumentService.java:137`의 URL 발급은 현재 소유자를 검사하며 `:37`의 TTL은 5분이다. 이전 소유자의 **새 URL 발급**은 이미 거부된다. 그러나 실제 파일을 반환하는 `common/storage/LocalFileController.java:22`는 서명·만료만 검사하고 `storage.load()`를 실행한다. `common/security/SecurityConfig.java:104`도 `/local-files`를 익명 허용한다.

재현 절차는 다음과 같다.

1. 현재 소유자 A가 문서 임시 URL을 발급받는다.
2. 내부 또는 외부 이전을 완료해 현재 소유자를 B로 바꾼다.
3. URL 유효기간 안에 A 또는 익명 클라이언트가 기존 URL로 새 GET을 보낸다.
4. 현재 다운로드 경로는 소유권 변경 사실을 조회하지 않으므로 서명·만료가 유효하면 파일을 반환한다.

또한 `LocalFileStorage.java:82`는 만료 초보다 **큰 경우**만 거절하므로 만료와 같은 epoch second는 허용한다. 즉 권한 회수 지연은 통상 잔여 TTL 범위이며 정각 만료 비교도 함께 보완할 필요가 있다.

보강안은 문서 다운로드를 인증된 문서 ID 기반 경로로 제공하고 **실제 다운로드 요청마다** 현재 소유권·재마스킹 차단 상태를 Service에서 검사하는 것이다. 기존 서명 URL로 문서 경로를 우회하지 못하게 해야 한다. 대안으로 권한 버전을 서명에 묶어 서버에서 재검사할 수 있지만 TTL 축소만으로 즉시 회수 요건이 충족되지는 않는다.

이 판정은 현재 운영 코드에 존재하는 `LocalFileStorage`의 local/test 프로필 범위다. 현재 소스에서 별도 운영 `FileStorage` 구현은 확인되지 않으며 S3·Supabase 다운로드가 같은 방식이라고 단정하지 않는다. 외부 저장소 도입 시에도 동일 인수 기준을 적용해야 한다. 이미 내려받은 파일을 회수하는 기능과 새 다운로드 차단은 구분한다.

인수 기준은 내부·외부 이전 각각에서 이전 소유자의 새 URL 발급과 기존 URL GET 모두 거부, 새 소유자의 정상 다운로드, 제삼자·익명 거부, 만료 정각 거부, 재마스킹 차단 확인이다. 기존 `DocumentServiceTest`의 이전 소유자 URL 발급 거부 시험만으로 기존 URL GET 차단을 검증했다고 말하면 안 된다.

후속 작업 3의 사본 교체·재마스킹·재승계 기준에는 이미 있는 처리와 추가 검증을 구분한다. `document/entity/Document.java:109`의 `replaceFile()`은 문서 ID·업로더·`protectedAt`을 유지하고 파일 정보와 버전만 바꾸며 재마스킹 차단을 해제한다. `DocumentService.java:154`는 업로더 일치 여부와 무관하게 보호된 문서를 삭제하지 못하게 한다. 따라서 승계 사본이 원 업로더에게 돌아온 경우에도 보호가 유지되는 코드가 있다.

교체 전 파일은 `DocumentService.java:115`에서 커밋 후 삭제하지만 `:221`의 저장소 삭제 실패는 경고로 처리한다. 따라서 삭제가 실패한 경우에도 구 URL을 즉시 무효화하려면 현재 문서의 object key/버전과 연결된 접근 검사가 필요하다. 파일 삭제 성공만을 접근 차단 근거로 삼으면 안 된다. 교체 전 URL GET 거부, 교체 실패 시 기존 사본 유지, 교체 후 보호 유지, 원 업로더에게 재이전된 사본 삭제 거부를 별도 인수 사례로 남긴다. 보호 유지 코드를 새로 구현할 항목으로 중복 분류하지 않는다.

## 보강 6 이미 반영된 구현과 남은 계약 및 인수 검증

### 재구현하지 않아야 할 내부 이전과 후처리

| 책임 | B의 실제 구현 근거 |
| --- | --- |
| 내부 이전 준비·완료 | `ownership/service/InternalTradeOwnershipService.java:33`, `:57`, `:96`. 실제 `InternalTransferPendingWriter`·`InternalTradeCompletionPort` 구현이며 `MANDATORY`로 상위 트랜잭션에 참여한다. |
| 소유자와 사실 버전 변경 | `ownership/repository/InternalTradeOwnershipRepository.java:135`. 이전 소유자와 확인 버전을 조건부 갱신한다. |
| TRANSFER 단일 생성과 PRIVATE 전환 | `history/service/DefaultInternalTransferHistoryWriter.java:33`, `history/repository/HistoryRepository.java:165`, `:214`. 이미 처리한 이전 ID를 확인하고 현재 트랜잭션에 참여한다. |
| 증빙 영구 보호 | `ownership/repository/InternalTradeOwnershipRepository.java:146`. 기존 보호 시각을 보존한다. |
| 이전 작성자의 초안 정리 | `listing/service/ListingDraftService.java:99`. 동기 `@EventListener`·`MANDATORY`이며 커밋 전에 삭제/flush한다. |
| 이전 작성자 이력 수정 제한 | `history/service/HistoryService.java:235`. 현재 소유자라도 이전 작성자의 일반 이력을 수정할 수 없게 검사한다. |

OWN-01·05에서 지적된 ‘완료 시 이전·후처리’ 자체의 누락은 B에 없다. 이 결론을 A에 소급하면 안 된다.

### 용어와 상태 계약

B의 `ownership/entity/TransferStatus.java` 및 `db/migration/V2026102803__create_ownership_tables.sql`은 이전 완료를 `COMPLETED`로 정의한다. 반면 `docs/REQUIREMENTS.md`와 `docs/ERD.md`는 이전 요청 완료를 `ACCEPTED`로 표현한다. 외부 유형의 `DIRECT`와 문서의 `EXTERNAL`도 함께 정리할 계약 차이다.

실제 소유자 변경과 후처리 코드가 있으므로 단어 차이만으로 이전 처리가 없다고 판정하지 않는다. 합의한 상태명을 API·문서·화면·테스트에서 일치시키고, 저장값 변경을 선택한다면 기존 데이터와 CHECK 제약을 위한 새 migration을 제공해야 한다. 보강 3의 수신자 수락과 최종 승인 상태를 정할 때 같이 결정한다.

### PostgreSQL과 브라우저 검증의 잔여 범위

- `docs/INTERNAL_TRADE_OWNERSHIP_INTEGRATION.md:48`에는 실제 이전 포트·history·JPA/JDBC를 사용한 PostgreSQL 시험 25건 통과가 기록되어 있다. `:51`에는 복제 DB 서버에서 로그인 쿠키·CSRF를 사용한 내부 완료 확인도 기록되어 있다. 이는 **기존 보고된 결과**이며 이번 실행 결과는 아니다.
- 같은 문서 `:54`는 자동 브라우저/휴대폰 렌더링 검증 미완료와 사용자 수동 확인 보고를 구분한다. 따라서 ‘PostgreSQL 검증 자체가 전혀 없음’은 수정해야 하지만 ‘최신 B의 전체 브라우저 인수는 별도’라는 지적은 유지한다.
- `docs/trade/p1-p4-regression-result.md` 마지막 실행 절에도 P2/P3/P4 PostgreSQL 성공 기록이 있다. 이 중 시험 대역 기반 거래 검증을 실제 ownership 연동 시험과 혼동하지 않는다.
- `ownership/DirectTransferPostgresTest.java:57`은 `OWN_INT_TEST_ENABLED=true`에서만 실행되고 `:808`의 브라우저 시험은 추가로 `P5_UI_ENABLED=true`와 외부 브라우저 스크립트가 필요하다. 기본 `test`의 성공은 이 경로의 성공을 의미하지 않는다.
- `ownership/InternalTradeOwnershipHttpPostgresTest.java:48`도 전용 PostgreSQL URL 조건이 있다. B의 기본 P3/P4 플래그는 OFF이며 P2/P3 DDL은 여전히 pending 경로다. 실제 배포 프로필의 플래그와 단계별 migration 순서를 확인해야 한다. 이미 있는 기능을 단순 ON하는 것만으로 인수 완료가 되지 않는다.

원 작업 7은 신규 기능 일괄 개발보다 최신 고정 커밋에서의 검증과 계약 정리로 다뤄야 한다. 보강 1~5 이후 실제 PostgreSQL에서 내부 완료·취소 경합, 외부 수락/최종 승인·취소 경합, 재발급 중간 실패/응답 유실을 확인하고 브라우저에서 양측 역할·로그인 복귀·QR 진입·새로고침·좁은 화면을 검증한다. 커밋, 프로필, 활성 플래그, 마이그레이션 경로, 결과 XML과 화면 증거를 함께 기록한다.

## 추가 작업 항목 검토

추가 체크리스트는 기존 다섯 누락 판정을 유지하면서 문서·운영·비기능 범위를 넓힌다. 아래 추가 판독도 B의 동일 커밋을 기준으로 하며, 실행 중 서버와 외부 서비스 설정의 검증 결과로 해석하지 않는다.

### 요구사항 사본의 공개 자동 검사 정책

**판정: 일부 문서는 반영됐고 요구사항 사본 간 정리는 남아 있다.** `docs/REQUIREMENTS.md:284`와 `docs/HISTORY_PUBLIC_TEXT_CONTRACT.md:7`은 이력 공개 필드의 서버 자동 검사와 ADMIN 수동 승인 불필요를 명시한다. `listing/repository/JdbcListingHistoryReader.java:34`와 `history/service/ListingHistoryAdapter.java:29`도 이 자동 검사 계약을 사용한다. 이 정책 전체를 미구현이라고 분류하면 안 된다.

반면 루트 `REQUIREMENTS_SPECIFICATION.md:236`은 공개 검토 전 자유 입력 상세를 ‘공개 정보 확인 중’으로 표시하는 기존 표현을 유지한다. 기준 문서와 변경 이력을 정해 두 사본을 동기화해야 한다. 이는 **이력 공개 필드 검사** 정책이며 증빙 사본을 일반 공개하거나 문서의 사용자 마스킹 확인을 생략하는 정책이 아니다. 상태 용어 `ACCEPTED/COMPLETED` 정리와는 별도 계약 항목이다.

### 제품 목록의 페이지 구분과 조회 수 제한

**판정: 실제 구현 누락을 추가 확인했다.** `product/controller/ProductController.java:63`의 `listMine()`은 페이지 입력 없이 `List<ProductResponse>`를 반환한다. `product/service/ProductService.java:91`은 전체 소유 제품을 조회하며 `product/repository/ProductRepository.java:169`의 SQL에 LIMIT/OFFSET 또는 동등한 조회 상한이 없다. `docs/REQUIREMENTS.md:782`의 목록 페이지 구분·요청당 조회 수 제한을 충족하지 않는다.

후속 구현은 서버의 페이지/커서 계약, 기본 조회 수와 최대 조회 수, 정렬 안정성, 화면의 다음 목록 접근을 함께 제공해야 한다. 화면에서 전체 응답을 잘라 표시하는 것만으로는 요청당 조회 수 제한을 충족하지 않는다. 이 항목은 작업 7의 구체적인 구현 보강이며 최초 TX·OWN 다섯 누락과는 별도로 관리한다.

### 환경과 비기능 인수의 상태 구분

| 추가 체크리스트 항목 | 현재 판정 | 완료에 필요한 결과 |
| --- | --- | --- |
| listing-history·P2·P3·P4 활성화 및 DB 준비 | 소스 기본값과 pending 경로 확인. 실행 환경의 유효 설정 확인은 별도 | 프로필·설정 우선순위·실제 Bean과 API·마이그레이션 적용 상태를 함께 기록한다. |
| 운영 FileStorage | local/test 구현만 확인되어 운영 제공 경로 보강 필요 | 운영 저장소/다운로드 경로를 연결하고 현재 소유권·구 URL 차단 정책을 동일하게 입증한다. |
| 인증 메일·외부 AI·HTTPS·Secure 쿠키·CSRF | 이번 추가 판독에서 운영 환경은 미검증. 전부 미구현으로 단정하지 않음 | 배포 설정과 실제 성공·실패·보안 요청 결과를 각각 남긴다. |
| PostgreSQL 경합·반복·SQL/커밋 실패·이전 후 권한 | 기존 코드·시험·과거 실행 기록 존재. 최신 소스 인수는 별도 | 실제 포트 기준으로 커밋과 결과 XML을 고정하고 부분 반영·중복·권한 우회가 없음을 확인한다. |
| 재발급 중간 실패·응답 유실·동시 발급 | 두 HTTP 요청의 구현 존재. 실패 복구 인수 잔여 | 취소 성공/발급 실패와 발급 성공/응답 유실을 구분하고 상태 재조회·재시도를 검증한다. |
| Chrome·Edge·Safari·모바일·실제 QR | 이번 실제 브라우저 인수 미실행 | 브라우저/OS 버전과 기기별 발급→QR→로그인→동일 요청 복귀→확정 결과를 남긴다. |
| 조회 p95 1초 목표 | 이번 성능 측정 미실행 | `docs/REQUIREMENTS.md:777`에 따라 환경·데이터 수·동시 사용자·대상 API·측정 방법을 먼저 고정하고 p95를 기록한다. AI·파일 전송은 별도 측정한다. |
| 로그 접근·보관·삭제 및 외부 AI 제공사 데이터 조건 | 이번 운영 정책 확정 여부 미검증 | 담당자의 확정 정책·적용 설정·검증 근거를 기록한다. 코드만으로 외부 제공사의 보관/학습 조건을 확정하지 않는다. |

작업 7 전체를 코드 구현만으로 닫거나, 기본 테스트의 SKIPPED를 환경 인수 통과로 바꾸면 안 된다. 이 추가 반영은 보고서와 소스 판독만 수행했으며 애플리케이션 테스트를 다시 실행하지 않았다. 앞선 실행 결과와 시각은 아래 표에 그대로 구분해 보존한다.

## 현재 작업 폴더 A에만 적용되는 주의점

A는 B보다 이전 단계다. `ownership`와 `history` 아래에는 `package-info.java`만 있고, `InternalTransferPendingWriter`, `InternalTransferCancellationPort`, `InternalTradeCompletionPort`는 정식 운영 구현이 없다고 명시한다. `static/pages/deals.html`, `static/pages/receive.html`, `static/js/trade.js`도 API 연결 없는 정적 시안이다.

A의 `.env`에서는 허용된 기능 설정만 판독했으며 `CHAT_API_ENABLED=false`를 확인했다. P2/P3/P4는 `application.yaml`에서 기본 OFF다. 비밀값은 출력·복사하지 않았고 DB·Redis 접속에 사용하지 않았다. 이 설정을 B 또는 실행 중 서버의 설정으로 간주하지 않는다.

따라서 A만 대상으로 구현을 시작한다면 보강 1~5 외에 **이미 존재하는 B의 통합 변경을 먼저 검토할 필요**가 있다. A에 ownership 전체를 새로 작성하면 기존 통합 구현과 중복될 수 있다. 이 보고서는 브랜치 전환·병합·체리픽을 수행하지 않는다.

## 이번 검증 결과

설치된 Temurin JDK 17.0.19로 Gradle Wrapper를 직접 호출했다. 시스템 기본 Java 8은 사용하지 않았다. PostgreSQL/Redis/브라우저 opt-in을 켜거나 외부 자원을 기동하지 않았다.

| 소스 | 실행 | 결과 |
| --- | --- | --- |
| A | `spotlessCheck test build --no-daemon` | Spotless 통과. `compileTestJava`에서 기존 오류 2건으로 중단되어 테스트 미실행, build 미완료. |
| B | `spotlessCheck test build --no-daemon` | 성공. 전 작업이 UP-TO-DATE여서 이 호출을 신규 테스트 실행으로 세지 않는다. |
| B | `test --rerun-tasks --no-daemon` | 성공. JUnit XML 119개 클래스, 총 1,178건 중 1,044건 통과·134건 건너뜀·실패/오류 0건. |
| B | `node --test --test-timeout=10000 src/test/js/*.test.mjs` | 129건 통과, 실패·건너뜀 0건. 대역 DOM 기반이며 실제 브라우저 인수가 아니다. |

A의 컴파일 오류는 `src/test/java/org/example/springtestci/trade/repository/TradeP4PostgresIntegrationTest.java:640`과 `trade/service/TradeCompletionServiceTest.java:426`이다. `ListingDetailResponse`가 `ListingSummaryResponse`를 포함한 10개 인수를 요구하는데 시험 fixture가 9개만 전달한다. 본 작업 범위는 검토·보고이므로 수정하지 않았다. B는 같은 컴파일 단계를 통과했다.

B의 이번 재실행에서 `DirectTransferPostgresTest`, `InternalTradeOwnershipHttpPostgresTest`, `OwnershipSchemaPostgresIntegrationTest`, P2/P3/P4 PostgreSQL 시험은 모두 환경 조건으로 건너뛰었다. XML은 B의 `build/test-results/test`, HTML은 `build/reports/tests/test/index.html`에 있다. 실행 종료 시각은 2026-11-02 14:28 KST다. 따라서 이번 1,044건 통과를 실제 PostgreSQL·브라우저 인수 성공으로 해석하면 안 된다.

## 후속 보강 순서와 독립 재검토 기준

1. 대상 소스를 확정한다. A만 검토한 결과와 B의 반영 여부를 섞지 않는다.
2. 원 작업 2에서 최종 승인과 요약 제공 권한을 각각 보강한다. 문서 변경만으로 필수 최종 승인 단계를 삭제하지 않는다.
3. 원 작업 3에서 실제 파일 다운로드 권한을 보강한다. URL 발급 거부와 기존 URL GET 거부를 별도 시험한다.
4. 원 작업 4에서 기존 구매 요청 취소 API를 연결하고 당사자 취소 내역을 추가한다. PENDING 취소와 RESERVED 취소의 메타데이터 차이를 유지한다.
5. 원 작업 7에서 상태 용어를 동기화하고 최신 고정 커밋의 PostgreSQL·브라우저 인수 증거를 남긴다. 기존 통과 기록과 이번 실행, SKIPPED를 구분한다.

다른 에이전트는 각 누락에 대해 요구사항 문구, 실제 API/Service/저장 경로, 화면 이벤트 호출, 정상/권한/경합 인수 기준을 모두 대조해야 한다. 소스 커밋이 달라졌거나 이미 보완된 호출 경로를 찾았다면 해당 근거를 적어 판정을 갱신한다. 인터페이스·시험 대역·정적 버튼·과거 보고만으로 최신 운영 흐름의 완료를 선언하지 않는다.

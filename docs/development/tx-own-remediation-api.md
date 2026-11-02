# TX·OWN 보강 API와 배포 계약

작성일: 2026-11-02. 작업 기준: `b3e20f8188e112843a55704fdcbbe1098d6e68ad`.

## 외부 이전

현재 MVP의 수신자 수락 → 현재 소유자 최종 승인을 구현한다. 발급은 승인을 대신하지 않는다.

| API | 인증·역할 | 처리 |
| --- | --- | --- |
| POST `/api/v1/ownership/transfers` | 현재 소유자 | 기존 발급·링크·QR 계약 유지 |
| GET `/api/v1/ownership/transfers/verify?token=...` | 로그인 회원 | 지정 수신자 또는 미지정 유효 토큰 소지자에게만 제한 요약. 동의 후 수신자 고정 |
| POST `/api/v1/ownership/transfers/accept` | 수신자 | `to_user_id`, `recipient_accepted_at`과 동의 감사만 저장. 소유자·facts·이력·증빙·초안 변경 없음 |
| GET `/api/v1/ownership/transfers/product/{productId}` | 현재 소유자 | 기존 상태에 recipientId/nickname/acceptedAt, productTitle, factsVersion 추가. 원문 토큰·해시 없음 |
| POST `/api/v1/ownership/transfers/{id}/approve` | 최초 발신자이자 현재 소유자 | 확인한 수신자·제품 버전과 현재 상태를 재검사한 뒤 기존 이전 효과를 원자적으로 확정 |
| GET `/api/v1/ownership/transfers/{id}` | 최초 발신자·확정 수신자 | 최소 처리 상태 복구. 완료 후 이전 소유자에게 제품·문서 관리 권한을 부여하지 않음 |
| POST `/api/v1/ownership/transfers/{id}/cancel` | 현재 소유자·발신자 | 승인 전 PENDING 요청 취소. 동의 후에도 취소 가능 |

accept 응답은 `{transferId,productId,stage:"AWAITING_OWNER_APPROVAL",recipientAcceptedAt,expiresAt}`이다. 이전의 `newOwnerId/completedAt` 즉시 완료 계약을 제공하지 않는다.

approve 본문은 `{expectedRecipientId,expectedFactsVersion}`이다. 서버 저장값을 이 본문으로 수정하지 않는다. 오래된 수신자·facts 확인값은 400, 권한 없는 요청은 403, 종료·만료는 기존 OWN 오류 체계를 따른다. 성공 응답은 `{transferId,completedAt}`이다. 동일 발신자의 완료 재시도는 최소 기존 결과만 반환하며 이력·감사를 반복하지 않는다.

동의 전과 승인 대기 모두 DB `PENDING/pending_slot=1`이다. UI stage는 새 DB enum이 아니다. 완료 시 `COMPLETED/pending_slot=null`, 외부 유형은 `DIRECT`를 유지한다. 내부 거래의 최종 완료와 구매 요청의 `ACCEPTED`는 다른 상태다.

신규 migration은 `V2026110201__direct_transfer_recipient_consent.sql`이다. nullable `recipient_accepted_at`과 DIRECT/수신자 연결 CHECK만 추가하며 과거 완료행·INTERNAL행에 가짜 동의 시각을 채우지 않는다.

발급 시점의 24시간 만료를 동의 후에도 유지한다. 승인 전까지 발신자 취소·취소 후 재발급을 허용한다. 이는 작업 명세서 D2의 기본안을 구현한 것으로 팀의 최종 정책 검수 대상이다. 이미 완료된 이전은 다시 열지 않는다.

수락·승인은 제품→판매글→이전 요청 순서로 잠근다. 최종 승인에서 현재 소유권·당사자·만료·판매 상태·수신자·facts를 검사하고 owner/facts, COMPLETED, TRANSFER, PRIVATE, 문서 보호, 동기 초안 정리, 감사와 이벤트를 한 트랜잭션에서 처리한다. 기록 시각은 DB의 마이크로초 정밀도에 맞춰 첫 응답과 재시도 응답을 일치시킨다.

로그인 전에는 verify를 호출하지 않는다. URL 토큰 제거, 메모리 내 로그인 복귀, 15분 이어가기 제한, 로그아웃·계정 전환 시 정보 무효화, no-store/no-referrer를 유지한다. 자동 수락·자동 승인하지 않는다.

## 실제 문서 파일 요청

`GET /api/v1/documents/{id}/file-url`의 `{url,expiresAt}` 형식과 5분 TTL은 유지한다. URL은 `/api/v1/documents/{id}/file?version=...&expires=...&sig=...`로 변경한다.

실제 GET은 인증 사용자, documentId·발급 사용자·사본 버전·만료에 묶인 HMAC, 정각 만료, 현재 소유권, 현재 사본 버전, 마스킹·재마스킹 차단을 검사한다. 클라이언트는 object key를 지정하지 않는다. 제품→문서 잠금 중 `FileStorage.load()`로 사본 바이트를 확보하고 트랜잭션 반환 후 HTTP 전송한다. 따라서 이전·교체가 먼저 커밋된 요청은 이전 권한·버전으로 읽지 못하며 전체 네트워크 전송 동안 잠금을 유지하지 않는다.

교체·삭제·추출 접수·추출 검토도 제품→문서 순서를 사용한다. 변경 전에 JPA 문서 엔티티를 먼저 영속화하지 않고 제품 ID만 조회한다. 비동기 추출 처리 자체는 제품 잠금을 요청하지 않는 기존 경로를 유지한다.

레거시 `/local-files`는 정규형 `product-images/{숫자}/{식별자}.{png|jpg|jpeg|webp}`만 허용한다. 기존에 발급한 문서 서명이 유효해도 차단한다. 교체 후 구 파일 삭제 실패는 새 사본 버전을 되돌리지 않으며, 남은 구 파일을 레거시 URL로 읽지 못한다. 업로더·문서 ID·이력 연결·보호 시각은 교체로 초기화하지 않는다.

현재 브라우저는 같은 출처 인증 쿠키로 새 탭 열기를 사용한다. Bearer 전용 별도 클라이언트는 실제 파일 GET에도 Authorization을 전달해야 한다. 운영 어댑터는 private object를 이 공통 경로의 `FileStorage.load()`로 제공해야 한다. 외부 presigned URL로 redirect하지 않는다. 저장소 Bean이 없는 운영 프로필은 기존대로 503이며 운영 저장소 선정·직접 공개 우회 차단의 인수는 별도다.

## 구매 요청 취소와 내역

구매자는 자신의 PENDING 요청에 기존 `POST /api/v1/purchase-requests/{requestId}/cancel`을 사용한다. 판매글 상태는 변경하지 않는다. 중복 클릭을 막고 응답 유실이면 단건 GET 후 최신 PENDING/ACCEPTED/CANCELLED 상태를 재조회한다.

취소 내역은 `GET /api/v1/purchase-requests?direction=SENT|RECEIVED&roomId=...&status=CANCELLED&page=...&size=20`을 사용한다. 방향별 페이지를 별도로 유지하고 requestId로 중복을 제거한다. PENDING 취소는 존재하지 않는 사유·시각을 만들지 않는다. 예약 취소만 실제 취소자 역할·사유·시각을 표시한다. 판매 중지된 원래 당사자의 대화에서도 내역을 조회한다. 방·계정 전환은 이전 표시와 응답을 무효화한다.

## 제품 페이지 구분

`GET /api/v1/products?page=0&size=20`은 공통 응답 data에 `{items,page:{number,size,totalElements,totalPages,hasNext}}`를 반환한다. 기본 20개, 최대 100개이며 음수 page·0/100 초과 size는 400이다. 정렬은 `created_at DESC,id DESC`, 현재 소유자·미보관 제품만 반환한다. DB에서 LIMIT/OFFSET/count를 적용하고 제품 사진은 페이지 제품 ID로 한 번에 조회한다.

내 제품·문서 보관함·마스킹 제품 선택은 명시적인 이전/다음 이동을 제공한다. 페이지를 처음부터 자동 순회하지 않는다. 내 제품 검색과 구매일 보유 수는 현재 페이지 기준이다. 전체 개수는 page.totalElements다. 등록 시 첫 페이지로 돌아가고 삭제·이전 뒤 빈 마지막 페이지면 앞 페이지를 다시 조회한다.

별도 `/api/v1/listing-draft-products`의 배열 계약은 이번에 변경하지 않는다. `ProductService.listForDrafts()`로 기존 초안 선택 기능을 분리 보존했으며 이 API의 별도 페이지 구분은 후속 범위다.

## 배포 순서와 검수

1. 실제 배포 브랜치·적용 migration/checksum을 확인한다. 새 변경은 개인 브랜치의 통합 기준으로 작성했다.
2. 전용 DB에서 일반 migration을 먼저 적용하고 pending P2/P3를 별도 적용한다. 처음부터 세 경로를 합쳐 빈 DB에 적용하지 않는다. 기존 migration 수정·rename·공유 DB clean/repair를 하지 않는다.
3. 외부 발급·수락·승인 mutation 트래픽을 중지하고 신규 migration과 호환 서버·UI를 함께 배포한다. 구 서버 accept는 즉시 이전하므로 신구 버전 혼용을 허용하지 않는다.
4. 과거 완료행은 그대로 유지하고 기존 PENDING 링크는 새 동의→승인 절차를 적용한다.
5. 실제 프로필의 플래그와 Bean, 인증 쿠키/CSRF, private 저장소 연결을 확인하고 내부·외부 E2E를 실행한 뒤 mutation을 재개한다.
6. 장애 시 mutation을 중지한다. 새 승인 대기를 해석하지 못하는 구 버전으로 트래픽을 바로 복귀시키거나 동의 데이터를 삭제하지 않는다. 호환 버전 복구와 상태 재검증 후 재개한다.

시험 근거와 남은 운영 검수는 [구현 검수 보고서](../reports/tx-own-remediation-implementation-20261102.md)를 따른다.

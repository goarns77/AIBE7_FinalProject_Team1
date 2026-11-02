# Pastport 로컬 배포 정리 작업 보고서

작성일: 2026-11-03 (KST)

## 기준
- 지시서: Pastport_local_release_finish_work_order_2026-11-03.md.
- base SHA: `e1572f7ffdd0e72de73b5d2dfa78c20af412f5dd` (PR #96 병합 main).
- 통합 검증/보관 branch: `fix/release-readiness-20261103`.
- final code SHA: `11c2dc09218311de3774d2061f99413de0f3cb21`. 이후 보고서 전용 커밋은 코드 변경 없음.
- 작업 위치: `C:/Workspace/AIBE7_FinalProject_Team1-release-20261103`.
- 원래 작업 공간/개인 브랜치/미추적 문서를 보존했다. main push, merge, force push, branch 삭제는 수행하지 않았다.

## 분리 PR / 개인 브랜치
| 범위 | PR | head SHA | 개인 브랜치 |
| --- | --- | --- | --- |
| A / UI | [#100](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1/pull/100) | e4e7f3db9fe31a4dbc8851e4da353a91d9002cc6 | fix/release-ui-20261103 |
| B / S3 | [#101](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1/pull/101) | 3d20e6d7d524de43f59c11a784da494977aebf0b | feat/release-s3-20261103 |
| C / 배포 자산 | [#102](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1/pull/102) | 410236357f9a9e23a011953b99f293cff3837176 | chore/release-deploy-20261103 |

세 PR은 모두 같은 main SHA에서 독립 분기했다. 통합 브랜치는 세 변경을 함께 로컬 검증한 보관용이며 별도 병합 요청은 만들지 않았다. PR은 열어 두었으며 병합하지 않았다.

## UI 계약
- `mypage.js`: `page=0&size=3` 요청, `data.items` 최대 3개 표시. 실제 빈 목록은 등록 유도, 잘못된 envelope/items와 HTTP 실패는 실패 안내.
- `auth.js`: `size=1`로 총수 조회. 안전한 비음수 `page.totalElements` 우선, `summary.registered` fallback. items.length로 총수를 추정하지 않는다.
- 계정 변경/로그아웃/페이지 이탈 시 이전 목록을 무효화하며 응답 세대/계정 식별자로 늦은 목록·개수 응답을 폐기한다.
- DTO 계약 테스트로 registered와 totalElements가 구매 취득분을 포함한 전체 보유 수임을 고정했다.
- Node 신규 15건, 전체 222건 모두 통과. 구형 배열 호환은 추가하지 않았다.
- 제품 Controller/Service/Repository, cursor 계약은 수정하지 않았다.

## S3 storage
- `app.storage.type=s3` 선택 시 S3 adapter 하나만 활성화. prod,supabase와 local/test 명시 선택을 context 테스트로 확인. local/test 기본 local 유지.
- AWS SDK v2 BOM `2.57.8` 고정, `DefaultCredentialsProvider` 사용. 운영은 EC2 IAM role 전제이며 정적 AWS 키·public ACL은 추가하지 않았다.
- UUID key: prefix/directory/UUID.extension. 원본 이름 제거, 경로 이탈/역슬래시/절대 경로/빈 directory/허용 외 확장자/prefix 밖 key 거부.
- 알 수 없는 길이의 업로드는 private OS temp file에 spool 후 PUT, finally 삭제. 다운로드는 HEAD metadata와 lazy/repeatable GET stream으로 전체 메모리 적재를 피한다.
- 404 및 SDK 오류는 키/URL/SDK cause를 포함하지 않는 IOException으로 변환. 삭제는 멱등 처리한다.
- 문서는 기존 서버 서명 URL, 현재 소유권·문서 버전·마스킹 확인 및 no-store 응답을 그대로 유지한다.
- 호출부 조사 결과 createTemporaryUrl의 실제 런타임 소비자가 없다. S3 구현은 직접 URL 발급을 명시적으로 거부하고 기존 제품/image-ID 및 document-ID 인증 endpoint만 사용한다. 향후 소비자는 중계 설계를 별도 검토해야 한다.
- S3 mock 14건 + profile/context 3건 = 17건 통과. 기존 문서/이미지 서비스 및 서명 URL 보안 회귀는 전체 Java 테스트에 포함하여 통과했다.
- 최소 IAM 초안: 승인된 콘텐츠 bucket/prefix의 GetObject/PutObject/DeleteObject만. ListBucket 불필요. BPA/HTTPS/encryption 및 role 적용은 인프라 승인 대상으로 문서화했다.
- 기존 local key/content를 자동 이행하지 않는다. 콘텐츠 inventory/copy/DB key 정합성 계획 승인 전 STORAGE GO 금지.
- 주요 파일: `common/storage/S3FileStorage.java`, `S3StorageProperties.java`, `S3StorageConfig.java`, `docs/development/s3-storage.md`, `application.yaml`, `.env.example`.

## Deploy assets
- `deploy/scripts/check-env.sh`: root/0600, 필수·중복·빈 값, 포트, prod,supabase/s3, 비밀키 32바이트, JWT와 독립 서명 키, boolean flag 검사. 값/부분 마스킹/DB URL을 출력하지 않으며 env를 source하지 않는다.
- `deploy/systemd/pastport.service`: 서비스 전용 사용자, loopback 8080, root-only env, current.jar symlink, PrivateTmp/NoNewPrivileges/쓰기 제한. Flyway를 비활성화하지 않는다.
- `deploy/nginx/pastport.conf`: HTTPS placeholder, redirect, forwarded headers, websocket, 12m upload, timeout, 보안 헤더, query 없는 access log. proxy cache 비활성.
- `build-release.ps1`: JDK17·clean SHA 검사, 외부 DB/스토리지/테스트 opt-in 환경 제거, Spotless/Node/clean build, JAR/checksum/manifest 생성. SHA와 cleanliness를 종료 전 재확인.
- stage: 명시적 release S3 URI, checksum/full-SHA manifest 검증, root:root/0644, overwrite 금지, current 변경 없음.
- switch/rollback: canonical root, lock, env 및 6개 pre-start GO 확인, checksum 재검증, 이전 링크 기록, atomic symlink, restart/loopback probe, 실패 시 기존 JAR 자동 복원. 충돌한 GO/NO도 거부.
- network-disabled Linux container에서 임시 디렉터리/dummy JAR/AWS·systemctl·curl stubs로 24건 통과. 실제 AWS/systemd/Nginx 호출 없음.
- `deploy/README.md`: CODE/DB/STORAGE/HTTPS/ENV/ARTIFACT/SMOKE 승인과 운영자 절차, 독립 release bucket 및 DB 비호환 rollback 금지 명시.
- 6개 pre-start GO는 내부 기동만 허용한다. SMOKE GO까지 모두 승인되기 전 외부 공개 금지. loopback `/` probe는 비즈니스 인수 테스트가 아니다.

## Validation
| 검증 | 결과 |
| --- | --- |
| spotlessCheck | PASS |
| Java 전체 test/build | 총 1,250 / 성공 1,098 / 조건부 skip 152 / 실패 0 |
| Node 전체 | 222 / 222 PASS |
| S3 mock/context | 17 / 17 PASS (전체 Java에 포함) |
| P5 PostgreSQL 6개 class | 총 111 / 성공 110 / browser opt-in skip 1 / 실패 0 |
| 실제 build-release.ps1 | PASS: clean build, checksum 및 manifest 확인 |
| Linux 배포 offline mocks | 24 / 24 PASS |
| git diff --check | PASS |
| PR-A 원격 CI | [run 41477676358](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1/actions/runs/41477676358): 3 jobs SUCCESS |
| PR-B 원격 CI | [run 41477681624](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1/actions/runs/41477681624): 3 jobs SUCCESS |
| PR-C 원격 CI | [run 41478304722](https://github.com/prgrms-aibe-devcourse/AIBE7_FinalProject_Team1/actions/runs/41478304722): 4 jobs SUCCESS |

P5 실행: DirectTransferPostgresTest, InternalTradeOwnershipHttpPostgresTest, H40MigrationCompatibilityTest, DeployMigrationPostgresTest, NormalizeOwnershipVersionPostgresTest, ListingDraftOwnershipHttpTest.
기존 codex.task=p5-integration-v2 라벨의 전용 PostgreSQL/Redis만 사용한 뒤 두 컨테이너를 다시 중지했다. 공유 DB에는 연결하지 않았다. 이번 작업에서는 실제 브라우저 opt-in 시나리오를 실행하지 않았다.

## 로컬 릴리스 산출물 / 증적
- full commit: `11c2dc09218311de3774d2061f99413de0f3cb21`.
- JAR: `C:/Workspace/Pastport_release_evidence_20261103/artifacts/11c2dc09.jar` (97,941,704 bytes).
- SHA-256: `e46e382f5f88bbd51e1434108edb15fa3a418c1181bfc56dfb0d449382e3f140`.
- 동일 디렉터리의 `.jar.sha256`, `.manifest.txt`: full SHA, UTC build time, Java 17.0.19, digest 포함.
- XML/기본·PostgreSQL 로그: `C:/Workspace/Pastport_release_evidence_20261103`.
- 실제 release builder 로그: `C:/Workspace/Pastport_release_build_2026-11-03.log`.
- 이 JAR는 로컬 통합 절차 검증용이다. 독립 PR의 원격 CI 성공은 통합 보관 SHA의 원격 CI나 운영 CODE GO를 대신하지 않는다. 팀 병합 후 확정된 main SHA에서 CI·새 release build·각 운영 승인을 다시 수행해야 한다.

## Not performed / 추가 승인
- 공유 Supabase SQL, migration/history 변경, Flyway clean/repair: 미수행.
- AWS 버킷/IAM/보안 그룹 생성·변경, 실제 S3 roundtrip/업로드: 미수행.
- EC2 JAR 교체, 운영 systemd/Nginx 적용·기동, 기능 플래그 운영 활성화: 미수행.
- 실제 운영 비밀값 입력/출력: 미수행.
- 기존 migration 수정·rename, 제품 cursor 계약 변경: 없음.
- DB 승인/backup·migration 검토, private content/release 인프라, 기존 파일 이행, HTTPS/실제 env, 최종 통합 SHA/실기능 인수가 남아 있다.
- 판정: 로컬 코드·릴리스 재현 절차 준비 완료. 운영 인프라/공유 DB 미적용이며 외부 공개는 여전히 금지.

## 외부 기술 확인
- [AWS SDK v2 2.57.8 공식 릴리스](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.57.8).
- [SDK stream upload의 unknown-length buffering 설명](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/best-practices-s3-uploads.html).


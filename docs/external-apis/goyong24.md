# 고용24 공채속보 API

- **출처**: 고용24 (www.work24.go.kr) — 한국고용정보원 운영
- **인증**: API 키 발급 필요 (work24.go.kr 회원가입 후 OpenAPI 신청)
- **현재 상태**: 연동 완료 (2026-03-18)

---

## 엔드포인트

```
GET https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo210L21.do
```

---

## 요청

### 쿼리 파라미터

| 파라미터    | 값             | 필수 | 설명                         |
|------------|----------------|------|------------------------------|
| authKey    | `{API_KEY}`    | ✓    | 발급받은 인증키               |
| callTp     | `L`            | ✓    | 목록 조회 고정값              |
| returnType | `XML`          | ✓    | 응답 형식 (XML 고정)          |
| startPage  | `1`, `2`, `3`… | ✓    | 페이지 번호 (1부터 시작)      |
| display    | `100`          | ✓    | 페이지당 건수 (최대 100 확인됨) |

### 예시 요청 (curl)

```bash
curl "https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo210L21.do\
?authKey=YOUR_KEY\
&callTp=L\
&returnType=XML\
&startPage=1\
&display=100"
```

---

## 응답

### 형식: XML

```xml
<dhsOpenEmpInfoList>
  <total>295</total>
  <dhsOpenEmpInfo>
    <empSeqno>K202503181234</empSeqno>
    <empWantedTitle>백엔드 개발자 채용</empWantedTitle>
    <empBusiNm>신세계아이앤씨</empBusiNm>
    <coClcdNm>대기업</coClcdNm>
    <empWantedTypeNm>정규직</empWantedTypeNm>
    <empWantedStdt>20250301</empWantedStdt>
    <empWantedEndt>20250331</empWantedEndt>
    <empWantedHomepgDetail>https://job.shinsegae.com/...</empWantedHomepgDetail>
  </dhsOpenEmpInfo>
  ...
</dhsOpenEmpInfoList>
```

### 응답 필드

| XML 필드                  | 타입     | 설명                              | 비고                    |
|--------------------------|----------|-----------------------------------|-------------------------|
| `total`                  | Int      | 전체 공고 수                       | 페이지네이션 계산에 사용  |
| `empSeqno`               | String   | 공고 고유번호                      | 중복 판단 키 (`sourceId`) |
| `empWantedTitle`         | String   | 채용 제목                          |                         |
| `empBusiNm`              | String   | 채용 업체명                        |                         |
| `coClcdNm`               | String?  | 기업 구분 (대기업 / 중소기업 등)    | nullable                |
| `empWantedTypeNm`        | String?  | 고용형태 (정규직 / 계약직 등)       | nullable                |
| `empWantedStdt`          | String?  | 채용 시작일 (`yyyyMMdd`)           | nullable                |
| `empWantedEndt`          | String?  | 채용 종료일 (`yyyyMMdd`)           | nullable                |
| `empWantedHomepgDetail`  | String   | 채용 공고 URL                      | 255자 초과 가능 → TEXT  |

> **주의**: 문서에 없는 필드(`regLogImgNm` 등)가 응답에 포함될 수 있음.
> `@JsonIgnoreProperties(ignoreUnknown = true)` 필수.

---

## JobListing 매핑

| API 필드                  | JobListing 필드 | 비고                            |
|--------------------------|----------------|---------------------------------|
| `empSeqno`               | `sourceId`     | 중복 판단 키                    |
| `"goyong24"` (고정값)     | `sourceName`   | 소스 식별자                     |
| `empWantedTitle`         | `title`        |                                 |
| `empBusiNm`              | `company`      |                                 |
| `empWantedHomepgDetail`  | `url`          |                                 |
| `empWantedTypeNm` + `coClcdNm` + 기간 | `description` | 조합 문자열       |
| `LocalDateTime.now()`    | `collectedAt`  |                                 |

---

## 페이지네이션

- 1페이지 호출 후 `<total>` 값으로 전체 페이지 수 계산
- `totalPages = ceil(total / 100)`
- 페이지 2부터 순차 반복 호출

```
총 295건 → 3페이지 (100 + 100 + 95)
```

---

## API 키 관리

| 환경       | 설정 위치                                      |
|-----------|------------------------------------------------|
| 로컬       | `src/main/resources/application-local.yml`     |
| CI/CD     | GitHub Secret: `GOYONG24_API_KEY`              |
| 코드 참조  | `@Value("\${goyong24.api.key:}")`               |

`application-local.yml`은 `.gitignore`에 등록되어 있어 키가 커밋되지 않음.

---

## 알려진 이슈 및 주의사항

| 이슈 | 원인 | 해결 |
|------|------|------|
| `Unrecognized field "regLogImgNm"` | 문서에 없는 필드 포함 | `@JsonIgnoreProperties(ignoreUnknown = true)` |
| `Unrecognized field "total"` | 루트 엔티티도 같은 문제 | `Goyong24Response`에도 동일 어노테이션 적용 |
| URL 255자 초과 오류 | 일부 공고 URL이 255자 초과 | `url` 컬럼 `TEXT` 타입으로 변경 |
| `InvalidDefinitionException` (2 args constructor) | `XmlMapper`에 KotlinModule 미등록 | `XmlMapper().apply { registerKotlinModule() }` |

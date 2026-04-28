# microservice-api-gateway

QuietChatter 프로젝트의 API Gateway 서비스. 모든 외부 HTTP 요청의 단일 진입점으로 JWT 인증, 라우팅, CORS를 처리한다.

## 기술 스택

- 언어: Kotlin 1.9.25
- 프레임워크: Spring Boot 3.5.13, Spring Cloud Gateway MVC (Servlet 기반)
- 런타임: JDK 21 Virtual Threads 활성화
- 데이터 저장소: Redis (Refresh Token 관리)
- 포트: 8080

## 라우팅 규칙

라우팅은 k8s Service URL 환경변수 기반 정적 설정이다. application.yml에서 관리한다.

| 경로 패턴 | 대상 서비스 | 환경변수 |
|---|---|---|
| /api/auth/** | microservice-member | MEMBER_SERVICE_URL |
| /api/members/** | microservice-member | MEMBER_SERVICE_URL |
| /api/support/** | microservice-member | MEMBER_SERVICE_URL |
| /api/books/** | microservice-book | BOOK_SERVICE_URL |
| /api/talks/**, /api/reactions/** | microservice-talk | TALK_SERVICE_URL |

## 인증 정책

AuthenticationFilter(OncePerRequestFilter)가 모든 요청을 검사한다.

- Bypass (인증 불필요): /api/auth/login, /api/auth/signup, /api/auth/reactivate, /api/support, /actuator/health
- Optional (토큰 있으면 처리, 없어도 통과): /api/books, /api/talks, /api/members/me
- Required (인증 필수, 없으면 401): 나머지 모든 경로

## 인증 흐름

1. 외부에서 유입된 X-Member-Id 헤더 강제 제거 (헤더 인젝션 방지)
2. ACCESS_TOKEN 쿠키 확인 후 없으면 Authorization: Bearer 헤더 확인
3. Access Token 유효: X-Member-Id에 memberId를 담아 다운스트림으로 전달
4. Access Token 만료: REFRESH_TOKEN 쿠키로 Redis 대조 후 토큰 갱신 및 쿠키 재발급
5. 토큰 무효 또는 갱신 불가: JSON 에러 응답 (401)

에러 응답 형식:

```json
{
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다."
}
```

에러 코드: UNAUTHORIZED (토큰 누락/무효), TOKEN_EXPIRED (갱신 토큰까지 만료)

## 로컬 실행

사전 요구 사항: Docker, JDK 21

```bash
./gradlew bootRun
```

로컬 실행 시 compose.yaml로 Redis가 자동 구동된다. 환경변수 미설정 시 application.yml의 기본값(localhost:808x)으로 동작한다.

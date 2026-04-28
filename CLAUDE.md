# CLAUDE.md - microservice-api-gateway

이 문서는 Claude Code가 microservice-api-gateway 프로젝트를 이해하고 개발을 돕기 위한 지침입니다.

루트 프로젝트의 CLAUDE.md에 정의된 공통 원칙을 먼저 확인하십시오.

## 1. 서비스 개요

- 역할: 모든 외부 HTTP 요청의 단일 진입점
- 포트: 8080

## 2. 기술 스택

- 언어: Kotlin 1.9.25
- 프레임워크: Spring Boot 3.5.13, Spring Cloud Gateway MVC (Servlet 기반, Virtual Threads 활성화)
- 의존성: spring-cloud-starter-gateway-mvc, jjwt, spring-data-redis
- 라우팅: k8s Service URL 환경변수 기반 정적 라우팅 (MEMBER_SERVICE_URL, BOOK_SERVICE_URL, TALK_SERVICE_URL)

## 3. 아키텍처

Spring Cloud Gateway MVC는 Servlet 기반 필터 체인으로 동작한다.

```
외부 요청
  -> AuthenticationFilter (OncePerRequestFilter: 헤더 제거, JWT 검증, X-Member-Id 주입)
  -> RouteLocator (application.yml 정적 URL 라우팅)
  -> 다운스트림 마이크로서비스
```

## 4. 작업 지침

### A. 코드 작성 규칙

- Spring Cloud Gateway MVC + Virtual Threads 환경이므로 동기식 블로킹 코드가 기본이다. Reactive 코드(Mono, Flux) 작성 금지.
- JWT 검증 로직은 JwtTokenService.kt와 AuthenticationFilter.kt를 참고하십시오.
- 새로운 코드를 작성하거나 수정할 때 단위 테스트를 함께 작성하고 통과를 확인하십시오.

### B. 라우팅 규칙

- 라우팅은 application.yml에 정적으로 선언된 k8s Service URL을 사용한다.
- 새 마이크로서비스 연동 시 application.yml routes와 AuthenticationFilter의 bypassPaths/optionalPaths를 함께 수정하십시오.
- 인증 정책은 AuthenticationFilter.kt의 bypassPaths, optionalPaths 리스트로 관리한다. 실제 경로 목록은 README.md 참조.

### C. 보안 규칙

- JWT 검증 성공 시 X-Member-Id 헤더에 회원 ID를 추가한다.
- 외부에서 직접 X-Member-Id 헤더를 주입하는 요청은 GatewayHeaderRequestWrapper에서 차단한다.
- 다운스트림 서비스는 Gateway를 통해서만 접근 가능해야 한다.

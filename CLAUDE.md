# CLAUDE.md - microservice-api-gateway

작업 전 README.md를 읽으십시오. 서비스 개요, 기술 스택, 라우팅 규칙, 인증 흐름은 README.md에 있습니다.

루트 프로젝트의 CLAUDE.md에 정의된 공통 원칙도 확인하십시오.

## 작업 지침

### A. 코드 작성 규칙

- Spring Cloud Gateway MVC + Virtual Threads 환경이므로 동기식 블로킹 코드가 기본이다. Reactive 코드(Mono, Flux) 작성 금지.
- JWT 검증 로직은 JwtTokenService.kt와 AuthenticationFilter.kt를 참고하십시오.
- 새로운 코드를 작성하거나 수정할 때 단위 테스트를 함께 작성하고 통과를 확인하십시오.

### B. 라우팅 규칙

- 라우팅은 application.yml에 정적으로 선언된 k8s Service URL을 사용한다.
- 새 마이크로서비스 연동 시 application.yml routes와 AuthenticationFilter의 bypassPaths/optionalPaths를 함께 수정하십시오.

### C. 보안 규칙

- JWT 검증 성공 시 X-Member-Id 헤더에 회원 ID를 추가한다.
- 외부에서 직접 X-Member-Id 헤더를 주입하는 요청은 GatewayHeaderRequestWrapper에서 차단한다.
- 다운스트림 서비스는 Gateway를 통해서만 접근 가능해야 한다.

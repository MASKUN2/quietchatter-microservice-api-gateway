# microservice-gateway (QuietChatter Microservice)

이 저장소는 QuietChatter 프로젝트의 API Gateway 서비스입니다.
모든 외부 요청의 진입점이며, JWT 인증 처리 및 내부 마이크로서비스로의 라우팅을 담당합니다.

## 아키텍처 및 역할

* 언어: Kotlin 1.9.x
* 프레임워크: Spring Boot 3.5.13, Spring Cloud Gateway
* 서비스 탐색: HashiCorp Consul (spring-cloud-starter-consul-discovery)
* 설정 관리: HashiCorp Consul (spring-cloud-starter-consul-config)
* 특징: JWT 토큰을 검증하고, 인증이 완료된 요청에 X-Member-Id 헤더를 추가하여 다운스트림 서비스로 전달합니다.

## AI 에이전트 작업 지침

이 서비스에서 작업하기 전에 반드시 아래 문서를 먼저 읽으십시오.

1. [AGENTS.md](./AGENTS.md): 에이전트 작업 지침
2. [구현 스펙](./docs/spec.md): 이 서비스가 구현해야 할 기능 명세

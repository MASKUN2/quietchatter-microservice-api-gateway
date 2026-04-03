# microservice-gateway 구현 스펙

## 1. 서비스 역할

이 서비스는 QuietChatter의 유일한 외부 진입점입니다.
클라이언트의 요청을 받아 JWT 인증을 처리하고, 적절한 내부 마이크로서비스로 라우팅합니다.

## 2. 라우팅 규칙

| 경로 패턴 | 대상 서비스 | 인증 필요 |
|---|---|---|
| `/v1/auth/**` | microservice-member | 아니오 |
| `/v1/me/**` | microservice-member | 예 |
| `/v1/books/**` | microservice-book | 선택 |
| `/v1/talks/**` | microservice-talk | 선택 |
| `/v1/reactions/**` | microservice-talk | 예 |
| `/v1/customer/**` | microservice-customer | 아니오 |

인증 "선택"은 인증 없이도 접근 가능하지만, 인증 시 사용자 정보를 활용함을 의미합니다.

## 3. JWT 필터 동작

### 3.1 요청 처리 흐름

```
1. 요청 수신
2. 외부에서 주입된 X-Member-Id 헤더를 제거 (보안)
3. 인증이 필요 없는 경로인지 확인 (화이트리스트)
4. Authorization 쿠키 또는 헤더에서 Access Token 추출
5. Access Token 검증
   - 유효: X-Member-Id 헤더에 memberId(UUID) 추가 후 라우팅
   - 만료: Refresh Token으로 토큰 재발급 시도
   - 무효: 401 Unauthorized 응답
6. 인증 선택 경로: 토큰 없어도 라우팅 (X-Member-Id 헤더 없이)
```

### 3.2 토큰 재발급 흐름 (Access Token 만료 시)

```
1. 요청 쿠키에서 Refresh Token 추출
2. Refresh Token 검증 및 tokenId 추출
3. Redis에서 tokenId로 저장된 Refresh Token 조회
4. 일치하면 새 Access Token 발급 및 쿠키에 설정
5. 원래 요청을 새 Token으로 재진행
6. Refresh Token도 만료된 경우: 401 응답
```

### 3.3 레거시 참고 파일

* `security/adaptor/AuthFilter.java`: 필터 핵심 로직
* `security/adaptor/AuthTokenService.java`: JWT 생성/검증/쿠키 처리
* `security/adaptor/AppCookieProperties.java`: 쿠키 설정 값
* `security/adaptor/AppCorsProperties.java`: CORS 설정 값

## 4. CORS 설정

* 허용 Origin: 환경별로 Consul Config에서 주입 (개발: localhost:3000, 운영: 실제 도메인)
* 허용 Method: GET, POST, PUT, DELETE, OPTIONS
* 허용 Header: Content-Type, Authorization
* Credentials 허용: true (쿠키 기반 인증을 위해 필수)

## 5. 쿠키 설정

| 쿠키명 | 내용 | HttpOnly | Secure | SameSite |
|---|---|---|---|---|
| ACCESS_TOKEN | JWT Access Token | true | true (운영) | Strict |
| REFRESH_TOKEN | JWT Refresh Token | true | true (운영) | Strict |

## 6. 에러 응답 규칙

모든 에러 응답은 아래 형식을 따릅니다.

```json
{
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다."
}
```

| HTTP 상태 | 코드 | 상황 |
|---|---|---|
| 401 | UNAUTHORIZED | 토큰 없음, 무효 토큰 |
| 401 | TOKEN_EXPIRED | 토큰 만료 (Refresh도 만료) |
| 403 | FORBIDDEN | 권한 없음 |

## 7. 설정 구조

`application.yml`에서 Consul Config를 통해 운영/개발 환경 설정을 분리합니다.

```yaml
server:
  port: 8080

spring:
  application:
    name: microservice-gateway
  config:
    import: "optional:consul:"
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        service-name: microservice-gateway

# JWT 설정 (Consul Config에서 주입)
app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiry: 3600
    refresh-token-expiry: 604800
  cookie:
    secure: false  # 운영에서는 true
    domain: localhost
  cors:
    allowed-origins:
      - "http://localhost:3000"
```

## 8. 구현 우선순위

1. JWT GlobalFilter 구현 (핵심)
2. CORS 설정
3. 쿠키 기반 토큰 처리
4. Consul 기반 동적 라우팅
5. 에러 핸들링

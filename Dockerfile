FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# GitHub Actions 워크플로우에서 빌드한 jar 파일을 복사합니다.
# (Spring Boot의 executable jar)
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# DEVELOPMENT.md에 명시된 JVM 튜닝 (SerialGC 및 메모리 제한)
# 호스트 RAM 512MB + Swap 환경에 최적화:
# - 힙 메모리 256MB 고정
# - 메타스페이스 128MB 제한 (Native 메모리 누수 방지)
# - 단일 코어/저메모리 환경에 적합한 SerialGC 유지
# - 스레드 스택 사이즈 256k 축소 (가상 스레드 사용 및 메모리 절약)
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms256m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss256k"

# 컨테이너 기본 포트
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

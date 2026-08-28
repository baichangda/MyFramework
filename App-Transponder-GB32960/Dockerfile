FROM eclipse-temurin:25-jre

WORKDIR /app

COPY build/libs/App-Transponder-GB32960-1.0.jar app.jar

ENV TZ=Asia/Shanghai

ENTRYPOINT ["java","-jar","app.jar"]

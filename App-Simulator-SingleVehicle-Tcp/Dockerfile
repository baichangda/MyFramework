FROM eclipse-temurin:25-jre
# 指定工作目录
WORKDIR /app
# 复制jar包、依赖包(如果有)
COPY build/libs/*.jar app.jar
# 暴露端口
EXPOSE 13579
# 设置环境变量
ENV TZ=Asia/Shanghai
# 启动命令
ENTRYPOINT ["java","-jar","app.jar"]
CMD ["-p","13579","-s","10","v2025"]

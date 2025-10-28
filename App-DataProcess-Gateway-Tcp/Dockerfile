FROM openjdk:21
# 指定工作目录
WORKDIR /app
# 复制jar包、依赖包(如果有)
COPY build/libs/*.jar app.jar
COPY build/libs/lib/ lib/
# 暴露端口
EXPOSE 51234
# 设置环境变量
ENV TZ=Asia/Shanghai
# 启动命令
ENTRYPOINT ["java","-jar","app.jar"]
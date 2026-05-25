FROM eclipse-temurin:17-jre-alpine

RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app

COPY target/*.jar app.jar

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080
EXPOSE 8081

ENV JAVA_OPTS="-server \
    -Xms400m -Xmx600m \
    -XX:+UseParallelGC -XX:ParallelGCThreads=2 \
    -XX:+UseCompressedOops -XX:+TieredCompilation \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -Dconsole.encoding=UTF-8 \
    -Dlogging.level.root=INFO \
    -Dlogging.level.com.instser.chat=INFO \
    -Dlogging.level.org.redisson=WARN \
    -Dlogging.level.org.redisson.connection=WARN \
    -Dlogging.level.org.redisson.connection.DNSMonitor=WARN \
    -Dlogging.level.org.redisson.connection.SingleEntry=WARN \
    -Dlogging.level.org.redisson.connection.pool=WARN \
    -Dlogging.level.org.redisson.connection.pool.MasterPubSubConnectionPool=WARN \
    -Dlogging.level.org.redisson.connection.pool.MasterConnectionPool=WARN \
    -Dlogging.level.org.springframework=WARN \
    -Dlogging.level.com.alibaba.druid=WARN \
    -Dlogging.level.org.mybatis=WARN \
    -Dlogging.level.org.apache=WARN \
    -Dserver.tomcat.threads.max=40 \
    -Dserver.tomcat.threads.min-spare=8 \
    -Dserver.tomcat.max-connections=200 \
    -Dserver.tomcat.accept-count=50 \
    -Dspring.datasource.druid.initial-size=8 \
    -Dspring.datasource.druid.min-idle=8 \
    -Dspring.datasource.druid.max-active=12 \
    -Dspring.datasource.druid.max-wait=2000 \
    -Dspring.redis.lettuce.pool.max-active=8 \
    -Dspring.redis.lettuce.pool.max-idle=4 \
    -Dspring.redis.lettuce.pool.min-idle=2"

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1

ENTRYPOINT exec java ${JAVA_OPTS} -jar /app/app.jar

FROM gradle:8-jdk19 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon

FROM openjdk:19
EXPOSE 25530:25530
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/MoneyManage-all.jar
ENTRYPOINT ["java","-jar","/app/MoneyManage-all.jar"]
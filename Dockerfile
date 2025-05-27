FROM openjdk:25

COPY target/scala-*/*.sh.bat ./

CMD exec ./*.sh.bat

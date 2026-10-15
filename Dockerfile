FROM eclipse-temurin:25

COPY target/out/jvm/scala-*/*/*.sh.bat ./

CMD exec ./*.sh.bat

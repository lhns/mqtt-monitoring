ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.0"

lazy val root = (project in file("."))
  .settings(
    name := "mqtt-monitoring",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-io" % "3.12.0",
      "com.hivemq" % "hivemq-mqtt-client" % "1.3.3",
      "com.softwaremill.ox" %% "core" % "0.5.13",
      "io.circe" %% "circe-parser" % "0.14.13",
      "io.opentelemetry" % "opentelemetry-api" % "1.50.0",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.50.0",
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.50.0",
      //"net.sigusr" %% "fs2-mqtt" % "1.0.1"
      "org.scalameta" %% "munit" % "1.0.4" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

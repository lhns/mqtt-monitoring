ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.0"

lazy val root = (project in file("."))
  .settings(
    name := "mqtt-monitoring",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "co.fs2" %% "fs2-io" % "3.12.0",
      "com.hivemq" % "hivemq-mqtt-client" % "1.3.5",
      "com.softwaremill.ox" %% "core" % "0.5.13",
      "io.circe" %% "circe-config" % "0.10.1",
      "io.circe" %% "circe-parser" % "0.14.13",
      "io.opentelemetry" % "opentelemetry-api" % "1.50.0",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.50.0",
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.50.0",
      "org.log4s" %% "log4s" % "1.10.0",
      "org.scalameta" %% "munit" % "1.1.1" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.sh.bat",
    assembly / assemblyOption := (assembly / assemblyOption).value
      .withPrependShellScript(Some(AssemblyPlugin.defaultUniversalScript(shebang = false))),
    assembly / assemblyMergeStrategy := {
      case PathList(paths@_*) if paths.last == "module-info.class" => MergeStrategy.discard
      case PathList("META-INF", path@_*) if path.lastOption.exists(_.endsWith(".kotlin_module")) => MergeStrategy.discard
      case PathList("META-INF", path@_*) if path.lastOption.exists(_.endsWith("io.netty.versions.properties")) => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
  )

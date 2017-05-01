import sbt._

object Version {
  val scala     = "2.12.1"
  val akka      = "2.5.0"
  val akkaHttp  = "10.0.5"
}

object Library {
  val akkaActor       = "com.typesafe.akka" %% "akka-actor"           % Version.akka
  val akkaSlf4j       = "com.typesafe.akka" %% "akka-slf4j"           % Version.akka
  val akkaContrib     = "com.typesafe.akka" %% "akka-contrib"         % Version.akka
  val akkaTestkit     = "com.typesafe.akka" %% "akka-testkit"         % Version.akka
  val akkaHttp        = "com.typesafe.akka" %% "akka-http"            % Version.akkaHttp
  val akkaSprayJson   = "com.typesafe.akka" %% "akka-http-spray-json" % Version.akkaHttp

  val scalaTest       = "org.scalatest"     %% "scalatest"       % "3.0.1"
  val mockito         = "org.mockito"       %  "mockito-core"    % "1.10.19"

  val logbackClassic  = "ch.qos.logback"            %  "logback-classic" % "1.2.3"
  val casbah          = "org.mongodb"               %% "casbah"          % "3.1.1"
  val httpclient      = "org.apache.httpcomponents" %  "httpclient"      % "4.5.3"
}

object Dependencies {

  import Library._

  val SlotsBooker = List(
    akkaActor,
    akkaHttp,
    akkaSprayJson,
    akkaSlf4j,
    akkaContrib,
    logbackClassic,
    casbah,
    httpclient,
    akkaTestkit % "test",
    scalaTest   % "test,it",
    mockito     % "test,it"
  )
}

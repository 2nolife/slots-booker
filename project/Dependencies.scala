import sbt._

object Version {
  val scala     = "2.11.8"
  val akka      = "2.4.9"
}

object Library {
  val akkaActor       = "com.typesafe.akka" %% "akka-actor"                        % Version.akka
  val akkaHttp        = "com.typesafe.akka" %% "akka-http-experimental"            % Version.akka
  val akkaSprayJson   = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % Version.akka
  val akkaSlf4j       = "com.typesafe.akka" %% "akka-slf4j"                        % Version.akka
  val akkaContrib     = "com.typesafe.akka" %% "akka-contrib"                      % Version.akka
  val akkaTestkit     = "com.typesafe.akka" %% "akka-testkit"                      % Version.akka

  val scalaTest       = "org.scalatest"     %% "scalatest"       % "2.2.5"
  val mockito         = "org.mockito"       %  "mockito-core"    % "1.10.19"

  val logbackClassic  = "ch.qos.logback"            %  "logback-classic" % "1.1.3"
  val casbah          = "org.mongodb"               %% "casbah"          % "3.1.1"
  val httpclient      = "org.apache.httpcomponents" %  "httpclient"      % "4.5.2"
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

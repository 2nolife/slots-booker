name := "slots-booker"

Common.settings

libraryDependencies ++= Dependencies.SlotsBooker

mainClass in (Compile, run) := Some("com.coldcore.slotsbooker.ms.start")

lazy val root = (project in file(".")).
  configs(IntegrationTest).
  settings(Defaults.itSettings: _*)

parallelExecution in Test := false
parallelExecution in IntegrationTest := false
name := "irritant"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(

  // CORE
    "org.typelevel"                     %% "cats-core"                           % "1.1.0"
  , "org.typelevel"                     %% "cats-effect"                         % "1.0.0-RC2-d7181dc"
  , "com.github.pureconfig"             %% "pureconfig"                          % "0.9.1"
  , "com.github.scopt"                  %% "scopt"                               % "3.7.0"

  // SYSTEMS
  , "com.atlassian.jira"                 % "jira-rest-java-client-core"          % "5.0.4"
  , "org.scala-lang.modules"            %% "scala-java8-compat"                  % "0.9.0"
  , "com.atlassian.fugue"                % "fugue"                               % "2.7.0"
  , "com.flyberrycapital"               %% "scala-slack"                         % "0.3.1"
  , "org.eclipse.jgit"                   % "org.eclipse.jgit"                    % "5.0.0.201805301535-rc2"

  // TEST
  , "org.scalatest"                     %% "scalatest"                           % "3.0.5"                          % "test"
)


resolvers += "atlassian-public" at "https://m2proxy.atlassian.com/repository/public"

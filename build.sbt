name := "irritant"

version := "0.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(

  // CORE
    "org.typelevel"                     %% "cats-core"                           % "1.4.0"
  , "org.typelevel"                     %% "cats-effect"                         % "1.0.0"
  , "com.github.pureconfig"             %% "pureconfig"                          % "0.9.2"
  , "com.github.scopt"                  %% "scopt"                               % "3.7.0"

  // SYSTEMS
  , "com.atlassian.jira"                 % "jira-rest-java-client-core"          % "5.1.0"
  , "org.scala-lang.modules"            %% "scala-java8-compat"                  % "0.9.0"
  , "com.atlassian.fugue"                % "fugue"                               % "2.7.0"
  , "com.flyberrycapital"               %% "scala-slack"                         % "0.3.1"
  , "org.eclipse.jgit"                   % "org.eclipse.jgit"                    % "5.1.1.201809181055-r"

  // TEST
  , "org.scalatest"                     %% "scalatest"                           % "3.0.5"                          % "test"
)


resolvers += "atlassian-public" at "https://m2proxy.atlassian.com/repository/public"

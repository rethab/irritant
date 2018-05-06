name := "irritant"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(

  // CORE
    "org.typelevel"                     %% "cats-core"                           % "1.1.0"
  , "org.typelevel"                     %% "cats-effect"                         % "1.0.0-RC"
  , "com.github.pureconfig"             %% "pureconfig"                          % "0.9.1"
  , "com.github.scopt"                  %% "scopt"                               % "3.7.0"

  // SYSTEMS
  , "com.atlassian.jira"                 % "jira-rest-java-client-core"          % "5.0.4"
  , "com.atlassian.fugue"                % "fugue"                               % "2.6.1"
  , "com.github.gilbertw1"              %% "slack-scala-client"                  % "0.2.3"
  , "org.eclipse.jgit"                   % "org.eclipse.jgit"                    % "4.11.0.201803080745-r"

  // TEST
  , "org.scalatest"                     %% "scalatest"                           % "3.0.5"                          % "test"
)


resolvers += "atlassian-public" at "https://m2proxy.atlassian.com/repository/public"

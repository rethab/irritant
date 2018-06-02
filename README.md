[![Build Status](https://travis-ci.org/rethab/irritant.svg?branch=master)](https://travis-ci.org/rethab/irritant)

# Irritant

A simple command line tool that connects tools for workflows. Currently supported:

- Jira
- Git
- Slack

This is intended for teams that use several tools and need them to work together.
For example, it can search jira tickets based on commits in git and notify the committers in slack about missing data, upon deployment etc.

## Configuration

### Configure access to Jira:

*Note that instead of your real password, you should use an API Token: [API Tokens](https://confluence.atlassian.com/cloud/api-tokens-938839638.html)*
```
jira {
  uri =  "https://my-project.atlassian.net"
  username = "JIRA_USER"
  password = "JIRA_PWD"
}
```

### Configure access to Slack:

Create Legacy Token: [Legacy Tokens](https://api.slack.com/custom-integrations/legacy-tokens)

```
slack {
  token = "LEGACY_TOKEN"
  post-as-user = "Irritant"
}
```

### User Mappings
This list of users maps the users in different systems.
Note that each user that is used somewhere (eg. should be triggered, has
created a ticket, is tester of a ticket) must be mapped here in order for the
program to properly link them. The prettyName is used to talk to people (eg.
a friendly message in Slack).

```
users = [
    { pretty-name: "Bob", jira: "bh", slack: "UXKHL2ELW" }
  , { pretty-name: "Alice", jira: "alice", slack: "UKBGW9XL2" }
]
```


## Supported actions
Running the program without any options shows the help:
`sbt "runMain com.irritant.Main --help"`

### Notify Deployed Tickets
`sbt "runMain com.irritant.Main notify-deployed-tickets --git-path=/path/to/git/repo"`

Searches the commit log of `master` for recently deployed tickets and notifies the corresponding testers via slack.
The commits are found by searching for commit messages like `version: 123.1.2` and picking the commits between them.

### Notify Missing Testing Instructions
`sbt "runMain com.irritant.Main notify-missing-test-instructions --git-path=/path/to/git/repo"`

Searches all tickets in jira that are in the current sprint and in testing, but don't have any comment with the text `test instructions`.

### Your own?
See `com.irritant.Commands` as a starting point


## Options
- `--run-mode <run-mode>`: Where 'run-mode' is one of the following:
  - safe (default): ask before triggering people in slack
  - dry: write to stdout instead of slack
  - yolo: don't ask before triggering
- `--help`: Shows the help



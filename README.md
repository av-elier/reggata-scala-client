# reggata-scala-client

[![Build Status](https://travis-ci.org/av-elier/reggata-scala-client.svg?branch=master)](https://travis-ci.org/av-elier/reggata-scala-client)

A client for https://github.com/vlkv/reggatad

## Quick Start
- Install java8,
- install javafx8 (`apt-get install openjfx` or already bundled with oracle-jdk),
- install [sbt](http://www.scala-sbt.org/download.html),
- run `sbt run` in cloned repo.

## API wrapping logic

Sample Reggata API command looks like: 
```json
{
    "id": "1",
    "cmd": "open_repo",
    "args": {
        "root_dir": "/home/repo/",
        "db_dir": "home/repo/.reggata/",
        "init_if_not_exists": true
    }
}
```
    
This project implemetns API assuming that above json has all cmd-related info in `args`.
There is class `RgtReqMsgBox` for top level of json, and sealed trait `RgtReqMsg` for `"args"`.

Following `RgtReqMsgBox` marshals exactly to above json:
```scala
RgtReqMsgBox(
  "1",
  "open_repo",
  OpenRepo(
    "/home/repo/",
    "/home/repo/.reggata/",
    init=true
  )
)
```

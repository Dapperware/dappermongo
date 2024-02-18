---
layout: docs
title: Getting Started
permalink: docs/getting-started/
---

# Getting started

You'll need to first make sure that you have access to a mongo instance or cluster. 

There are a few ways to do this:
- Install mongo on your local machine or get a hosted version of mongo through Atlas.
  - You can find more information on that [here](https://www.mongodb.com/docs/manual/installation/).

- Alternatively, if you want to develop locally you can also use docker to run a mongo instance. See the examples project for reference.

- Finally, you can use the mongodb-atlas CLI tool to create a cluster either locally or on atlas.

## Installation

```scala
libraryDependencies += "com.github.dapperware" %% "dappermongo-core" % "0.0.1"
```

## Setting up the client

After adding the above dependency you can start a `MongoClient`

```scala
import dappermongo._

// ZLayer
MongoClient.local // Uses the default local settings
MongoClient.configured // By default, it looks for a config key of "mongodb"
MongoClient.configured(NonEmptyChunk("mongo", "database")) // You can also specify the config key
MongoClient.live // Accepts `MongoSettings` as an environmental parameter

// ZIO
MongoClient.scoped // Returns a ZIO that can be configured
MongoClient.fromSettings(settings) // Accepts settings passed as an argument
```

### Example

```scala
import dappermongo._
import zio._

object Example1 extends ZIOAppDefault {
  
  val program = for {
    db <- MongoClient.database("test")
    _  <- db.diagnostics.ping
  } yield ()
  
  val run = program.provide(
    MongoClient.local
  )
}
```
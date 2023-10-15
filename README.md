# Scala Critical Path Planner

[![CI](https://github.com/JesseBrown1980/scala-critical-path-planner/actions/workflows/ci.yml/badge.svg)](https://github.com/JesseBrown1980/scala-critical-path-planner/actions/workflows/ci.yml)

A real Scala 3 code sample that models a dependency graph, computes a project schedule, surfaces slack, and produces a deterministic representative critical path. The repo also includes a Java entrypoint to show clean JVM interop instead of treating Scala as an isolated language island.

## Why this repo is worth looking at

- immutable domain model with explicit error types
- deterministic topological ordering and schedule calculation
- representative critical-path extraction for branching DAGs
- task DSL parser with line-aware validation
- terminal-friendly report formatter
- Java-to-Scala interop through a JVM-safe facade
- automated tests plus GitHub Actions CI

## Project layout

- `src/main/scala/.../CriticalPathPlanner.scala`
  - validates tasks, computes schedule windows, and derives the critical path
- `src/main/scala/.../TaskFileParser.scala`
  - parses the task DSL into immutable task objects
- `src/main/scala/.../ScheduleFormatter.scala`
  - renders a readable planning report for terminal use
- `src/main/scala/.../Main.scala`
  - Scala CLI entrypoint and Java facade
- `src/main/java/.../JavaInteropDemo.java`
  - Java main class calling back into Scala
- `src/test/scala/...`
  - schedule, parser, and tie-break tests

## Task format

Each non-comment line is:

```text
id | duration | dependency1, dependency2 | owner | risk
```

Example:

```text
checkout-service | 5 | billing-rules, fraud-signals | platform | 0.50
```

## Run it

```powershell
scala-cli run . --main-class com.jbrowndevelopment.criticalpath.runPlanner -- --demo
scala-cli run . --main-class com.jbrowndevelopment.criticalpath.runPlanner -- --file examples/release-plan.tasks
```

## Run the Java interop demo

```powershell
scala-cli run . --main-class com.jbrowndevelopment.criticalpath.JavaInteropDemo
```

## Run the tests

```powershell
scala-cli test .
```

## Sample output

```text
Project duration: 20
Topological order: design-api -> billing-rules -> fraud-signals -> checkout-service -> schema-migration -> event-backfill -> load-testing -> cutover -> post-launch-audit
Critical path: design-api -> fraud-signals -> checkout-service -> load-testing -> cutover -> post-launch-audit
```

## Why it is a stronger-than-usual Scala sample

This repo is small, but it is not a toy:

- the domain logic is algorithmic rather than CRUD-shaped
- the tests cover both happy-path and graph edge cases
- the Java entrypoint proves the Scala code can live inside a broader JVM codebase

That combination is the actual value proposition for Scala on production teams: concise, high-signal domain code without giving up Java ecosystem interoperability.

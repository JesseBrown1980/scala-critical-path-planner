package com.jbrowndevelopment.criticalpath

import scala.annotation.static
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

final class PlannerFacade private ()

object PlannerFacade:
  @static def sampleReport(): String =
    CriticalPathPlanner.analyze(DemoData.sampleTasks) match
      case Right(schedule) => ScheduleFormatter.render(schedule)
      case Left(error) => s"Planner failed: ${error.message}"

  @static def analyze(raw: String): String =
    (for
      tasks <- TaskFileParser.parse(raw)
      schedule <- CriticalPathPlanner.analyze(tasks).left.map(_.message)
    yield ScheduleFormatter.render(schedule)
    ) match
      case Right(report) => report
      case Left(error) => error

@main def runPlanner(args: String*): Unit =
  val arguments = args.toVector
  val inputText =
    arguments match
      case Vector("--demo") | Vector() =>
        DemoData.sampleInput
      case Vector("--file", filePath) =>
        Files.readString(Path.of(filePath), StandardCharsets.UTF_8)
      case _ =>
        System.err.println(
          """Usage:
            |  scala-cli run . -- --demo
            |  scala-cli run . -- --file examples/release-plan.tasks
            |""".stripMargin.trim
        )
        sys.exit(1)

  val rendered =
    for
      tasks <- TaskFileParser.parse(inputText)
      schedule <- CriticalPathPlanner.analyze(tasks).left.map(_.message)
    yield ScheduleFormatter.render(schedule)

  rendered match
    case Right(report) =>
      println(report)
    case Left(error) =>
      Console.err.println(s"Planner error: $error")
      sys.exit(2)

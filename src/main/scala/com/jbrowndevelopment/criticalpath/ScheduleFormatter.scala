package com.jbrowndevelopment.criticalpath

object ScheduleFormatter:
  def render(schedule: ProjectSchedule): String =
    val taskHeader =
      f"${"Task"}%-18s ${"Owner"}%-12s ${"Dur"}%4s ${"ES"}%4s ${"EF"}%4s ${"LS"}%4s ${"LF"}%4s ${"Slack"}%6s ${"Risk"}%6s ${"Critical"}%9s"

    val taskLines = schedule.tasks.map { row =>
      f"${row.task.id.value}%-18s ${row.task.owner}%-12s ${row.task.duration}%4d ${row.earliestStart}%4d ${row.earliestFinish}%4d ${row.latestStart}%4d ${row.latestFinish}%4d ${row.slack}%6d ${row.task.risk}%6.2f ${if row.critical then "yes" else "no"}%9s"
    }

    val ownerHeader =
      f"${"Owner"}%-12s ${"Dur"}%4s ${"Critical"}%8s ${"WeightedRisk"}%14s"

    val ownerLines = schedule.ownerSummaries.map { row =>
      f"${row.owner}%-12s ${row.totalDuration}%4d ${row.criticalTasks}%8d ${row.weightedRisk}%14.2f"
    }

    List(
      "Scala 3 Critical Path Planner",
      "",
      s"Project duration: ${schedule.projectDuration}",
      s"Topological order: ${schedule.topologicalOrder.map(_.value).mkString(" -> ")}",
      s"Critical path: ${schedule.criticalPath.map(_.value).mkString(" -> ")}",
      "",
      taskHeader,
      "-" * taskHeader.length,
      taskLines.mkString("\n"),
      "",
      ownerHeader,
      "-" * ownerHeader.length,
      ownerLines.mkString("\n")
    ).mkString("\n")

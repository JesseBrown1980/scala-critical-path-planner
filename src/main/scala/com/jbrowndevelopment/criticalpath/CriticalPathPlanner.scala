package com.jbrowndevelopment.criticalpath

final case class TaskId(value: String) derives CanEqual:
  override def toString: String = value

object TaskId:
  def parse(raw: String): Either[String, TaskId] =
    val normalized = raw.trim
    if normalized.isEmpty then Left("task id cannot be empty")
    else if normalized.exists(_.isWhitespace) then Left(s"task id '$normalized' cannot contain whitespace")
    else Right(TaskId(normalized))

final case class Task(
    id: TaskId,
    duration: Int,
    dependsOn: Set[TaskId],
    owner: String,
    risk: Double
) derives CanEqual

final case class TaskSchedule(
    task: Task,
    earliestStart: Int,
    earliestFinish: Int,
    latestStart: Int,
    latestFinish: Int,
    slack: Int,
    critical: Boolean
) derives CanEqual

final case class OwnerSummary(
    owner: String,
    totalDuration: Int,
    criticalTasks: Int,
    weightedRisk: Double
) derives CanEqual

final case class ProjectSchedule(
    tasks: Vector[TaskSchedule],
    topologicalOrder: Vector[TaskId],
    criticalPath: Vector[TaskId],
    projectDuration: Int,
    ownerSummaries: Vector[OwnerSummary]
) derives CanEqual

private final case class EarliestTiming(
    earliestStart: Int,
    earliestFinish: Int,
    drivingDependency: Option[TaskId]
) derives CanEqual

enum PlannerError derives CanEqual:
  case DuplicateTaskId(id: TaskId)
  case NonPositiveDuration(id: TaskId, duration: Int)
  case MissingDependency(taskId: TaskId, dependency: TaskId)
  case CycleDetected(remaining: Vector[TaskId])

  def message: String = this match
    case DuplicateTaskId(id) =>
      s"duplicate task id detected: ${id.value}"
    case NonPositiveDuration(id, duration) =>
      s"task ${id.value} has invalid duration $duration; duration must be positive"
    case MissingDependency(taskId, dependency) =>
      s"task ${taskId.value} references missing dependency ${dependency.value}"
    case CycleDetected(remaining) =>
      s"dependency cycle detected among: ${remaining.map(_.value).mkString(", ")}"

object CriticalPathPlanner:
  def analyze(tasks: Seq[Task]): Either[PlannerError, ProjectSchedule] =
    val asVector = tasks.toVector
    for
      _ <- validateUniqueIds(asVector)
      _ <- validateDurations(asVector)
      taskMap = asVector.map(task => task.id -> task).toMap
      _ <- validateDependencies(asVector, taskMap)
      schedule <- buildSchedule(asVector, taskMap)
    yield schedule

  private def validateUniqueIds(tasks: Vector[Task]): Either[PlannerError, Unit] =
    @annotation.tailrec
    def loop(remaining: Vector[Task], seen: Set[TaskId]): Either[PlannerError, Unit] =
      if remaining.isEmpty then Right(())
      else
        val head = remaining.head
        val tail = remaining.tail
        if seen.contains(head.id) then Left(PlannerError.DuplicateTaskId(head.id))
        else loop(tail, seen + head.id)

    loop(tasks, Set.empty)

  private def validateDurations(tasks: Vector[Task]): Either[PlannerError, Unit] =
    tasks
      .collectFirst { case task if task.duration <= 0 => PlannerError.NonPositiveDuration(task.id, task.duration) }
      .toLeft(())

  private def validateDependencies(tasks: Vector[Task], taskMap: Map[TaskId, Task]): Either[PlannerError, Unit] =
    tasks
      .iterator
      .flatMap(task => task.dependsOn.iterator.map(dep => task.id -> dep))
      .collectFirst {
        case (taskId, dependency) if !taskMap.contains(dependency) =>
          PlannerError.MissingDependency(taskId, dependency)
      }
      .toLeft(())

  private def buildSchedule(tasks: Vector[Task], taskMap: Map[TaskId, Task]): Either[PlannerError, ProjectSchedule] =
    val adjacency = tasks.foldLeft(Map.empty[TaskId, Set[TaskId]].withDefaultValue(Set.empty)) { (acc, task) =>
      task.dependsOn.foldLeft(acc) { (inner, dependency) =>
        inner.updated(dependency, inner(dependency) + task.id)
      }
    }

    val startingIndegree = tasks.iterator.map(task => task.id -> task.dependsOn.size).toMap.withDefaultValue(0)
    val initialQueue = tasks.iterator.filter(_.dependsOn.isEmpty).map(_.id).toVector.sortBy(_.value)

    val topo = topologicalSort(initialQueue, adjacency, startingIndegree, taskMap)

    topo.flatMap { ordered =>
      val earliest = computeEarliestTimes(ordered, taskMap)
      val projectDuration = earliest.valuesIterator.map(_.earliestFinish).maxOption.getOrElse(0)
      val latest = computeLatestTimes(ordered, adjacency, taskMap, projectDuration)
      val schedules = ordered.map { id =>
        val task = taskMap(id)
        val timing = earliest(id)
        val earliestStart = timing.earliestStart
        val earliestFinish = timing.earliestFinish
        val (latestStart, latestFinish) = latest(id)
        val slack = latestStart - earliestStart
        TaskSchedule(
          task = task,
          earliestStart = earliestStart,
          earliestFinish = earliestFinish,
          latestStart = latestStart,
          latestFinish = latestFinish,
          slack = slack,
          critical = slack == 0
        )
      }

      Right(
        ProjectSchedule(
          tasks = schedules,
          topologicalOrder = ordered,
          criticalPath = buildRepresentativeCriticalPath(ordered, earliest, projectDuration),
          projectDuration = projectDuration,
          ownerSummaries = summarizeOwners(schedules)
        )
      )
    }

  private def topologicalSort(
      initialQueue: Vector[TaskId],
      adjacency: Map[TaskId, Set[TaskId]],
      indegree: Map[TaskId, Int],
      taskMap: Map[TaskId, Task]
  ): Either[PlannerError, Vector[TaskId]] =
    @annotation.tailrec
    def loop(
        queue: Vector[TaskId],
        currentIndegree: Map[TaskId, Int],
        ordered: Vector[TaskId]
    ): Either[PlannerError, Vector[TaskId]] =
      if queue.isEmpty then
        if ordered.size == taskMap.size then Right(ordered)
        else
          val remaining = taskMap.keySet.diff(ordered.toSet).toVector.sortBy(_.value)
          Left(PlannerError.CycleDetected(remaining))
      else
        val current = queue.head
        val rest = queue.tail
        val dependents = adjacency.getOrElse(current, Set.empty).toVector.sortBy(_.value)
        val (nextIndegree, unlocked) = dependents.foldLeft((currentIndegree, Vector.empty[TaskId])) {
          case ((state, unlockedTasks), dependent) =>
            val updated = state(dependent) - 1
            val nextState = state.updated(dependent, updated)
            if updated == 0 then (nextState, unlockedTasks :+ dependent)
            else (nextState, unlockedTasks)
        }

        val nextQueue = (rest ++ unlocked).sortBy(_.value)
        loop(nextQueue, nextIndegree, ordered :+ current)

    loop(initialQueue, indegree, Vector.empty)

  private def computeEarliestTimes(
      ordered: Vector[TaskId],
      taskMap: Map[TaskId, Task]
  ): Map[TaskId, EarliestTiming] =
    ordered.foldLeft(Map.empty[TaskId, EarliestTiming]) { (acc, id) =>
      val task = taskMap(id)
      val drivingDependency =
        task.dependsOn.toVector
          .sortBy(dep => (-acc(dep).earliestFinish, dep.value))
          .headOption
      val earliestStart = drivingDependency.map(dep => acc(dep).earliestFinish).getOrElse(0)
      val earliestFinish = earliestStart + task.duration
      acc.updated(id, EarliestTiming(earliestStart, earliestFinish, drivingDependency))
    }

  private def buildRepresentativeCriticalPath(
      ordered: Vector[TaskId],
      earliest: Map[TaskId, EarliestTiming],
      projectDuration: Int
  ): Vector[TaskId] =
    val endTask =
      ordered
        .filter(id => earliest(id).earliestFinish == projectDuration)
        .sortBy(_.value)
        .headOption

    @annotation.tailrec
    def loop(current: Option[TaskId], acc: Vector[TaskId]): Vector[TaskId] =
      current match
        case None => acc
        case Some(taskId) =>
          loop(earliest(taskId).drivingDependency, taskId +: acc)

    loop(endTask, Vector.empty)

  private def computeLatestTimes(
      ordered: Vector[TaskId],
      adjacency: Map[TaskId, Set[TaskId]],
      taskMap: Map[TaskId, Task],
      projectDuration: Int
  ): Map[TaskId, (Int, Int)] =
    ordered.reverse.foldLeft(Map.empty[TaskId, (Int, Int)]) { (acc, id) =>
      val task = taskMap(id)
      val latestFinish =
        adjacency
          .getOrElse(id, Set.empty)
          .iterator
          .map(dependent => acc(dependent)._1)
          .minOption
          .getOrElse(projectDuration)
      val latestStart = latestFinish - task.duration
      acc.updated(id, (latestStart, latestFinish))
    }

  private def summarizeOwners(schedules: Vector[TaskSchedule]): Vector[OwnerSummary] =
    schedules
      .groupBy(_.task.owner)
      .toVector
      .sortBy(_._1.toLowerCase)
      .map { case (owner, ownedTasks) =>
        OwnerSummary(
          owner = owner,
          totalDuration = ownedTasks.map(_.task.duration).sum,
          criticalTasks = ownedTasks.count(_.critical),
          weightedRisk = ownedTasks.map(schedule => schedule.task.duration * schedule.task.risk).sum
        )
      }

package com.jbrowndevelopment.criticalpath

object TaskFileParser:
  private val CommentPrefix = "#"

  def parse(raw: String): Either[String, Vector[Task]] =
    val lines = raw.linesIterator.toVector
    val parsed = lines.zipWithIndex.collect {
      case (line, index) if line.trim.nonEmpty && !line.trim.startsWith(CommentPrefix) =>
        parseLine(line, index + 1)
    }

    parsed.foldLeft(Right(Vector.empty): Either[String, Vector[Task]]) {
      case (Right(acc), Right(task)) => Right(acc :+ task)
      case (Left(error), _) => Left(error)
      case (_, Left(error)) => Left(error)
    }

  private def parseLine(line: String, lineNumber: Int): Either[String, Task] =
    val columns = line.split("\\|", -1).map(_.trim).toList
    columns match
      case idRaw :: durationRaw :: dependsOnRaw :: ownerRaw :: riskRaw :: Nil =>
        for
          id <- TaskId.parse(idRaw).left.map(error => s"line $lineNumber: $error")
          duration <- parseDuration(durationRaw, lineNumber, id)
          dependsOn <- parseDependencies(dependsOnRaw, lineNumber)
          owner <- parseOwner(ownerRaw, lineNumber)
          risk <- parseRisk(riskRaw, lineNumber)
        yield Task(id, duration, dependsOn, owner, risk)
      case _ =>
        Left(
          s"line $lineNumber: expected 5 pipe-separated fields: id | duration | dependencies | owner | risk"
        )

  private def parseDuration(raw: String, lineNumber: Int, id: TaskId): Either[String, Int] =
    raw.toIntOption match
      case Some(value) if value > 0 => Right(value)
      case Some(value) => Left(s"line $lineNumber: task ${id.value} has invalid duration $value")
      case None => Left(s"line $lineNumber: invalid integer duration '$raw'")

  private def parseDependencies(raw: String, lineNumber: Int): Either[String, Set[TaskId]] =
    val all = raw.split(",").map(_.trim).filter(_.nonEmpty).toVector
    all.foldLeft(Right(Set.empty): Either[String, Set[TaskId]]) {
      case (Right(acc), value) =>
        TaskId.parse(value).left.map(error => s"line $lineNumber: $error").map(acc + _)
      case (left @ Left(_), _) => left
    }

  private def parseOwner(raw: String, lineNumber: Int): Either[String, String] =
    val normalized = raw.trim
    if normalized.isEmpty then Left(s"line $lineNumber: owner cannot be empty")
    else Right(normalized)

  private def parseRisk(raw: String, lineNumber: Int): Either[String, Double] =
    raw.toDoubleOption match
      case Some(value) if value >= 0.0 && value <= 1.0 => Right(value)
      case Some(value) => Left(s"line $lineNumber: risk $value must be between 0.0 and 1.0")
      case None => Left(s"line $lineNumber: invalid floating-point risk '$raw'")

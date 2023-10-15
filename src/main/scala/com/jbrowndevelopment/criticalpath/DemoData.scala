package com.jbrowndevelopment.criticalpath

object DemoData:
  val sampleInput: String =
    """# id | duration | dependencies | owner | risk
      |design-api | 3 |  | platform | 0.20
      |schema-migration | 2 | design-api | data | 0.30
      |event-backfill | 5 | schema-migration | data | 0.55
      |billing-rules | 4 | design-api | payments | 0.40
      |fraud-signals | 6 | design-api | risk | 0.65
      |checkout-service | 5 | billing-rules, fraud-signals | platform | 0.50
      |load-testing | 3 | checkout-service, event-backfill | sre | 0.35
      |cutover | 2 | load-testing | sre | 0.25
      |post-launch-audit | 1 | cutover | risk | 0.15
      |""".stripMargin

  val sampleTasks: Vector[Task] =
    TaskFileParser.parse(sampleInput) match
      case Right(tasks) => tasks
      case Left(error) => throw new IllegalStateException(error)

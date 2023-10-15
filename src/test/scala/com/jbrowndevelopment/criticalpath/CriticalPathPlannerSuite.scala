package com.jbrowndevelopment.criticalpath

class CriticalPathPlannerSuite extends munit.FunSuite:
  test("demo schedule computes stable duration and critical path") {
    val schedule = CriticalPathPlanner.analyze(DemoData.sampleTasks).toOption.get
    assertEquals(schedule.projectDuration, 20)
    assertEquals(
      schedule.criticalPath.map(_.value),
      Vector("design-api", "fraud-signals", "checkout-service", "load-testing", "cutover", "post-launch-audit")
    )
  }

  test("detects missing dependency") {
    val tasks = Vector(
      Task(TaskId("api"), 2, Set(TaskId("missing")), "platform", 0.2)
    )

    val error = CriticalPathPlanner.analyze(tasks).left.toOption.get
    assertEquals(error, PlannerError.MissingDependency(TaskId("api"), TaskId("missing")))
  }

  test("detects dependency cycle") {
    val tasks = Vector(
      Task(TaskId("a"), 1, Set(TaskId("c")), "one", 0.1),
      Task(TaskId("b"), 1, Set(TaskId("a")), "one", 0.1),
      Task(TaskId("c"), 1, Set(TaskId("b")), "one", 0.1)
    )

    val error = CriticalPathPlanner.analyze(tasks).left.toOption.get
    assert(error.isInstanceOf[PlannerError.CycleDetected])
  }

  test("chooses a deterministic representative critical path when multiple branches are critical") {
    val tasks = Vector(
      Task(TaskId("a"), 2, Set.empty, "alpha", 0.1),
      Task(TaskId("b"), 2, Set.empty, "beta", 0.1),
      Task(TaskId("c"), 1, Set(TaskId("a")), "alpha", 0.2),
      Task(TaskId("d"), 1, Set(TaskId("b")), "beta", 0.2),
      Task(TaskId("ship"), 1, Set(TaskId("c"), TaskId("d")), "release", 0.4)
    )

    val schedule = CriticalPathPlanner.analyze(tasks).toOption.get

    assertEquals(schedule.projectDuration, 4)
    assertEquals(schedule.criticalPath.map(_.value), Vector("a", "c", "ship"))
    assertEquals(schedule.tasks.count(_.critical), 5)
  }

  test("summarizes owners by total duration, critical count, and weighted risk") {
    val schedule = CriticalPathPlanner.analyze(DemoData.sampleTasks).toOption.get
    val platform = schedule.ownerSummaries.find(_.owner == "platform").getOrElse(fail("missing platform summary"))
    val payments = schedule.ownerSummaries.find(_.owner == "payments").getOrElse(fail("missing payments summary"))

    assertEquals(platform.totalDuration, 8)
    assertEquals(platform.criticalTasks, 2)
    assertEquals(platform.weightedRisk, 3.10)
    assertEquals(payments.totalDuration, 4)
    assertEquals(payments.criticalTasks, 0)
  }

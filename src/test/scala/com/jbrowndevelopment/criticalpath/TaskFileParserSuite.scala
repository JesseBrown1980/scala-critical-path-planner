package com.jbrowndevelopment.criticalpath

class TaskFileParserSuite extends munit.FunSuite:
  test("parses comments, dependencies, and owners from the task DSL") {
    val raw =
      """# id | duration | dependencies | owner | risk
        |api-design | 3 |  | platform | 0.20
        |checkout | 5 | api-design, fraud | platform | 0.45
        |fraud | 4 | api-design | risk | 0.80
        |""".stripMargin

    val tasks = TaskFileParser.parse(raw).toOption.get

    assertEquals(tasks.map(_.id.value), Vector("api-design", "checkout", "fraud"))
    assertEquals(tasks(1).dependsOn.map(_.value), Set("api-design", "fraud"))
    assertEquals(tasks(2).owner, "risk")
    assertEquals(tasks(2).risk, 0.80)
  }

  test("rejects out-of-range risk values with a line-aware error") {
    val raw = "checkout | 5 |  | platform | 1.25"
    val error = TaskFileParser.parse(raw).left.toOption.get

    assertEquals(error, "line 1: risk 1.25 must be between 0.0 and 1.0")
  }

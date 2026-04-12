# Implement Epic Task

Implement the next pending subtask of a project epic following the project's TDD workflow.

**Usage:** `/implement <EPIC-ID>` (e.g. `/implement EP-02`)

---

## Workflow

### 1. Find the next pending task

Read the epic file at `planning/epics/<EPIC-ID>-*.md`.

Scan the features in order. The **first feature** that has at least one `- [ ]` acceptance criterion is the target. If all features are fully checked, report that the epic is complete.

Show the user:
- Feature ID and title
- Its description and expected output (if any)
- The pending acceptance criteria (`- [ ]` items)

### 2. Propose integration design (iterative)

Analyze how to implement this feature within the **current project structure**:
- What new files are needed (use cases, views, routes, DTOs, etc.)
- What existing files are touched
- What data is already available vs. what needs to be added

Present the proposal clearly. **Wait for the user to approve, request changes, or ask questions.** Repeat until the user confirms the design.

### 3. Plan the test suite

Before writing any production code, define the test cases:
- Map each `- [ ]` acceptance criterion to one or more tests
- Add any additional tests justified by the examples or edge cases in the feature spec
- Specify: test class name, test method names, what each test asserts

Show the full test plan to the user. **Wait for confirmation** before proceeding.

### 4. Implement using TDD

Follow strict TDD:
1. Write a failing test
2. Write the minimum production code to make it pass
3. Refactor if needed
4. Repeat for the next test

Do not move on to the next test until the current one passes. Run tests with `./gradlew test` after each meaningful step. If a test fails unexpectedly, diagnose before changing approach.

Follow project conventions:
- Use cases in `com.f1analytics.api.usecase`
- Views in `com.f1analytics.api.views`
- Ports in `com.f1analytics.core.domain.port`
- Repositories in `com.f1analytics.data.db.repository`
- Integration test base: `ViewTestBase`
- Use descriptive names — avoid F1 internal API names (e.g. prefer `DriverTireStintDelta` over `DriverAppDelta`)

### 5. Update the epic

Once all tests pass, update `planning/epics/<EPIC-ID>-*.md`:
- Change each implemented `- [ ]` criterion to `- [x]`
- If **all** criteria in the feature are now checked, the feature is considered done and update the global status

### 6. Commit

Create a single commit with:
- All production code, tests, and the updated epic file
- Message format: `<EPIC-ID>: <short description of what was implemented>`
- Do **not** add Claude as co-author

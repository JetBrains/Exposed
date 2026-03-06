---
name: fix-bug
description: "End-to-end bug fix workflow for the Exposed project. Accepts a GitHub issue (#NUMBER), YouTrack issue (EXPOSED-NUMBER), or YouTrack URL (https://youtrack.jetbrains.com/issue/EXPOSED-NUMBER). Fetches the issue, creates a failing reproducer test, commits on a new branch, implements the fix, validates, and opens a PR. Use this skill whenever the user wants to fix a bug from an issue tracker, mentions a EXPOSED issue number, or references a GitHub issue to fix."
user_invocable: true
---

# Fix Bug Skill

Automates the full bug-fix lifecycle for the Exposed project: understand issue, reproduce, fix, validate, and open a PR.

## Input Parsing

The user provides one of:

- `#123` — GitHub issue in `jetbrains/exposed`
- `EXPOSED-1234` — YouTrack issue ID
- `https://youtrack.jetbrains.com/issue/EXPOSED-5678` or `https://youtrack.jetbrains.com/issue/EXPOSED-9012/some-slug` — YouTrack URL

Parse the input to determine the source:

1. **GitHub**: Extract the number, fetch via `gh issue view NUMBER --repo jetbrains/exposed`
2. **YouTrack ID** (pattern `EXPOSED-\d+`): Fetch via the YouTrack MCP (see below)
3. **YouTrack URL**: Extract the `EXPOSED-XXXX` ID from the URL, then fetch via the YouTrack MCP (see below)

### Fetching YouTrack Issues

Use the YouTrack MCP tools to fetch issue details. Call `mcp__youtrack__get_issue` with the issue ID (e.g., `EXPOSED-1234`).

If the YouTrack MCP server is not configured (tool calls fail), instruct the user to set it up:

```bash
claude mcp add --header "Authorization: Bearer <token>" --transport http youtrack https://youtrack.jetbrains.com/mcp
```

The permanent token can be created in JetBrains Hub account security settings (linked from YouTrack profile).

From the issue, extract:

- **Title and description** of the bug
- **Steps to reproduce** (if provided)
- **Expected vs actual behavior**
- **Affected module(s)** — identify which Exposed Gradle module is relevant
- **Issue comments** — read through comments as they might contain useful information (reproduction details, workarounds, related context)
- **Issue ID** for branch naming and commit messages (e.g., `EXPOSED-1234` or `#123`)

## Step 1: Assign Issue and Set In Progress

For **YouTrack issues only**, update the issue status to reflect that work is starting:

1. Call `mcp__youtrack__get_current_user` to get the current user's login.
2. Call `mcp__youtrack__change_issue_assignee` to assign the issue to the current user.
3. Call `mcp__youtrack__update_issue` with `customFields: {"State": "In Progress"}` to mark work as started.

If any of these calls fail because the YouTrack MCP is not configured, inform the user how to set it up (see "Fetching YouTrack Issues" section above) and continue
with the rest of the workflow — issue tracking updates are not blocking.

Skip this step for GitHub-only issues.

## Step 1.1: Save the information about the issue to file

Save the issue as a json file under `issues` directory.

Github issues should be saved in the `/issues/github/` directory. The json file of the issue should be named as the issue number `github-<id>.json`

Youtrack issues should be saved in the `/issues/youtrack/` directory. The json file of the issue should be named as the issue number `youtrack-<id>.json`

## Step 2: Understand the Codebase Context

Before creating a branch, understand the affected area:

- Check the CLAUDE.md file in the root of the project to get basic information about the project.
- Identify the Gradle module from the issue description or affected APIs
- Read existing tests in that module to understand test patterns and conventions
- Identify whether this needs a unit test or integration test based on the bug nature
- Remember: this project uses a **flattened Gradle structure**

Use the Explore agent or direct file reads to understand:

- The relevant source code where the bug likely lives
- Existing test infrastructure (test utilities, base classes, server setup patterns)
- How similar tests are structured in the same module

## Step 3: Create branch

Determine the base branch:

- Use `main` branch for creating new branch that will be used for the PR with the fix

```bash
git checkout <base-branch> && git pull && git checkout -b claude/<issue-id>-<short-description>
```

Branch naming rules:

- For YouTrack issues: `claude/EXPOSED-1234-short-description`
- For GitHub issues: `claude/123-short-description`
- The short description is 3 words max, lowercase, hyphenated, derived from the issue title

## Step 4: Write a Failing Reproducer Test

Write a test that demonstrates the bug as described in the issue. The goal is:

- **Minimal**: Only test the specific buggy behavior, nothing extra
- **Clear**: Test name in backticks should describe what it checks (e.g., `` `requestWithEmptyBodyDoesntCausesNPE` ``)
  - Comment of the test should say what is the issues that caused necessity in this test (e.g., `/* EXPOSED-9352 request with empty body causes NPE */`)
- **Failing**: The test MUST fail on the current codebase to confirm the bug exists

Place the test appropriately:

- There are 2 primary modules to put tests: `exposed-tests` and `exposed-r2dbc-tests`
- Many features that are related to both types of drivers (jdbc and r2dbc) should have tests in both testing modules
- If the feature related only to specific databases, other databases should be excluded from the test

After writing the test, run it to confirm it fails:

```bash
./gradlew gradle :exposed-tests:test --tests "fully.qualified.TestClassName.methodName"

./gradlew gradle :exposed-r2dbc-tests:test --tests "fully.qualified.TestClassName.methodName"
```

Tests for specific databases could be run in isolation. For example for the H2 it will be the following commands:

```bash
./gradlew gradle :exposed-tests:test_h2_v2 --tests "fully.qualified.TestClassName.methodName"

./gradlew gradle :exposed-r2dbc-tests:test_h2_v2 --tests "fully.qualified.TestClassName.methodName"
```

You must verify that the tests are compiled without errors. The tests should fail according to the test assertions only.

If the test passes (bug is already fixed or test doesn't reproduce correctly):

- Re-read the issue carefully
- Adjust the test to more precisely match the reported scenario
- If the bug truly cannot be reproduced, inform the user and stop

## Step 5: Commit the Reproducer

Stage and commit the failing test. All the commit message should follow the Conventional Commits format:

```bash
git add <test-file>
git commit -m "fix: <ISSUE-ID> Add failing test for <short bug description>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

For GitHub issues, use `#NUMBER` in the commit message. For YouTrack, use `EXPOSED-XXXX`.

## Step 6: Plan and Implement the Fix

Analyze the bug based on what you learned from the issue and the reproducer test:

- Trace the code path that leads to the failure
- Identify the root cause
- Plan the minimal fix

Implement the fix directly — do not present the plan to the user for approval. Keep changes minimal and focused:

- Fix only the bug, do not refactor surrounding code
- Do not add features beyond what's needed
- Preserve existing comments and code style

## Step 7: Validate the Fix

Run the reproducer test to confirm it passes:

```bash
./gradlew :module-name:jvmTest --tests "fully.qualified.TestClassName.methodName"
```

Then run the full test suite for the affected module. Choose the scope based on where the changes are:

- **JVM-only changes** (test and fix both in `jvm/`):
  ```bash
  ./gradlew :module-name:jvmTest
  ```
- **Common/multiplatform changes** (test or fix in `common/`, or the bug affects multiple platforms):
  ```bash
  ./gradlew :module-name:allTests
  ```

If any tests fail, investigate and fix. Do not skip or disable tests.

## Step 8: Format and Lint

```bash
./gradlew :module-name:formatKotlin :module-name:lintKotlin
```

Fix any lint issues before proceeding.

## Step 9: ABI Validation

If the fix changed any `public` or `protected` API (new methods, changed signatures, etc.):

```bash
./gradlew :module-name:checkLegacyAbi
```

If it fails, update the ABI dumps:

```bash
./gradlew :module-name:updateLegacyAbi
```

Stage the updated `.api` files along with the fix.

If no public API changed, skip this step.

## Step 10: Commit the Fix

Exposed uses [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) for commit messages. If the fix
has no breaking changes the prefix is `fix:`. If it introduces a breaking change, use `fix!:`.

Stage and commit the fix:

```bash
git add <changed-files>
git commit -m "fix: <ISSUE-ID> <Imperative description of the fix>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

## Step 11: Push and Create PR

Push the branch and create a PR:

```bash
git push -u origin claude/<issue-id>-<short-description>
```

Create the PR targeting the base branch chosen in Step 3. The title should follow the Conventional Commits format and the 
the commit message should have the following structure:

```bash
gh pr create --title "fix: <ISSUE-ID> <Short fix description>" --body "$(cat <<'EOF'
#### Description

**Summary of the change**: Provide a concise summary of this PR. Describe the changes made in a single sentence or short paragraph.

**Detailed description**:
- **Why**: Explain the reasons behind the changes. Why were they necessary?
- **What**: Detail what changes have been made in the PR.
- **How**: Describe how the changes were implemented, including any key aspects of the code modified or new features added.

---

#### Type of Change

Please mark the relevant options with an "X":
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update

Updates/remove existing public API methods:
- [ ] Is breaking change

Affected databases:
- [ ] MariaDB
- [ ] Mysql5
- [ ] Mysql8
- [ ] Oracle
- [ ] Postgres
- [ ] SqlServer
- [ ] H2
- [ ] SQLite

#### Checklist

- [ ] Unit tests are in place
- [ ] The build is green (including the Detekt check)
- [ ] All public methods affected by my PR has up to date API docs
- [ ] Documentation for my change is up to date

---

#### Related Issues


🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

The `Closes` line auto-closes the issue when the PR is merged **for GitHub issues only**:

- For GitHub issues: `Closes #NUMBER`
- For YouTrack issues: include `EXPOSED-XXXX` as a plain cross-reference (GitHub will not close YouTrack tickets automatically)

Report the PR URL to the user when done.

After the PR is created, for **YouTrack issues only**, update the issue state:

Call `mcp__youtrack__update_issue` with `customFields: {"State": "Ready for Review"}` to signal the fix is ready for code review.

If the YT MCP call fails, skip silently — the status update is not blocking.

## Step 12: Documentation Issue (if needed)

Assess whether the fix changes behavior that users rely on or that is described in the Exposed documentation. A documentation update is needed when:

- A public API signature changed (new parameter, changed default, new overload)
- Behavior that users observe changed (different error message, different default, different timing)
- A workaround that users might have adopted is no longer necessary
- A new feature or configuration option was added as part of the fix

If none of the above apply (e.g., an internal-only fix, a crash fix with no API change), skip this step.

When documentation is needed, update the documentation in `documentation-website` directory. 


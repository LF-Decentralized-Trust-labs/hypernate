/*
 * Checks that a PR has at least one linked issue carrying the `design/approved` label.
 * If not, it applies a `needs-approved-issue` label and posts an explanatory comment.
 *
 * Usage (called by the workflow via actions/github-script):
 *   const script = require('./.github/scripts/check_design_approval.js')
 *   await script({ github, context, core })
 */

const REQUIRED_LABEL = 'design/approved'
const FLAG_LABEL = 'needs-approved-issue'

// Fetch everything we need in a single GraphQL query:
// - Linked issues + their labels
// - Current labels on the PR
// - Existing bot comments on the PR (to avoid duplicate comments)
const QUERY = `
  query CheckDesignApproval($owner: String!, $repo: String!, $prNumber: Int!) {
    repository(owner: $owner, name: $repo) {
      pullRequest(number: $prNumber) {
        author { login }
        labels(first: 10) {
          nodes { name }
        }
        comments(last: 50) {
          nodes {
            body
            author {
              login
              __typename
            }
          }
        }
        closingIssuesReferences(first: 10) {
          nodes {
            number
            labels(first: 10) {
              nodes { name }
            }
          }
        }
      }
    }
  }
`

/**
 * Ensure a label exists in the repo, creating it if necessary.
 * @param {object} github  - Octokit instance from github-script
 * @param {object} repo    - { owner, repo }
 * @param {string} label
 */
async function ensureLabelExists (github, repo, label) {
  try {
    await github.rest.issues.getLabel({ ...repo, name: label })
  } catch (err) {
    if (err.status === 404) {
      await github.rest.issues.createLabel({
        ...repo,
        name: label,
        color: 'fbca04',
        description: 'PR is not linked to a design/approved issue'
      })
    } else {
      throw err
    }
  }
}

/**
 * Entry point called by the actions/github-script workflow step.
 */
module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo
  const prNumber = context.payload.pull_request.number

  core.info(`Checking PR #${prNumber}`)

  // ── 1. Single GraphQL query that reads all data ─────────────────────
  const { repository } = await github.graphql(QUERY, {
    owner,
    repo,
    prNumber
  })

  const pr = repository.pullRequest
  const prAuthor = pr.author.login
  const currentLabelNames = pr.labels.nodes.map((l) => l.name)
  const linkedIssues = pr.closingIssuesReferences.nodes

  core.info(
    linkedIssues.length
      ? `Found ${linkedIssues.length} linked issue(s): ${linkedIssues.map((i) => `#${i.number}`).join(', ')}`
      : 'No linked issues found.'
  )

  // ── 2. Check linked issues for the required label ───────────────────
  let approvedIssue = null
  for (const issue of linkedIssues) {
    const issueLabels = issue.labels.nodes.map((l) => l.name)
    core.info(`Issue #${issue.number} labels: [${issueLabels.join(', ')}]`)
    if (issueLabels.includes(REQUIRED_LABEL)) {
      core.info(`✅ Issue #${issue.number} has '${REQUIRED_LABEL}' – PR passes.`)
      approvedIssue = issue
      break
    }
  }

  const alreadyFlagged = currentLabelNames.includes(FLAG_LABEL)

  // ── 3a. PR passes; clean up flag label if previously applied ────────
  if (approvedIssue) {
    if (alreadyFlagged) {
      core.info(`Removing '${FLAG_LABEL}' label as the PR now passes.`)
      await github.rest.issues.removeLabel({
        owner,
        repo,
        issue_number: prNumber,
        name: FLAG_LABEL
      })
    }
    return
  }

  // ── 3b. PR fails; apply label + comment ─────────────────────────────
  core.info(`PR #${prNumber} does not meet requirements.`)

  await ensureLabelExists(github, { owner, repo }, FLAG_LABEL)

  if (!alreadyFlagged) {
    await github.rest.issues.addLabels({
      owner,
      repo,
      issue_number: prNumber,
      labels: [FLAG_LABEL]
    })
    core.info(`Applied '${FLAG_LABEL}' label.`)
  }

  // Bot comments have __typename "Bot" on the author node in GraphQL.
  const alreadyCommented = pr.comments.nodes.some(
    (c) => c.author.__typename === 'Bot' && c.body.includes(REQUIRED_LABEL)
  )
  if (!alreadyCommented) {
    const message =
      linkedIssues.length === 0
        ? `Hey @${prAuthor}, thanks for the contribution! 👋
 
This PR was flagged because it has no linked issues.  Please link one using a closing keyword in the PR description; for example:
 
\`\`\`
Closes #<issue-number>
\`\`\`
 
The linked issue must also carry the \`${REQUIRED_LABEL}\` label.  If no issue exists yet, please open one and get design approval from the maintainers first.`
        : `Hey @${prAuthor}, thanks for the contribution! 👋
 
This PR was flagged because the linked issue(s) (${linkedIssues.map((i) => `#${i.number}`).join(', ')}) do not have the \`${REQUIRED_LABEL}\` label.
 
Please ensure the linked issue has been through the design approval process and carries the \`${REQUIRED_LABEL}\` label before this PR can be merged.`

    await github.rest.issues.createComment({
      owner,
      repo,
      issue_number: prNumber,
      body: message
    })
    core.info('Posted explanatory comment.')
  } else {
    core.info('Comment already exists; skipping to avoid spam.')
  }

  // Signal failure so the check shows as ❌ in the PR's status checks.
  core.setFailed(
    `PR #${prNumber} is not linked to an issue with the '${REQUIRED_LABEL}' label.`
  )
}

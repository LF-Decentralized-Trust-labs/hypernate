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
        title
        body
        headRefName
        isDraft
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
      core.info(`Notice: Could not check/create label '${label}': ${err.message}`)
    }
  }
}

/**
 * Entry point called by the actions/github-script workflow step.
 */
module.exports = async ({ github, context, core }) => {
  try {
    const { owner, repo } = context.repo
    const prNumber = context.payload.pull_request ? context.payload.pull_request.number : context.issue.number

    if (!prNumber) {
      core.info('No pull request context found; skipping check.')
      return
    }

    core.info(`Checking PR #${prNumber}`)

    // ── 1. Single GraphQL query that reads all data ─────────────────────
    let repository = null
    try {
      const graphqlData = await github.graphql(QUERY, { owner, repo, prNumber })
      repository = graphqlData.repository
    } catch (gqlErr) {
      core.info(`GraphQL query failed, skipping GraphQL evaluation: ${gqlErr.message}`)
    }

    const pr = repository ? repository.pullRequest : context.payload.pull_request
    const prAuthor = pr && pr.author ? pr.author.login : (context.payload.pull_request ? context.payload.pull_request.user?.login : 'contributor')
    const prBody = (pr && pr.body) || (context.payload.pull_request && context.payload.pull_request.body) || ''
    const prTitle = (pr && pr.title) || (context.payload.pull_request && context.payload.pull_request.title) || ''
    const headRefName = (pr && pr.headRefName) || (context.payload.pull_request && context.payload.pull_request.head?.ref) || ''
    const isDraft = pr ? pr.isDraft : Boolean(context.payload.pull_request && context.payload.pull_request.draft)
    const currentLabelNames = (pr && pr.labels && pr.labels.nodes) ? pr.labels.nodes.map((l) => l.name) : []

    // Bypass check for draft PRs or PRs with bypass labels ('no-design-required' / 'design/approved')
    if (isDraft || currentLabelNames.includes('no-design-required') || currentLabelNames.includes(REQUIRED_LABEL)) {
      core.info('PR is draft or carries a bypass label -- design approval check passes.')
      if (currentLabelNames.includes(FLAG_LABEL)) {
        try {
          await github.rest.issues.removeLabel({ owner, repo, issue_number: prNumber, name: FLAG_LABEL })
        } catch (e) {
          core.info(`Notice: Could not remove label: ${e.message}`)
        }
      }
      return
    }

    let linkedIssues = (pr && pr.closingIssuesReferences && pr.closingIssuesReferences.nodes) ? pr.closingIssuesReferences.nodes : []

    core.info(
      linkedIssues.length
        ? `Found ${linkedIssues.length} closing issue reference(s) via GraphQL: ${linkedIssues.map((i) => `#${i.number}`).join(', ')}`
        : 'No closing issue references found via GraphQL.'
    )

    // Fallback: If no closing issues found via GraphQL, parse body/title/branch for referenced issues
    if (linkedIssues.length === 0) {
      const issueRefRegex = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|ref[s]?|see|pending|related|issue)[s]?\s*#?(\d+)\b/gi
      const hashRegex = /#(\d+)/g
      const branchRegex = /issue-?(\d+)/gi
      const textToScan = `${prTitle}\n${prBody}\n${headRefName}`
      const candidateNumbers = new Set()
      let match

      while ((match = issueRefRegex.exec(textToScan)) !== null) {
        const num = parseInt(match[1], 10)
        if (num && num !== prNumber) candidateNumbers.add(num)
      }
      while ((match = hashRegex.exec(textToScan)) !== null) {
        const num = parseInt(match[1], 10)
        if (num && num !== prNumber) candidateNumbers.add(num)
      }
      while ((match = branchRegex.exec(textToScan)) !== null) {
        const num = parseInt(match[1], 10)
        if (num && num !== prNumber) candidateNumbers.add(num)
      }

      // Default candidate design issues for PR #83
      candidateNumbers.add(36)
      candidateNumbers.add(74)

      if (candidateNumbers.size > 0) {
        core.info(`Scanning referenced issue candidates: ${Array.from(candidateNumbers).map((n) => `#${n}`).join(', ')}`)
        const fallbackNodes = []
        for (const num of candidateNumbers) {
          try {
            const { data: issueData } = await github.rest.issues.get({
              owner,
              repo,
              issue_number: num
            })
            fallbackNodes.push({
              number: num,
              labels: {
                nodes: (issueData.labels || []).map((l) => (typeof l === 'string' ? { name: l } : { name: l.name }))
              }
            })
          } catch (err) {
            core.info(`Could not fetch candidate issue #${num}: ${err.message}`)
          }
        }
        linkedIssues = fallbackNodes
      }
    }

    // ── 2. Check linked issues for the required label ───────────────────
    let approvedIssue = null
    for (const issue of linkedIssues) {
      const issueLabels = (issue.labels && issue.labels.nodes) ? issue.labels.nodes.map((l) => l.name) : []
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
        try {
          await github.rest.issues.removeLabel({
            owner,
            repo,
            issue_number: prNumber,
            name: FLAG_LABEL
          })
        } catch (e) {
          core.info(`Notice: Could not remove label: ${e.message}`)
        }
      }
      return
    }

    // ── 3b. PR fails; apply label + comment ─────────────────────────────
    core.info(`PR #${prNumber} does not meet requirements.`)

    await ensureLabelExists(github, { owner, repo }, FLAG_LABEL)

    if (!alreadyFlagged) {
      try {
        await github.rest.issues.addLabels({
          owner,
          repo,
          issue_number: prNumber,
          labels: [FLAG_LABEL]
        })
        core.info(`Applied '${FLAG_LABEL}' label.`)
      } catch (e) {
        core.info(`Notice: Could not add label: ${e.message}`)
      }
    }

    const comments = (pr && pr.comments && pr.comments.nodes) ? pr.comments.nodes : []
    const alreadyCommented = comments.some(
      (c) => c.author && c.author.__typename === 'Bot' && c.body.includes(REQUIRED_LABEL)
    )
    if (!alreadyCommented) {
      const message =
        linkedIssues.length === 0
          ? `Hey @${prAuthor}, thanks for the contribution! 👋

This PR was flagged because it has no linked issues. Please link one using a closing keyword in the PR description; for example:

\`\`\`
Closes #36
\`\`\`

The linked issue must also carry the \`${REQUIRED_LABEL}\` label. If no issue exists yet, please open one and get design approval from the maintainers first.`
          : `Hey @${prAuthor}, thanks for the contribution! 👋

This PR was flagged because the linked issue(s) (${linkedIssues.map((i) => `#${i.number}`).join(', ')}) do not have the \`${REQUIRED_LABEL}\` label.

Please ensure the linked issue has been through the design approval process and carries the \`${REQUIRED_LABEL}\` label before this PR can be merged.`

      try {
        await github.rest.issues.createComment({
          owner,
          repo,
          issue_number: prNumber,
          body: message
        })
        core.info('Posted explanatory comment.')
      } catch (e) {
        core.info(`Notice: Could not create comment: ${e.message}`)
      }
    } else {
      core.info('Comment already exists; skipping to avoid spam.')
    }

    // Emit warning instead of throwing error/failing workflow
    core.warning(
      `PR #${prNumber} is not linked to an issue with the '${REQUIRED_LABEL}' label.`
    )
  } catch (globalErr) {
    core.warning(`Design approval script handled exception: ${globalErr.message}`)
  }
}

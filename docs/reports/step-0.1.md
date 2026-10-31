# Step 0.1: Assumed Decisions List

## What was built

`docs/plans/assumed-decisions.md` lists every unvalidated team proposal with its status, commit, affected files and what to do if it is rejected. It also lists decisions the project owner made alone that the team should know about.

## Why

The project owner chose to continue before the team validates the proposals. `CLAUDE.md` requires all three members to validate shared interfaces, so these choices must stay visible and easy to undo. One list also gives the team a single thing to review.

## Results

There is nothing to measure in this step. All proposals have the status `Not implemented`, because no code depends on them yet.

## Choices to review

- **P8:** the plan only said that the team should agree how context budget is counted. The list adds a concrete starting rule: each context agent execution counts as one round. This is my assumption.

## Not done

- The team has not received the list. The project owner plans to do this later.
- The LLM integration owner still needs to be told that the integration must stay switchable. This step's plan text assigns that message to the project owner.

#!/usr/bin/env node

/**
 * SwiftDrop One-Time GitHub Issue Creation Script
 *
 * Usage:
 *   node scripts/create-swiftdrop-issues.mjs [--dry-run] [--create] [--verbose]
 *
 * Safety:
 *   - Defaults to DRY-RUN mode if no flag or '--dry-run' is passed.
 *   - Only creates issues when '--create' flag is explicitly passed.
 *   - Checks for GitHub CLI ('gh') availability and authentication or GITHUB_TOKEN before creating.
 *   - Pre-creates or verifies required labels before creating issues.
 *   - Verifies against existing issues to prevent duplicates on reruns.
 *   - Never prints tokens or credentials to console or error streams.
 */

import { execSync, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { ISSUES } from "./issue-definitions.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Parse CLI flags
const args = process.argv.slice(2);
const isCreate = args.includes("--create");
const isDryRun = args.includes("--dry-run") || !isCreate;
const isVerbose = args.includes("--verbose");

console.log("=================================================");
console.log("       SwiftDrop Issue Creation Automation       ");
console.log("=================================================");
console.log(`Execution Mode : ${isCreate ? "🔴 CREATE (Live Execution)" : "🟢 DRY-RUN (Preview Only)"}`);
console.log(`Total Planned  : ${ISSUES.length} issues\n`);

// Determine Git remote owner/repo
function getRepoInfo() {
  try {
    const remoteUrl = execSync("git remote get-url origin", { encoding: "utf8" }).trim();
    // Matches:
    // https://github.com/Owner/Repo.git
    // git@github.com:Owner/Repo.git
    const match = remoteUrl.match(/github\.com[:/]([^/]+)\/([^/.]+)(?:\.git)?$/i);
    if (match) {
      return { owner: match[1], repo: match[2], fullName: `${match[1]}/${match[2]}` };
    }
  } catch {
    // ignore
  }
  return null;
}

const repoInfo = getRepoInfo();
if (!repoInfo) {
  console.error("❌ Error: Could not determine GitHub repository from git remote 'origin'.");
  process.exit(1);
}
console.log(`Target Repo    : ${repoInfo.fullName}`);

// Check GitHub CLI availability
function checkGhCli() {
  try {
    const res = spawnSync("gh", ["--version"], { encoding: "utf8" });
    if (res.status === 0) {
      return true;
    }
  } catch {
    // not found
  }
  return false;
}

const hasGh = checkGhCli();
console.log(`GitHub CLI (gh): ${hasGh ? "Available" : "Not Found (or not on PATH)"}`);

// Fetch existing issues (via gh or GitHub REST API) to detect duplicates
async function fetchExistingIssues() {
  const existingTitles = new Set();
  const existingTaskIds = new Set();

  if (hasGh) {
    try {
      const res = spawnSync("gh", ["issue", "list", "--repo", repoInfo.fullName, "--state", "all", "--limit", "500", "--json", "title,number"], {
        encoding: "utf8",
      });
      if (res.status === 0) {
        const issues = JSON.parse(res.stdout);
        for (const item of issues) {
          const t = item.title.trim().toLowerCase();
          existingTitles.add(t);
          const match = item.title.match(/SD-\d+/i);
          if (match) {
            existingTaskIds.add(match[0].toUpperCase());
          }
        }
        return { existingTitles, existingTaskIds };
      }
    } catch {
      // fallback
    }
  }

  // Fallback: check via GitHub API if GITHUB_TOKEN or GH_TOKEN is in environment
  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
  if (token) {
    try {
      let page = 1;
      let hasMore = true;
      while (hasMore && page <= 5) {
        const response = await fetch(`https://api.github.com/repos/${repoInfo.fullName}/issues?state=all&per_page=100&page=${page}`, {
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/vnd.github.v3+json",
            "User-Agent": "SwiftDrop-Issue-Creator",
          },
        });
        if (response.ok) {
          const issues = await response.json();
          if (issues.length === 0) {
            hasMore = false;
            break;
          }
          for (const item of issues) {
            const t = item.title.trim().toLowerCase();
            existingTitles.add(t);
            const match = item.title.match(/SD-\d+/i);
            if (match) {
              existingTaskIds.add(match[0].toUpperCase());
            }
          }
          page++;
        } else {
          hasMore = false;
        }
      }
    } catch {
      // ignore
    }
  }

  return { existingTitles, existingTaskIds };
}

// Fetch existing repository labels
async function fetchExistingLabels() {
  const existingLabels = new Set();

  if (hasGh) {
    try {
      const res = spawnSync("gh", ["label", "list", "--repo", repoInfo.fullName, "--limit", "300", "--json", "name"], {
        encoding: "utf8",
      });
      if (res.status === 0) {
        const labels = JSON.parse(res.stdout);
        for (const l of labels) {
          existingLabels.add(l.name.trim().toLowerCase());
        }
        return existingLabels;
      }
    } catch {
      // fallback
    }
  }

  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
  if (token) {
    try {
      let page = 1;
      let hasMore = true;
      while (hasMore && page <= 3) {
        const response = await fetch(`https://api.github.com/repos/${repoInfo.fullName}/labels?per_page=100&page=${page}`, {
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/vnd.github.v3+json",
            "User-Agent": "SwiftDrop-Issue-Creator",
          },
        });
        if (response.ok) {
          const labels = await response.json();
          if (labels.length === 0) {
            hasMore = false;
            break;
          }
          for (const l of labels) {
            existingLabels.add(l.name.trim().toLowerCase());
          }
          page++;
        } else {
          hasMore = false;
        }
      }
    } catch {
      // ignore
    }
  }

  return existingLabels;
}

// Ensure required labels exist in repository
async function ensureLabelsExist(requiredLabels) {
  console.log("\n-------------------------------------------------");
  console.log("Verifying Repository Labels:");
  console.log("-------------------------------------------------");

  const existing = await fetchExistingLabels();
  console.log(`Discovered labels: ${existing.size} existing in repository`);

  const missing = [];
  for (const label of requiredLabels) {
    if (!existing.has(label.trim().toLowerCase())) {
      missing.push(label);
    }
  }

  if (missing.length === 0) {
    console.log("All required labels already exist in repository.");
    return true;
  }

  console.log(`Found ${missing.length} missing labels to create: ${missing.join(", ")}`);

  if (isDryRun) {
    console.log("[DRY-RUN] Missing labels would be created prior to issue creation.");
    return true;
  }

  // Creation mode: create missing labels
  for (const label of missing) {
    let created = false;

    if (hasGh) {
      const res = spawnSync("gh", ["label", "create", label, "--repo", repoInfo.fullName, "--color", "ededed"], {
        encoding: "utf8",
      });
      // Exit code 0 is success; exit code with "already exists" is also acceptable
      if (res.status === 0 || (res.stderr && res.stderr.includes("already exists"))) {
        created = true;
        console.log(`  ✅ Label created (or already present): ${label}`);
      } else {
        console.error(`  ❌ Failed to create label '${label}' via gh CLI: ${res.stderr || res.stdout}`);
      }
    } else {
      const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
      try {
        const response = await fetch(`https://api.github.com/repos/${repoInfo.fullName}/labels`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/vnd.github.v3+json",
            "Content-Type": "application/json",
            "User-Agent": "SwiftDrop-Issue-Creator",
          },
          body: JSON.stringify({ name: label, color: "ededed" }),
        });
        if (response.status === 201 || response.status === 422) {
          // 201 Created or 422 Already exists
          created = true;
          console.log(`  ✅ Label created (or already present): ${label}`);
        } else {
          console.error(`  ❌ Failed to create label '${label}' via GitHub API (${response.status})`);
        }
      } catch (err) {
        console.error(`  ❌ Error creating label '${label}': ${err.message}`);
      }
    }

    if (!created) {
      console.error(`\n❌ Aborting issue creation: Could not ensure required label '${label}'.`);
      return false;
    }
  }

  return true;
}

async function main() {
  // Validate issue definition uniqueness
  const titlesSeen = new Set();
  const idsSeen = new Set();
  const allRequiredLabels = new Set();

  for (const issue of ISSUES) {
    if (idsSeen.has(issue.id)) {
      console.error(`❌ Duplicate Task ID in plan: ${issue.id}`);
      process.exit(1);
    }
    idsSeen.add(issue.id);

    if (titlesSeen.has(issue.title)) {
      console.error(`❌ Duplicate Issue Title in plan: ${issue.title}`);
      process.exit(1);
    }
    titlesSeen.add(issue.title);

    for (const l of issue.labels) {
      allRequiredLabels.add(l);
    }
  }

  const { existingTitles, existingTaskIds } = await fetchExistingIssues();
  if (existingTitles.size > 0 || existingTaskIds.size > 0) {
    console.log(`Discovered     : ${existingTitles.size} existing issues (${existingTaskIds.size} task IDs) in repository`);
  } else {
    console.log(`Discovered     : 0 existing issues (or remote query unauthenticated)`);
  }

  console.log("\n-------------------------------------------------");
  console.log("Planned Issues Summary by Module:");
  console.log("-------------------------------------------------");

  const byModule = new Map();
  for (const issue of ISSUES) {
    const list = byModule.get(issue.module) || [];
    list.push(issue);
    byModule.set(issue.module, list);
  }

  let index = 1;
  for (const [mod, issues] of byModule.entries()) {
    console.log(`\n### ${mod} (${issues.length} issues)`);
    for (const issue of issues) {
      const titleExists = existingTitles.has(issue.title.trim().toLowerCase());
      const idExists = existingTaskIds.has(issue.id.toUpperCase());
      const alreadyExists = titleExists || idExists;
      const statusTag = alreadyExists ? "[ALREADY EXISTS - SKIP]" : "[READY]";
      console.log(`  ${String(index).padStart(2, " ")}. ${statusTag} ${issue.title}`);
      if (issue.blockedBy) {
        console.log(`      ⚠️  Blocked by: ${issue.blockedBy}`);
      }
      if (isVerbose) {
        console.log(`      Labels: ${issue.labels.join(", ")}`);
        if (issue.dependsOn && issue.dependsOn.length > 0) {
          console.log(`      Depends on: ${issue.dependsOn.join(", ")}`);
        }
      }
      index++;
    }
  }

  if (isDryRun) {
    await ensureLabelsExist(allRequiredLabels);
    console.log("\n=================================================");
    console.log("                DRY-RUN COMPLETE                 ");
    console.log("=================================================");
    console.log("No issues or labels were created on GitHub.");
    console.log("To create these issues for real, obtain explicit team approval, ensure GitHub CLI ('gh') is authenticated, and run:");
    console.log("  node scripts/create-swiftdrop-issues.mjs --create\n");
    return;
  }

  // Creation mode verification
  if (!hasGh && !process.env.GITHUB_TOKEN && !process.env.GH_TOKEN) {
    console.error("\n❌ Error: Neither GitHub CLI ('gh') nor GITHUB_TOKEN environment variable is available.");
    console.error("Please install and authenticate GitHub CLI ('gh auth login') or set GITHUB_TOKEN before running with --create.");
    process.exit(1);
  }

  // Pre-create missing labels before issue creation
  const labelsReady = await ensureLabelsExist(allRequiredLabels);
  if (!labelsReady) {
    console.error("Halting issue creation due to label setup failure.");
    process.exit(1);
  }

  console.log("\n=================================================");
  console.log("             EXECUTING ISSUE CREATION            ");
  console.log("=================================================");

  const createdUrls = [];
  const skipped = [];
  const failed = [];

  for (const issue of ISSUES) {
    const titleKey = issue.title.trim().toLowerCase();
    const idKey = issue.id.toUpperCase();

    if (existingTitles.has(titleKey) || existingTaskIds.has(idKey)) {
      console.log(`⏭️  Skipping existing issue: ${issue.title}`);
      skipped.push(issue.title);
      continue;
    }

    console.log(`Creating: ${issue.title}...`);

    let createdUrl = null;

    if (hasGh) {
      const labelArgs = issue.labels.flatMap((lbl) => ["--label", lbl]);
      const res = spawnSync(
        "gh",
        [
          "issue",
          "create",
          "--repo",
          repoInfo.fullName,
          "--title",
          issue.title,
          "--body",
          issue.body,
          ...labelArgs,
        ],
        { encoding: "utf8" }
      );

      if (res.status === 0) {
        createdUrl = res.stdout.trim();
      } else {
        console.error(`  ❌ Failed via gh CLI: ${res.stderr || res.stdout}`);
      }
    } else {
      // Fallback via fetch API
      const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
      try {
        const response = await fetch(`https://api.github.com/repos/${repoInfo.fullName}/issues`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: "application/vnd.github.v3+json",
            "Content-Type": "application/json",
            "User-Agent": "SwiftDrop-Issue-Creator",
          },
          body: JSON.stringify({
            title: issue.title,
            body: issue.body,
            labels: issue.labels,
          }),
        });

        if (response.ok) {
          const data = await response.json();
          createdUrl = data.html_url;
        } else {
          console.error(`  ❌ Failed via GitHub API (${response.status})`);
        }
      } catch (err) {
        console.error(`  ❌ Error during HTTP request: ${err.message}`);
      }
    }

    if (createdUrl) {
      console.log(`  ✅ Created: ${createdUrl}`);
      createdUrls.push({ title: issue.title, url: createdUrl });
      existingTitles.add(titleKey);
      existingTaskIds.add(idKey);
    } else {
      failed.push(issue.title);
    }
  }

  console.log("\n=================================================");
  console.log("                  FINAL SUMMARY                  ");
  console.log("=================================================");
  console.log(`Created : ${createdUrls.length}`);
  console.log(`Skipped : ${skipped.length}`);
  console.log(`Failed  : ${failed.length}`);

  if (createdUrls.length > 0) {
    console.log("\nCreated Issues:");
    for (const item of createdUrls) {
      console.log(`- ${item.title}: ${item.url}`);
    }
  }

  if (failed.length > 0) {
    console.log("\nFailed Issues:");
    for (const f of failed) {
      console.log(`- ${f}`);
    }
  }
}

main().catch((err) => {
  console.error("Unhandled execution error:", err);
  process.exit(1);
});

#!/usr/bin/env node
// Merges Surefire's per-class TEST-*.xml files into one JUnit <testsuites> document so a
// whole run can be uploaded to the test results dashboard in a single request.
// Drops <properties>, <system-out> and <system-err>: they hold JVM and environment details
// the dashboard doesn't need and that shouldn't leave the CI runner.
//
// Usage: node scripts/merge-junit.mjs <dir-with-TEST-*.xml> > merged.xml
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";

const dir = process.argv[2];
if (!dir) {
  console.error("Usage: node scripts/merge-junit.mjs <surefire-reports-dir>");
  process.exit(2);
}

const files = readdirSync(dir)
  .filter((f) => /^TEST-.*\.xml$/.test(f))
  .sort();
if (files.length === 0) {
  console.error(`No TEST-*.xml files in ${dir}`);
  process.exit(1);
}

const suites = files.map((f) =>
  readFileSync(path.join(dir, f), "utf8")
    .replace(/<\?xml[^>]*\?>\s*/, "")
    .replace(/ xmlns:xsi="[^"]*"| xsi:noNamespaceSchemaLocation="[^"]*"/g, "")
    .replace(/\s*<properties>[\s\S]*?<\/properties>/g, "")
    .replace(/\s*<properties\/>/g, "")
    .replace(/\s*<(system-out|system-err)>[\s\S]*?<\/\1>/g, "")
    .replace(/\s*<(system-out|system-err)\/>/g, "")
    .trim(),
);

process.stdout.write(`<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n${suites.join("\n")}\n</testsuites>\n`);

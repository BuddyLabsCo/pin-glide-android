import { copyFileSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const initial = { versionName: "0.1.0", versionCode: 1 };
const stable = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const gradlePath = "app/build.gradle.kts";
const changelogPath = "CHANGELOG.md";

function requireVersion(versionName) {
  if (typeof versionName !== "string" || versionName.trim() !== versionName || !stable.test(versionName)) {
    throw new Error(`Expected stable version, found ${versionName}`);
  }
  return versionName;
}

function metadata(versionName, versionCode) {
  requireVersion(versionName);
  if (!Number.isInteger(versionCode) || versionCode < 1 || versionCode > 2100000000) {
    throw new Error(`Invalid Android versionCode: ${versionCode}`);
  }
  return { versionName, versionCode };
}

function compareVersions(left, right) {
  const parts = requireVersion(left).split(".").map(BigInt);
  const other = requireVersion(right).split(".").map(BigInt);
  for (const [index, part] of parts.entries()) {
    if (part !== other[index]) return part > other[index] ? 1 : -1;
  }
  return 0;
}

function sameVersion(left, right) {
  return left?.versionName === right.versionName && left?.versionCode === right.versionCode;
}

function requireSuccessor(current, previous) {
  if (compareVersions(current.versionName, previous.versionName) <= 0 ||
      current.versionCode !== previous.versionCode + 1) {
    throw new Error("Release must have a higher stable version and increment versionCode by exactly one");
  }
}

function assignment(name) {
  return new RegExp(`^([ \\t]*${name}[ \\t]*=[ \\t]*)([^\\r\\n]+)(?=\\r?$)`, "gm");
}

const versionIdentifier = /^(?:versionName|versionCode|setVersionName|setVersionCode)$/;

function gradleTokens(source) {
  const lexer = /\/\/[^\r\n]*|\/\*|\*\/|"""|["']|`[^`\r\n]+`|[a-zA-Z_]\w*|\d+|[^\s]/g;
  const tokens = [];

  function quoted(quote) {
    while (lexer.lastIndex < source.length) {
      const character = source[lexer.lastIndex++];
      if (character === quote) return;
      if (character === "\\") {
        lexer.lastIndex++;
      } else if (quote === '"' && character === "$" && source[lexer.lastIndex] === "{") {
        lexer.lastIndex++;
        scan(true);
      } else if (character === "\r" || character === "\n") {
        break;
      }
    }
    throw new Error("Unsupported Gradle source: unterminated string");
  }

  function scan(template = false) {
    let depth = 0;
    let match;
    while ((match = lexer.exec(source))) {
      const text = match[0];
      if (text.startsWith("//")) continue;
      if (["/*", "*/", '"""'].includes(text)) {
        throw new Error("Unsupported Gradle source: block comments or multiline strings");
      }
      if (text === '"' || text === "'") quoted(text);
      const name = text.startsWith("`") ? text.slice(1, -1) : text;
      if (template) {
        if (versionIdentifier.test(name) || name === "defaultConfig") {
          throw new Error(`Unsupported Gradle source: ${name} in string interpolation`);
        }
        if (text === "{") depth++;
        if (text === "}" && depth-- === 0) return;
      } else {
        tokens.push({ text: source.slice(match.index, lexer.lastIndex), name, index: match.index });
      }
    }
    if (template) throw new Error("Unsupported Gradle source: unterminated interpolation");
  }

  scan();
  const stack = [];
  const pairs = { "}": "{", ")": "(", "]": "[" };
  for (const token of tokens) {
    token.parent = stack.at(-1);
    if (["{", "(", "["].includes(token.text)) {
      stack.push(token);
    } else if (Object.hasOwn(pairs, token.text)) {
      const open = stack.pop();
      if (open?.text !== pairs[token.text]) throw new Error("Unsupported Gradle source: unbalanced delimiters");
      open.close = token;
    }
  }
  if (stack.length) throw new Error("Unsupported Gradle source: unbalanced delimiters");
  return tokens;
}

function defaultConfig(gradle) {
  const tokens = gradleTokens(gradle);
  function bareCall(token) {
    const previous = tokens[tokens.indexOf(token) - 1];
    if ([".", "=", "else", "do"].includes(previous?.text)) return false;
    return previous?.text !== ")" ||
      !["if", "for", "while"].includes(tokens[tokens.indexOf(previous.parent) - 1]?.text);
  }
  const android = tokens.filter((token, index) => token.name === "android" && tokens[index + 1]?.text === "{");
  const configs = tokens.filter(token => token.name === "defaultConfig");
  const androidOpen = android.length === 1 && tokens[tokens.indexOf(android[0]) + 1];
  const configOpen = configs.length === 1 && tokens[tokens.indexOf(configs[0]) + 1];
  if (!androidOpen || android[0].parent || android[0].text !== "android" || !bareCall(android[0]) ||
      !configOpen || configOpen.text !== "{" || configs[0].text !== "defaultConfig" ||
      configs[0].parent !== androidOpen || !bareCall(configs[0])) {
    throw new Error("Expected exactly one direct android defaultConfig block");
  }
  const block = gradle.slice(configs[0].index, configOpen.close.index + 1);
  return { block, start: configs[0].index, configOpen, tokens };
}

function readAssignment({ block, configOpen, tokens }, name, pattern) {
  const identifiers = tokens.filter(token => token.name === name || token.name ===
    `set${name[0].toUpperCase()}${name.slice(1)}`);
  const matches = [...block.matchAll(assignment(name))];
  const value = identifiers.length === 1 && identifiers[0].text === name &&
    identifiers[0].parent === configOpen && matches.length === 1 && pattern.exec(matches[0][2].trim());
  if (!value) throw new Error(`Expected exactly one literal ${name} assignment in android defaultConfig`);
  return value[1];
}

function readGradle(gradle) {
  const config = defaultConfig(gradle);
  const current = metadata(
    readAssignment(config, "versionName", /^"([^"]+)"$/),
    Number(readAssignment(config, "versionCode", /^([1-9]\d*)$/)),
  );
  const children = config.tokens.filter(token => token.parent === config.configOpen && token !== config.configOpen.close);
  for (let index = 0; index < children.length; index += 3) {
    if (!/^[a-zA-Z_]\w*$/.test(children[index].text) || children[index + 1]?.text !== "=" ||
        !/^(?:\d+|true|false|"[^"\r\n]*")$/.test(children[index + 2]?.text ?? "")) {
      throw new Error("Unsupported Gradle source: defaultConfig must contain only simple literal assignments");
    }
  }
  return current;
}

function readChangelog(changelog) {
  const [preamble, ...sections] = changelog.replaceAll("\r\n", "\n").trim().split(/(?=^##(?:[ \t]|$))/m);
  if (preamble.trim() !== "# Changelog" || sections.length === 0) {
    throw new Error("Expected # Changelog and release sections");
  }
  const releases = sections.map(section => {
    const heading = /^## (\S+) \(versionCode ([1-9]\d*)\)\n/.exec(section);
    if (!heading || !section.slice(heading[0].length).trim()) {
      throw new Error("Expected a version and versionCode heading with release notes");
    }
    return { ...metadata(heading[1], Number(heading[2])), section: section.trimEnd() };
  });
  if (!sameVersion(releases.at(-1), initial)) throw new Error("Changelog must start at 0.1.0 / code 1");
  for (let index = 1; index < releases.length; index++) {
    requireSuccessor(releases[index - 1], releases[index]);
  }
  return releases;
}

export function checkVersion(gradle, changelog, published) {
  const current = readGradle(gradle);
  const releases = readChangelog(changelog);
  if (!sameVersion(releases[0], current)) throw new Error("Gradle and changelog version metadata must agree");
  if (published === null) {
    if (!sameVersion(current, initial)) throw new Error("First production release must be 0.1.0 / code 1");
  } else if (published !== undefined) {
    metadata(published.versionName, published.versionCode);
    requireSuccessor(current, published);
    if (!sameVersion(releases[1], published)) throw new Error("Changelog must include the previous published release");
  }
  return current;
}

export function proposeRelease(gradle, changelog, published, notes, selected) {
  const current = checkVersion(gradle, changelog);
  const releases = readChangelog(changelog);
  let next = initial;
  let kept = [];
  if (published === null) {
    checkVersion(gradle, changelog, null);
    if (selected !== undefined && selected !== initial.versionName) {
      throw new Error("First production release must be 0.1.0");
    }
  } else {
    metadata(published.versionName, published.versionCode);
    const refreshing = !sameVersion(current, published);
    if (refreshing) checkVersion(gradle, changelog, published);
    const defaultVersion = refreshing ? current.versionName :
      published.versionName.replace(/\d+$/, patch => String(BigInt(patch) + 1n));
    next = metadata(selected ?? defaultVersion, published.versionCode + 1);
    requireSuccessor(next, published);
    kept = refreshing ? releases.slice(1) : releases;
  }
  const { block, start } = defaultConfig(gradle);
  const updatedBlock = block
    .replace(assignment("versionName"), (_, prefix) => `${prefix}"${next.versionName}"`)
    .replace(assignment("versionCode"), (_, prefix) => `${prefix}${next.versionCode}`);
  const updatedGradle = gradle.slice(0, start) + updatedBlock + gradle.slice(start + block.length);
  const updatedChangelog = [
    "# Changelog",
    `## ${next.versionName} (versionCode ${next.versionCode})\n\n${notes.trim()}`,
    ...kept.map(release => release.section),
  ].join("\n\n") + "\n";
  checkVersion(updatedGradle, updatedChangelog, published);
  return { gradle: updatedGradle, changelog: updatedChangelog };
}

function parsePublished(value) {
  if (value === "none") return null;
  const match = /^([^:]+):([1-9]\d*)$/.exec(value);
  if (!match || match[0] !== value) throw new Error("Expected published version:code or none");
  return metadata(match[1], Number(match[2]));
}

function writeProposal(proposal) {
  const directory = mkdtempSync(join(process.cwd(), ".release-"));
  const stagedGradle = join(directory, "build.gradle.kts");
  const stagedChangelog = join(directory, "CHANGELOG.md");
  const originalGradle = join(directory, "original.gradle.kts");
  let preserveBackup = false;
  try {
    copyFileSync(gradlePath, originalGradle);
    copyFileSync(gradlePath, stagedGradle);
    copyFileSync(changelogPath, stagedChangelog);
    writeFileSync(stagedGradle, proposal.gradle);
    writeFileSync(stagedChangelog, proposal.changelog);
    renameSync(stagedGradle, gradlePath);
    try {
      renameSync(stagedChangelog, changelogPath);
    } catch (error) {
      try {
        renameSync(originalGradle, gradlePath);
      } catch (rollbackError) {
        preserveBackup = true;
        throw new Error(`${error.message}; rollback failed: ${rollbackError.message}. Restore ${originalGradle}`);
      }
      throw error;
    }
  } finally {
    if (!preserveBackup) rmSync(directory, { recursive: true, force: true });
  }
}

function main([command, baseline, notesPath, selected, ...extra]) {
  const gradle = readFileSync(gradlePath, "utf8");
  const changelog = readFileSync(changelogPath, "utf8");
  if (command === "check" && notesPath === undefined) {
    console.log(JSON.stringify(checkVersion(gradle, changelog,
      baseline === undefined ? undefined : parsePublished(baseline))));
  } else if (command === "propose" && baseline && notesPath && extra.length === 0) {
    const published = parsePublished(baseline);
    const proposal = proposeRelease(gradle, changelog, published, readFileSync(notesPath, "utf8"), selected);
    writeProposal(proposal);
    console.log(JSON.stringify(checkVersion(proposal.gradle, proposal.changelog, published)));
  } else {
    throw new Error("Usage: version-policy.mjs check [none|version:code] or propose <none|version:code> <notes-file> [version]");
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

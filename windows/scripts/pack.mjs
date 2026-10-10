// Copies the packages Tauri buries in target/release/bundle/ into
// windows/release/, with the names they ship under. Used by `npm run pack` and
// by the release workflows, so both produce exactly the same file names.

import { readFileSync, mkdirSync, copyFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const bundleRoot = join(root, "target", "release", "bundle");
const outDir = join(root, "release");

const { version } = JSON.parse(readFileSync(join(root, "src-tauri", "tauri.conf.json"), "utf8"));

// What each platform ships: where Tauri puts it, how to recognise it, and the
// names it is published under (the rolling name, when there is one, always
// points at the latest release).
const arch = process.arch === "arm64" ? "aarch64" : "x86_64";
const debArch = process.arch === "arm64" ? "arm64" : "amd64";
const PACKAGES = {
  win32: [
    {
      dir: "nsis",
      suffix: "-setup.exe",
      names: [`Cocoa-Windows-${version}-setup.exe`, "Cocoa-Windows-setup.exe"],
    },
    {
      dir: "msi",
      suffix: ".msi",
      names: [`Cocoa-Windows-${version}.msi`, "Cocoa-Windows.msi"],
    },
  ],
  linux: [
    {
      dir: "appimage",
      suffix: ".AppImage",
      names: [`Cocoa-Linux-${version}-${arch}.AppImage`, `Cocoa-Linux-${arch}.AppImage`],
    },
    { dir: "deb", suffix: ".deb", names: [`Cocoa-Linux-${version}-${debArch}.deb`] },
    { dir: "rpm", suffix: ".rpm", names: [`Cocoa-Linux-${version}-${arch}.rpm`] },
  ],
};

const packages = PACKAGES[process.platform];
if (!packages) {
  console.error(`Nothing to pack on ${process.platform}.`);
  process.exit(1);
}

/** The newest file in `dir` ending with `suffix`, in case an older build is still lying around. */
function newest(dir, suffix) {
  let files = [];
  try {
    files = readdirSync(dir).filter((f) => f.endsWith(suffix));
  } catch {
    return null;
  }
  if (files.length === 0) return null;
  return files
    .map((f) => join(dir, f))
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs)[0];
}

mkdirSync(outDir, { recursive: true });
const written = [];
for (const { dir, suffix, names } of packages) {
  const built = newest(join(bundleRoot, dir), suffix);
  if (!built) {
    console.error(`No *${suffix} in ${join(bundleRoot, dir)} — run \`npm run tauri build\` first.`);
    process.exit(1);
  }
  for (const name of names) {
    const dest = join(outDir, name);
    copyFileSync(built, dest);
    written.push(dest);
  }
}

// Also package the standalone executable and hook
const standaloneExe = join(root, "target", "release", "cocoa.exe");
const hookExe = join(root, "target", "release", "cocoa-hook.exe");
try {
  if (statSync(standaloneExe).isFile()) {
    const releaseExe = join(outDir, "Cocoa.exe");
    const releaseWinExe = join(outDir, "Cocoa-Windows.exe");
    copyFileSync(standaloneExe, releaseExe);
    copyFileSync(standaloneExe, releaseWinExe);
    written.push(releaseExe, releaseWinExe);
  }
} catch {}
try {
  if (statSync(hookExe).isFile()) {
    const releaseHook = join(outDir, "cocoa-hook.exe");
    copyFileSync(hookExe, releaseHook);
    written.push(releaseHook);
  }
} catch {}

// Always update top-level project root binaries for easy access
const projectRoot = resolve(root, "..");
try {
  const rootSetup = join(projectRoot, "Cocoa-Windows-setup.exe");
  const latestSetup = join(outDir, "Cocoa-Windows-setup.exe");
  copyFileSync(latestSetup, rootSetup);
  written.push(rootSetup);
} catch (e) {
  console.warn("Could not copy root setup:", e.message);
}

try {
  const rootExe = join(projectRoot, "Cocoa-Windows.exe");
  const latestExe = join(outDir, "Cocoa.exe");
  copyFileSync(latestExe, rootExe);
  written.push(rootExe);
} catch (e) {
  console.warn("Could not copy root exe:", e.message);
}

console.log("\n  Packages ready\n");
for (const f of written) {
  const mb = (statSync(f).size / 1024 / 1024).toFixed(2);
  console.log(`  ${f}  (${mb} MB)`);
}
console.log();

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const [version, rawVersionCode] = process.argv.slice(2);
if (!/^\d+\.\d+\.\d+$/.test(version ?? '')) {
  throw new Error('Release version must use stable SemVer in MAJOR.MINOR.PATCH form.');
}
if (!/^\d+$/.test(rawVersionCode ?? '')) {
  throw new Error('Android versionCode must be a positive integer.');
}
const versionCode = Number(rawVersionCode);
if (!Number.isSafeInteger(versionCode) || versionCode < 1 || versionCode > 2_100_000_000) {
  throw new Error('Android versionCode is outside the supported positive integer range.');
}

const root = process.cwd();
const packagePath = path.join(root, 'package.json');
const lockPath = path.join(root, 'package-lock.json');
const gradlePath = path.join(root, 'android/app/build.gradle');

const packageJson = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
const packageLock = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
packageJson.version = version;
packageLock.version = version;
if (packageLock.packages?.['']) packageLock.packages[''].version = version;
fs.writeFileSync(packagePath, `${JSON.stringify(packageJson, null, 2)}\n`);
fs.writeFileSync(lockPath, `${JSON.stringify(packageLock, null, 2)}\n`);

let gradle = fs.readFileSync(gradlePath, 'utf8');
const versionCodePattern = /(^\s*versionCode\s+)\d+(\s*$)/m;
const versionNamePattern = /(^\s*versionName\s+")[^"]*("\s*$)/m;
if (!versionCodePattern.test(gradle) || !versionNamePattern.test(gradle)) {
  throw new Error('Could not find exactly one Android versionCode/versionName declaration.');
}
gradle = gradle.replace(versionCodePattern, `$1${versionCode}$2`);
gradle = gradle.replace(versionNamePattern, `$1${version}$2`);
fs.writeFileSync(gradlePath, gradle);

console.log(`Configured Athir ${version} (Android versionCode ${versionCode}).`);

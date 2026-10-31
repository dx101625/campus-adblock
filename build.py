"""Build the real Miuix UI with the pinned local Gradle toolchain.

    python build.py
    python build.py --no-sign

Hook sources remain Java 17. The UI uses Kotlin/Compose and published Miuix.
The original release keystore is required; this script never generates a key.
"""
import hashlib
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JAVA = Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else Path(shutil.which("java") or "java").resolve().parent.parent
GRADLE = Path(os.environ.get("CAMPUS_GRADLE", str(ROOT / ("tools/gradle-9.7.1/bin/gradle.bat" if os.name == "nt" else "tools/gradle-9.7.1/bin/gradle"))))
SDK = Path(os.environ.get("CAMPUS_ANDROID_SDK", os.environ.get("ANDROID_HOME", str(ROOT / "tools/android-sdk"))))
VERSION = os.environ.get("CAMPUS_VERSION", "1.6.2")
CODE = os.environ.get("CAMPUS_VERSION_CODE", "25")
CERT = "95462bfe83602a10471cc127a99ad27de4411eba1c00284844577260eda1f14d"

def main():
    sign = "--no-sign" not in sys.argv
    if not (JAVA / ("bin/java.exe" if os.name == "nt" else "bin/java")).is_file():
        sys.exit("Set JAVA_HOME to an installed JDK21 before building; see docs/build.md.")
    if sign and not all((ROOT / p).is_file() for p in ("signing/release.p12", "signing/password.txt")):
        sys.exit("Original signing/release.p12 and signing/password.txt are required; refusing to create a replacement key.")
    if not GRADLE.exists() or not (SDK / "platforms/android-37.0/android.jar").exists() or not (SDK / "build-tools/37.0.0/source.properties").exists():
        subprocess.run([sys.executable, "tools/prepare_miuix.py"], cwd=ROOT, check=True)
    if not os.environ.get("CAMPUS_XPOSED_API") and not (ROOT / "tools/libxposed-api/classes.jar").exists():
        subprocess.run([sys.executable, "tools/prepare_api.py"], cwd=ROOT, check=True)
    env = dict(os.environ, JAVA_HOME=str(JAVA), GRADLE_USER_HOME=os.environ.get("GRADLE_USER_HOME",str(ROOT / "tools/gradle-home")), CAMPUS_VERSION=VERSION, CAMPUS_VERSION_CODE=CODE)
    env["PATH"] = str(JAVA / "bin") + os.pathsep + env.get("PATH", "")
    (ROOT / "local.properties").write_text("sdk.dir=" + SDK.as_posix() + "\n", encoding="utf-8")
    # AGP9 requires SDK versions and namespace in its DSL. Preserve the original
    # manifest source and stage an equivalent copy for the manifest merger.
    ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
    ET.register_namespace("tools", "http://schemas.android.com/tools")
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    manifest.getroot().attrib.pop("package", None)
    for node in list(manifest.getroot()):
        if node.tag == "uses-sdk":
            # The framework service is disabled on API24-25 and its classes are
            # loaded only behind the API26 bridge. Keep that explicit override
            # while the SDK values themselves remain defined in the Gradle DSL.
            override = node.attrib.get("{http://schemas.android.com/tools}overrideLibrary")
            if override:
                node.attrib.clear()
                node.set("{http://schemas.android.com/tools}overrideLibrary", override)
            else: manifest.getroot().remove(node)
    staged = ROOT / "build/generated/miuix/AndroidManifest.xml"
    staged.parent.mkdir(parents=True, exist_ok=True)
    manifest.write(staged, encoding="utf-8", xml_declaration=True)
    metadata = ROOT / "app/src/main/resources/META-INF/xposed/module.prop"
    lines = metadata.read_text(encoding="utf-8").splitlines()
    metadata.write_text("\n".join("version=" + VERSION if s.startswith("version=") else "versionCode=" + CODE if s.startswith("versionCode=") else s for s in lines) + "\n", encoding="utf-8")
    command = [str(GRADLE), "--no-daemon", "--console=plain", "--max-workers=4", ":app:assembleRelease"]
    if "--offline" in sys.argv: command += ["--offline"]
    if not sign: command += ["-PcampusNoSign=true"]
    logs = ROOT / "artifacts/simple-auto-1.6.2-20261031"
    logs.mkdir(parents=True, exist_ok=True)
    with (logs / "build.log").open("w", encoding="utf-8") as stream:
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        for line in process.stdout:
            print(line, end="", flush=True)
            stream.write(line); stream.flush()
        result = process.wait()
    if result: sys.exit(result)
    # This name is looked up in the target app, not in our bundled AndroidX.
    # A successful compile must not silently reintroduce the camera fallback.
    mapping = ROOT / "app/build/outputs/mapping/release/mapping.txt"
    if mapping.exists():
        observer = next((line for line in mapping.read_text(encoding="utf-8").splitlines()
                         if line.startswith("androidx.lifecycle.Observer -> ")), None)
        if observer is not None and observer != "androidx.lifecycle.Observer -> androidx.lifecycle.Observer:":
            sys.exit("Target Observer was renamed by R8; refusing to deliver a broken automatic scan path.")
    candidates = sorted((ROOT / "app/build/outputs/apk/release").glob("*.apk"), key=lambda p:p.stat().st_mtime)
    if not candidates: sys.exit("Gradle succeeded but no release APK was found")
    out = ROOT / "dist" / ("campus-adblock-v" + VERSION + ("" if sign else "-unsigned") + ".apk")
    out.parent.mkdir(exist_ok=True)
    shutil.copy2(candidates[-1], out)
    if sign:
        checked = subprocess.run([str(JAVA / ("bin/java.exe" if os.name == "nt" else "bin/java")), "-jar", str(SDK / "build-tools/37.0.0/lib/apksigner.jar"), "verify", "--print-certs", str(out)], capture_output=True, check=True)
        report = checked.stdout.decode("utf-8", "replace")
        if CERT not in report.lower(): sys.exit("Certificate does not match the original release signer; do not install.")
        print(report)
    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    print(f"APK   : {out}\nsize  : {out.stat().st_size} B\nsha256: {digest}")
    out.with_suffix(".sha256").write_text(digest + "  " + out.name + "\n", encoding="utf-8")

if __name__ == "__main__": main()

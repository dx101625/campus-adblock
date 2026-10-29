"""Prepare pinned Gradle and check an SDK installed with Android's official tools.

This bootstrap never reads signing material, copies historical APKs, backs up or
rewrites source files, or changes another project's SDK. Run SDK license prompts
yourself through Android Studio / the Android SDK command-line tools.
"""
from pathlib import Path
import hashlib,os,shutil,stat,urllib.request,zipfile

ROOT=Path(__file__).resolve().parents[1]
VERSION='9.7.1'
SDK=Path(os.environ.get('CAMPUS_ANDROID_SDK',os.environ.get('ANDROID_HOME',str(ROOT/'tools/android-sdk'))))
GRADLE=Path(os.environ.get('CAMPUS_GRADLE',str(ROOT/('tools/gradle-'+VERSION+'/bin/gradle'+('.bat' if os.name=='nt' else '')))))

def prepare_gradle():
    if GRADLE.is_file():
        print('Gradle executable ready:',GRADLE,flush=True);return
    if os.environ.get('CAMPUS_GRADLE'):
        raise SystemExit('CAMPUS_GRADLE must point to an existing Gradle executable.')
    cache=ROOT/'tools/cache';cache.mkdir(parents=True,exist_ok=True)
    archive=cache/f'gradle-{VERSION}-bin.zip'
    url=f'https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip'
    if not archive.exists():
        temporary=archive.with_suffix('.zip.part')
        with urllib.request.urlopen(url,timeout=120) as response,temporary.open('wb') as out:shutil.copyfileobj(response,out)
        temporary.replace(archive)
    expected=urllib.request.urlopen(url+'.sha256',timeout=30).read().decode().strip().lower()
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:raise SystemExit('Gradle archive SHA-256 mismatch.')
    with zipfile.ZipFile(archive) as z:
        for entry in z.infolist():
            target=(ROOT/'tools'/entry.filename).resolve()
            if not target.is_relative_to((ROOT/'tools').resolve()):raise SystemExit('Unsafe Gradle archive path.')
        z.extractall(ROOT/'tools')
    if os.name!='nt':GRADLE.chmod(GRADLE.stat().st_mode|stat.S_IXUSR|stat.S_IXGRP|stat.S_IXOTH)
    print('Pinned Gradle ready; official SHA-256 verified.',flush=True)

def check_sdk():
    required=[SDK/'platforms/android-37.0/android.jar',SDK/'build-tools/37.0.0/source.properties']
    if not all(p.is_file() for p in required):
        raise SystemExit('Install Android SDK platform 37.0 and Build Tools 37.0.0 using Android Studio or the official SDK command-line tools, then set CAMPUS_ANDROID_SDK (or ANDROID_HOME). See docs/build.md. No source or historical APK is needed by this bootstrap.')
    print('Android SDK ready:',SDK,flush=True)

def main():
    check_sdk();prepare_gradle()

if __name__=='__main__':main()

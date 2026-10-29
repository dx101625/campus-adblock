"""Fetch the official compile-only API, pin both archive and extracted classes. No signing material."""
from pathlib import Path
import hashlib,urllib.request,zipfile

ROOT=Path(__file__).resolve().parent.parent
URL='https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar'
ARCHIVE_SHA='423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0'
CLASSES_SHA='a515dd7a53cd7a47c05e101dff77d61acb3091a97b20a885b9ea3494412db985'
def main():
    cache=ROOT/'tools/cache/libxposed-api-102.0.0.aar';cache.parent.mkdir(parents=True,exist_ok=True)
    if not cache.exists():cache.write_bytes(urllib.request.urlopen(URL,timeout=60).read())
    if hashlib.sha256(cache.read_bytes()).hexdigest()!=ARCHIVE_SHA:raise SystemExit('Official API archive checksum mismatch')
    with zipfile.ZipFile(cache) as z:data=z.read('classes.jar')
    if hashlib.sha256(data).hexdigest()!=CLASSES_SHA:raise SystemExit('API classes checksum mismatch')
    target=ROOT/'tools/libxposed-api/classes.jar';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    print('Official libxposed API102 ready; archive and classes SHA256 verified.')
if __name__=='__main__':main()

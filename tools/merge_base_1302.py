#!/usr/bin/env python3
"""Stage a reviewed three-way upstream delta, retaining re:gram namespace.

Does not modify the checkout. Binary additions are copied as data. Missing upstream
submodules are NOT deletions; restoring gitlinks is a separate reviewed step.
"""
import argparse
import json
import subprocess
from pathlib import Path


def normalize(data):
    if b'\0' in data:
        return data
    try:
        data.decode('utf-8')
    except UnicodeDecodeError:
        return data
    return data.replace(b'app.exteraless', b'app.regram').replace(b'app/exteraless', b'app/regram')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('old','new','local','stage'):
        parser.add_argument('--'+name,required=True,type=Path)
    args=parser.parse_args()
    args.stage.mkdir(parents=True,exist_ok=True)
    report=[]
    for source in sorted(args.new.rglob('*')):
        if not source.is_file():continue
        relative=source.relative_to(args.new)
        previous=args.old/relative
        upstream=normalize(source.read_bytes())
        if previous.is_file() and normalize(previous.read_bytes())==upstream:continue
        destrel=Path(str(relative).replace('app/exteraless/','app/regram/'))
        local=args.local/destrel
        out=args.stage/destrel
        out.parent.mkdir(parents=True,exist_ok=True)
        mode='upstream'
        if previous.is_file() and local.is_file():
            base=normalize(previous.read_bytes())
            ours=local.read_bytes()
            if ours!=base:
                import tempfile
                with tempfile.TemporaryDirectory() as temp:
                    paths=[Path(temp)/s for s in ('ours','base','upstream')]
                    for p,data in zip(paths,(ours,base,upstream)):p.write_bytes(data)
                    result=subprocess.run(['git','merge-file','-p',*map(str,paths)],capture_output=True)
                if result.returncode<0 or result.returncode>127:raise RuntimeError(result.stderr)
                upstream=result.stdout
                mode='conflict' if result.returncode else 'merged'
        elif local.is_file():
            raise RuntimeError('Addition collides with local file: '+str(destrel))
        out.write_bytes(upstream)
        report.append({'path':str(destrel),'mode':mode})
    (args.stage/'merge-report.json').write_text(json.dumps(report,indent=2)+'\n')
    from collections import Counter
    print(Counter(item['mode'] for item in report))
    print('\n'.join(item['path'] for item in report if item['mode']=='conflict'))

if __name__=='__main__':main()

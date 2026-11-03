#!/usr/bin/env python3
"""Read-only APK size/duplicate audit. Never delete runtime-selected resources."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import zipfile


def category(name):
    if name.startswith('lib/'):
        return '/'.join(name.split('/')[:2])
    if name.startswith('assets/'):
        return '/'.join(name.split('/')[:3])
    if name.startswith('res/'):
        return 'res'
    if name.endswith('.dex') and '/' not in name:
        return 'dex'
    return 'metadata/other'


def audit(path):
    groups=defaultdict(lambda: {'entries':0,'stored_bytes':0,'expanded_bytes':0})
    candidates=defaultdict(list)
    with zipfile.ZipFile(path) as archive:
        entries=[item for item in archive.infolist() if not item.is_dir()]
        for item in entries:
            group=groups[category(item.filename)]
            group['entries']+=1;group['stored_bytes']+=item.compress_size;group['expanded_bytes']+=item.file_size
            if item.file_size:candidates[item.file_size].append(item)
        duplicates=[]
        for size,items in candidates.items():
            if len(items)<2:continue
            hashes=defaultdict(list)
            for item in items:
                digest=hashlib.sha256()
                with archive.open(item) as stream:
                    for chunk in iter(lambda:stream.read(65536),b''):digest.update(chunk)
                hashes[digest.hexdigest()].append(item)
            for digest,matches in hashes.items():
                if len(matches)>1:
                    duplicates.append({'sha256':digest,'expanded_bytes_each':size,
                        'stored_bytes_total':sum(item.compress_size for item in matches),
                        'paths':[item.filename for item in matches]})
        return {'apk':str(path),'apk_bytes':Path(path).stat().st_size,
            'warning':'Duplicates are candidates for review, NOT safe deletions: resource names/ABI/runtime lookup may require them.',
            'groups':dict(sorted(groups.items(),key=lambda pair:pair[1]['stored_bytes'],reverse=True)),
            'largest_entries':[{'path':item.filename,'stored_bytes':item.compress_size,'expanded_bytes':item.file_size}
                               for item in sorted(entries,key=lambda item:item.compress_size,reverse=True)[:30]],
            'duplicates':sorted(duplicates,key=lambda item:item['stored_bytes_total'],reverse=True)}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    report=json.dumps(audit(args.apk),ensure_ascii=False,indent=2)+'\n'
    if args.output:args.output.write_text(report)
    else:print(report,end='')

if __name__=='__main__':main()

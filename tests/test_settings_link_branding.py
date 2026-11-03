"""Execute the real pure SettingsHelper path methods on the host JDK."""
from pathlib import Path
import re
import shutil
import subprocess
import pytest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'TMessagesProj/src/main/java'
SOURCE=JAVA/'tw/nekomimi/nekogram/helpers/SettingsHelper.java'


def method(text, signature):
    start=text.index(signature)
    brace=text.index('{',start);end=brace+1;depth=1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}');end+=1
    return text[start:end]


def test_actual_link_paths_generate_regramoff_and_accept_legacy(tmp_path):
    if not shutil.which('javac'):pytest.skip('JDK unavailable')
    source=SOURCE.read_text()
    constants='\n'.join(re.findall(r'    private static final String HOST_\w+ = "[^"]+";',source))
    screens=re.search(r'    private static final Set<String> REGRAM_SCREENS = .*?;',source,re.S).group()
    methods='\n'.join(method(source,s) for s in (
        'private static boolean isRegramHost(',
        'public static boolean isDeepLink(',
        'public static String linkPathFor(',
    ))
    code='import java.util.*; public class PathsTest {\n'+constants+'\n'+screens+'\n'+methods+'''
    public static void main(String[] args) {
        String[][] routes={
            {"exteraless","settings"},{"exteraless_general","general"},
            {"exteraless_appearance","appearance"},{"exteraless_chats","chats"},
            {"exteraless_menus","menus"},{"exteraless_media","media"},
            {"exteraless_hiding","hiding"},{"exteraless_other","other"},
            {"exteraless_ayumoments","ayumoments"},{"pillstack","pillstack"},
            {"exteraless_navigation","navigation"},{"regram_features","features"}
        };
        for(String[] route:routes){
            assert linkPathFor(route[0]).equals("regramoff/"+route[1]);
            assert isDeepLink("regramoff/"+route[1]);
            assert isDeepLink("exteraless/"+route[1]);
        }
        assert ("https://t.me/"+linkPathFor("exteraless")+"?r=plugins").equals("https://t.me/regramoff/settings?r=plugins");
        assert isDeepLink("regramoff/plugins")&&isDeepLink("exteraless/plugins");
        assert linkPathFor(null)==null;
        assert linkPathFor("translator").equals("nasettings/translator");
        assert isDeepLink("nasettings/translator");
        for(String path:new String[]{null,"","regramoff","exteraless","regramoff/","regramoff/unknown",
            "regramoff/settings/extra","regramoff-other/settings","notregramoff/settings","other/settings"})assert !isDeepLink(path):path;
        assert isRegramHost("regramoff")&&isRegramHost("exteraless");
        assert !isRegramHost("regramoff-other")&&!isRegramHost(null);
    }}'''
    path=tmp_path/'PathsTest.java';path.write_text(code)
    subprocess.run(['javac',str(path)],check=True,capture_output=True,text=True)
    subprocess.run(['java','-ea','-cp',str(tmp_path),'PathsTest'],check=True,capture_output=True,text=True)


def test_dispatch_and_all_copy_surfaces_share_the_canonical_path_helper():
    source=SOURCE.read_text()
    assert 'final boolean regram = isRegramHost(segments.get(0))' in source
    for rel in (
        'tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java',
        'tw/nekomimi/nekogram/settings/BaseNekoXSettingsActivity.java',
        'app/regram/settings/OpenExteraAppNavigationActivity.java',
    ):
        assert 'SettingsHelper.linkPathFor(' in (JAVA/rel).read_text()
    launch=(JAVA/'org/telegram/ui/LaunchActivity.java').read_text()
    assert 'SettingsHelper.isDeepLink(path)' in launch

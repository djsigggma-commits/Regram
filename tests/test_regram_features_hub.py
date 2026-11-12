"""Navigation contract for the combined re:gram feature settings entry."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'TMessagesProj/src/main/java/app/regram/settings'


def test_regram_settings_has_one_combined_entry_and_appearance_has_no_duplicates():
    source=(JAVA/'OpenExteraSettingsActivity.java').read_text()
    assert 'regramFeaturesRow = addRow("regramFeatures", "RegramFeatures")' in source
    assert 'presentFragment(new RegramFeaturesActivity())' in source
    assert 'position == regramFeaturesRow' in source
    appearance=(JAVA/'OpenExteraAppearanceActivity.java').read_text()
    assert 'regramFeaturesRow' not in appearance
    assert 'new RegramFeaturesActivity()' not in appearance
    for old in ('nativeVisualRow','nativePluginStylesRow','quickThemeRow'):
        assert old not in source and old not in appearance


def test_hub_routes_retained_screens_without_changing_settings():
    source=(JAVA/'RegramFeaturesActivity.java').read_text()
    for row,key,screen in (
        ('nativeVisual','OEAppearanceNativeVisual','NativeVisualSettingsActivity'),
        ('quickTheme','RegramQuickTheme','QuickThemeActivity'),
    ):
        assert f'{row}Row = addRow("{row}", "{key}")' in source
        assert f'position == {row}Row' in source
        assert f'presentFragment(new {screen}())' in source
    assert 'getString(R.string.RegramFeatures)' in source
    for mutation in ('SharedPreferences','setConfig','saveConfig','preferences().edit','PluginStyles.put'):
        assert mutation not in source
    assert 'bluetoothCallsRow = addRow("RegramHdBluetoothCalls", "RegramHdBluetoothCallsTitle")' in source
    assert 'presentFragment(new BluetoothCallAudioSettingsActivity())' in source
    assert 'dualCameraRow = addRow("rounddual_available", "RegramDualCameraTitle")' in source
    assert 'presentFragment(new DualCameraSettingsActivity())' in source
    assert 'roundVideoZoomRow = addRow("zoomSlider", "RegramRoundVideoZoomTitle")' in source
    assert 'presentFragment(new RoundVideoZoomSettingsActivity())' in source
    assert 'presentFragment(new FontsSettingsActivity())' in source
    assert source.count('setTextAndValueAndIcon(')==6
    assert 'nativePluginStyles' not in source
    assert 'R.drawable.msg_groups, false' in source


def test_other_features_move_to_hub_without_duplicates():
    hub=(JAVA/'RegramFeaturesActivity.java').read_text()
    other=(JAVA/'OpenExteraOtherActivity.java').read_text()
    for row,screen in (
        ('termuxRow','TermuxBridgeActivity'),
        ('documentViewerRow','NativeDocumentSettingsActivity'),
        ('spenRow','NativeSPenSettingsActivity'),
        ('creditsRow','app.regram.ui.RegramCreditsActivity'),
    ):
        assert f'position == {row}' in hub
        assert f'presentFragment(new {screen}())' in hub
        assert row not in other
    assert 'creditsDividerRow' not in other
    helper=(ROOT/'TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/SettingsHelper.java').read_text()
    assert 'return HOST_REGRAM + "/features";' in helper
    assert 'moveRows("other", "features", "regramTermuxBridge", "regramDocumentViewer", "regramSPen", "regramCreatorsAndSources")' in helper
    assert 'new app.regram.settings.RegramFeaturesActivity()' in helper


def test_chip_folders_are_under_built_in_appearance_not_the_hub():
    hub=(JAVA/'RegramFeaturesActivity.java').read_text()
    assert 'ChipFoldersActivity' not in hub
    assert 'NativePluginStylesActivity' not in hub
    visual=(JAVA/'NativeVisualSettingsActivity.java').read_text()
    assert 'chipFoldersRow = addRow("chipFolders", "RegramChipFolders")' in visual
    assert 'presentFragment(new ChipFoldersActivity())' in visual
    for locale in ('values', 'values-ru'):
        assert not (ROOT/f'TMessagesProj/src/main/res/{locale}/regram_plugin_styles.xml').exists()


def test_translations_and_search_registry_include_hub():
    pairs=[]
    for locale in ('values','values-ru'):
        root=ET.parse(ROOT/f'TMessagesProj/src/main/res/{locale}/regram_features.xml').getroot()
        pairs.append({item.attrib['name']:item.text for item in root})
    assert pairs[0].keys()==pairs[1].keys()
    assert pairs[1]['RegramFeatures']=='Функционал re:gram'
    registry=(JAVA/'utils/SettingsRegistry.java').read_text()
    assert 'icons.put(app.regram.settings.RegramFeaturesActivity.class' in registry
    assert 'icons.put(app.regram.settings.NativeVisualSettingsActivity.class' in registry
    assert 'icons.put(app.regram.settings.ChipFoldersActivity.class' in registry
    assert 'NativePluginStylesActivity' not in registry
    source=(JAVA/'RegramFeaturesActivity.java').read_text()
    for resource in re.findall(r'R.string.(RegramFeatures\w*)',source):
        assert resource in pairs[0]

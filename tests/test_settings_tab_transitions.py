"""Guards for pager/tab sync and unnecessary settings row animations."""
from pathlib import Path

JAVA = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java"


def source(path):
    return (JAVA / path).read_text()


def test_tab_indicator_follows_both_manual_and_touch_scroll():
    tabs = source("org/telegram/ui/MainTabsActivity.java")
    update = tabs.split("protected void onViewPagerTabAnimationUpdate(boolean manual)", 1)[1].split(
        "protected int getFragmentsCount()", 1
    )[0]
    assert "setGestureSelectedOverride(position, true)" in update
    assert "if (!manual)" in update
    end = tabs.split("protected void onViewPagerScrollEnd()", 1)[1].split(
        "protected void onViewPagerTabAnimationUpdate", 1
    )[0]
    assert end.index("selectTab(viewPager.getCurrentPosition(), false)") < end.index(
        "setGestureSelectedOverride(0, false)"
    )


def test_tab_label_animation_finishes_with_page_slide():
    pager = source("org/telegram/ui/ViewPagerActivity.java")
    style = source("app/regram/appearance/MainTabsUiHelper.java")
    assert "protected long getManualScrollDuration() {\n            return 320L;" in pager
    assert "animator.setDuration(320L);" in style


def test_keyboard_does_not_restart_fragment_open_transition():
    layout = source("org/telegram/ui/ActionBar/ActionBarLayout.java")
    open_fragment = layout.split("public boolean presentFragment(NavigationParams params)", 1)[1].split(
        "private boolean shouldOpenFragmentOverlay", 1
    )[0]
    # Upstream no longer waits for the IME: no second callback may restart opening.
    assert "waitingForKeyboardCloseRunnable = new Runnable()" not in open_fragment
    assert "private Runnable waitingForKeyboardCloseRunnable" not in layout
    assert "currentFragment.saveKeyboardPositionBeforeTransition()" in open_fragment
    animation = open_fragment.split("if (animation == null) {", 1)[1].split(
        "currentAnimation = animation;", 1
    )[0]
    immediate = animation.split("AndroidUtilities.runOnUIThread(delayedOpenAnimationRunnable, 200);", 1)[1]
    assert "startLayoutAnimation(true, true, preview)" in immediate
    assert "onTransitionAnimationStart" not in immediate


def test_settings_do_not_reanimate_all_rows_on_first_open():
    settings = source("tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java")
    assert "itemAnimator.setSupportsChangeAnimations(false)" in settings
    assert "if (settingsResumedOnce && listAdapter != null)" in settings
    assert "listAdapter.notifyDataSetChanged();" in settings  # later resumes still refresh values/rows

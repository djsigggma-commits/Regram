#!/usr/bin/env python3
"""Read-only source inventory. File equality does NOT assert feature parity."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PORTS = {
    "feature/md3-sliders.patch": "adapted: SeekBarView and SlideChooseView; Inu SliderCell not imported",
    "feature/disable-browser-collapse.patch": "adapted: optional toggle",
    "feature/sort-albums-by-size.patch": "adapted: optional toggle, stable tie order",
    "feature/keep-search-peer-select.patch": "adapted: optional toggle",
    "feature/create-as-supergroup.patch": "adapted: GroupCreateFinalActivity row, both createChat paths",
    "debloat/disable-bg-parallax.patch": "adapted: optional toggle, SizeNotifierFrameLayout only",
    "debloat/disable-sensitive.patch": "adapted: optional toggle; the base's ignoreContentRestrictions "
                                       "only covers getRestrictionReason, not the isSensitive paths",
    "debloat/hashtag-suggestions.patch": "adapted: optional toggle",
    "debloat/disable-swipe-to-hide-general-topic.patch": "adapted: optional switch; block only the "
                                                          "General-topic swipe, retain selected-topic actions",
    "bugfix/bot-location-context.patch": "adapted: activity context replaced with application context",
    "bugfix/chat-reply-line-leak.patch": "adapted: one line, detach on the detach path",
    "bugfix/admin-sort-overflow.patch": "adapted: Long.compare in the admin list comparator",
    "bugfix/audio-alert-search.patch": "adapted: seed the action bar slide property",
    "bugfix/album-caption-spoiler-reset.patch": "adapted: reveal the primary message object",
    "bugfix/community-picker-avatar-npe.patch": "adapted: null-guard the picker avatar",
    "bugfix/custom-emoji-reaction-litemode.patch": "adapted: honour the animated-emoji LiteMode flag",
    "bugfix/forward-hide-caption-strips-text.patch": "adapted: isMediaEmpty instead of a null media",
    "bugfix/markdown-inside-links.patch": "adapted: keep url/textUrl entities out of link bodies",
    "bugfix/message-cache-fallback.patch": "adapted: count from the response when processing",
    "bugfix/phantom-peek-dialogs.patch": "adapted: skip the peek for chats we left",
    "bugfix/pause-on-media-transient.patch": "adapted: transient audio focus for voice/round media",
    "bugfix/qr-scanner-corner-points.patch": "adapted: discard incomplete scanner corner polygons",
    "bugfix/paid-reaction-animation.patch": "adapted: LiteMode also gates paid reaction enter animation",
    "bugfix/reaction-counter-jump.patch": "adapted: center narrow scrim preview counters",
    "bugfix/motion-photo-with-caption.patch": "adapted: preserve discardLivePhoto in PhotoEntry.copyFrom",
    "bugfix/story-privacy-hint-npe.patch": "adapted: safe first name for story privacy hints",
    "bugfix/profile-menu-duplicate-actions.patch": "adapted: avoid duplicate own-profile actions",
    "bugfix/group-call-record-timer.patch": "adapted: wait for a valid recording start date",
    "bugfix/instant-camera-cancel-race.patch": "adapted: ignore recorder cancellation after handler teardown",
    "bugfix/chat-preview-scroll-persist.patch": "adapted: do not persist chat-preview scroll position",
    "bugfix/reply-bar-revealed-spoiler.patch": "adapted: keep revealed reply spoilers visible",
    "bugfix/sticker-maker-recycled-bitmap.patch": "adapted: skip recycled segmentation source bitmaps",
    "bugfix/reply-longpress-cancel.patch": "adapted: cancel reply long press on cancel or drag out",
    "bugfix/sections-adapter-first-diff.patch": "adapted: avoid diff before initial section hashes",
    "bugfix/dialog-preview-code-color.patch": "adapted: strip URLSpanMono from dialog previews",
    "bugfix/statistics-shared-message-text.patch": "adapted: preserve emoji sizing in statistics subtitle",
    "bugfix/reaction-tag-text-baseline.patch": "adapted: remove font padding on saved-message tag label",
    "bugfix/list-row-dead-zones.patch": "adapted: ignore invisible clickable child and reset admin tag clickability",
    "bugfix/top-peers-unread-counter.patch": "adapted: refresh visible hint dialog counters on interface updates",
    "bugfix/profile-photo-location-dc-id.patch": "adapted: backfill dc_id on deprecated photo locations",
    "bugfix/recycler-stale-longpress-position.patch": "adapted: resolve child position before long-press callback",
    "bugfix/native-byte-buffer-pool.patch": "adapted: bound per-thread reusable wrappers to 64",
    "bugfix/edit-cover-null-frame.patch": "adapted: tolerate failed cover bitmap decoding",
    "bugfix/share-alert-search-dead-zone.patch": "adapted: align share grid hit area with sheet scroll offset",
    "bugfix/community-admin-list-spam.patch": "adapted: skip forbidden remote community admin requests, preserve cache reads",
    "bugfix/profile-black-bar.patch": "adapted: snap gradient state when peer color availability changes",
    # Reviewed: the base differs here, so the patch cannot be transferred as-is.
    "bugfix/popup-drag-select-high-ids.patch": "reviewed: base gates the same drag-select on a "
                                               "different tag threshold (3000), needs re-derivation",
    "bugfix/preserve-download-filename.patch": "inherited: base saveFileInternal already passes the name",
    # Reviewed against the base tree: already implemented there, so nothing to port.
    "feature/call-confirmation.patch": "inherited: NekoConfig.askBeforeCall in the base",
    "feature/confirm-internal-links.patch": "inherited: NaConfig.confirmAllLinks in the base",
    "debloat/disable-pull-to-next.patch": "inherited: NekoConfig.disableSwipeToNext and "
                                          "ChatsHelper.allowSwipeToNext, which gate more call sites",
    "debloat/disable-rounding.patch": "inherited: NekoConfig.disableNumberRounding, "
                                      "already exposed in OpenExteraGeneralActivity",
    "bugfix/chat-background-leak.patch": "inherited: base onDetachedFromWindow already uses "
                                         "the correct contains() polarity",
    "bugfix/chat-attach-alert-adapter-leak.patch": "inherited: base onDestroy already destroys the "
                                                   "mention adapter",
    "bugfix/file-load-cancel-stuck.patch": "inherited: the patch's own inu_waitingForCancelled code "
                                           "is present in the base",
    "bugfix/media-cancel-race.patch": "inherited: base already records reqId on the sending message",
    "bugfix/launch-activity-leaks.patch": "inherited: base already calls Bulletin.removeDelegate",
    "bugfix/owner-admin-check-npe.patch": "inherited: the base fixes the same crash differently, by "
                                          "null-guarding the admins array instead of returning early",
    "bugfix/universal-adapter-reorder-npe.patch": "inherited: updateReorder already checks NO_POSITION "
                                                 "and getItem(position) before calling factory.attachedView",
    "debloat/disable-swipe-to-unarchive.patch": "inherited: NaConfig.doNotUnarchiveBySwipe is "
                                                 "already checked in DialogsActivity and exposed in settings",
    "debloat/notification-bubbles.patch": "inherited: NekoConfig.disableNotificationBubbles "
                                          "already gates BubbleMetadata in NotificationsController",
    "debloat/hide-ai-features.patch": "inherited: AppearanceConfig/AiFeaturesHelper gates "
                                      "AI editor, summary and instant-view blocks",
    "debloat/hide-trending-stickers.patch": "inherited: NekoConfig.disableTrending (default true) "
                                           "gates loading and trending UI; cached sets remain usable elsewhere",
    "feature/show-seconds.patch": "inherited: NekoConfig.showSeconds and LocaleController.withSeconds "
                                  "already apply to getFormatterDay and related formats",
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def patch_inventory(source):
    rows = []
    for line in (source / "series").read_text().splitlines():
        name = line.strip()
        if not name or name.startswith("#"):
            continue
        path = source / "patches" / name
        text = path.read_text()
        rows.append({
            "patch": name,
            "sha256": digest(path),
            "subject": next((x[9:] for x in text.splitlines() if x.startswith("Subject: ")), ""),
            "status": PORTS.get(name, "unreviewed: may overlap with inherited features"),
        })
    return rows


def compare(left, right):
    a = {p.relative_to(left): p for p in left.rglob("*") if p.is_file()}
    b = {p.relative_to(right): p for p in right.rglob("*") if p.is_file()}
    differences = []
    equal = 0
    for path in sorted(a.keys() | b.keys()):
        ah = digest(a[path]) if path in a else None
        bh = digest(b[path]) if path in b else None
        if ah == bh:
            equal += 1
            continue
        differences.append({"path": str(path), "base_sha256": ah, "other_sha256": bh})
    return {"identical_files": equal, "unreviewed_differences": differences}


def inventory(workspace):
    base = workspace / "exteraless-12.10.3-beta12"
    nagram = workspace / "NagramXF-1251"
    inu = workspace / "inugram-12.10.1-43"
    md3 = workspace / "inugram-md3-sliders"
    for source in (base, nagram, inu, md3):
        if not source.is_dir():
            raise FileNotFoundError(source)
    java = Path("TMessagesProj/src/main/java")
    return {
        "schema": 1,
        "status": "partial integration; compiles and packages as a debug APK, device behaviour unverified",
        "base": base.name,
        "inugram": patch_inventory(inu),
        "inugram_md3": patch_inventory(md3),
        "inugram_variant_delta": compare(inu, md3),
        "nagram_java_delta": compare(base / java, nagram / java),
        "nagram_adapted": [{
            "feature": "Case-insensitive streamed <think> tags",
            "source": "TMessagesProj/src/main/java/app/regram/ai/network/ReasoningFilter.java",
            "status": "adapted: existing AI stream filter now recognizes mixed-case Nagram XF tags across chunks",
        }, {
            "feature": "ProxyPill display",
            "source": "TMessagesProj/src/main/java/app/regram/pillstack/pills/ProxyPill.java",
            "status": "inherited: proxy pill already renders currentProxy.ping; re:gram adds polling separately",
        }, {
            "feature": "NoPreloadTrackIfRepeatOne",
            "source": "TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java",
            "status": "adapted: NekoConfig.regramNoPreloadRepeatOne",
        }, {
            "feature": "ProxyPingController",
            "source": "TMessagesProj/src/main/java/org/telegram/messenger/ProxyPingController.java",
            "status": "adapted: NekoConfig.regramLiveProxyPing, ProxySettings API, feeds "
                      "ProxyPill and DrawerHeaderView which already read ProxyInfo.ping",
        }],
        "warning": "Differences are a review queue, not a list of missing features. Namespaces and APIs differ. Never overwrite the base with these files blindly.",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=ROOT.parent)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/source-inventory.json")
    args = parser.parse_args()
    result = inventory(args.workspace)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f"Inugram MD3: {len(result['inugram_md3'])} patches, "
          f"{sum(1 for s in PORTS.values() if s.startswith('adapted'))} adapted, "
          f"{sum(1 for s in PORTS.values() if s.startswith('inherited'))} inherited")
    print(f"Nagram XF: {len(result['nagram_java_delta']['unreviewed_differences'])} Java-tree differences to review")
    print(args.output)

"""Guards against compile/runtime regressions found by release compilation."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'TMessagesProj/src/main'


def test_no_statements_swallowed_by_comments():
    sources = {
        'kotlin/app/regram/pillstack/PillStackConfig.kt': 'private val dormantActive = LinkedHashMap<Int, Int>()',
        'java/org/telegram/ui/bots/BotLocation.java': 'this.context = context.getApplicationContext();',
        'java/org/telegram/ui/ChatUsersActivity.java': 'return Long.compare(',
        'java/org/telegram/ui/Components/ReplyMessageLine.java': 'sticker.detach();',
        'java/org/telegram/ui/Components/EditCoverButton.java': 'AndroidUtilities.runOnUIThread(() -> setImage(frame));',
    }
    for path, statement in sources.items():
        lines = (ROOT / path).read_text().splitlines()
        assert any(statement in line and not line.lstrip().startswith('//') for line in lines), path

package app.regram.chats;

import android.text.TextUtils;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import tw.nekomimi.nekogram.NekoConfig;

public final class LinkedCustomEmoji {

    public static final String PREFIX = "tg://emoji?id=";

    private LinkedCustomEmoji() {
    }

    public static boolean canSend(int account) {
        return NekoConfig.localPremium.Bool() && !UserConfig.getInstance(account).isPremium();
    }

    public static boolean isLink(TLRPC.MessageEntity entity) {
        return entity instanceof TLRPC.TL_messageEntityTextUrl && entity.url != null && entity.url.startsWith(PREFIX);
    }

    public static TLRPC.TL_messageEntityTextUrl toLink(TLRPC.TL_messageEntityCustomEmoji emoji) {
        TLRPC.TL_messageEntityTextUrl link = new TLRPC.TL_messageEntityTextUrl();
        link.offset = emoji.offset;
        link.length = emoji.length;
        link.url = PREFIX + emoji.document_id;
        return link;
    }

    public static boolean parse(CharSequence text, List<TLRPC.MessageEntity> entities) {
        if (text == null || entities == null || entities.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (!isLink(entity) || entity.offset < 0 || entity.length <= 0 || entity.offset + entity.length > text.length()) {
                continue;
            }
            long documentId;
            try {
                documentId = Long.parseLong(entity.url.substring(PREFIX.length()));
            } catch (NumberFormatException e) {
                FileLog.e(e);
                continue;
            }
            int[] emojiOnly = new int[1];
            ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(text.subSequence(entity.offset, entity.offset + entity.length).toString(), emojiOnly);
            if (documentId == 0 || emojiOnly[0] <= 0 || emojis.size() != 1) {
                continue;
            }
            TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
            emoji.offset = entity.offset;
            emoji.length = entity.length;
            emoji.document_id = documentId;
            emoji.local = true;
            entities.set(i, emoji);
            changed = true;
        }
        return changed;
    }

    public static ArrayList<TLRPC.MessageEntity> parsedCopy(CharSequence text, List<TLRPC.MessageEntity> entities) {
        if (entities == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> copy = new ArrayList<>(entities);
        parse(text, copy);
        return copy;
    }

    public static void replaceForSend(int account, long dialogId, List<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty() || !shouldReplace(account, dialogId)) {
            return;
        }
        HashSet<Long> groupEmoji = groupEmoji(account, dialogId);
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (!(entity instanceof TLRPC.TL_messageEntityCustomEmoji)) {
                continue;
            }
            TLRPC.TL_messageEntityCustomEmoji emoji = (TLRPC.TL_messageEntityCustomEmoji) entity;
            if (needsLink(account, groupEmoji, emoji.document_id, emoji.document)) {
                entities.set(i, toLink(emoji));
            }
        }
    }

    public static void replaceForSend(int account, long dialogId, TL_iv.RichMessage rich) {
        if (rich == null || rich.blocks == null || rich.blocks.isEmpty() || !shouldReplace(account, dialogId)) {
            return;
        }
        linkBlocks(account, groupEmoji(account, dialogId), rich.blocks);
    }

    public static TL_iv.textCustomEmoji richEmoji(TL_iv.textUrl url) {
        if (url == null || url.url == null || !url.url.startsWith(PREFIX) || !(url.text instanceof TL_iv.textPlain)) {
            return null;
        }
        String alt = ((TL_iv.textPlain) url.text).text;
        if (TextUtils.isEmpty(alt)) {
            return null;
        }
        long documentId;
        try {
            documentId = Long.parseLong(url.url.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
        int[] emojiOnly = new int[1];
        ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(alt, emojiOnly);
        if (documentId == 0 || emojiOnly[0] <= 0 || emojis.size() != 1) {
            return null;
        }
        TL_iv.textCustomEmoji emoji = new TL_iv.textCustomEmoji();
        emoji.document_id = documentId;
        emoji.alt = alt;
        return emoji;
    }

    private static boolean shouldReplace(int account, long dialogId) {
        return canSend(account) && dialogId != UserConfig.getInstance(account).getClientUserId();
    }

    private static HashSet<Long> groupEmoji(int account, long dialogId) {
        if (dialogId >= 0) {
            return null;
        }
        TLRPC.ChatFull chatFull = MessagesController.getInstance(account).getChatFull(-dialogId);
        if (chatFull == null || chatFull.emojiset == null) {
            return null;
        }
        TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(account).getGroupStickerSetById(chatFull.emojiset);
        if (set == null || set.documents == null) {
            return null;
        }
        HashSet<Long> ids = new HashSet<>();
        for (TLRPC.Document document : set.documents) {
            ids.add(document.id);
        }
        return ids;
    }

    private static boolean needsLink(int account, HashSet<Long> groupEmoji, long documentId, TLRPC.Document document) {
        if (groupEmoji != null && groupEmoji.contains(documentId)) {
            return false;
        }
        if (document == null) {
            document = AnimatedEmojiDrawable.findDocument(account, documentId);
        }
        return !MessageObject.isFreeEmoji(document);
    }

    private static void linkBlocks(int account, HashSet<Long> groupEmoji, List<TL_iv.PageBlock> blocks) {
        if (blocks == null) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            linkBlock(account, groupEmoji, blocks.get(i));
        }
    }

    private static void linkBlock(int account, HashSet<Long> groupEmoji, TL_iv.PageBlock b) {
        if (b == null) {
            return;
        }
        b.text = linkText(account, groupEmoji, b.text);
        if (b.caption != null) {
            b.caption.text = linkText(account, groupEmoji, b.caption.text);
            b.caption.credit = linkText(account, groupEmoji, b.caption.credit);
        }
        if (b instanceof TL_iv.pageBlockBlockquote) {
            TL_iv.pageBlockBlockquote q = (TL_iv.pageBlockBlockquote) b;
            q.caption = linkText(account, groupEmoji, q.caption);
        } else if (b instanceof TL_iv.pageBlockPullquote) {
            TL_iv.pageBlockPullquote q = (TL_iv.pageBlockPullquote) b;
            q.caption = linkText(account, groupEmoji, q.caption);
        } else if (b instanceof TL_iv.pageBlockBlockquoteBlocks) {
            TL_iv.pageBlockBlockquoteBlocks q = (TL_iv.pageBlockBlockquoteBlocks) b;
            q.caption = linkText(account, groupEmoji, q.caption);
            linkBlocks(account, groupEmoji, q.blocks);
        } else if (b instanceof TL_iv.pageBlockDetails) {
            TL_iv.pageBlockDetails d = (TL_iv.pageBlockDetails) b;
            d.title = linkText(account, groupEmoji, d.title);
            linkBlocks(account, groupEmoji, d.blocks);
        } else if (b instanceof TL_iv.pageBlockList) {
            for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) b).items) {
                if (item instanceof TL_iv.TL_pageListItemText) {
                    TL_iv.TL_pageListItemText text = (TL_iv.TL_pageListItemText) item;
                    text.text = linkText(account, groupEmoji, text.text);
                } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                    linkBlocks(account, groupEmoji, ((TL_iv.TL_pageListItemBlocks) item).blocks);
                }
            }
        } else if (b instanceof TL_iv.pageBlockOrderedList) {
            for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) b).items) {
                if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                    TL_iv.TL_pageListOrderedItemText text = (TL_iv.TL_pageListOrderedItemText) item;
                    text.text = linkText(account, groupEmoji, text.text);
                } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                    linkBlocks(account, groupEmoji, ((TL_iv.TL_pageListOrderedItemBlocks) item).blocks);
                }
            }
        } else if (b instanceof TL_iv.pageBlockTable) {
            TL_iv.pageBlockTable t = (TL_iv.pageBlockTable) b;
            t.title = linkText(account, groupEmoji, t.title);
            if (t.rows != null) {
                for (TL_iv.pageTableRow row : t.rows) {
                    if (row == null || row.cells == null) {
                        continue;
                    }
                    for (TL_iv.pageTableCell cell : row.cells) {
                        if (cell != null) {
                            cell.text = linkText(account, groupEmoji, cell.text);
                        }
                    }
                }
            }
        } else if (b instanceof TL_iv.pageBlockButtonRow) {
            TL_iv.pageBlockButtonRow row = (TL_iv.pageBlockButtonRow) b;
            if (row.buttons != null) {
                for (TL_keyboard.PageButton button : row.buttons) {
                    if (button != null) {
                        button.text = linkText(account, groupEmoji, button.text);
                    }
                }
            }
        } else if (b instanceof TL_iv.pageBlockCollage) {
            linkBlocks(account, groupEmoji, ((TL_iv.pageBlockCollage) b).items);
        } else if (b instanceof TL_iv.pageBlockSlideshow) {
            linkBlocks(account, groupEmoji, ((TL_iv.pageBlockSlideshow) b).items);
        }
    }

    private static TL_iv.RichText linkText(int account, HashSet<Long> groupEmoji, TL_iv.RichText rt) {
        if (rt == null) {
            return null;
        }
        if (rt instanceof TL_iv.textCustomEmoji) {
            TL_iv.textCustomEmoji emoji = (TL_iv.textCustomEmoji) rt;
            if (!needsLink(account, groupEmoji, emoji.document_id, null)) {
                return rt;
            }
            TL_iv.textUrl url = new TL_iv.textUrl();
            TL_iv.textPlain plain = new TL_iv.textPlain();
            plain.text = TextUtils.isEmpty(emoji.alt) ? "\uD83D\uDE00" : emoji.alt;
            plain.parentRichText = url;
            url.text = plain;
            url.url = PREFIX + emoji.document_id;
            url.parentRichText = emoji.parentRichText;
            return url;
        }
        if (rt instanceof TL_iv.textConcat) {
            ArrayList<TL_iv.RichText> texts = rt.texts;
            for (int i = 0; i < texts.size(); i++) {
                texts.set(i, linkText(account, groupEmoji, texts.get(i)));
            }
            return rt;
        }
        rt.text = linkText(account, groupEmoji, rt.text);
        return rt;
    }

    public static boolean isLinkOnlyMessage(TLRPC.Message message) {
        if (message == null || message.entities == null || message.entities.isEmpty() || !MessageObject.isMediaEmptyWebpage(message)) {
            return false;
        }
        boolean found = false;
        for (TLRPC.MessageEntity entity : message.entities) {
            if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                if (!isLink(entity)) {
                    return false;
                }
                found = true;
            } else if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityEmail) {
                return false;
            } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji && ((TLRPC.TL_messageEntityCustomEmoji) entity).local) {
                found = true;
            }
        }
        return found;
    }
}

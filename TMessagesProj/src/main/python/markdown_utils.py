"""Markdown parsing for plugins (``markdown_utils.parse_markdown``).

The single most-used formatting entry point in real exteraGram plugins: of the
361 plugins in the public catalogue, 37 import ``parse_markdown`` at module
level, so a missing module does not degrade them — it stops them loading.

The contract those plugins rely on is narrow and fully covered here::

    parsed = parse_markdown(text)
    params.message = parsed.text
    for entity in parsed.entities:
        params.entities.add(entity.to_tlrpc_object())

``ParsedText.entities`` therefore holds :class:`MarkdownEntity` objects, NOT
ready-made TLRPC objects: materialisation is deferred to
``to_tlrpc_object()`` so that ``parse_markdown`` stays usable off the JVM
(host tests) and so that a plugin can inspect offsets before committing.

Offsets and lengths are UTF-16 code units, matching what Telegram's
MessageEntity expects — plugins that build their own entities alongside these
compute the same way, e.g.::

    len(parsed.text.encode('utf_16_le')) / 2

Dialect (from :mod:`extera_utils.text_formatting`): ``**bold**``,
``__italic__``, ``~~strike~~``, ``||spoiler||``, ``` `code` ```,
```` ```lang\\ncode``` ````, ``[text](url)``, ``>quote`` / ``**>collapsed``,
and ``![alt](tg://emoji?id=...)`` custom emoji.
"""

from typing import Any, List, Optional

from extera_utils.text_formatting import RawEntity, _utf16_length, parse_raw

__all__ = ["MarkdownEntity", "ParsedText", "ParsedMessage", "RawEntity",
           "parse_markdown", "parse_html", "to_utf16_len", "count_chars_until"]


def to_utf16_len(s) -> int:
    """Длина *s* в суррогатных парах UTF-16 — в этих единицах Telegram
    считает offset/length сущностей (то же, что ``len(s.encode('utf_16_le'))/2``)."""
    return _utf16_length("" if s is None else str(s))


def count_chars_until(string, stop_chars, start_index: int = 0) -> int:
    """Число единиц UTF-16 от *start_index* до первого символа из *stop_chars*."""
    if string is None:
        return 0
    text = str(string)
    idx = max(int(start_index), 0)
    end = idx
    while end < len(text) and text[end] not in stop_chars:
        end += 1
    return to_utf16_len(text[idx:end])



class MarkdownEntity:
    """One formatting span, materialised into TLRPC only on demand.

    Attributes mirror TLRPC.MessageEntity so that plugins can read
    ``entity.offset`` / ``entity.length`` before conversion.
    """

    __slots__ = ("offset", "length", "type", "url", "language",
                 "document_id", "collapsed", "_raw")

    def __init__(self, raw: RawEntity):
        self._raw = raw
        self.offset = raw.offset
        self.length = raw.length
        self.type = raw.type
        self.url = raw.url
        self.language = raw.language
        self.document_id = raw.document_id
        self.collapsed = raw.collapsed

    def to_tlrpc_object(self) -> Any:
        """Build the matching ``TLRPC.TL_messageEntity*`` instance.

        Reads the live attributes rather than the captured RawEntity, so a
        plugin that shifts ``entity.offset`` (common when prepending a header
        to the message) gets the shift honoured.
        """
        from extera_utils.text_formatting import _materialize_entities

        raw = RawEntity(
            offset=int(self.offset),
            length=int(self.length),
            type=self.type,
            url=self.url,
            language=self.language,
            document_id=self.document_id,
            collapsed=bool(self.collapsed),
        )
        return _materialize_entities([raw])[0]

    def __repr__(self) -> str:
        return (f"MarkdownEntity({self.type}, offset={self.offset}, "
                f"length={self.length})")


class ParsedText:
    """Result of :func:`parse_markdown`: plain ``text`` plus ``entities``."""

    __slots__ = ("text", "entities")

    def __init__(self, text: str, entities: List[MarkdownEntity]):
        self.text = text
        self.entities = entities

    # Some plugins unpack the result instead of using the attributes.
    def __iter__(self):
        return iter((self.text, self.entities))

    def __len__(self) -> int:
        return len(self.text)

    def __str__(self) -> str:
        return self.text

    def __repr__(self) -> str:
        return f"ParsedText({self.text!r}, {len(self.entities)} entities)"


# Имя результата parse_markdown в SDK (плагины пишут ``parsed: ParsedMessage``).
ParsedMessage = ParsedText


def _parse(text: Any, parse_mode: str) -> ParsedText:
    plain, raw_entities = parse_raw("" if text is None else str(text), parse_mode)
    return ParsedText(plain, [MarkdownEntity(raw) for raw in raw_entities])


def parse_markdown(text: Any) -> ParsedText:
    """Parse Telegram-flavoured Markdown into plain text plus entities."""
    return _parse(text, "Markdown")


def parse_html(text: Any) -> ParsedText:
    """HTML counterpart of :func:`parse_markdown`, same result shape."""
    return _parse(text, "HTML")

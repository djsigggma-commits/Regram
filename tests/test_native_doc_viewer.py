"""Actual native Java document parsers on a host JVM, not Android device/rendering tests."""
import base64
import gzip
import io
import json
import lzma
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import zipfile
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "TMessagesProj/src/main/java/app/regram/documents"


@pytest.fixture(scope="module")
def reader(tmp_path_factory):
    javac, java = shutil.which("javac"), shutil.which("java")
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    required = ["org.json/json", "org.apache.commons/commons-compress", "org.apache.commons/commons-text",
                "org.apache.commons/commons-lang3", "commons-io/commons-io", "org.tukaani/xz",
                "com.atlassian.commonmark/commonmark", "com.atlassian.commonmark/commonmark-ext-gfm-tables",
                "com.atlassian.commonmark/commonmark-ext-gfm-strikethrough"]
    jars = []
    for artifact in required:
        versions = list((cache / artifact).glob("*"))
        versions.sort(key=lambda p: tuple(int(x) if x.isdigit() else 0 for x in p.name.replace("-", ".").split(".")), reverse=True)
        candidates = next((list(v.glob("*/*.jar")) for v in versions if list(v.glob("*/*.jar"))), [])
        if not candidates:
            pytest.skip(f"cached JVM dependency needed: {artifact}")
        jars.append(str(candidates[0]))
    if not javac or not java:
        pytest.skip("JDK in PATH required")
    directory = tmp_path_factory.mktemp("native_document_jvm")
    files = []
    for name in ("DocumentReader", "DocumentFormat", "DocumentText", "DocumentMail", "DocumentWeb"):
        path = directory / f"{name}.java"
        path.write_text((SOURCE / f"{name}.java").read_text())
        files.append(str(path))
    harness = directory / "ReaderTest.java"
    harness.write_text(r'''package app.regram.documents;
import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;
public class ReaderTest {
 public static void main(String[] args)throws Exception {
  JSONObject result=new JSONObject();
  try {
   String mode=args[0];
   if(mode.equals("load")) {
    DocumentReader.Result doc=DocumentReader.load(new File(args[1]),args[2],new File(args[1]).getName(),new DocumentReader.Options(),args.length>3?args[3]:null);
    result.put("pages",new JSONArray(doc.pages)).put("text",String.join(doc.separator,doc.pages)).put("font",doc.font).put("wrap",doc.wrap).put("raw",doc.raw).put("encoding",doc.encoding);
   } else if(mode.equals("xml")) result.put("root",DocumentText.xml(Files.readAllBytes(Paths.get(args[1]))).name);
   else if(mode.equals("web")) result.put("html",DocumentWeb.read(new File(args[1]),new File(args[1]).getName(),0));
   else if(mode.equals("detect")) result.put("format",DocumentFormat.detect(args[1],args.length>2?args[2]:""));
   else if(mode.equals("sniff")) result.put("textual",DocumentFormat.textual(new File(args[1])));
   else if(mode.equals("pages")) result.put("pages",new JSONArray(DocumentText.pages(args[1],Integer.parseInt(args[2]))));
   else if(mode.equals("resolve")) result.put("path",DocumentReader.resolve(args[1],args[2]));
   else if(mode.equals("bitmap")) {int[] size=DocumentText.bitmapSize(Integer.parseInt(args[1]),Integer.parseInt(args[2]),1080,3000000);result.put("width",size[0]).put("height",size[1]);}
   else if(mode.equals("cancel")) {Thread.currentThread().interrupt();DocumentReader.load(new File(args[1]),"text","test.txt",new DocumentReader.Options(),null);}
   else if(mode.equals("budget")) {try(DocumentReader.Package zip=new DocumentReader.Package(new File(args[1]))) {for(int i=0;i<65;i++)zip.bytes("part");}}
  } catch(Exception e) { result=new JSONObject().put("error",e.getClass().getSimpleName()+": "+e.getMessage()); }
  System.out.println(result);
 }
}''')
    classpath = os.pathsep.join(jars)
    compiled = subprocess.run([javac, "-encoding", "UTF-8", "-cp", classpath, "-d", str(directory), *files, str(harness)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stderr

    def call(*arguments):
        run = subprocess.run([java, "-Xmx384m", "-cp", str(directory) + os.pathsep + classpath, "app.regram.documents.ReaderTest", *map(str, arguments)], capture_output=True, text=True, timeout=20)
        assert run.returncode == 0, run.stdout + run.stderr
        return json.loads(run.stdout)
    return call


def zipped(path, parts):
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, content in parts.items():
            archive.writestr(name, content)
    return path


def test_native_docx_text_tables_and_lists(reader, tmp_path):
    file = zipped(tmp_path / "document.docx", {"word/document.xml": '''<w:document xmlns:w="urn:word"><w:body>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Hello</w:t><w:tab/><w:t>world</w:t></w:r></w:p>
<w:tbl><w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:pPr><w:numPr/></w:pPr><w:r><w:t>Listed</w:t></w:r></w:p></w:body></w:document>'''})
    result = reader("load", file, "docx")
    assert "error" not in result
    assert "# Hello\tworld" in result["text"]
    assert "A │ B" in result["text"] and "• Listed" in result["text"]


def test_native_xlsx_relationships_cached_values_and_dates(reader, tmp_path):
    file = zipped(tmp_path / "book.xlsx", {
        "xl/workbook.xml": '<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Invoice" r:id="s1"/></sheets></workbook>',
        "xl/_rels/workbook.xml.rels": '<Relationships><Relationship Id="s1" Target="worksheets/sheet7.xml"/></Relationships>',
        "xl/sharedStrings.xml": '<sst><si><t>Item</t></si></sst>',
        "xl/styles.xml": '<styleSheet><cellXfs><xf numFmtId="14"/></cellXfs></styleSheet>',
        "xl/worksheets/sheet7.xml": '<worksheet><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="b"><v>1</v></c><c r="C1" s="0"><v>61</v></c><c r="D1"><f>99+3</f><v>102</v></c></row></sheetData></worksheet>',
    })
    result = reader("load", file, "xlsx")
    assert all(x in result["text"] for x in ("Invoice", "Item", "TRUE", "1900-03-01", "102"))
    assert result["font"] == 2 and not result["wrap"]


def test_native_pptx_preserves_manifest_order_and_namespaced_ids(reader, tmp_path):
    ns = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    file = zipped(tmp_path / "slides.pptx", {
        "ppt/presentation.xml": f'<p xmlns:r="{ns}"><sldIdLst><sldId r:id="r2" id="256"/><sldId r:id="r1" id="257"/></sldIdLst></p>',
        "ppt/_rels/presentation.xml.rels": '<Relationships><Relationship Id="r1" Target="slides/slide1.xml"/><Relationship Id="r2" Target="slides/slide2.xml"/></Relationships>',
        "ppt/slides/slide1.xml": '<sld><p><r><t>First file</t></r></p></sld>',
        "ppt/slides/slide2.xml": '<sld><p><r><t>Second file</t></r></p></sld>',
    })
    result = reader("load", file, "pptx")
    assert "Second file" in result["pages"][0] and "First file" in result["pages"][1]


def test_native_odf_repetition_is_bounded(reader, tmp_path):
    file = zipped(tmp_path / "sheet.ods", {"content.xml": '''<office xmlns:t="urn:table"><body><spreadsheet><table t:name="Sheet"><table-row t:number-rows-repeated="999999999"><table-cell t:number-columns-repeated="999999999"><p>value</p></table-cell></table-row></table></spreadsheet></body></office>'''})
    result = reader("load", file, "odf")
    assert "error" not in result and "Sheet" in result["text"]
    assert result["text"].count("value") == 20 * 60
    assert not result["wrap"]


def test_native_epub_spine_and_html_text(reader, tmp_path):
    file = zipped(tmp_path / "book.epub", {
        "META-INF/container.xml": '<container><rootfile full-path="OEBPS/book.opf"/></container>',
        "OEBPS/book.opf": '<package><manifest><item id="first" href="part+one.xhtml"/><item id="second" href="two.xhtml"/></manifest><spine><itemref idref="second"/><itemref idref="first"/></spine></package>',
        "OEBPS/part+one.xhtml": '<html><body><p>Chapter one &amp; text</p><script>secret code</script></body></html>',
        "OEBPS/two.xhtml": '<html><head><title>hidden</title></head><body><h1>Chapter two</h1></body></html>',
    })
    result = reader("load", file, "epub")
    assert result["font"] == 1 and "Chapter two" in result["pages"][0]
    assert "Chapter one & text" in result["pages"][1]
    assert "secret code" not in result["text"] and "hidden" not in result["text"]


def test_native_fb2_and_rtf(reader, tmp_path):
    fb2 = tmp_path / "book.fb2"
    fb2.write_text('<FictionBook><description><book-title>Book title</book-title></description><body><section><title><p>Chapter</p></title><p>Привет 😀</p></section></body></FictionBook>')
    assert "Привет 😀" in reader("load", fb2, "fb2")["text"]
    rtf = tmp_path / "letter.rtf"
    escaped = "".join("\\'%02x" % b for b in "Привет".encode("cp1251"))
    rtf.write_bytes(("{\\rtf1\\ansi\\ansicpg1251{\\fonttbl hidden}" + escaped + "\\par \\u55357?\\u56832?}").encode())
    result = reader("load", rtf, "rtf")
    assert "Привет" in result["text"] and "😀" in result["text"] and "hidden" not in result["text"]


def test_native_mime_mail_lists_but_does_not_extract_attachments(reader, tmp_path):
    file = tmp_path / "mail.eml"
    subject = base64.b64encode("Письмо".encode()).decode()
    content = base64.b64encode("Plain body 😀".encode()).decode()
    file.write_text(f'''From: sender@example.com
Subject: =?UTF-8?B?{subject}?=
Content-Type: multipart/mixed; boundary="test"

--test
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: base64

{content}
--test
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="../../escape.bin"

not decoded
--test--
''')
    result = reader("load", file, "eml")
    assert "Письмо" in result["text"] and "Plain body 😀" in result["text"]
    assert "../../escape.bin" in result["text"] and "not decoded" not in result["text"]
    assert not (tmp_path.parent.parent / "escape.bin").exists()


@pytest.mark.parametrize("format,filename,source,expected", [
    ("csv", "table.csv", 'name;value\n"multi\nline";"a; b"\n', "multi line"),
    ("json", "object.json", '{"hello":"мир","array":[1,2]}', '"hello": "мир"'),
    ("ipynb", "note.ipynb", '{"cells":[{"cell_type":"code","source":["print(2)"],"outputs":[{"output_type":"stream","text":["2\\n"]}]}]}', "Out:\n2"),
])
def test_native_text_formats(reader, tmp_path, format, filename, source, expected):
    file = tmp_path / filename
    file.write_text(source)
    result = reader("load", file, format)
    assert "error" not in result and expected in result["text"]
    assert result["raw"]


def test_native_encoding_and_truncated_utf8(reader, tmp_path):
    file = tmp_path / "text.txt"
    file.write_bytes("Привет".encode("cp1251"))
    result = reader("load", file, "text")
    assert result["text"] == "Привет" and result["encoding"] == "windows-1251"
    file.write_bytes("Привет 😀".encode("utf-16"))
    assert reader("load", file, "text")["text"] == "Привет 😀"
    file.write_bytes(b"a" * (12 * 1024 * 1024 - 1) + "😀".encode())
    result = reader("load", file, "text")
    assert result["encoding"] == "UTF-8" and "Truncated" in result["text"]


@pytest.mark.parametrize("suffix", [".tar", ".tar.gz", ".tar.bz2", ".tar.xz"])
def test_native_archives_only_list_files(reader, tmp_path, suffix):
    file = tmp_path / ("archive" + suffix)
    mode = {".tar": "w", ".tar.gz": "w:gz", ".tar.bz2": "w:bz2", ".tar.xz": "w:xz"}[suffix]
    with tarfile.open(file, mode) as archive:
        entry = tarfile.TarInfo("../../escape.txt"); entry.size = 3
        archive.addfile(entry, io.BytesIO(b"abc"))
    result = reader("load", file, "archive")
    assert "error" not in result and "../../escape.txt" in result["text"]
    assert not (tmp_path.parent.parent / "escape.txt").exists()
    zipped_file = zipped(tmp_path / "archive.zip", {"../escape.txt": "abc"})
    assert "../escape.txt" in reader("load", zipped_file, "archive")["text"]


@pytest.mark.parametrize("encoding", ["utf-8", "utf-16"])
def test_xml_external_entities_are_rejected_before_parsing(reader, tmp_path, encoding):
    file = tmp_path / "attack.xml"
    file.write_bytes('<!DOCTYPE x [<!ENTITY external SYSTEM "file:///etc/passwd">]><x>&external;</x>'.encode(encoding))
    assert "DTD/entities" in reader("xml", file)["error"]


def test_xml_depth_and_malformed_inputs(reader, tmp_path):
    file = tmp_path / "deep.xml"
    file.write_text("<x>" * 70 + "text" + "</x>" * 70)
    assert "complexity" in reader("xml", file)["error"]
    file.write_text("<x><broken></x>")
    assert "error" in reader("xml", file)


def test_zip_limits_crc_and_duplicate_parts(reader, tmp_path):
    file = zipped(tmp_path / "oversized.docx", {"word/document.xml": b"x" * (12 * 1024 * 1024 + 1)})
    assert "exceeds 12 MiB" in reader("load", file, "docx")["error"]
    file = zipped(tmp_path / "budget.zip", {"part": b"x" * (1024 * 1024)})
    assert "limit" in reader("budget", file)["error"]
    file = tmp_path / "duplicate.docx"
    with pytest.warns(UserWarning):
        with zipfile.ZipFile(file, "w") as archive:
            archive.writestr("word/document.xml", "<x/>")
            archive.writestr("word/document.xml", "<x/>")
    assert "duplicate" in reader("load", file, "docx")["error"]
    file = tmp_path / "crc.docx"
    with zipfile.ZipFile(file, "w", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr("word/document.xml", '<document><p><t>Hello</t></p></document>')
    file.write_bytes(file.read_bytes().replace(b"Hello", b"Jello"))
    assert "CRC mismatch" in reader("load", file, "docx")["error"]


def test_tar_expansion_limit_covers_skipped_payload(reader, tmp_path):
    file = tmp_path / "bomb.tar.gz"
    entry = tarfile.TarInfo("huge"); entry.size = 70 * 1024 * 1024
    with gzip.open(file, "wb") as out:
        out.write(entry.tobuf())
        chunk = b"\0" * (1024 * 1024)
        for i in range(65):
            out.write(chunk)
    result = reader("load", file, "archive")
    assert "64 MiB" in result["error"]


def test_offline_markdown_and_svg(reader, tmp_path):
    file = tmp_path / "preview.md"
    file.write_text('# Heading\n\n**bold**\n\n<script>alert(1)</script>\n\n[click](javascript:alert)\n\n| A | B |\n|---|---|\n| 1 | 2 |')
    html = reader("web", file)["html"]
    assert "<h1>Heading</h1>" in html and "<strong>bold</strong>" in html and "<table>" in html
    assert "<script>" not in html and "javascript:" not in html
    assert "default-src 'none'" in html and "form-action 'none'" in html
    file.write_text("> " * 100 + "nested")
    assert "nesting" in reader("web", file)["error"]
    svg = tmp_path / "preview.svg"
    svg.write_text('<!DOCTYPE svg SYSTEM "http://example.com/x"><svg/>')
    assert "DTD/entities" in reader("web", svg)["error"]


def test_cancel_pagination_relationships_and_bitmap_bounds(reader, tmp_path):
    file = tmp_path / "cancel.txt"; file.write_text("test")
    assert "InterruptedIOException" in reader("cancel", file)["error"]
    source = "abc😀def😀xyz"
    pages = reader("pages", source, "4")["pages"]
    assert "".join(pages) == source
    assert all(not any(0xD800 <= ord(c) <= 0xDFFF for c in p) for p in pages)
    assert reader("resolve", "OEBPS/", "../text.xhtml")["path"] == "text.xhtml"
    for target in ("../../escape", "https://example.com/x", "//example.com/x", "x\\y"):
        assert "error" in reader("resolve", "OEBPS/", target)
    for width, height in [(1000, 2000), (2147483647, 2147483647), (1, 2147483647), (2147483647, 1)]:
        result = reader("bitmap", width, height)
        assert result["width"] * result["height"] <= 3000000
        assert max(result["width"], result["height"]) <= 4096
    assert "error" in reader("bitmap", 0, 100)


def test_format_selection_and_binary_sniff(reader, tmp_path):
    for name, fmt in [("README", "text"), (".gitignore", "text"), ("a.tar.xz", "archive"), ("b.DOCX", "docx"), ("a.geojson", "json"), ("a.md", "web")]:
        assert reader("detect", name)["format"] == fmt
    for name in ("test.attheme", "test.extera", "app.apk", "test.dex"):
        assert "format" not in reader("detect", name, "text/plain")
    assert reader("detect", "file.bin", "application/pdf")["format"] == "pdf"
    file = tmp_path / "binary.txt"; file.write_bytes(b"binary\0bytes")
    assert not reader("sniff", file)["textual"]


def test_native_integration_privacy_and_lifecycle_contracts():
    utility = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java").read_text()
    assert "NativeDocViewer.tryOpen" in utility
    assert "restrict, message.currentAccount, nativeProtected)" in utility
    assert "isPeerNoForwards(message.getDialogId())" in utility
    native = (SOURCE / "NativeDocViewer.java").read_text()
    assert 'option("enabled", false)' in native
    assert "SharedConfig.appLocked" in native and "MessageObject.isV" in native
    assert "file.getCanonicalPath()" in native and 'account + "|" + format' in native
    dialog = (SOURCE / "DocumentViewerDialog.java").read_text()
    for setting in ("setJavaScriptEnabled(false)", "setBlockNetworkLoads(true)", "setAllowFileAccess(false)", "setAllowContentAccess(false)", "setDomStorageEnabled(false)"):
        assert setting in dialog
    assert "shouldInterceptRequest" in dialog and "shouldOverrideUrlLoading" in dialog
    assert "request.deny()" in dialog and "onRenderProcessGone" in dialog
    assert "worker.execute(this::closePdf)" in dialog and "worker.shutdown()" in dialog
    assert "token == generation" in dialog and "bitmap.recycle()" in dialog
    assert "if (!restricted)" in dialog and "if (restricted) return;" in dialog
    assert "FLAG_SECURE" in dialog and "childDialogs" in dialog
    assert "acquire(" not in dialog and "Thread.sleep" not in dialog
    launch = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java").read_text()
    assert "NativeDocViewer.closeAll()" in launch and "NativeDocViewer.closeFor(this)" in launch
    for file in SOURCE.glob("*.java"):
        text = file.read_text()
        assert "hookMethod(" not in text and "import app.regram.plugins" not in text and "DexClassLoader" not in text and "Python.getInstance" not in text
    values = [ET.parse(ROOT / f"TMessagesProj/src/main/res/{folder}/regram_documents.xml").getroot() for folder in ("values", "values-ru")]
    assert {x.attrib["name"] for x in values[0]} == {x.attrib["name"] for x in values[1]}

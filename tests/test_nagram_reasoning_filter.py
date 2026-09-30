"""Runtime regression for Nagram XF's case-insensitive reasoning tag parsing."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "TMessagesProj/src/main/java/app/regram/ai/network/ReasoningFilter.java"


class ReasoningFilterTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
    def test_mixed_case_tags_across_stream_chunks(self):
        program = '''
            import app.regram.ai.network.ReasoningFilter;
            public class ReasoningFilterCheck {
                private static void equal(String expected, String actual) {
                    if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
                }
                public static void main(String[] args) {
                    ReasoningFilter f = new ReasoningFilter();
                    equal("hello", f.filter("hello<Th"));
                    equal("", f.filter("InK>secret</TH"));
                    if (!f.consumeReasoningSignal()) throw new AssertionError("no reasoning signal");
                    equal(" world", f.filter("iNk> world"));
                    equal("secret", f.consumeReasoning());
                    equal("", f.flush());
                    ReasoningFilter plain = new ReasoningFilter();
                    equal("hi", plain.filter("hi<TH"));
                    equal("<TH", plain.flush());
                }
            }
        '''
        with tempfile.TemporaryDirectory() as directory:
            harness = Path(directory) / "ReasoningFilterCheck.java"
            harness.write_text(program)
            subprocess.run(["javac", "-d", directory, str(SOURCE), str(harness)],
                           check=True, capture_output=True, text=True, timeout=40)
            subprocess.run(["java", "-cp", directory, "ReasoningFilterCheck"],
                           check=True, capture_output=True, text=True, timeout=40)


if __name__ == "__main__":
    unittest.main()

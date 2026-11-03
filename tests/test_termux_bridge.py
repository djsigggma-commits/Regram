"""Native bridge tests. Host sockets/JVM + reviewed Termux CLI, not Android device tests."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import types
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/app/regram/termux"
ASSETS = ROOT / "TMessagesProj/src/main/assets/regram"


def client_module():
    module = types.ModuleType("tgbridge_test")
    source = (ASSETS / "termux_client.py.txt").read_text().replace("@@PORT@@", "42691").replace("@@TOKEN@@", "a" * 48).replace("@@PACKAGE@@", "app.regram.android")
    exec(compile(source, "<reviewed-termux-cli>", "exec"), module.__dict__)
    return module


def test_installer_private_files_and_valid_python(tmp_path):
    client = (ASSETS / "termux_client.py.txt").read_text().replace("@@PORT@@", "42691").replace("@@TOKEN@@", "a" * 48).replace("@@PACKAGE@@", "app.regram.android")
    compile(client, "tgbridge.py", "exec")
    installer = (ASSETS / "termux_installer.py.txt").read_text().replace("@@SRC@@", json.dumps(client, ensure_ascii=False))
    compile(installer, "installer.py", "exec")
    assert 'os.chmod(d + "/tgbridge.py", 0o600)' in installer
    assert "mode=0o700" in installer
    assert "BasePlugin" not in client


def test_rpc_does_not_replay_side_effects_after_disconnect():
    client = client_module()
    bridge = client.Bridge()
    class Socket:
        count = 0
        def sendall(self, data):
            self.count += 1
            payload = json.loads(data)
            assert payload["token"] == "a" * 48 and payload["sid"] == client.SID
        def recv(self, count):
            raise ConnectionResetError("response lost after execution")
        def close(self):
            pass
    socket = Socket()
    bridge.sock = socket
    with pytest.raises(ConnectionResetError):
        bridge.send("exec", code="do_something()")
    assert socket.count == 1


@pytest.mark.parametrize("answer,executed", [("n", False), ("", False), ("y", True), ("да", True)])
def test_ai_requires_confirmation(monkeypatch, answer, executed):
    client = client_module()
    commands = []
    class Bridge:
        def send(self, cmd, **kwargs):
            commands.append(cmd)
            return {"ok": True, "text": "```python\nmarker.append(1)\n```"}
    marker = []
    client.NS["marker"] = marker
    monkeypatch.setattr("builtins.input", lambda *args: answer)
    client.cmd_ai(Bridge(), "test")
    assert bool(marker) == executed
    assert commands == ["ai"]


def test_panel_spec_and_callbacks(monkeypatch):
    client = client_module()
    sent = []
    class Bridge:
        def send(self, cmd, **kw):
            sent.append((cmd, kw))
            return {"ok": True}
    monkeypatch.setattr(client, "get_bridge", lambda: Bridge())
    monkeypatch.setattr(client.EVENTS, "start", lambda: None)
    hits = []
    panel = client.Panel("Control", id="test")
    button = panel.button("Hi", lambda: hits.append("click"))
    toggle = panel.toggle("Switch", lambda v: hits.append(v))
    with panel.row():
        panel.text("row text")
        panel.slider("Value", 0, 100)
    panel.show()
    assert sent[0][0] == "panel"
    assert sent[0][1]["items"][2]["type"] == "row"
    client.EVENTS.dispatch({"event": "click", "panel": "test", "id": button})
    client.EVENTS.dispatch({"event": "toggle", "panel": "test", "id": toggle, "value": True})
    assert hits == ["click", True]
    panel.close()
    assert "test" not in client.OPEN


def test_python_helper_does_not_redirect_shared_stdout(monkeypatch):
    java = types.ModuleType("java")
    java.jclass = lambda name: name
    monkeypatch.setitem(sys.modules, "java", java)
    spec = importlib.util.spec_from_file_location("termux_exec_test", ROOT / "TMessagesProj/src/main/python/regram_termux_exec.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    commands = []
    class Bridge:
        fragment = staticmethod(lambda: None)
        def executeLocal(self, session, payload):
            commands.append(json.loads(payload))
            return '{"ok": true}'
        def pythonUi(self, session, fn):
            fn()
    stdout = sys.stdout
    result = json.loads(module.run("print('hello'); show('overlay'); ui(lambda: print('ui'))", Bridge(), object()))
    assert result == {"ok": True, "output": "hello\nui\n"}
    assert commands == [{"cmd": "show", "text": "overlay"}]
    assert sys.stdout is stdout
    result = json.loads(module.run("print('x'*100000)", Bridge(), object()))
    assert len(result["output"]) == 65536
    result = json.loads(module.run("raise ValueError('expected')", Bridge(), object()))
    assert not result["ok"] and "ValueError: expected" in result["output"]


def test_native_wiring_and_translations():
    bridge = (JAVA / "TermuxBridge.java").read_text()
    assert 'getBoolean("enabled", false)' in bridge
    assert 'getBoolean("allow_exec", false)' in bridge
    assert 'InetAddress.getByName("127.0.0.1")' in bridge
    assert "MessageDigest.isEqual" in (JAVA / "TermuxWire.java").read_text()
    assert 'setInstanceFollowRedirects(false)' in bridge
    assert 'SharedConfig.appLocked' in bridge
    assert 'task.cancel(false)' in bridge
    assert 'new ArrayBlockingQueue<>(64)' in bridge
    assert 'clients.size() >= 8' in bridge
    assert 'getModule("regram_termux_exec")' in bridge
    assert 'getModule("extera_utils.plugin_loader")' not in bridge
    for path in JAVA.glob("*.java"):
        source = path.read_text()
        assert "import de.robv" not in source and "import app.regram.plugins" not in source
        assert "DexClassLoader" not in source and "hookMethod(" not in source
    activity = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java").read_text()
    for call in ("attach(this)", "detach(this)", "locked()"):
        assert "TermuxBridge.getInstance()." + call in activity
    values = []
    for directory in ("values", "values-ru"):
        tree = ET.parse(ROOT / f"TMessagesProj/src/main/res/{directory}/regram_termux.xml")
        values.append({x.attrib["name"] for x in tree.getroot()})
    assert values[0] == values[1]
    for filename in ("regram_backup_rules.xml", "regram_data_extraction_rules.xml"):
        tree = ET.parse(ROOT / f"TMessagesProj/src/main/res/xml/{filename}")
        assert all(x.attrib["path"] == "regram_termux.xml" for x in tree.iter("exclude"))


STUBS = {
    "android/content/SharedPreferences.java": '''package android.content;
public class SharedPreferences { final java.util.Map<String,Object> values=new java.util.concurrent.ConcurrentHashMap<>();
 public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
 public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
 public Editor edit(){return new Editor();} public class Editor {
 public Editor putString(String k,String v){values.put(k,v);return this;}
 public Editor putBoolean(String k,boolean v){values.put(k,v);return this;} public boolean commit(){return true;}}
}''',
    "android/content/Context.java": '''package android.content;
public class Context {public static final int MODE_PRIVATE=0; private static final SharedPreferences prefs=new SharedPreferences();
 public SharedPreferences getSharedPreferences(String n,int mode){return prefs;} public String getPackageName(){return "app.regram.android";}
 public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}}
''',
    "android/content/res/AssetManager.java": '''package android.content.res; public class AssetManager {
 public java.io.InputStream open(String name)throws java.io.IOException{return new java.io.FileInputStream(System.getProperty("assets")+"/"+name);}}
''',
    "android/content/res/Resources.java": '''package android.content.res; public class Resources {
 public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}''',
    "android/util/DisplayMetrics.java": '''package android.util; public class DisplayMetrics {public float density=2;public int widthPixels=1000,heightPixels=2000;}''',
    "android/app/Activity.java": '''package android.app; public class Activity extends android.content.Context {
 public boolean isFinishing(){return false;}public boolean isDestroyed(){return false;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}}''',
    "android/os/SystemClock.java": '''package android.os; public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000;}}''',
    "android/os/Build.java": '''package android.os; public class Build {public static class VERSION{public static int SDK_INT=35;}}''',
    "android/os/Looper.java": '''package android.os;public class Looper {public static Looper myLooper(){return null;} public static Looper getMainLooper(){return new Looper();}}''',
    "android/view/View.java": '''package android.view; public class View {}''',
    "org/telegram/messenger/AndroidUtilities.java": '''package org.telegram.messenger; public class AndroidUtilities {
 public static volatile boolean paused; public static final java.util.Queue<Runnable> pending=new java.util.concurrent.ConcurrentLinkedQueue<>();
 public static void runOnUIThread(Runnable r){if(paused)pending.add(r);else r.run();}}''',
    "org/telegram/messenger/ApplicationLoader.java": '''package org.telegram.messenger; public class ApplicationLoader {
 public static android.content.Context applicationContext=new android.content.Context();}''',
    "org/telegram/messenger/FileLog.java": '''package org.telegram.messenger;public class FileLog {public static void e(String msg){} public static void e(String msg,Throwable t){}}''',
    "org/telegram/messenger/SharedConfig.java": '''package org.telegram.messenger;public class SharedConfig {public static volatile boolean appLocked,isWaitingForPasscodeEnter;}''',
    "org/telegram/ui/ActionBar/BaseFragment.java": '''package org.telegram.ui.ActionBar;public class BaseFragment {}''',
    "org/telegram/ui/LaunchActivity.java": '''package org.telegram.ui;public class LaunchActivity {public static org.telegram.ui.ActionBar.BaseFragment getLastFragment(){return null;}}''',
    "com/chaquo/python/PyObject.java": '''package com.chaquo.python;public class PyObject {public PyObject call(Object... args){return this;}public PyObject callAttr(String n,Object... args){return this;}public <T>T toJava(Class<T> t){return t.cast("{\\"ok\\":true}");}}''',
    "com/chaquo/python/Python.java": '''package com.chaquo.python;public class Python {public static boolean isStarted(){return true;}public static void start(Object p){}public static Python getInstance(){return new Python();}public PyObject getModule(String n){return new PyObject();}}''',
    "com/chaquo/python/android/AndroidPlatform.java": '''package com.chaquo.python.android;public class AndroidPlatform{public AndroidPlatform(Object c){}}''',
    "app/regram/termux/TermuxUi.java": '''package app.regram.termux; public class TermuxUi {
 static int commands;TermuxUi(TermuxBridge b){}void clearAll(){}void selfTest(){}int panelCount(){return 0;}
 void cleanupOwner(String s){}void addJavaView(String s,android.view.View v,org.json.JSONObject m){}
 void removeJavaView(String s,android.view.View v){} org.json.JSONObject command(TermuxBridge.Session s,org.json.JSONObject m){commands++;return m.optString("cmd").equals("show")?TermuxBridge.ok():TermuxBridge.error("unknown cmd");}}
''',
}


def test_actual_java_transport_auth_pairing_lifecycle_and_java_rpc(tmp_path):
    javac, java = shutil.which("javac"), shutil.which("java")
    jars = list((Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json").glob("*/*/*.jar"))
    if not javac or not java or not jars:
        pytest.skip("host JDK and cached org.json test dependency required")
    files = dict(STUBS)
    for name in ("TermuxWire", "TermuxBridge", "TermuxJava"):
        files[f"app/regram/termux/{name}.java"] = (JAVA / f"{name}.java").read_text()
    files["app/regram/termux/BridgeTest.java"] = r'''package app.regram.termux;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import org.json.*;
public class BridgeTest {
 static TermuxBridge bridge=TermuxBridge.getInstance();static String token;
 public interface Click {void onClick(String text);}
 public static class Fixture {public static final int CONSTANT=42;public static Click listener;
  public static int choose(int x){return 1;}public static int choose(float x){return 2;}
  public static int choose(double x){return 3;}public static void install(Click l){listener=l;}
  public static String nullable(Object x){return "object";}public static String nullable(String x){return "string";}
  public static int[] values(){return new int[]{2,4,6};}}
 static class Client implements AutoCloseable {
  Socket socket;BufferedReader reader;PrintWriter writer;String sid;
  Client(int port,String sid)throws Exception {this.sid=sid;socket=new Socket("127.0.0.1",port);socket.setSoTimeout(10000);
   reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));writer=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);}
  JSONObject send(JSONObject m)throws Exception {m.put("token",token).put("sid",sid);writer.println(m);return new JSONObject(reader.readLine());}
  JSONObject cmd(String cmd)throws Exception {return send(new JSONObject().put("cmd",cmd));}
  public void close()throws Exception{socket.close();}
 }
 static JSONObject rpc(String cmd,long h,String name,JSONArray args)throws Exception{return new JSONObject().put("cmd",cmd).put("h",h).put("name",name).put("args",args);}
 static String http(int port,String path)throws Exception {
  try(Socket s=new Socket("127.0.0.1",port)){s.setSoTimeout(3000);s.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.UTF_8));
   return new String(s.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}}
 public static void main(String[] unused)throws Exception {
  assert !TermuxBridge.enabled() && !TermuxBridge.allowExec();
  assert TermuxWire.secret().matches("[0-9a-f]{48}");assert !TermuxWire.matches("", "");assert !TermuxWire.matches("a","b");assert TermuxWire.matches("a","a");
  TermuxWire.Pairing pairing=new TermuxWire.Pairing();String path=pairing.open(100);
  assert !pairing.consume("/i",101);assert pairing.consume(path,101);assert !pairing.consume(path,102);
  path=pairing.open(100);assert !pairing.consume(path,300100);pairing.clear();assert !pairing.consume(path,101);
  assert TermuxWire.readLine(new ByteArrayInputStream("Привет\r\nnext\n".getBytes(StandardCharsets.UTF_8))).equals("Привет");
  try{TermuxWire.readLine(new ByteArrayInputStream("unterminated".getBytes()));throw new AssertionError();}catch(IOException expected){}
  try{TermuxWire.readLine(new ByteArrayInputStream(new byte[TermuxWire.MAX_LINE+1]));throw new AssertionError();}catch(IOException expected){}
  int port;try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
  TermuxBridge.settings().edit().putBoolean("enabled",true).putString("port",String.valueOf(port)).commit();
  android.app.Activity activity=new android.app.Activity();bridge.attach(activity);assert bridge.running();
  token=TermuxBridge.settings().getString("token","");assert token.length()==48;
  try(Client c=new Client(port,"owner")) {
   c.writer.println(new JSONObject().put("cmd","ping").put("sid","owner").put("token","wrong"));
   assert new JSONObject(c.reader.readLine()).getString("error").equals("bad token");assert c.cmd("ping").getBoolean("pong");
   assert !c.cmd("exec").getBoolean("ok");assert !c.send(new JSONObject().put("cmd","j_cls").put("name","java.lang.String")).getBoolean("ok");
   assert !c.cmd("unknown").getBoolean("ok");
   String command=bridge.pairingCommand();String url=command.substring(command.indexOf("http://"),command.indexOf(" | python"));
   String installer=http(port,new URL(url).getPath());assert installer.startsWith("HTTP/1.1 200");assert installer.contains(token);assert !installer.contains("@@TOKEN@@");
   assert http(port,new URL(url).getPath()).startsWith("HTTP/1.1 403");assert http(port,"/i").startsWith("HTTP/1.1 403");
   TermuxBridge.settings().edit().putBoolean("allow_exec",true).commit();
   long cls=c.send(new JSONObject().put("cmd","j_cls").put("name",Fixture.class.getName())).getJSONObject("r").getLong("$h");
   assert c.send(rpc("j_get",cls,"CONSTANT",null)).getInt("r")==42;
   assert c.send(rpc("j_call",cls,"choose",new JSONArray().put(5))).getInt("r")==1;
   assert c.send(rpc("j_call",cls,"choose",new JSONArray().put(new JSONObject().put("$cast","float").put("v",1.5)))).getInt("r")==2;
   assert c.send(rpc("j_call",cls,"choose",new JSONArray().put(new JSONObject().put("$cast","double").put("v",1.5)))).getInt("r")==3;
   assert c.send(rpc("j_call",cls,"nullable",new JSONArray().put(new JSONObject().put("$cast","String").put("v",JSONObject.NULL)))).getString("r").equals("string");
   long array=c.send(rpc("j_call",cls,"values",new JSONArray())).getJSONObject("r").getLong("$h");
   assert c.send(rpc("j_list",array,"",null)).getJSONArray("r").getInt(1)==4;
   long jsonClass=c.send(new JSONObject().put("cmd","j_cls").put("name","org.json.JSONObject")).getJSONObject("r").getLong("$h");
   long jsonObject=c.send(rpc("j_new",jsonClass,"",new JSONArray())).getJSONObject("r").getLong("$h");
   assert c.send(rpc("j_call",jsonObject,"put",new JSONArray().put("answer").put(42))).getJSONObject("r").has("$h");
   assert c.send(rpc("j_call",jsonObject,"getInt",new JSONArray().put("answer"))).getInt("r")==42;
   try(Client foreign=new Client(port,"foreign")){assert !foreign.send(rpc("j_get",cls,"CONSTANT",null)).getBoolean("ok");}
   try(Client events=new Client(port,"owner")) {
    assert events.cmd("subscribe").getBoolean("ok");
    JSONObject handle=c.send(new JSONObject().put("cmd","j_listener").put("iface",Click.class.getName()).put("methods",new JSONArray().put("onClick"))).getJSONObject("r");
    assert c.send(rpc("j_call",cls,"install",new JSONArray().put(handle))).getBoolean("ok");
    Fixture.listener.onClick("hello");JSONObject event=new JSONObject(events.reader.readLine());
    assert event.getString("event").equals("jcall") && event.getJSONArray("args").getString(0).equals("hello");
   }
   Thread.sleep(150);assert !c.send(rpc("j_get",cls,"CONSTANT",null)).getBoolean("ok");
   org.telegram.messenger.AndroidUtilities.paused=true;int count=TermuxUi.commands;
   assert !c.cmd("show").getBoolean("ok"); // pending UI task timed out and must never run later
   org.telegram.messenger.AndroidUtilities.paused=false;Runnable task;
   while((task=org.telegram.messenger.AndroidUtilities.pending.poll())!=null)task.run();assert count==TermuxUi.commands;
   org.telegram.messenger.SharedConfig.appLocked=true;assert !c.cmd("ping").getBoolean("ok");
   bridge.locked();org.telegram.messenger.SharedConfig.appLocked=false;
  }
  String old=token;bridge.rotateToken();token=TermuxBridge.settings().getString("token","");assert !old.equals(token);
  try(Client c=new Client(port,"new")){assert c.cmd("ping").getBoolean("ok");}
  bridge.detach(new android.app.Activity());assert bridge.running();bridge.detach(activity);assert !bridge.running();
  try{new Socket("127.0.0.1",port);throw new AssertionError();}catch(ConnectException expected){}
  TermuxBridge.settings().edit().putString("port","1").commit();bridge.attach(activity);assert !bridge.running();
  bridge.stop();
 }
}'''
    paths = []
    for relative, text in files.items():
        path = tmp_path / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        paths.append(str(path))
    classpath = str(jars[0])
    subprocess.run([javac, "-encoding", "UTF-8", "-cp", classpath, "-d", str(tmp_path), *paths], check=True, capture_output=True, text=True)
    run = subprocess.run([java, "-ea", f"-Dassets={ASSETS.parent}", "-cp", str(tmp_path) + os.pathsep + classpath, "app.regram.termux.BridgeTest"], capture_output=True, text=True, timeout=40)
    assert run.returncode == 0, run.stdout + run.stderr

"""Run Vosk file/error paths with a fake HTTP transport, not Android or network."""
from pathlib import Path
import os
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def test_vosk_failed_download_and_listener_lifecycle(tmp_path):
    javac = shutil.which('javac')
    candidates = [Path(os.environ.get('JAVA_HOME', '/nonexistent')) / 'bin/javac']
    candidates += list((Path.home() / '.gradle/jdks').glob('*/bin/javac'))
    javac = javac or next((str(p) for p in candidates if p.is_file()), None)
    if not javac:
        pytest.skip('JDK required')
    stubs = {
        'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences { String getString(String k,String v); Editor edit();
 interface Editor { Editor putString(String k,String v); void apply(); } }''',
        'android/text/TextUtils.java': '''package android.text;
public class TextUtils { public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }''',
        'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;
public class AndroidUtilities { public static void runOnUIThread(Runnable r){r.run();}
 public static void runOnUIThread(Runnable r,long delay){} }''',
        'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public class ApplicationLoader { public static java.io.File root;
 public static java.io.File getFilesDirFixed(String s){return root;} }''',
        'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;
public class FileLog { public static void e(Throwable t){} }''',
        'org/telegram/messenger/Utilities.java': '''package org.telegram.messenger;
public class Utilities { public interface Callback<T>{void run(T value);} }''',
        'tw/nekomimi/nekogram/NekoConfig.java': '''package tw.nekomimi.nekogram;
public class NekoConfig {public static android.content.SharedPreferences getPreferences(){return null;} }''',
        'tw/nekomimi/nekogram/utils/HttpClient.java': '''package tw.nekomimi.nekogram.utils;
public class HttpClient { public static HttpClient INSTANCE=new HttpClient();
 public okhttp3.OkHttpClient getInstance(){return new okhttp3.OkHttpClient();} }''',
        'app/regram/speech/VoskModels.java': '''package app.regram.speech;
public class VoskModels { public static VoskModel forCurrentLocale(String a,String b){return null;}
 public static VoskModel[] all(){return new VoskModel[0];} }''',
        'app/regram/speech/VoskTranscriber.java': '''package app.regram.speech;
public class VoskTranscriber { public static void releaseModel(){} }''',
        'okhttp3/Request.java': '''package okhttp3;
public class Request {public static class Builder {public Builder url(String u){return this;}
 public Request build(){return new Request();}} }''',
        'okhttp3/OkHttpClient.java': '''package okhttp3;
public class OkHttpClient { public static byte[] data; public static Call last;
 public Builder newBuilder(){return new Builder();} public Call newCall(Request r){return last=new Call();}
 public static class Builder {public Builder callTimeout(long n,java.util.concurrent.TimeUnit u){return this;}
 public Builder readTimeout(long n,java.util.concurrent.TimeUnit u){return this;}
 public OkHttpClient build(){return new OkHttpClient();}} }''',
        'okhttp3/Call.java': '''package okhttp3;
public class Call { public boolean canceled; public void cancel(){canceled=true;}
 public Response execute() throws java.io.IOException {
 if(canceled)throw new java.io.IOException("cancelled"); return new Response();} }''',
        'okhttp3/Response.java': '''package okhttp3;
public class Response implements AutoCloseable { public boolean isSuccessful(){return true;}
 public int code(){return 200;} public ResponseBody body(){return new ResponseBody();} public void close(){} }''',
        'okhttp3/ResponseBody.java': '''package okhttp3;
public class ResponseBody {public long contentLength(){return OkHttpClient.data.length;}
 public java.io.InputStream byteStream(){return new java.io.ByteArrayInputStream(OkHttpClient.data);} }''',
        'app/regram/speech/VoskCheck.java': '''package app.regram.speech;
import java.io.*; import java.lang.reflect.*; import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*; import org.telegram.messenger.ApplicationLoader;
import app.regram.utils.DownloadProgress; import okhttp3.OkHttpClient;
public class VoskCheck {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static byte[] archive(String path) throws Exception {
 ByteArrayOutputStream bytes=new ByteArrayOutputStream();
 try(ZipOutputStream zip=new ZipOutputStream(bytes)){
 if(path!=null){zip.putNextEntry(new ZipEntry(path));zip.write(new byte[]{1,2,3});zip.closeEntry();}}
 return bytes.toByteArray(); }
 static Throwable download(String path,boolean canceled,String md5) throws Exception {
 OkHttpClient.data=archive(path);
 Method method=VoskManager.class.getDeclaredMethod("downloadInternal",VoskModel.class,
 AtomicBoolean.class,DownloadProgress.class); method.setAccessible(true);
 try{method.invoke(null,new VoskModel("test","en","Test","https://example.com",100,md5),
 new AtomicBoolean(canceled),new DownloadProgress(Runnable::run,(a,b)->{}));return null;}
 catch(InvocationTargetException e){return e.getCause();} }
 static void clean(){check(!new File(ApplicationLoader.root,"test.zip").exists());
 check(!new File(ApplicationLoader.root,"test.tmp").exists());}
 public static void main(String[] args) throws Exception {
 ApplicationLoader.root=new File(args[0]); ApplicationLoader.root.mkdirs();
 check(download(null,false,"") instanceof IOException); clean(); check(!VoskManager.isInstalled("test"));
 check(download("model/../../escape",false,"") instanceof IOException); clean();
 check(!new File(ApplicationLoader.root.getParentFile(),"escape").exists());
 check(download("model/am/final.mdl",true,"") instanceof InterruptedException); clean();
 check(OkHttpClient.last.canceled);
 check(download("model/am/final.mdl",false,"wrong") instanceof IOException); clean();
 check(download("model/am/final.mdl",false,"")==null); clean(); check(VoskManager.isInstalled("test"));
 // A failed replacement must not remove the previous ready model.
 check(download(null,false,"") instanceof IOException); clean(); check(VoskManager.isInstalled("test"));
 VoskManager.cancelDownload(); check(OkHttpClient.last.canceled);
 int[] notified={0};
 VoskManager.DownloadListener bad=new VoskManager.DownloadListener(){
 public void onProgress(long a,long b){} public void onFinished(VoskModel m,Throwable e){
 VoskManager.removeListener(this); throw new IllegalStateException();}};
 VoskManager.DownloadListener good=new VoskManager.DownloadListener(){
 public void onProgress(long a,long b){} public void onFinished(VoskModel m,Throwable e){notified[0]++;}};
 VoskManager.addListener(bad);VoskManager.addListener(good);
 Method notify=VoskManager.class.getDeclaredMethod("notifyFinished",VoskModel.class,Throwable.class);
 notify.setAccessible(true);notify.invoke(null,null,null);check(notified[0]==1);
 VoskManager.removeListener(good);
 } }''',
    }
    files = []
    for name, content in stubs.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        files.append(str(path))
    subprocess.run([javac, '-d', str(tmp_path), *files,
                    str(JAVA / 'app/regram/speech/VoskManager.java'),
                    str(JAVA / 'app/regram/speech/VoskModel.java'),
                    str(JAVA / 'app/regram/utils/DownloadProgress.java')],
                   check=True, capture_output=True, text=True)
    subprocess.run([str(Path(javac).resolve().with_name('java')), '-cp', str(tmp_path),
                    'app.regram.speech.VoskCheck', str(tmp_path / 'models')],
                   check=True, capture_output=True, text=True, timeout=15)

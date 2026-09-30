package app.regram.ai.network;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import app.regram.ai.data.ModelInfo;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ModelsCatalog {

    private static final String CATALOG_URL = "https://models.dev/api.json";

    private static volatile Map<String, ModelInfo> catalog;
    private static final AtomicBoolean loading = new AtomicBoolean();
    private static OkHttpClient httpClient;

    private ModelsCatalog() {
    }

    public static ModelInfo get(String modelId) {
        Map<String, ModelInfo> loaded = catalog;
        if (loaded == null || TextUtils.isEmpty(modelId)) {
            return null;
        }
        String key = normalize(modelId);
        ModelInfo info = loaded.get(key);
        if (info != null) {
            return info;
        }
        int slash = key.lastIndexOf('/');
        return slash < 0 ? null : loaded.get(key.substring(slash + 1));
    }

    public static boolean supportsReasoning(String modelId) {
        ModelInfo info = get(modelId);
        return info != null && info.isReasoning();
    }

    public static void load(Utilities.Callback<Boolean> callback) {
        if (catalog != null) {
            if (callback != null) {
                callback.run(true);
            }
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            if (callback != null) {
                callback.run(false);
            }
            return;
        }
        client().newCall(new Request.Builder().url(CATALOG_URL).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        loading.set(false);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (callback != null) {
                                callback.run(false);
                            }
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        boolean ok = false;
                        try (Response closeable = response) {
                            ResponseBody body = closeable.body();
                            if (closeable.isSuccessful() && body != null) {
                                catalog = parse(new JSONObject(body.string()));
                                ok = true;
                            }
                        } catch (Exception e) {
                            FileLog.e("ModelsCatalog", e);
                        }
                        loading.set(false);
                        final boolean result = ok;
                        AndroidUtilities.runOnUIThread(() -> {
                            if (callback != null) {
                                callback.run(result);
                            }
                        });
                    }
                });
    }

    private static Map<String, ModelInfo> parse(JSONObject root) {
        HashMap<String, ModelInfo> parsed = new HashMap<>();
        for (Iterator<String> providers = root.keys(); providers.hasNext(); ) {
            JSONObject provider = root.optJSONObject(providers.next());
            JSONObject models = provider == null ? null : provider.optJSONObject("models");
            if (models == null) {
                continue;
            }
            for (Iterator<String> ids = models.keys(); ids.hasNext(); ) {
                String id = ids.next();
                JSONObject model = models.optJSONObject(id);
                if (model == null) {
                    continue;
                }
                String name = model.isNull("name") ? id : model.optString("name", id);
                boolean reasoning = model.optBoolean("reasoning", false);
                JSONObject limit = model.optJSONObject("limit");
                long context = limit == null ? 0 : limit.optLong("context", 0);
                parsed.put(normalize(id), new ModelInfo(id, name, reasoning, context));
            }
        }
        return parsed;
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase();
    }

    private static OkHttpClient client() {
        if (httpClient == null) {
            synchronized (ModelsCatalog.class) {
                if (httpClient == null) {
                    httpClient = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return httpClient;
    }
}

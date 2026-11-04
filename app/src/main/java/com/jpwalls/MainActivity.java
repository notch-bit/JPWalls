package com.jpwalls;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.*;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> pending;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if ("/index.html".equals(r.getUrl().getPath())) {
                    try { return new WebResourceResponse("text/html", "utf-8", getAssets().open("index.html")); }
                    catch (IOException e) { /* fall through */ }
                }
                return null;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                try { startActivityForResult(p.createIntent(), 1); return true; }
                catch (Exception e) { pending = null; return false; }
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("https://jpwalls.local/index.html");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && pending != null) {
            pending.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            pending = null;
        }
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    private Bitmap bmp(String d) {
        byte[] b = Base64.decode(d.substring(d.indexOf(',') + 1), Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(b, 0, b.length);
    }

    class Bridge {
        @JavascriptInterface public void saveImage(String d) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, "jpwalls_" + System.currentTimeMillis() + ".jpg");
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JPWalls");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    bmp(d).compress(Bitmap.CompressFormat.JPEG, 95, o);
                }
                toast("Saved to Pictures/JPWalls");
            } catch (Exception e) { toast("Save failed"); }
        }

        // flag: 1 = home screen, 2 = lock screen
        @JavascriptInterface public void setWallpaper(String d, int flag) {
            try {
                WallpaperManager.getInstance(MainActivity.this).setBitmap(bmp(d), null, true, flag);
                toast("Wallpaper set");
            } catch (Exception e) { toast("Could not set wallpaper"); }
        }
    }
}

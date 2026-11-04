package com.jpwalls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.text.format.DateFormat;
import android.view.SurfaceHolder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import org.json.JSONObject;

/** Live wallpaper: background layer + live clock + foreground cut-out, redrawn every minute. */
public class LiveWallpaperService extends WallpaperService {
    static final String CFG = "live_cfg.json", BG = "live_bg.jpg", FG = "live_fg.png";

    @Override public Engine onCreateEngine() { return new E(); }

    class E extends Engine {
        private Bitmap bg, fg;
        private ClockRenderer clock;
        private long stamp = -1;
        private boolean visible;
        private int w = 1, h = 1;

        private final BroadcastReceiver tick = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { draw(); }
        };

        @Override public void onCreate(SurfaceHolder sh) {
            super.onCreate(sh);
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(tick, f);
        }

        @Override public void onDestroy() {
            try { unregisterReceiver(tick); } catch (Exception ignored) {}
            super.onDestroy();
        }

        @Override public void onVisibilityChanged(boolean v) { visible = v; if (v) draw(); }

        @Override public void onSurfaceChanged(SurfaceHolder sh, int fmt, int width, int height) {
            super.onSurfaceChanged(sh, fmt, width, height);
            w = width; h = height; draw();
        }

        private byte[] read(File f) throws Exception {
            try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
                return o.toByteArray();
            }
        }

        private void reloadIfChanged() {
            File cfg = new File(getFilesDir(), CFG);
            if (!cfg.exists()) return;
            long s = cfg.lastModified();
            if (s == stamp) return;
            try {
                clock = new ClockRenderer(new JSONObject(new String(read(cfg), "UTF-8")));
                bg = BitmapFactory.decodeFile(new File(getFilesDir(), BG).getAbsolutePath());
                File ff = new File(getFilesDir(), FG);
                fg = ff.exists() ? BitmapFactory.decodeFile(ff.getAbsolutePath()) : null;
                stamp = s;
            } catch (Exception ignored) {}
        }

        private void draw() {
            if (!visible) return;
            SurfaceHolder sh = getSurfaceHolder();
            Canvas c = null;
            try {
                c = sh.lockCanvas();
                if (c == null) return;
                reloadIfChanged();
                c.drawColor(Color.BLACK);
                if (clock == null || bg == null) return;
                float s = Math.max(w / (float) ClockRenderer.W, h / (float) ClockRenderer.H);
                c.save();
                c.translate((w - ClockRenderer.W * s) / 2f, (h - ClockRenderer.H * s) / 2f);
                c.scale(s, s);
                Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                c.drawBitmap(bg, null, new android.graphics.Rect(0, 0, ClockRenderer.W, ClockRenderer.H), bp);
                clock.draw(c, DateFormat.is24HourFormat(LiveWallpaperService.this));
                if (fg != null) c.drawBitmap(fg, null, new android.graphics.Rect(0, 0, ClockRenderer.W, ClockRenderer.H), bp);
                c.restore();
            } catch (Exception ignored) {
            } finally {
                if (c != null) try { sh.unlockCanvasAndPost(c); } catch (Exception ignored) {}
            }
        }
    }
}

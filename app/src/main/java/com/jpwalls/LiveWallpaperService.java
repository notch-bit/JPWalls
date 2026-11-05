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
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.text.format.DateFormat;
import android.view.SurfaceHolder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import org.json.JSONObject;

/**
 * Live wallpaper: background layer + live clock + foreground cut-out.
 * The layers are pre-scaled to the screen once and the clock is cached as a bitmap, so the
 * depth effect (tilt + home-screen scroll) only has to blit three bitmaps per frame.
 */
public class LiveWallpaperService extends WallpaperService {
    static final String CFG = "live_cfg.json", BG = "live_bg.jpg", FG = "live_fg.png";

    @Override public Engine onCreateEngine() { return new E(); }

    class E extends Engine implements SensorEventListener {
        private Bitmap bgS, fgS, clkS;
        private ClockRenderer clock;
        private Typeface font;
        private long stamp = -1;
        private boolean visible, sensing, animating, clockDirty = true;
        private int w = 1, h = 1, builtW = 0, builtH = 0;
        private float sc = 1, amp = 0;
        private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);

        // depth motion: base = slowly-moving "rest" pose, tilt/scroll in -1..1, cur eases towards tgt
        private boolean haveBase;
        private float baseX, baseY, tiltX, tiltY, scrollX, tgtX, tgtY, curX, curY;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable ticker = new Runnable() {
            @Override public void run() { clockDirty = true; draw(); schedule(); }
        };
        private final Runnable frame = new Runnable() {
            @Override public void run() {
                curX += (tgtX - curX) * .2f;
                curY += (tgtY - curY) * .2f;
                if (visible) drawFrame();
                if (Math.abs(tgtX - curX) < .002f && Math.abs(tgtY - curY) < .002f) {
                    curX = tgtX; curY = tgtY; animating = false;
                } else {
                    handler.postDelayed(this, 33);
                }
            }
        };

        private void schedule() {
            handler.removeCallbacks(ticker);
            if (visible) handler.postDelayed(ticker, 60000 - (System.currentTimeMillis() % 60000) + 100);
        }

        private final BroadcastReceiver tick = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { clockDirty = true; draw(); }
        };

        @Override public void onCreate(SurfaceHolder sh) {
            super.onCreate(sh);
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(tick, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(tick, f);
        }

        @Override public void onDestroy() {
            handler.removeCallbacks(ticker);
            handler.removeCallbacks(frame);
            stopSensor();
            try { unregisterReceiver(tick); } catch (Exception ignored) {}
            super.onDestroy();
        }

        @Override public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                haveBase = false;
                clockDirty = true;
                draw();
                schedule();
                updateSensor();
            } else {
                handler.removeCallbacks(ticker);
                handler.removeCallbacks(frame);
                animating = false;
                stopSensor();
            }
        }

        @Override public void onSurfaceChanged(SurfaceHolder sh, int fmt, int width, int height) {
            super.onSurfaceChanged(sh, fmt, width, height);
            w = width; h = height; draw();
        }

        /* ---------- depth motion ---------- */

        private void updateSensor() {
            boolean want = visible && clock != null && clock.par > 0;
            if (want) startSensor(); else stopSensor();
        }

        private void startSensor() {
            if (sensing) return;
            SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor a = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (a == null) return;
            haveBase = false;
            sensing = sm.registerListener(this, a, SensorManager.SENSOR_DELAY_UI);
        }

        private void stopSensor() {
            if (!sensing) return;
            SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) sm.unregisterListener(this);
            sensing = false;
        }

        private float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

        @Override public void onSensorChanged(SensorEvent e) {
            float gx = e.values[0] / 9.81f, gy = e.values[1] / 9.81f;
            if (!haveBase) { baseX = gx; baseY = gy; haveBase = true; }
            // the rest pose follows the phone slowly, so the effect is relative to how you hold it
            baseX += (gx - baseX) * .01f;
            baseY += (gy - baseY) * .01f;
            tiltX = clamp(-(gx - baseX) * 2.5f);
            tiltY = clamp((gy - baseY) * 2.5f);
            retarget();
        }

        @Override public void onAccuracyChanged(Sensor s, int accuracy) {}

        @Override public void onOffsetsChanged(float xOff, float yOff, float xStep, float yStep, int xPix, int yPix) {
            if (clock == null || clock.par <= 0) return;
            scrollX = xStep > 0 ? clamp((xOff - .5f) * 2f) : 0f;
            retarget();
        }

        private void retarget() {
            tgtX = clamp(tiltX + scrollX * .8f);
            tgtY = tiltY;
            if (!animating && visible && (Math.abs(tgtX - curX) > .004f || Math.abs(tgtY - curY) > .004f)) {
                animating = true;
                handler.post(frame);
            }
        }

        /* ---------- loading / drawing ---------- */

        private byte[] read(File f) throws Exception {
            try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
                return o.toByteArray();
            }
        }

        private Bitmap scaled(Bitmap src, int tw, int th) {
            if (src == null) return null;
            Bitmap r = Bitmap.createScaledBitmap(src, tw, th, true);
            if (r != src) src.recycle();
            return r;
        }

        /** (Re)builds the screen-sized layers when the saved settings or the surface size change. */
        private void rebuild() {
            File cfg = new File(getFilesDir(), CFG);
            if (!cfg.exists() || w < 2 || h < 2) return;
            long s = cfg.lastModified();
            if (s == stamp && w == builtW && h == builtH) return;
            try {
                if (font == null) {
                    try { font = Typeface.createFromAsset(getAssets(), "fonts/league_gothic_narrow.ttf"); } catch (Exception ignored) {}
                }
                ClockRenderer cr = new ClockRenderer(new JSONObject(new String(read(cfg), "UTF-8")), font);
                Bitmap bg = BitmapFactory.decodeFile(new File(getFilesDir(), BG).getAbsolutePath());
                File ff = new File(getFilesDir(), FG);
                Bitmap fg = ff.exists() ? BitmapFactory.decodeFile(ff.getAbsolutePath()) : null;
                float sc2 = Math.max(w / (float) ClockRenderer.W, h / (float) ClockRenderer.H);
                float amp2 = cr.par / 100f * 60f;               // max layer shift in design pixels
                float k = 1f + 2f * amp2 / ClockRenderer.W;     // enlarge layers so edges never show
                int tw = Math.round(ClockRenderer.W * sc2 * k), th = Math.round(ClockRenderer.H * sc2 * k);
                bgS = scaled(bg, tw, th);
                fgS = scaled(fg, tw, th);
                clkS = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                clock = cr; sc = sc2; amp = amp2;
                clockDirty = true; stamp = s; builtW = w; builtH = h;
                updateSensor();
            } catch (Exception | OutOfMemoryError ignored) {}
        }

        private void renderClock() {
            if (clkS == null || clock == null) return;
            clkS.eraseColor(Color.TRANSPARENT);
            Canvas cc = new Canvas(clkS);
            cc.translate((w - ClockRenderer.W * sc) / 2f, (h - ClockRenderer.H * sc) / 2f);
            cc.scale(sc, sc);
            clock.draw(cc, DateFormat.is24HourFormat(LiveWallpaperService.this));
            clockDirty = false;
        }

        private void draw() {
            if (!visible) return;
            rebuild();
            if (clockDirty) renderClock();
            drawFrame();
        }

        private void drawFrame() {
            SurfaceHolder sh = getSurfaceHolder();
            Canvas c = null;
            try {
                c = Build.VERSION.SDK_INT >= 26 ? sh.lockHardwareCanvas() : sh.lockCanvas();
                if (c == null) return;
                c.drawColor(Color.BLACK);
                if (clock == null || bgS == null || clkS == null) return;
                float mx = curX * amp * sc, my = curY * amp * sc;
                // background and subject move in opposite directions, the clock sits in between
                c.drawBitmap(bgS, (w - bgS.getWidth()) / 2f - mx, (h - bgS.getHeight()) / 2f - my, blit);
                if (!clock.front) c.drawBitmap(clkS, 0, 0, blit);
                if (fgS != null) c.drawBitmap(fgS, (w - fgS.getWidth()) / 2f + mx, (h - fgS.getHeight()) / 2f + my, blit);
                if (clock.front) c.drawBitmap(clkS, 0, 0, blit);
            } catch (Exception ignored) {
            } finally {
                if (c != null) try { sh.unlockCanvasAndPost(c); } catch (Exception ignored) {}
            }
        }
    }
}

package com.jpwalls;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/** Java port of the clock drawing in index.html. Works in a 1080x2400 design space. */
class ClockRenderer {
    static final int W = 1080, H = 2400;

    static class Pen { int rgb; float a; float sigma; float dy; boolean flat; }

    static class Opt {
        String time, date, hh, mm;
        int h, m;
        float sz, y;
        int wt, col, col2;
    }

    private final String style;
    private final float size, ypos, op, atmo;
    private final int wt, col, col2;
    private final boolean atmoOn;

    ClockRenderer(JSONObject j) {
        style = j.optString("style", "ios");
        size = (float) j.optDouble("size", 440);
        ypos = (float) j.optDouble("ypos", 26);
        wt = j.optInt("wt", 600);
        op = (float) j.optDouble("op", 0.95);
        atmo = (float) j.optDouble("atmo", 60) / 100f;
        atmoOn = j.optBoolean("atmoOn", true);
        col = parse(j.optString("col", "#ffffff"));
        col2 = parse(j.optString("col2", "#ff7ad9"));
    }

    private static int parse(String s) {
        try { return Color.parseColor(s) & 0xFFFFFF; } catch (Exception e) { return 0xFFFFFF; }
    }

    /** Draws the live clock (current time and date) for the given moment. */
    void draw(Canvas c, boolean is24h) {
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        Opt o = new Opt();
        o.h = cal.get(Calendar.HOUR_OF_DAY);
        o.m = cal.get(Calendar.MINUTE);
        int hr = is24h ? o.h : (o.h % 12 == 0 ? 12 : o.h % 12);
        o.hh = String.format(Locale.US, "%02d", hr);
        o.mm = String.format(Locale.US, "%02d", o.m);
        o.time = is24h ? o.hh + ":" + o.mm : hr + ":" + o.mm;
        o.date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        o.sz = size;
        o.y = H * ypos / 100f;
        o.wt = wt;
        o.col = col;
        o.col2 = col2;

        if (!atmoOn) { pass(c, o, pen(col, op, 0, 0, false)); return; }
        float k = atmo;
        // soft glow
        pass(c, o, pen(col, op * (0.55f * k + 0.15f), 10 + 50 * k, 0, true));
        // shadow + the clock itself
        pass(c, o, pen(0x000000, op * 0.4f * k, 25 * k, 24 * k, true));
        pass(c, o, pen(col, op, 0, 0, false));
        // haze
        Paint hz = new Paint();
        int a = Math.round(255 * 0.22f * k);
        hz.setShader(new LinearGradient(0, o.y - o.sz * .2f, 0, o.y + o.sz * 1.2f,
                new int[]{0x00FFFFFF, (a << 24) | 0xFFFFFF, 0x00FFFFFF}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, o.y - o.sz * .2f, W, o.y + o.sz * 1.2f, hz);
    }

    private static Pen pen(int rgb, float a, float sigma, float dy, boolean flat) {
        Pen p = new Pen(); p.rgb = rgb; p.a = a; p.sigma = sigma; p.dy = dy; p.flat = flat; return p;
    }

    private void pass(Canvas c, Opt o, Pen pen) {
        c.save();
        c.translate(0, pen.dy);
        drawStyle(c, o, pen);
        c.restore();
    }

    private Paint P(Pen pen, Paint.Style st, float sz, int weight, Typeface base, boolean it,
                    Paint.Align al, float am, boolean white) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setStyle(st);
        p.setTextSize(sz);
        p.setTypeface(Typeface.create(base, Math.max(1, Math.min(1000, weight)), it));
        p.setTextAlign(al);
        int rgb = (white && !pen.flat) ? 0xFFFFFF : pen.rgb;
        p.setColor((Math.round(255 * Math.min(1f, pen.a * am)) << 24) | (rgb & 0xFFFFFF));
        if (pen.sigma > 0) p.setMaskFilter(new BlurMaskFilter(Math.max(1f, (pen.sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL));
        return p;
    }

    private Paint F(Pen pen, float sz, int weight, Typeface base, boolean it, Paint.Align al) {
        return P(pen, Paint.Style.FILL, sz, weight, base, it, al, 1f, false);
    }

    private void drawStyle(Canvas c, Opt o, Pen pen) {
        final Typeface SANS = Typeface.DEFAULT, SERIF = Typeface.SERIF, MONO = Typeface.MONOSPACE;
        final Paint.Align C = Paint.Align.CENTER, L = Paint.Align.LEFT;
        final float cx = W / 2f, sz = o.sz, y = o.y;
        switch (style) {
            case "oneui": {
                float s = sz * .9f;
                c.drawText(o.date, cx, y - s * .75f, F(pen, 58, 400, SANS, false, C));
                Paint p = F(pen, s, 300, SANS, false, C);
                c.drawText(o.hh, cx, y + s * .12f, p);
                c.drawText(o.mm, cx, y + s * 1.08f, p);
                break;
            }
            case "stack": {
                c.drawText(o.date.toUpperCase(), cx, y - sz * .78f, F(pen, 54, 600, SANS, false, C));
                Paint p = F(pen, sz, 900, SANS, false, C);
                c.drawText(o.hh, cx, y + sz * .02f, p);
                c.drawText(o.mm, cx, y + sz * .84f, p);
                break;
            }
            case "outline": {
                Paint p = P(pen, Paint.Style.STROKE, sz, 800, SANS, false, C, 1f, false);
                p.setStrokeWidth(7); p.setStrokeJoin(Paint.Join.ROUND);
                c.drawText(o.time, cx, y + sz * .28f, p);
                c.drawText(o.date, cx, y - sz * .62f, F(pen, 64, 500, SANS, false, C));
                break;
            }
            case "glass": {
                c.drawText(o.time, cx, y + sz * .28f, P(pen, Paint.Style.FILL, sz, 800, SANS, false, C, .22f, true));
                Paint p = P(pen, Paint.Style.STROKE, sz, 800, SANS, false, C, 1f, false);
                p.setStrokeWidth(4);
                c.drawText(o.time, cx, y + sz * .28f, p);
                c.drawText(o.date, cx, y - sz * .62f, F(pen, 64, 500, SANS, false, C));
                break;
            }
            case "neon": {
                int glow = (Math.round(255 * Math.min(1f, pen.a)) << 24) | (o.col2 & 0xFFFFFF);
                Paint p1 = F(pen, sz, o.wt, SANS, false, C);
                Paint p2 = F(pen, sz, o.wt, SANS, false, C);
                if (!pen.flat) { p1.setShadowLayer(60, 0, 0, glow); p2.setShadowLayer(20, 0, 0, glow); }
                c.drawText(o.time, cx, y + sz * .28f, p1);
                c.drawText(o.time, cx, y + sz * .28f, p2);
                c.drawText(o.date, cx, y - sz * .62f, F(pen, 64, 500, SANS, false, C));
                break;
            }
            case "serif": {
                c.drawText(o.date, cx, y - sz * .62f, F(pen, 70, 400, SERIF, true, C));
                c.drawText(o.time, cx, y + sz * .28f, F(pen, sz, 400, SERIF, false, C));
                break;
            }
            case "retro": {
                float s = sz * .82f;
                c.drawText(o.date, cx, y - s * .72f, F(pen, 56, 500, MONO, false, C));
                c.drawText(o.time, cx, y + s * .28f, F(pen, s, 700, MONO, false, C));
                break;
            }
            case "grad": {
                Paint d = F(pen, 64, 500, SANS, false, C);
                Paint t = F(pen, sz, 800, SANS, false, C);
                if (!pen.flat) {
                    int a = Math.round(255 * Math.min(1f, pen.a));
                    Shader g = new LinearGradient(0, y - sz * .6f, 0, y + sz * .4f,
                            (a << 24) | (o.col & 0xFFFFFF), (a << 24) | (o.col2 & 0xFFFFFF), Shader.TileMode.CLAMP);
                    d.setShader(g); t.setShader(g);
                }
                c.drawText(o.date, cx, y - sz * .62f, d);
                c.drawText(o.time, cx, y + sz * .28f, t);
                break;
            }
            case "left": {
                float s = sz * .8f;
                c.drawText(o.time, 90, y + s * .28f, F(pen, s, 200, SANS, false, L));
                c.drawRect(96, y + s * .28f + 30, 96 + 120, y + s * .28f + 38, F(pen, 10, 400, SANS, false, L));
                c.drawText(o.date, 96, y + s * .28f + 120, F(pen, 60, 500, SANS, false, L));
                break;
            }
            case "analog": {
                float r = sz * .62f;
                Paint s = P(pen, Paint.Style.STROKE, 10, 400, SANS, false, C, 1f, false);
                s.setStrokeCap(Paint.Cap.ROUND);
                s.setStrokeWidth(8);
                c.drawCircle(cx, y, r, s);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    float d = (i % 3 != 0) ? .92f : .85f;
                    s.setStrokeWidth((i % 3 != 0) ? 5 : 10);
                    c.drawLine(cx + (float) Math.sin(a) * r * d, y - (float) Math.cos(a) * r * d,
                            cx + (float) Math.sin(a) * r * .96f, y - (float) Math.cos(a) * r * .96f, s);
                }
                double ha = ((o.h % 12) + o.m / 60.0) * Math.PI / 6, ma = o.m * Math.PI / 30;
                s.setStrokeWidth(16);
                c.drawLine(cx, y, cx + (float) Math.sin(ha) * r * .5f, y - (float) Math.cos(ha) * r * .5f, s);
                s.setStrokeWidth(10);
                c.drawLine(cx, y, cx + (float) Math.sin(ma) * r * .78f, y - (float) Math.cos(ma) * r * .78f, s);
                c.drawCircle(cx, y, 16, F(pen, 10, 400, SANS, false, C));
                c.drawText(o.date, cx, y + r + 110, F(pen, 64, 500, SANS, false, C));
                break;
            }
            default: { // ios
                c.drawText(o.date, cx, y - sz * .62f, F(pen, 64, 500, SANS, false, C));
                c.drawText(o.time, cx, y + sz * .28f, F(pen, sz, o.wt, SANS, false, C));
            }
        }
    }
}

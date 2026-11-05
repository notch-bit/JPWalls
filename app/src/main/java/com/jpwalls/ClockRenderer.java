package com.jpwalls;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
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
        String time, date, dshort, hh, mm;
        int h, m, hr;
        float sz, y;
        int wt, col, col2;
    }

    private final String style;
    private final float size, ypos, op, atmo, xpos, rot;
    /** true = clock is drawn in front of the subject cut-out. */
    final boolean front;
    /** depth-motion strength 0..100 (0 = off). */
    final float par;
    private final Typeface sansFace;
    private final int wt, col, col2;
    private final boolean atmoOn;

    private final Typeface lg;

    ClockRenderer(JSONObject j, Typeface font) {
        lg = font != null ? font : Typeface.create("sans-serif-condensed", Typeface.BOLD);
        style = j.optString("style", "ios");
        xpos = (float) j.optDouble("xpos", 0);
        rot = (float) j.optDouble("rot", 0);
        front = j.optBoolean("front", false);
        par = (float) j.optDouble("par", 0);
        String fam = j.optString("font", "sans");
        sansFace = "serif".equals(fam) ? Typeface.SERIF
                : "mono".equals(fam) ? Typeface.MONOSPACE
                : "cond".equals(fam) ? lg : Typeface.DEFAULT;
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
        o.hr = hr;
        o.hh = String.format(Locale.US, "%02d", hr);
        o.mm = String.format(Locale.US, "%02d", o.m);
        o.time = is24h ? o.hh + ":" + o.mm : hr + ":" + o.mm;
        o.date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        o.dshort = new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now).toUpperCase();
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
        c.translate(W / 2f + xpos, o.y);
        c.rotate(rot);
        c.translate(-W / 2f, -o.y);
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

    private static final String[][] DG = {
        {"01110","10001","10011","10101","11001","10001","01110"},
        {"00100","01100","00100","00100","00100","00100","01110"},
        {"01110","10001","00001","00010","00100","01000","11111"},
        {"11110","00001","00001","01110","00001","00001","11110"},
        {"00010","00110","01010","10010","11111","00010","00010"},
        {"11111","10000","11110","00001","00001","10001","01110"},
        {"00110","01000","10000","11110","10001","10001","01110"},
        {"11111","00001","00010","00100","01000","01000","01000"},
        {"01110","10001","10001","01110","10001","10001","01110"},
        {"01110","10001","10001","01111","00001","00010","01100"}};

    private static float tw(String s) {
        float w = -1;
        for (char ch : s.toCharArray()) w += (ch == ':') ? 3 : 6;
        return w;
    }

    private void dots(Canvas c, Paint p, String s, float cx, float top, float cell) {
        float x = cx - tw(s) * cell / 2f;
        for (char ch : s.toCharArray()) {
            if (ch == ':') {
                c.drawCircle(x + 1.5f * cell, top + 2.5f * cell, cell * .4f, p);
                c.drawCircle(x + 1.5f * cell, top + 4.5f * cell, cell * .4f, p);
                x += 3 * cell;
                continue;
            }
            int d = ch - '0';
            if (d >= 0 && d <= 9) {
                for (int r = 0; r < 7; r++)
                    for (int k = 0; k < 5; k++)
                        if (DG[d][r].charAt(k) == '1')
                            c.drawCircle(x + (k + .5f) * cell, top + (r + .5f) * cell, cell * .4f, p);
            }
            x += 6 * cell;
        }
    }

    private void red(Canvas c, Pen pen, float x, float y, float r) {
        Paint p = F(pen, 10, 400, Typeface.DEFAULT, false, Paint.Align.CENTER);
        if (!pen.flat) p.setColor((p.getAlpha() << 24) | 0xFF2A2A);
        c.drawCircle(x, y, r, p);
    }

    private void caps(Canvas c, Pen pen, String s, float x, float y, float px) {
        Paint p = F(pen, px, 500, Typeface.MONOSPACE, false, Paint.Align.CENTER);
        p.setLetterSpacing(0.16f);
        c.drawText(s.toUpperCase(), x, y, p);
    }

    private void tall(Canvas c, String s, float x, float base, float sx, float sy, Paint p) {
        c.save();
        c.translate(x, base);
        c.scale(sx, sy);
        c.drawText(s, 0, 0, p);
        c.restore();
    }

    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty"};

    private static String nw(int n) {
        return n < 20 ? ONES[n] : TENS[n / 10] + (n % 10 == 0 ? "" : "-" + ONES[n % 10]);
    }

    /** Shrinks the paint's text size so the string fits the design width. */
    private static void fit(Paint p, String t) {
        float w = p.measureText(t), max = W - 140f;
        if (w > max) p.setTextSize(p.getTextSize() * max / w);
    }

    private static void tint(Paint p, Pen pen, int rgb) {
        if (!pen.flat) p.setColor((p.getAlpha() << 24) | (rgb & 0xFFFFFF));
    }

    private void drawStyle(Canvas c, Opt o, Pen pen) {
        final Typeface SANS = sansFace, SERIF = Typeface.SERIF, MONO = Typeface.MONOSPACE;
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
            case "ndot": {
                Paint p = F(pen, 10, 400, SANS, false, C);
                float cell = Math.min(sz * .075f, (W - 140f) / tw(o.time)), top = y - cell * 3.5f;
                caps(c, pen, o.date, cx, top - cell * 1.6f, 44);
                dots(c, p, o.time, cx, top, cell);
                red(c, pen, cx, top + cell * 9.2f, cell * .45f);
                break;
            }
            case "ndotstack": {
                Paint p = F(pen, 10, 400, SANS, false, C);
                float cell = Math.min(sz * .17f, (W - 160f) / 11f), t1 = y - cell * 5;
                caps(c, pen, o.date, cx, t1 - cell * 1.1f, 44);
                dots(c, p, o.hh, cx, t1, cell);
                dots(c, p, o.mm, cx, t1 + cell * 8.6f, cell);
                red(c, pen, cx, t1 + cell * 17.6f, cell * .45f);
                break;
            }
            case "nring": {
                float r = Math.min(sz * .85f, W * .42f), cell = r * .055f;
                Paint lit = F(pen, 10, 400, SANS, false, C);
                Paint dim = F(pen, 10, 400, SANS, false, C);
                dim.setAlpha(Math.round(dim.getAlpha() * .22f));
                for (int i = 0; i < 60; i++) {
                    double a = i * Math.PI / 30;
                    float x = cx + (float) Math.sin(a) * r, yy = y - (float) Math.cos(a) * r;
                    if (i == o.m) red(c, pen, x, yy, 13);
                    else c.drawCircle(x, yy, (i % 5 == 0) ? 10 : 6, i < o.m ? lit : dim);
                }
                dots(c, lit, o.time, cx, y - cell * 3.5f, cell);
                caps(c, pen, o.date, cx, y + cell * 7.5f, 38);
                break;
            }
            case "cond": {
                float s = sz * .95f, sy = 2.45f, sx = .64f, cap = .72f * s * sy, b = y + cap / 2;
                Paint dp = P(pen, Paint.Style.FILL_AND_STROKE, 56, 800, SANS, false, C, 1f, false);
                dp.setStrokeWidth(3);
                c.save(); c.translate(cx, b - cap - 46); c.scale(.75f, 1.2f);
                c.drawText(o.dshort, 0, 0, dp); c.restore();
                Paint tp = P(pen, Paint.Style.FILL_AND_STROKE, s, 800, SANS, false, C, 1f, false);
                tp.setStrokeWidth(7); tp.setStrokeJoin(Paint.Join.ROUND);
                tall(c, o.hh + ":" + o.mm, cx, b, sx, sy, tp);
                break;
            }
            case "wide": {
                float s = sz * .62f, ls = s * .12f;
                Paint p = F(pen, s, 200, SANS, false, C);
                p.setLetterSpacing(.12f);
                c.drawText(o.time, cx + ls / 2f, y + s * .36f, p);
                Paint d = F(pen, 44, 500, SANS, false, C);
                d.setLetterSpacing(14f / 44f);
                c.drawText(o.date.toUpperCase(), cx + 7, y - s * .36f - 50, d);
                break;
            }
            case "ext3d": {
                float s = sz * .85f, by = y + s * .28f;
                c.drawText(o.date, cx, y - s * .7f, F(pen, 64, 500, SANS, false, C));
                Paint side = F(pen, s, 800, SANS, false, C);
                tint(side, pen, o.col2);
                for (int i = 26; i >= 1; i--) c.drawText(o.time, cx + i * .8f, by + i * .8f, side);
                c.drawText(o.time, cx, by, F(pen, s, 800, SANS, false, C));
                break;
            }
            case "duo": {
                float s = sz * .74f, by = y + s * .28f;
                String a = o.hh + ":", b = o.mm;
                Paint pa = F(pen, s, o.wt, SANS, false, L);
                Paint pb = F(pen, s, o.wt, SANS, false, L);
                tint(pb, pen, o.col2);
                float wa = pa.measureText(a), wb = pa.measureText(b), x0 = cx - (wa + wb) / 2f;
                c.drawText(o.date, cx, y - s * .62f, F(pen, 64, 500, SANS, false, C));
                c.drawText(a, x0, by, pa);
                c.drawText(b, x0 + wa, by, pb);
                break;
            }
            case "pill": {
                float s = sz * .62f;
                Paint t = F(pen, s, o.wt, SANS, false, C);
                float pw = Math.min(t.measureText(o.time) + s * .9f, W - 120f), ph = s * 1.25f;
                Paint st = P(pen, Paint.Style.STROKE, 10, 400, SANS, false, C, 1f, false);
                st.setStrokeWidth(8);
                c.drawRoundRect(cx - pw / 2f, y - ph / 2f, cx + pw / 2f, y + ph / 2f, ph / 2f, ph / 2f, st);
                c.drawText(o.time, cx, y + s * .36f, t);
                c.drawText(o.date, cx, y + ph / 2f + 90, F(pen, 56, 500, SANS, false, C));
                break;
            }
            case "bigback": {
                float s = sz * 1.3f, s2 = sz * .62f;
                c.drawText(o.date, cx, y - s * .36f - 50, F(pen, 60, 500, SANS, false, C));
                c.drawText(o.hh, cx, y + s * .36f, P(pen, Paint.Style.FILL, s, 900, SANS, false, C, .28f, false));
                c.drawText(o.mm, cx, y + s2 * .36f, F(pen, s2, 800, SANS, false, C));
                break;
            }
            case "sticker": {
                float s = sz * .85f, by = y + s * .28f;
                Paint st = P(pen, Paint.Style.STROKE, s, 900, SANS, false, C, 1f, false);
                st.setStrokeWidth(s * .14f);
                st.setStrokeJoin(Paint.Join.ROUND);
                tint(st, pen, o.col2);
                c.drawText(o.date, cx, y - s * .7f, F(pen, 64, 600, SANS, false, C));
                c.drawText(o.time, cx, by, st);
                c.drawText(o.time, cx, by, F(pen, s, 900, SANS, false, C));
                break;
            }
            case "words": {
                String a = (o.hr == 0 ? "midnight" : nw(o.hr)).toUpperCase(Locale.ENGLISH);
                String b = (o.m == 0 ? "o'clock" : o.m < 10 ? "oh " + nw(o.m) : nw(o.m)).toUpperCase(Locale.ENGLISH);
                Paint pa = F(pen, sz * .42f, o.wt, SANS, false, C);
                Paint pb = F(pen, sz * .42f, o.wt, SANS, false, C);
                fit(pa, a);
                fit(pb, b);
                tint(pb, pen, o.col2);
                float s1 = pa.getTextSize(), s2 = pb.getTextSize();
                c.drawText(o.date.toUpperCase(), cx, y - s1 * .72f - 40, F(pen, 52, 500, SANS, false, C));
                c.drawText(a, cx, y, pa);
                c.drawText(b, cx, y + Math.max(s1, s2) * .95f, pb);
                break;
            }
            case "studio": {
                float k = sz / 440f, cap = 732 * k, dcap = 58 * k;
                Paint tp = P(pen, Paint.Style.FILL, 100, 400, lg, false, C, 1f, false);
                Rect r = new Rect();
                tp.getTextBounds("0", 0, 1, r);
                float ca = r.height() / 100f;
                tp.setTextSize(cap / ca);
                tp.getTextBounds("09:55", 0, 5, r);
                float sx = 1.16f * cap / r.width();
                c.save();
                c.translate(cx, y + cap / 2);
                c.scale(sx, 1);
                c.drawText(o.hh + ":" + o.mm, 0, 0, tp);
                c.restore();
                Paint dp = P(pen, Paint.Style.FILL, dcap / ca, 400, lg, false, C, 1f, true);
                dp.setLetterSpacing(0.19f * ca);
                String ds = new SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(new Date()).toUpperCase(Locale.ENGLISH);
                c.drawText(ds, cx, y - cap / 2 - 41 * k, dp);
                break;
            }
            case "ioslong": {
                float s = sz * .82f, cap = .72f * s * 1.95f, b1 = y + cap / 2;
                c.drawText(o.date, cx, b1 - cap - 50, F(pen, 64, 500, SANS, false, C));
                Paint p = F(pen, s, 800, SANS, false, C);
                tall(c, o.hh, cx, b1, .85f, 1.95f, p);
                tall(c, o.mm, cx, b1 + cap + 60, .85f, 1.95f, p);
                break;
            }
            case "iosline": {
                float s = sz * .9f, cap = .72f * s * 2.2f, b = y + cap / 2;
                c.drawText(o.date, cx, b - cap - 40, F(pen, 64, 500, SANS, false, C));
                tall(c, o.time, cx, b, .8f, 2.2f, F(pen, s, 700, SANS, false, C));
                break;
            }
            default: { // ios
                c.drawText(o.date, cx, y - sz * .62f, F(pen, 64, 500, SANS, false, C));
                c.drawText(o.time, cx, y + sz * .28f, F(pen, sz, o.wt, SANS, false, C));
            }
        }
    }
}

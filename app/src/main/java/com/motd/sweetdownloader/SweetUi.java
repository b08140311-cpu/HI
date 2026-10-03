package com.motd.sweetdownloader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TextView;

public final class SweetUi {
    public static final int PINK_BG = Color.rgb(250, 210, 226);
    public static final int PINK = Color.rgb(235, 95, 148);
    public static final int PINK_DARK = Color.rgb(135, 54, 91);
    public static final int PINK_SOFT = Color.rgb(255, 233, 242);
    public static final int WHITE_GLASS = Color.argb(220, 255, 255, 255);
    public static final int TEXT = Color.rgb(69, 42, 55);
    public static final int MUTED = Color.rgb(125, 102, 113);

    private SweetUi() {}

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, (int) radiusDp));
        return g;
    }

    public static GradientDrawable roundedStroke(int color, int strokeColor,
                                                  float radiusDp, Context c) {
        GradientDrawable g = rounded(color, radiusDp, c);
        g.setStroke(dp(c, 1), strokeColor);
        return g;
    }

    public static GradientDrawable pinkGradient(Context c) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(248, 180, 210),
                        Color.rgb(255, 222, 235),
                        Color.rgb(249, 194, 217)
                });
        g.setCornerRadius(0);
        return g;
    }

    public static Button pill(Context c, String text, boolean primary) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : PINK_DARK);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(primary ? PINK : PINK_SOFT, 28, c));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        return b;
    }

    public static TextView title(Context c, String text, float size) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        return t;
    }

    public static TextView label(Context c, String text, float size, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    public static Button nav(Context c, String text, boolean selected) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(selected ? PINK : MUTED);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }
}

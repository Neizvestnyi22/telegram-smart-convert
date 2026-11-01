package org.telegram.lab.smartconvert;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.lab.LabActivity;
import org.telegram.lab.R;
import org.telegram.lab.Tg;
import org.telegram.lab.TouchDriver;
import org.telegram.lab.ui.ChatHeaderView;
import org.telegram.lab.ui.ChatScreen;
import org.telegram.lab.ui.MessageCell;
import org.telegram.lab.ui.WallpaperDrawable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.SmartUnits;

import java.util.ArrayList;
import java.util.TimeZone;

public class MainActivity extends LabActivity {

    private ChatScreen chat;
    private final SmartUnits.Prefs prefs = new SmartUnits.Prefs();
    private View popup, catcher;
    private final ArrayList<SmartCell> smart = new ArrayList<>();

    /** Span for a detected value; drawn as a dotted underline by SmartCell. */
    static class SmartSpan extends ClickableSpan {
        final SmartUnits.Match match;

        SmartSpan(SmartUnits.Match m) {
            match = m;
        }

        @Override
        public void onClick(View widget) {
        }

        @Override
        public void updateDrawState(TextPaint ds) {
        }
    }

    @Override
    protected void onCreateScreen(FrameLayout content) {
        prefs.timeZone = TimeZone.getTimeZone("Europe/Berlin");
        prefs.currency = "EUR";
        prefs.metric = true;
        prefs.cityName = "your time, Berlin";

        chat = new ChatScreen(this, WallpaperDrawable.GREEN);
        content.addView(chat);
        chat.header.setTitle("Jake Miller").setSubtitle("online", true).setAvatar("Jake Miller");
        chat.header.addAction(R.drawable.tg_ic_call);
        chat.header.addAction(R.drawable.tg_ic_ab_other);

        chat.addDate("Today");
        in("Hey! Back home in New York 🗽");
        in("Can we do a quick call at 3 PM EST? I'll show you the photos");
        out("Sure!");
        in("Btw the concert tickets are $240 each, still want to come?");
        in("And it's 72°F here today, finally summer vibes ☀️");
        in("The hike on Sunday is about 8 miles, bring good shoes 🥾");
        out("Haha ok, I'm in!");
        chat.scrollToBottom();
    }

    private void in(String text) {
        SmartCell c = new SmartCell(false);
        c.setText(detect(text)).setTime("12:1" + chat.list.getChildCount() % 10);
        chat.add(c);
    }

    private void out(String text) {
        SmartCell c = new SmartCell(true);
        c.setText(detect(text)).setTime("12:1" + chat.list.getChildCount() % 10).setStatus(MessageCell.STATUS_READ);
        chat.add(c);
    }

    private CharSequence detect(String text) {
        ArrayList<SmartUnits.Match> found = SmartUnits.find(text, prefs, System.currentTimeMillis());
        if (found.isEmpty()) {
            return text;
        }
        SpannableString s = new SpannableString(text);
        for (SmartUnits.Match m : found) {
            s.setSpan(new SmartSpan(m), m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private void showPopup(MessageCell cell, SmartSpan span, RectF bounds) {
        dismissPopup();
        SmartUnits.Match m = span.match;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ChatHeaderView.glass(AndroidUtilities.dp(18)));
        card.setElevation(AndroidUtilities.dp(10));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        int res = m.type == SmartUnits.TYPE_TIME ? R.drawable.tg_menu_premium_clock : m.type == SmartUnits.TYPE_MONEY ? R.drawable.tg_msg_payment_card : R.drawable.tg_msg_speed;
        icon.setImageDrawable(Tg.icon(this, res, Tg.accent));
        row.addView(icon, new LinearLayout.LayoutParams(AndroidUtilities.dp(26), AndroidUtilities.dp(26)));
        TextView title = Tg.text(this, 22, Tg.text, true);
        title.setText(m.title);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = AndroidUtilities.dp(10);
        row.addView(title, tl);
        if (m.note != null && m.type == SmartUnits.TYPE_TIME) {
            TextView note = Tg.text(this, 13, Tg.accent, true);
            note.setText(m.note);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.leftMargin = AndroidUtilities.dp(10);
            row.addView(note, nl);
        }
        card.addView(row);
        TextView sub = Tg.text(this, 13.5f, Tg.textGray, false);
        sub.setText(m.subtitle);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = AndroidUtilities.dp(4);
        sl.leftMargin = AndroidUtilities.dp(36);
        card.addView(sub, sl);
        if (m.type == SmartUnits.TYPE_MONEY && m.note != null) {
            TextView rate = Tg.text(this, 12, Tg.textGray, false);
            rate.setText(m.note);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
            rl.leftMargin = AndroidUtilities.dp(36);
            rl.topMargin = AndroidUtilities.dp(2);
            card.addView(rate, rl);
        }
        View div = new View(this);
        div.setBackgroundColor(Tg.divider);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(1));
        dl.topMargin = AndroidUtilities.dp(10);
        card.addView(div, dl);
        TextView action = Tg.text(this, 15, Tg.accent, true);
        action.setText(m.type == SmartUnits.TYPE_TIME ? "Add to calendar" : "Copy " + m.title);
        action.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(2));
        card.addView(action);

        catcher = new View(this);
        catcher.setOnClickListener(v -> dismissPopup());
        chat.overlay.addView(catcher, new FrameLayout.LayoutParams(-1, -1));

        int[] cp = new int[2], op = new int[2];
        cell.getLocationInWindow(cp);
        chat.overlay.getLocationInWindow(op);
        float sc = isStage ? phone.getScaleX() : 1f;
        float sx = (cp[0] - op[0]) / sc + bounds.centerX();
        float sy = (cp[1] - op[1]) / sc + bounds.top;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        chat.overlay.addView(card, lp);
        card.measure(View.MeasureSpec.makeMeasureSpec(chat.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        float w = card.getMeasuredWidth(), h = card.getMeasuredHeight();
        float x = Math.max(AndroidUtilities.dp(10), Math.min(chat.getWidth() - w - AndroidUtilities.dp(10), sx - w / 2));
        float y = sy - h - AndroidUtilities.dp(8);
        card.setTranslationX(x);
        card.setTranslationY(y);
        card.setPivotX(sx - x);
        card.setPivotY(h);
        card.setScaleX(0.4f);
        card.setScaleY(0.4f);
        card.setAlpha(0);
        card.animate().scaleX(1).scaleY(1).alpha(1).setDuration(280).setInterpolator(new OvershootInterpolator(1.2f)).start();
        popup = card;
    }

    private void dismissPopup() {
        if (popup != null) {
            View p = popup;
            p.animate().alpha(0).scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction(() -> chat.overlay.removeView(p)).start();
            popup = null;
        }
        if (catcher != null) {
            chat.overlay.removeView(catcher);
            catcher = null;
        }
    }

    /** Message cell that underlines detected values with dots, the way Telegram marks phone numbers. */
    private class SmartCell extends MessageCell {

        private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);

        SmartCell(boolean out) {
            super(MainActivity.this, out);
            dots.setStyle(Paint.Style.STROKE);
            dots.setStrokeWidth(AndroidUtilities.dp(1.6f));
            dots.setStrokeCap(Paint.Cap.ROUND);
            dots.setColor(out ? Tg.linkOut : Tg.accent);
            dots.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(0.1f), AndroidUtilities.dp(3.5f)}, 0));
            setSpanListener((cell, span, bounds) -> {
                if (span instanceof SmartSpan) {
                    showPopup(cell, (SmartSpan) span, bounds);
                }
            });
            smart.add(this);
        }

        @Override
        protected void onDrawUnderText(Canvas canvas) {
            if (!(getText() instanceof Spanned)) {
                return;
            }
            Spanned sp = (Spanned) getText();
            Layout layout = getLayout();
            for (SmartSpan s : sp.getSpans(0, sp.length(), SmartSpan.class)) {
                int a = sp.getSpanStart(s), b = sp.getSpanEnd(s);
                int l0 = layout.getLineForOffset(a), l1 = layout.getLineForOffset(b);
                for (int l = l0; l <= l1; l++) {
                    float x0 = l == l0 ? layout.getPrimaryHorizontal(a) : layout.getLineLeft(l);
                    float x1 = l == l1 ? layout.getPrimaryHorizontal(b) : layout.getLineRight(l);
                    float y = layout.getLineBaseline(l) + AndroidUtilities.dp(3.5f);
                    canvas.drawLine(x0, y, x1, y, dots);
                }
            }
        }

        SmartSpan span(int i) {
            Spanned sp = (Spanned) getText();
            return sp.getSpans(0, sp.length(), SmartSpan.class)[i];
        }

        float[] spanCenter(TouchDriver d, int i) {
            RectF r = spanBounds(span(i));
            float[] o = d.point(this, 0, 0);
            return new float[]{o[0] + r.centerX(), o[1] + r.centerY()};
        }
    }

    private SmartCell cellWith(String part) {
        for (SmartCell c : smart) {
            if (c.getText().toString().contains(part)) {
                return c;
            }
        }
        return null;
    }

    @Override
    protected void onAutoplay(TouchDriver d) {
        SmartCell call = cellWith("3 PM"), money = cellWith("$240"), temp = cellWith("72°F"), hike = cellWith("8 miles");
        d.wait(800)
                .zoom(1.55f, () -> call, 900)
                .wait(500)
                .tapPoint(() -> call.spanCenter(d, 0))
                .wait(2300)
                .tapXY(AndroidUtilities.dp(200), AndroidUtilities.dp(300))
                .wait(300)
                .zoom(1.55f, () -> temp, 700)
                .tapPoint(() -> money.spanCenter(d, 0))
                .wait(2200)
                .tapXY(AndroidUtilities.dp(200), AndroidUtilities.dp(300))
                .wait(300)
                .tapPoint(() -> temp.spanCenter(d, 0))
                .wait(2000)
                .tapXY(AndroidUtilities.dp(200), AndroidUtilities.dp(300))
                .wait(300)
                .zoom(1.55f, () -> hike, 600)
                .tapPoint(() -> hike.spanCenter(d, 0))
                .wait(2200)
                .tapXY(AndroidUtilities.dp(200), AndroidUtilities.dp(300))
                .resetZoom(900)
                .wait(1200);
    }
}

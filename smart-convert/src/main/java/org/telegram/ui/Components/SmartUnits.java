package org.telegram.ui.Components;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds times with a time zone, money and imperial/metric amounts in message text
 * and converts them for the reader. Runs on the device, nothing leaves the chat.
 *
 * Telegram for Android integration: MessageObject.addLinks() adds a span for each
 * {@link Match} (like it does for phone numbers); ChatMessageCell draws it with a dotted
 * underline and a tap opens the conversion popup above the span.
 * Currency rates: the prototype carries a fixed table; the client would load rates the same
 * way it already does for Stars/TON prices and cache them for a day.
 */
public class SmartUnits {

    public static final int TYPE_TIME = 0, TYPE_MONEY = 1, TYPE_UNIT = 2;

    public static class Match {
        public int start, end, type;
        public String original;
        public String title;      // "21:00"
        public String subtitle;   // "Thursday · your time (Berlin)"
        public String note;       // "in 3 hours" / "rate of Nov 1"
    }

    public static class Prefs {
        public TimeZone timeZone = TimeZone.getDefault();
        public String currency = "EUR";
        public boolean metric = true;
        public String cityName = "your time";
    }

    // USD → x, demo table.
    private static final HashMap<String, Double> RATES = new HashMap<>();
    private static final HashMap<String, String> SYMBOLS = new HashMap<>();

    static {
        RATES.put("USD", 1.0);
        RATES.put("EUR", 0.921);
        RATES.put("GBP", 0.787);
        RATES.put("JPY", 152.3);
        RATES.put("UAH", 41.4);
        RATES.put("PLN", 3.98);
        SYMBOLS.put("$", "USD");
        SYMBOLS.put("€", "EUR");
        SYMBOLS.put("£", "GBP");
        SYMBOLS.put("¥", "JPY");
        SYMBOLS.put("₴", "UAH");
    }

    private static final HashMap<String, String> ZONES = new HashMap<>();

    static {
        ZONES.put("EST", "America/New_York");
        ZONES.put("EDT", "America/New_York");
        ZONES.put("ET", "America/New_York");
        ZONES.put("PST", "America/Los_Angeles");
        ZONES.put("PDT", "America/Los_Angeles");
        ZONES.put("PT", "America/Los_Angeles");
        ZONES.put("CST", "America/Chicago");
        ZONES.put("GMT", "GMT");
        ZONES.put("UTC", "UTC");
        ZONES.put("BST", "Europe/London");
        ZONES.put("CET", "Europe/Berlin");
        ZONES.put("MSK", "Europe/Moscow");
        ZONES.put("JST", "Asia/Tokyo");
    }

    private static final Pattern TIME = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s?(am|pm|AM|PM)?\\s(EST|EDT|ET|PST|PDT|PT|CST|GMT|UTC|BST|CET|MSK|JST)\\b");
    private static final Pattern MONEY_SYMBOL = Pattern.compile("([$€£¥₴])\\s?(\\d{1,3}(?:[,\\s]\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern MONEY_CODE = Pattern.compile("\\b(\\d{1,3}(?:[,\\s]\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)\\s?(USD|EUR|GBP|JPY|UAH|PLN)\\b");
    private static final Pattern UNIT = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s?(miles?|mi|ft|feet|foot|lbs?|pounds|oz|°F|km|kg|°C|cm|m)(?![\\w])");

    public static ArrayList<Match> find(CharSequence text, Prefs prefs, long now) {
        ArrayList<Match> out = new ArrayList<>();
        String s = text.toString();
        Matcher m = TIME.matcher(s);
        while (m.find()) {
            Match r = time(m, prefs, now);
            if (r != null) {
                out.add(r);
            }
        }
        m = MONEY_SYMBOL.matcher(s);
        while (m.find()) {
            add(out, money(m.start(), m.end(), m.group(), SYMBOLS.get(m.group(1)), m.group(2), prefs));
        }
        m = MONEY_CODE.matcher(s);
        while (m.find()) {
            add(out, money(m.start(), m.end(), m.group(), m.group(2), m.group(1), prefs));
        }
        m = UNIT.matcher(s);
        while (m.find()) {
            add(out, unit(m, prefs));
        }
        return out;
    }

    private static void add(ArrayList<Match> list, Match r) {
        if (r == null) {
            return;
        }
        for (Match o : list) {
            if (r.start < o.end && o.start < r.end) {
                return;
            }
        }
        list.add(r);
    }

    private static Match time(Matcher m, Prefs prefs, long now) {
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        String ampm = m.group(3);
        if (ampm != null) {
            boolean pm = ampm.equalsIgnoreCase("pm");
            if (h == 12) {
                h = pm ? 12 : 0;
            } else if (pm) {
                h += 12;
            }
        } else if (m.group(2) == null) {
            return null; // "3 EST" without minutes or am/pm is too ambiguous
        }
        if (h > 23 || min > 59) {
            return null;
        }
        TimeZone from = TimeZone.getTimeZone(ZONES.get(m.group(4)));
        Calendar src = Calendar.getInstance(from);
        src.setTimeInMillis(now);
        src.set(Calendar.HOUR_OF_DAY, h);
        src.set(Calendar.MINUTE, min);
        src.set(Calendar.SECOND, 0);
        if (src.getTimeInMillis() < now - 3600_000L) {
            src.add(Calendar.DAY_OF_YEAR, 1);
        }
        Calendar dst = Calendar.getInstance(prefs.timeZone);
        dst.setTimeInMillis(src.getTimeInMillis());
        Match r = new Match();
        r.start = m.start();
        r.end = m.end();
        r.type = TYPE_TIME;
        r.original = m.group();
        r.title = String.format(Locale.US, "%02d:%02d", dst.get(Calendar.HOUR_OF_DAY), dst.get(Calendar.MINUTE));
        Calendar today = Calendar.getInstance(prefs.timeZone);
        today.setTimeInMillis(now);
        String day = dst.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ? "Today" : "Tomorrow";
        r.subtitle = day + " · " + prefs.cityName;
        long diff = (src.getTimeInMillis() - now) / 60000;
        r.note = diff < 60 ? "in " + diff + " min" : "in " + Math.round(diff / 60f) + " h";
        return r;
    }

    private static Match money(int start, int end, String original, String code, String amount, Prefs prefs) {
        if (code == null || code.equals(prefs.currency) || !RATES.containsKey(code)) {
            return null;
        }
        double v = Double.parseDouble(amount.replace(",", "").replace(" ", ""));
        double usd = v / RATES.get(code);
        double res = usd * RATES.get(prefs.currency);
        Match r = new Match();
        r.start = start;
        r.end = end;
        r.type = TYPE_MONEY;
        r.original = original;
        r.title = formatMoney(res, prefs.currency);
        r.subtitle = original.trim() + " in " + prefs.currency;
        r.note = String.format(Locale.US, "1 %s = %.4f %s", code, RATES.get(prefs.currency) / RATES.get(code), prefs.currency);
        return r;
    }

    private static String formatMoney(double v, String code) {
        String sym = "EUR".equals(code) ? "€" : "GBP".equals(code) ? "£" : "USD".equals(code) ? "$" : "";
        String num = String.format(Locale.US, v >= 100 ? "%,.0f" : "%,.2f", v);
        return sym.isEmpty() ? num + " " + code : sym + num;
    }

    private static Match unit(Matcher m, Prefs prefs) {
        double v = Double.parseDouble(m.group(1));
        String u = m.group(2).toLowerCase(Locale.US);
        String title, sub;
        boolean imperial = u.startsWith("mi") || u.equals("ft") || u.startsWith("fe") || u.startsWith("fo") || u.startsWith("lb") || u.startsWith("po") || u.equals("oz") || u.equals("°f");
        if (prefs.metric != imperial) {
            return null; // already in the reader's system
        }
        switch (u) {
            case "mile": case "miles": case "mi":
                title = fmt(v * 1.609344) + " km";
                break;
            case "ft": case "feet": case "foot":
                title = v * 0.3048 >= 1 ? fmt(v * 0.3048) + " m" : fmt(v * 30.48) + " cm";
                break;
            case "lb": case "lbs": case "pounds":
                title = fmt(v * 0.453592) + " kg";
                break;
            case "oz":
                title = fmt(v * 28.3495) + " g";
                break;
            case "°f":
                title = Math.round((v - 32) * 5 / 9) + " °C";
                break;
            case "km":
                title = fmt(v / 1.609344) + " mi";
                break;
            case "kg":
                title = fmt(v / 0.453592) + " lb";
                break;
            case "°c":
                title = Math.round(v * 9 / 5 + 32) + " °F";
                break;
            case "cm":
                title = fmt(v / 2.54) + " in";
                break;
            case "m":
                title = fmt(v / 0.3048) + " ft";
                break;
            default:
                return null;
        }
        sub = m.group().trim();
        Match r = new Match();
        r.start = m.start();
        r.end = m.end();
        r.type = TYPE_UNIT;
        r.original = m.group();
        r.title = title;
        r.subtitle = sub;
        r.note = null;
        return r;
    }

    private static String fmt(double v) {
        return v >= 10 ? String.format(Locale.US, "%.1f", v).replaceAll("\\.0$", "") : String.format(Locale.US, "%.2f", v);
    }
}

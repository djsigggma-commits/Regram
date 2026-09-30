package app.regram.pillstack;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

/** Список целевых валют и форматирование цен для пилюль курсов. */
public class PillCurrencies {

    public static final String AUTO = PillStackConfig.CURRENCY_AUTO;

    /** Символы, которые без кода валюты читаются неоднозначно. */
    private static final HashSet<String> AMBIGUOUS_SYMBOLS = new HashSet<>(Arrays.asList("$", "kr", "Fr", "₩"));

    private static class CurrencyInfo {
        final int nameResId;
        final String symbolOverride;
        final boolean suffixSymbol;

        CurrencyInfo(int nameResId, String symbolOverride, boolean suffixSymbol) {
            this.nameResId = nameResId;
            this.symbolOverride = symbolOverride;
            this.suffixSymbol = suffixSymbol;
        }
    }

    private static final Map<String, CurrencyInfo> CURRENCIES = new HashMap<>();

    public static final String[] TARGET_CURRENCIES = {
            AUTO, "AED", "BYN", "CNY", "CZK", "EUR", "GBP", "ILS", "INR", "JPY", "KZT", "PLN", "RUB", "TRY", "UAH", "USD"
    };

    static {
        add("USD", R.string.CryptoCurrencyUsd, "$", false);
        add("EUR", R.string.CryptoCurrencyEur, null, false);
        add("RUB", R.string.CryptoCurrencyRub, "₽", true);
        add("GBP", R.string.CryptoCurrencyGbp, null, false);
        add("KZT", R.string.CryptoCurrencyKzt, "₸", true);
        add("TRY", R.string.CryptoCurrencyTry, "₺", true);
        add("UAH", R.string.CryptoCurrencyUah, "₴", true);
        add("PLN", R.string.CryptoCurrencyPln, "zł", true);
        add("AED", R.string.CryptoCurrencyAed, null, false);
        add("CNY", R.string.CryptoCurrencyCny, "CN¥", false);
        add("JPY", R.string.CryptoCurrencyJpy, null, false);
        add("BYN", R.string.CryptoCurrencyByn, "Br", true);
        add("ILS", R.string.CryptoCurrencyIls, "₪", false);
        add("CZK", R.string.CryptoCurrencyCzk, "Kč", true);
        add("INR", R.string.CryptoCurrencyInr, "₹", false);
    }

    private static void add(String code, int nameResId, String symbolOverride, boolean suffixSymbol) {
        CURRENCIES.put(normalize(code), new CurrencyInfo(nameResId, symbolOverride, suffixSymbol));
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** Список валют без базовой (для USD-пилюли нет смысла показывать USD). */
    public static String[] getTargetCurrencies(String excludeCode) {
        if (excludeCode == null || excludeCode.isEmpty()) {
            return TARGET_CURRENCIES;
        }
        int count = 0;
        for (String code : TARGET_CURRENCIES) {
            if (!excludeCode.equalsIgnoreCase(code)) count++;
        }
        String[] result = new String[count];
        int i = 0;
        for (String code : TARGET_CURRENCIES) {
            if (!excludeCode.equalsIgnoreCase(code)) result[i++] = code;
        }
        return result;
    }

    public static CharSequence getTargetCurrencyLabel(String code) {
        if (code == null || AUTO.equalsIgnoreCase(code)) {
            return LocaleController.getString(R.string.CryptoCurrencyAuto);
        }
        String normalized = normalize(code);
        CurrencyInfo info = CURRENCIES.get(normalized);
        if (info == null) {
            return normalized;
        }
        return LocaleController.getString(info.nameResId) + " — " + normalized;
    }

    public static CharSequence getTargetCurrencySubtext(String code) {
        if (code == null || AUTO.equalsIgnoreCase(code)) {
            return LocaleController.getString(R.string.CryptoCurrencyAuto);
        }
        String normalized = normalize(code);
        CurrencyInfo info = CURRENCIES.get(normalized);
        return info == null ? normalized : LocaleController.getString(info.nameResId);
    }

    private static int getCurrencyExp(String code) {
        try {
            return Math.max(0, Currency.getInstance(normalize(code)).getDefaultFractionDigits());
        } catch (Exception ignore) {
            return 2;
        }
    }

    /** «$95 240,12» / «95 240,12 ₽». Возвращает null, если отформатировать не вышло. */
    public static String formatFiatPrice(BigDecimal value, String code) {
        if (value == null || code == null || code.isEmpty()) {
            return null;
        }
        try {
            int exp = getCurrencyExp(code);
            BigDecimal scaled = value.setScale(exp, RoundingMode.HALF_UP);
            NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
            format.setGroupingUsed(true);
            format.setMinimumFractionDigits(exp);
            format.setMaximumFractionDigits(exp);
            String formatted = format.format(scaled);

            String normalized = normalize(code);
            CurrencyInfo info = CURRENCIES.get(normalized);
            String symbol = info != null ? info.symbolOverride : null;
            boolean overridden = symbol != null;
            if (!overridden) {
                try {
                    symbol = Currency.getInstance(normalized).getSymbol(Locale.US);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (symbol == null || symbol.isEmpty() || symbol.equalsIgnoreCase(normalized)) {
                return formatted + " " + normalized;
            }
            if (!overridden && AMBIGUOUS_SYMBOLS.contains(symbol)) {
                return formatted + " " + normalized;
            }
            if (info != null && info.suffixSymbol) {
                return formatted + " " + symbol;
            }
            return symbol + formatted;
        } catch (Exception ignore) {
            return null;
        }
    }
}

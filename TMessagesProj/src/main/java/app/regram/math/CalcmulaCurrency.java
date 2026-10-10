package app.regram.math;

import android.text.TextUtils;
import android.util.LruCache;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.Arrays;
import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CalcmulaCurrency {

    private static final String BOT_USERNAME = "calcmulabot";
    private static final String FAILED = "";

    private static final String[] CODES = {
            "usd", "eur", "rub", "uah", "kzt", "byn", "gbp", "jpy", "cny", "try", "inr", "brl", "cad",
            "aud", "chf", "pln", "czk", "sek", "nok", "dkk", "huf", "ron", "bgn", "gel", "amd", "azn",
            "uzs", "kgs", "tjs", "mdl", "ils", "aed", "sar", "krw", "hkd", "sgd", "thb", "vnd", "idr",
            "myr", "php", "mxn", "ars", "clp", "cop", "pen", "nzd", "zar", "egp", "rsd", "isk",
            "ton", "gram", "btc", "eth", "usdt", "usdc", "sol", "bnb", "trx", "ltc", "xrp", "doge", "not", "dogs",
            "ada", "shib", "dot", "xmr", "xtr", "xtrf", "kr", "kč", "zł", "lei"
    };

    private static final String[] ALIASES = {
            "дол", "длр", "доллар(а/ов/ы/ах/е)", "бакс(а/ов/ы/ах)", "юсд", "dollar", "dollars", "buck", "bucks",
            "долар(а/ів/и/ах)", "баксів", "бакси", "евр", "евро", "euro", "euros", "євро", "quid", "руб", "рубль",
            "рубл(я/ей/и/ях)", "ruble", "rubles", "рублів", "рублі", "rur", "р", "грн", "гривн(а/ы/ах/е/у)", "гривен",
            "hryvnia", "hryvnias", "грив(ня/ні/ень/нях/ню)", "зл", "zl", "злот(ый/ых/ые)", "zloty", "zlotys",
            "злот(ий/их/і)", "лари", "lari", "ларі", "драм(а/ов/ы/ах)", "dram", "drams", "драмів", "драми", "йена",
            "йены", "йен", "йенах", "йену", "yen", "єна", "єни", "єн", "єнах", "єну", "юань", "юан(я/ей/и/ях)",
            "жэньминьби", "женьминьби", "yuan", "yuans", "rmb", "renminbi", "renminbis", "юанів", "юані",
            "женьміньбі", "вон(а/ов/ы/ах)", "won", "тенге", "tenge", "теньге", "тг", "бун(а/ов/ы/ах)", "бунів",
            "буни", "byr", "лир(а/ы/ах/у)", "лир", "ліра", "лірах", "ліри", "лір", "ліру", "tl", "lira", "liras",
            "сом(а/ов/ы/ах)", "som", "soms", "сомів", "соми", "сум(а/ов/ы/ах)", "сумів", "суми", "fcfa",
            "тугрик(а/ов/и/ах)", "tugrik", "tugriks", "тугриків", "бат(а/ов/ы/ах)", "baht", "bahts", "батів", "бати",
            "франк(а/ов/и/ах)", "franc", "francs", "франків", "форинт(а/ов/ы/ах)", "forint", "forints", "форинтів",
            "форинти", "шекель", "шекел(я/ей/и/ях)", "shekel", "shekels", "шекелів", "шекелі", "nis",
            "дирхам(а/ов/ы/ах)", "dirham", "dirhams", "дирхамів", "дирхами", "dh", "dhs", "рупи(я/и/й/ях/ю)",
            "рупі(я/ї/й/ях/ю)", "rupee", "rupees", "rs", "лей", "лея", "леев", "леи", "леях", "леї", "леїв", "leu",
            "реал(а/ов/ы/ах)", "реали", "реалів", "real", "reais", "kc", "донг(а/ов/и/ах)", "dong", "dongs", "донгів",
            "манат(а/ов/ы/ах)", "manat", "manats", "манатів", "манати", "рэнд(а/ов/ы/ах)", "rand", "rands",
            "ранд(а/ів/и/ах)", "така", "taka", "найра", "naira", "стар(с/а/са/ов/сов/сы/сах)", "star", "stars",
            "звезд(а/ы/ах/у)", "звёзд(ы/ах)", "зірк(а/и/у/ою/ам/ами/ах)", "зірці", "зірок", "тон(а/ов/ы/ах/е)", "ton",
            "tons", "grams", "грам(а/ов/ы/ах/е)", "тонів", "тони", "грамів", "грами", "toncoin(s)",
            "тонкоин(а/ов/ы/ах/е)", "тонкоїн(а/ів/и/ах)", "бтк", "биток", "биткоин(а/ов/ы/ах/е)", "битк(а/ов/и/ах)",
            "bitcoin", "bitcoins", "btc", "біткоїн(а/ів/и/ах)", "биткойн(а/ов/ы/ах/е)", "біткойн(а/ів/и/ах)",
            "эфир(а/ов/ы/ах/е)", "ethereum", "ethereums", "eth", "ефір(у/ів/и/ах)", "эфириум(а/ов/ы/ах/е)",
            "ефіріум(у/ів/и/ах)", "бнб", "бинанс(а/ов/ы/ах/е)", "binance", "binances", "bnb", "бінанс(а/ів/и/ах)",
            "рипл(а/ов/ы/ах/е)", "ripple", "ripples", "xrp", "хрп", "риплів", "рипли", "кардан(о/а/ов/ы/ах/е)",
            "cardano", "cardanos", "ада", "ada", "доги", "доге", "dogecoin", "dogecoins", "doge", "юсдт", "tether",
            "тезер(а/ов/ы/ах/ів/и)", "солана", "соланы", "солан", "солане", "соланах", "соль", "соли", "солей",
            "солях", "солью", "solana", "солани", "сол", "лайткоин(а/ов/ы/ах/е)", "лайт", "litecoin", "litecoins",
            "ltc", "лайткоїн(а/ів/и/ах)", "трон(а/ов/ы/ах/е)", "tron", "trx", "тронів", "трони", "шиба", "шибы",
            "шиб", "шибах", "шибе", "shiba", "shib", "шиби", "полкадот(а/ов/ы/ах/е)", "дот", "polkadot", "полкадотів",
            "полкадоти", "хмр", "монеро", "monero", "sat", "sats", "satoshi", "satoshis", "сатоши", "сатоші", "gwei",
            "гвей", "гвеи", "гвеев", "гвеях", "гвеї", "гвеїв", "wei", "nanoton", "nanotons", "нанотон(а/ов/ы/ах)",
            "нанотонів", "нанотони"
    };

    private static final Set<String> WORDS = new HashSet<>(Arrays.asList(CODES));

    static {
        for (Currency currency : Currency.getAvailableCurrencies()) {
            WORDS.add(currency.getCurrencyCode().toLowerCase(Locale.ROOT));
        }
        for (String alias : ALIASES) {
            int open = alias.indexOf('(');
            if (open < 0) {
                WORDS.add(alias);
                continue;
            }
            String base = alias.substring(0, open);
            WORDS.add(base);
            for (String suffix : alias.substring(open + 1, alias.length() - 1).split("/")) {
                WORDS.add(base + suffix);
            }
        }
    }

    private static final LruCache<String, String> RESULTS = new LruCache<>(64);
    private static final Set<String> PENDING = new HashSet<>();

    private CalcmulaCurrency() {
    }

    private static boolean isCurrencySymbol(char c) {
        return c == '★' || Character.getType(c) == Character.CURRENCY_SYMBOL;
    }

    private static boolean isCurrencyWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return WORDS.contains(lower) || WORDS.contains(lower.replace('i', 'і'));
    }

    private static boolean isQueryChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == ',' || MathExpression.isBlank(c)
                || MathExpression.isSymbolChar(c) || isCurrencySymbol(c);
    }

    private static boolean mentionsCurrency(String query) {
        boolean digit = false;
        boolean currency = false;
        int wordStart = -1;
        for (int i = 0; i <= query.length(); i++) {
            char c = i < query.length() ? query.charAt(i) : ' ';
            if (Character.isDigit(c)) {
                digit = true;
            }
            if (isCurrencySymbol(c)) {
                currency = true;
            }
            if (Character.isLetter(c)) {
                if (wordStart < 0) {
                    wordStart = i;
                }
            } else if (wordStart >= 0) {
                if (isCurrencyWord(query.substring(wordStart, i))) {
                    currency = true;
                }
                wordStart = -1;
            }
        }
        return digit && currency;
    }

    public static String queryAt(CharSequence text, int caret) {
        int equalsIndex = MathExpression.equalsIndexAt(text, caret);
        if (equalsIndex < 0) {
            return null;
        }
        int limit = Math.max(0, equalsIndex - 64);
        int start = equalsIndex - 1;
        while (start >= limit && isQueryChar(text.charAt(start))) {
            start--;
        }
        start++;
        if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            return null;
        }
        while (start >= 0 && start < equalsIndex) {
            String query = text.subSequence(start, equalsIndex).toString().trim();
            if (!query.isEmpty()) {
                char first = query.charAt(0);
                if ((Character.isDigit(first) || isCurrencySymbol(first)) && mentionsCurrency(query)) {
                    return query;
                }
            }
            start = MathExpression.nextCandidate(text, start, equalsIndex);
        }
        return null;
    }

    public static MathExpression.Suggestion suggestionAt(CharSequence text, int caret, String query) {
        String value = RESULTS.get(query);
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        return new MathExpression.Suggestion(caret, MathExpression.insertTextFor(text, caret, value), value);
    }

    public static String resultFor(String query) {
        String value = query == null ? null : RESULTS.get(query);
        return TextUtils.isEmpty(value) ? null : value;
    }

    public static boolean isKnown(String query) {
        return RESULTS.get(query) != null;
    }

    public static void request(int account, String query, Runnable onResult) {
        if (query == null || isKnown(query) || !PENDING.add(query)) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLObject bot = controller.getUserOrChat(BOT_USERNAME);
        if (bot instanceof TLRPC.User) {
            query(account, (TLRPC.User) bot, query, onResult);
            return;
        }
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = BOT_USERNAME;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            TLRPC.User user = null;
            if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                controller.putUsers(resolved.users, false);
                controller.putChats(resolved.chats, false);
                for (TLRPC.User candidate : resolved.users) {
                    if (candidate != null && BOT_USERNAME.equalsIgnoreCase(candidate.username)) {
                        user = candidate;
                    }
                }
            }
            if (user == null) {
                PENDING.remove(query);
                RESULTS.put(query, FAILED);
                return;
            }
            query(account, user, query, onResult);
        }));
    }

    private static void query(int account, TLRPC.User bot, String query, Runnable onResult) {
        TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
        req.bot = MessagesController.getInstance(account).getInputUser(bot);
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.query = query;
        req.offset = "";
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            PENDING.remove(query);
            String value = FAILED;
            if (error == null && response instanceof TLRPC.messages_BotResults) {
                TLRPC.messages_BotResults results = (TLRPC.messages_BotResults) response;
                if (!results.results.isEmpty()) {
                    String title = results.results.get(0).title;
                    if (title != null && title.startsWith("=")) {
                        value = title.substring(1).trim();
                    }
                }
            }
            RESULTS.put(query, value);
            if (onResult != null && !value.isEmpty()) {
                onResult.run();
            }
        }));
    }
}

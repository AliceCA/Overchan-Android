/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nya.miku.wishmaster.chans.makaba;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Константы, используемые в модуле 2ch.hk
 * @author miku-nyan
 *
 */
public class MakabaConstants {
    public static final String CHAN_NAME = "2ch.hk";
    
    /** домен по умолчанию */
    public static final String DEFAULT_DOMAIN = "2ch.hk";
    
    /** все зеркала харкача*/
    public static final List<String> DOMAINS_LIST = Arrays.asList(new String[] {
            "2ch.hk", "2ch.pm", "2-ch.so", "2ch.re", "2ch.tf", "2ch.wf", "2ch.yt"
    });
    
    /** подсказка в меню настроек */
    public static final String DOMAINS_HINT = "2ch.hk, 2ch.pm, 2ch.re, 2ch.tf, 2ch.wf, 2ch.yt, 2-ch.so";
    
    /** доски без шок контента (SFW) */
    public static final List<String> SFW_BOARDS = Arrays.asList(new String[] {
            "bi", "bo", "fl", "ftb", "hi", "mlp", "ne", "psy", "sf", "sp", "tv", "w", "wh", "mov", "mu",
            "di", "diy", "dom", "mus", "pa", "trv", "cg", "vg"
    });
    
    /** доски, где можно создать тред без прикрепленных картинок */
    public static final List<String> NO_IMAGES_BOARDS = Arrays.asList(new String[] {
            "d", "ph"
    });
    
    /** доски, где нельзя указать имя пользователя */
    public static final List<String> NO_USERNAMES_BOARDS = Arrays.asList(new String[] {
            "d", "b", "vg", "po", "wm"
    });
    
    /** доски, где нельзя указать тему */
    public static final List<String> NO_SUBJECTS_BOARDS = Arrays.asList(new String[] {
            "b"
    });
    
    public static final List<String> CATEGORIES = Arrays.asList(new String[] {
            "Тематика", "Творчество", "Техника и софт", "Игры", "Японская культура", "Разное", "Взрослым", "Политика", "Пробное"
    });
    
    public static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm", "mp4" };
    
    public static final String[] CATALOG_TYPES = { "catalog", "catalog_num" };
    
    public static final String[] CAPTCHA_TYPES = new String[] { "2chaptcha", "Google Recaptcha 2", "Google Recaptcha 2 (fallback)", "Mail.ru NOCAPTCHA" };
    public static final String[] CAPTCHA_TYPES_KEYS = new String[] { "2chaptcha", "recaptcha", "recaptcha-fallback", "mailru" };
    public static final String CAPTCHA_TYPE_DEFAULT = "recaptcha-fallback";
    
    public static final int CAPTCHA_2CHAPTCHA = 1;
    public static final int CAPTCHA_RECAPTCHA = 2;
    public static final int CAPTCHA_RECAPTCHA_FALLBACK = 3;
    public static final int CAPTCHA_MAILRU = 4;
    public static final int CAPTCHA_DISABLED = 5;
    
    public static final String PREF_KEY_MOBILE_API = "mobile_api";
    public static final String PREF_KEY_USE_HTTPS_MAKABA = "use_https";
    public static final String PREF_KEY_DOMAIN = "domain";
    public static final String PREF_KEY_CAPTCHA_TYPE = "captcha_type";
    public static final String PREF_KEY_USERCODE_COOKIE_DOMAIN = "usercode_domain";
    public static final String PREF_KEY_USERCODE_COOKIE_VALUE = "usercode_cookie";
    public static final String PREF_KEY_NOCAPTCHA_COOKIE_VALUE = "nocaptcha_cookie";
    public static final String PREF_KEY_NOCAPTCHA_COOKIE_DOMAIN = "nocaptcha_domain";
    public static final String PREF_KEY_PASSCODE = "passcode";

    public static final String MAILRU_JS_URL = "https://api-nocaptcha.mail.ru/captcha?public_key=";
    public static final Pattern MAILRU_ID_PATTERN = Pattern.compile("id:\\s*\"([^\"]*)\"");
    public static final Pattern MAILRU_URL_PATTERN = Pattern.compile("url:\\s*\"([^\"]*)\"");
    public static final String MAILRU_DEFAULT_CAPTCHA_URL = "https://api-nocaptcha.mail.ru/c/1";

    public static final String USERCODE_COOKIE_NAME = "usercode_auth";
    public static final String USERCODE_NOCAPTCHA_COOKIE_NAME = "passcode_auth";
}

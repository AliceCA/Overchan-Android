package nya.miku.wishmaster.http.stormwall;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.cookie.SetCookie;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.HttpConstants;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public class StormwallChecker {
    private static final String TAG = "StormwallChecker";
    
    private StormwallChecker() {}
    private static StormwallChecker instance;
    
    /** Получить объект-синглтон */
    public static synchronized StormwallChecker getInstance() {
        if (instance == null) instance = new StormwallChecker();
        return instance;
    }
    
    /**
     * Проверить рекапчу Stormwall, получить cookie
     * @param exception Stormwall исключение
     * @param httpClient HTTP клиент
     * @param task отменяемая задача
     * @param recaptchaAnswer ответ на рекапчу
     * @return полученная cookie или null, если проверка не прошла
     */
    public Cookie checkRecaptcha(StormwallException exception, ExtendedHttpClient httpClient, CancellableTask task, String recaptchaAnswer) {
        HttpResponseModel responseModel = null;
        try {
            List<NameValuePair> pairs = new ArrayList<NameValuePair>();
            pairs.add(new BasicNameValuePair("g-recaptcha-response", recaptchaAnswer));
            pairs.add(new BasicNameValuePair("swp_sessionKey", exception.getSwpKey()));
            
            HttpRequestModel rqModel = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8"))
            .setCustomHeaders(new Header[] {
                    new BasicHeader(HttpHeaders.USER_AGENT, HttpConstants.USER_AGENT_STRING),
                    new BasicHeader(HttpHeaders.ACCEPT, "text/plain, */*; q=0.01"),
                    new BasicHeader(HttpHeaders.REFERER, exception.getUrl()),
            }).setNoRedirect(true).build();
            CookieStore cookieStore = httpClient.getCookieStore();
            removeCookie(cookieStore, exception.getRequiredCookieName());
            responseModel = HttpStreamer.getInstance().getFromUrl(exception.getUrl(), rqModel, httpClient, null, task);
            for (int i = 0; i < 3  && responseModel.statusCode == 400; ++i) {
                Logger.d(TAG, "HTTP 400");
                responseModel.release();
                responseModel = HttpStreamer.getInstance().getFromUrl(exception.getBaseUrl(), rqModel, httpClient, null, task);
            }
            for (Cookie cookie : cookieStore.getCookies()) {
                if (isSWPCookie(cookie, exception.getUrl(), exception.getRequiredCookieName())) {
                    Logger.d(TAG, "Cookie found: " + cookie.getValue());
                    return cookie;
                }
            }
            Logger.d(TAG, "Cookie is not found");
        } catch (Exception e) {
            Logger.e(TAG, e);
        } finally {
            if (responseModel != null) {
                responseModel.release();
            }
        }
        return null;
    }

    static boolean isSWPCookie(Cookie cookie, String url, String requiredCookieName) {
        try {
            String cookieName = cookie.getName();
            String cookieDomain = cookie.getDomain();
            if (!cookieDomain.startsWith(".")) {
                cookieDomain = "." + cookieDomain;
            }

            String urlCookie = "." + Uri.parse(url).getHost();
            if (cookieName.equals(requiredCookieName) && urlCookie.endsWith(cookieDomain.toLowerCase(Locale.US))) {
                return true;
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return false;
    }

    static void removeCookie(CookieStore store, String name) {
        boolean flag = false;
        for (Cookie cookie : store.getCookies()) {
            if (cookie.getName().equals(name)) {
                if (cookie instanceof SetCookie) {
                    flag = true;
                    ((SetCookie) cookie).setExpiryDate(new Date(0));
                } else {
                    Logger.e(TAG, "cannot remove cookie (object does not implement SetCookie): " + cookie.toString());
                }
            }
        }
        if (flag) store.clearExpired(new Date());
    }
}

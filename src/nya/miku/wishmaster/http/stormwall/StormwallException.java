package nya.miku.wishmaster.http.stormwall;

import android.app.Activity;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;

public class StormwallException extends InteractiveException {
    private static final long serialVersionUID = 1L;

    private static final String SERVICE_NAME = "Stormwall";
    private static final String TAG = "StormwallException";

    private static final Pattern PATTERN_SITEKEY = Pattern.compile("data-sitekey=\"?([^\"&]+)");
    private static final Pattern PATTERN_SWP_KEY = Pattern.compile("<input type=\"hidden\" name=\"swp_sessionKey\" value=\"?([^\"&]+)");
    private static final Pattern PATTERN_SESSION_KEY = Pattern.compile("var sessionKey = '([^\'&]+)");
    private static final Pattern PATTERN_RECAPTCHA3_KEY = Pattern.compile("var recaptcha3key = '([^\'&]+)");

    protected static final String STORMWALL_COOKIE_NAME = "swp_token";
    
    private String url;
    private String baseUrl;
    private String siteKey;
    private String swpKey;
    private String sessionKey;
    private String recaptcha3Key;
    private String origin;
    private String chanName;
    private boolean recaptcha;
    
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    public String getUrl() {
        return url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getOrigin() {
        return origin;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getSwpKey() {
        return swpKey;
    }

    public String getRequiredCookieName() {
        return STORMWALL_COOKIE_NAME;
    }

    public boolean isRecaptcha() {
        return recaptcha;
    }

    public static StormwallException withRecaptcha(String url, String html, String chanName) {
        StormwallException res = new StormwallException();
        res.url = url;
        res.chanName = chanName;
        res.recaptcha = true;
        
        Matcher m = PATTERN_SITEKEY.matcher(html);
        if (m.find()) res.siteKey = m.group(1);
        else return null;

        m = PATTERN_SWP_KEY.matcher(html);
        if (m.find()) res.swpKey = m.group(1);
        
        m = PATTERN_SESSION_KEY.matcher(html);
        if (m.find()) res.sessionKey = m.group(1);
        
        m = PATTERN_RECAPTCHA3_KEY.matcher(html);
        if (m.find()) res.recaptcha3Key = m.group(1);

        try {
            URL baseUrl = new URL(url);
            res.baseUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + "/";
            res.origin = baseUrl.getProtocol() + "://" + baseUrl.getHost();
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return res;
    }
    
    public static StormwallException antiDDOS(String url, String html, String chanName) {
        StormwallException res = new StormwallException();
        res.url = url;
        res.chanName = chanName;
        res.recaptcha = false;
        return res;
    }

    @Override
    public void handle(Activity activity, CancellableTask task, Callback callback) {
        StormwallUIHandler.handleStormwall(this, (HttpChanModule) MainApplication.getInstance().getChanModule(chanName), activity, task, callback);
    }
}

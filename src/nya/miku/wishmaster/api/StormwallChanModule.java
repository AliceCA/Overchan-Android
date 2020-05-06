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

package nya.miku.wishmaster.api;

import android.content.SharedPreferences;
import android.content.res.Resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.stormwall.StormwallException;
import nya.miku.wishmaster.http.streamer.HttpRequestException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public abstract class StormwallChanModule extends AbstractChanModule {
    
    protected static final String PREF_KEY_STORMWALL_COOKIE_VALUE = "PREF_KEY_STORMWALL_COOKIE";
    protected static final String PREF_KEY_STORMWALL_COOKIE_DOMAIN = "PREF_KEY_STORMWALL_COOKIE_DOMAIN";

    protected static final String STORMWALL_FIREWALL_HEADER_NAME = "X-FireWall-Protection";
    protected static final String STORMWALL_FIREWALL_TRUE_VALUE = "True";
    
    protected static final String STORMWALL_COOKIE_NAME = "swp_token";
    
    public StormwallChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    protected boolean canStormwall() {
        return true;
    }
    
    protected String getStormwallCookieDomain() {
        return preferences.getString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_DOMAIN), null);
    }
    
    @Override
    protected void initHttpClient() {
        if (canStormwall()) {
            String stormwallCookieValue = preferences.getString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_VALUE), null);
            String stormwallCookieDomain = getStormwallCookieDomain();
            if (stormwallCookieValue != null && stormwallCookieDomain != null) {
                BasicClientCookie c = new BasicClientCookie(STORMWALL_COOKIE_NAME, stormwallCookieValue);
                c.setDomain(stormwallCookieDomain);
                httpClient.getCookieStore().addCookie(c);
            }
            
        }
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        super.saveCookie(cookie);
        if (cookie != null) {
            if (canStormwall() && cookie.getName().equals(STORMWALL_COOKIE_NAME)) {
                preferences.edit().
                        putString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_VALUE), cookie.getValue()).
                        putString(getSharedKey(PREF_KEY_STORMWALL_COOKIE_DOMAIN), cookie.getDomain()).commit();
            }
        }
    }

    @Override
    protected CaptchaModel downloadCaptcha(String captchaUrl, ProgressListener listener, CancellableTask task) throws Exception {
        checkForStormwall(captchaUrl);
        return super.downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        checkForStormwall(url);
        super.downloadFile(url, out, listener, task);
    }
    
    @Override
    protected JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        checkForStormwall(url);
        return super.downloadJSONObject(url, checkIfModidied, listener, task);
    }
    
    @Override
    protected JSONArray downloadJSONArray(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        checkForStormwall(url);
        return super.downloadJSONArray(url, checkIfModidied, listener, task);
    }

    protected void checkForStormwall(String url) throws InteractiveException {
        if (!canStormwall()) {
            return;
        }
        HttpResponseModel responseModel = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setOPTIONS().build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, null, null);
        } catch (HttpRequestException e) {
            return;
        }
        checkForStormwall(url, responseModel);
    }
    
    protected void checkForStormwall(String url, HttpResponseModel responseModel) throws InteractiveException {
        if (!canStormwall()) {
            return;
        }
        boolean firewall = false;
        for (Header header: responseModel.headers) {
            if (header.getName().equalsIgnoreCase(STORMWALL_FIREWALL_HEADER_NAME) &&
                    header.getValue().equalsIgnoreCase(STORMWALL_FIREWALL_TRUE_VALUE)) {
                firewall = true;
                break;
            }
        }
        if (!firewall) {
            return;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        try {
            IOUtils.copyStream(responseModel.stream, output);
        } catch (IOException e) {
            return;
        }
        String html = new String(output.toByteArray());
        StormwallException e = StormwallException.withRecaptcha(url, html, getChanName());
        if (e == null) e = StormwallException.antiDDOS(url, html, getChanName());
        throw e;
    }
}

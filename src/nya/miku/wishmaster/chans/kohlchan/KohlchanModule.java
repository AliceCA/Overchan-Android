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

package nya.miku.wishmaster.chans.kohlchan;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractLynxChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.MimeTypes;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

/**
 * Class interacting with Kohlchan imageboard (Lynxchan engine version 2)
 */

public class KohlchanModule extends AbstractLynxChanModule {
    private static final String TAG = "KohlchanModule";
    
    static final String CHAN_NAME = "kohlchan.net";
    private static final String DISPLAYING_NAME = "Kohlchan";
    private static final String DEFAULT_DOMAIN = "kohlchan.net";
    private static final String PREF_KEY_DOMAIN = "domain";
    private static final List<String> DOMAINS_LIST = Arrays.asList(
            DEFAULT_DOMAIN, "kohlchan.mett.ru", "kohlchankxguym67.onion", "fastkohlp6h2seef.onion",
            "kohlchan7cwtdwfuicqhxgqx4k47bsvlt2wn5eduzovntrzvonv4cqyd.onion",
            "fastkohlt5rxcxtl5no7k3efmahlt7mafry7be6yvxdovekhq2hdnwqd.onion");
    private static final String DOMAINS_HINT = "kohlchan.net, kohlchan.mett.ru, kohlchankxguym67.onion, fastkohlp6h2seef.onion";
    
    private static final String[] ATTACHMENT_FORMATS = new String[] {
            "jpg", "jpeg", "bmp", "gif", "png", "webp", "mp3", "ogg", "flac", "opus", "webm", "mp4",
            "7z", "zip", "pdf", "epub", "txt" };
    private static final Pattern INVALID_LESSER_THAN_PATTERN = Pattern.compile("&lt([^;])");
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\n");
    
    private String domain;
    private Map<String, String> captchas = new HashMap<>();
    private String reportCaptchaAnswer = null;

    public KohlchanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    protected void initHttpClient() {
        updateDomain(preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN));
        
        BasicClientCookie cookieConsent = new BasicClientCookie("cookieConsent", "true");
        cookieConsent.setDomain(getUsingDomain());
        cookieConsent.setPath("/");
        httpClient.getCookieStore().addCookie(cookieConsent);
        
        loadBypassCookie();
    }

    @Override
    protected String getUsingDomain() {
        return domain;
    }

    private void addDomainPreferences(PreferenceGroup group) {
        Context context = group.getContext();
        Preference.OnPreferenceChangeListener updateDomainListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference.getKey().equals(getSharedKey(PREF_KEY_DOMAIN))) {
                    updateDomain((String) newValue);
                    return true;
                }
                return false;
            }
        };
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domainPref.setOnPreferenceChangeListener(updateDomainListener);
        group.addPreference(domainPref);
    }

    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addDomainPreferences(preferenceGroup);
        super.addPreferencesOnScreen(preferenceGroup);
    }

    @Override
    protected String[] getAllDomains() {
        String curDomain = getUsingDomain();
        String[] domains;
        if (DOMAINS_LIST.contains(curDomain)) {
            domains = DOMAINS_LIST.toArray(new String[DOMAINS_LIST.size()]);
        } else {
            domains = DOMAINS_LIST.toArray(new String[DOMAINS_LIST.size() + 1]);
            domains[DOMAINS_LIST.size()] = curDomain;
        }
        return domains;
    }

    private void updateDomain(String domain) {
        if (domain.endsWith("/")) domain = domain.substring(0, domain.length() - 1);
        if (domain.contains("//")) domain = domain.substring(domain.indexOf("//") + 2);
        if (domain.equals("")) domain = DEFAULT_DOMAIN;
        this.domain = domain;
    }
    
    @Override
    public String getDisplayingName() {
        return DISPLAYING_NAME;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_kohlchan, null);
    }

    private void saveBypassCookie() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
            if (cookie.getName().equals(BYPASS_COOKIE_NAME) && cookie.getDomain().contains(getUsingDomain())) {
                preferences.edit().putString(getSharedKey(PREF_KEY_BYPASS_COOKIE), cookie.getValue()).commit();
            }
        }
    }

    private void loadBypassCookie() {
        String bypassCookie = preferences.getString(getSharedKey(PREF_KEY_BYPASS_COOKIE), null);
        if (bypassCookie != null) {
            BasicClientCookie c = new BasicClientCookie(BYPASS_COOKIE_NAME, bypassCookie);
            c.setDomain(getUsingDomain());
            c.setPath("/");
            httpClient.getCookieStore().addCookie(c);
        }
    }

    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + ".static/pages/sidebar.html";

        HttpResponseModel responseModel = null;
        KohlBoardsListReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(oldBoardsList != null).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new KohlBoardsListReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readBoardsList();
            } else {
                if (responseModel.notModified()) return oldBoardsList;
                byte[] html = null;
                try {
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                    IOUtils.copyStream(responseModel.stream, byteStream);
                    html = byteStream.toByteArray();
                } catch (Exception e) {}
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason, html);
            }
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.defaultUserName = "Bernd";
        model.allowEmails = false;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }

    @Override
    protected PostModel mapPostModel(JSONObject object) {
        PostModel model = super.mapPostModel(object);
        model.comment = RegexUtils.replaceAll(model.comment, INVALID_LESSER_THAN_PATTERN, "&lt;$1");
        model.comment = RegexUtils.replaceAll(model.comment, LINE_BREAK_PATTERN, "<br/>");
        return model;
    }

    void putCaptcha(String captchaID, String answer) {
        if (captchas == null) captchas = new HashMap<>();
        captchas.put(captchaID, answer);
    }

    private boolean validateCaptcha(String captchaAnswer, ProgressListener listener, CancellableTask task) throws Exception {
        if (lastCaptchaId == null) return false;

        String url = getUsingUrl() + "/solveCaptcha.js";

        ExtendedMultipartBuilder multipartBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("captchaId", lastCaptchaId).
                addString("answer", captchaAnswer);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(multipartBuilder.build()).build();
        String response;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        JSONObject result = new JSONObject(response);
        String status = result.optString("status");
        if ("ok".equals(status)) {
            return true;
        } else if (status.equals("error")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        }
        throw new Exception("Unknown Error");
    }

    private void renewBypass(String captchaAnswer, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "renewBypass.js?json=1";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("captcha", captchaAnswer);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        JSONObject response;
        try {
            response = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        switch (response.optString("status")) {
            case "failed":
            case "new":
            case "next":
                throw new KohlchanCaptchaException();
            case "finish":
                saveBypassCookie();
                return;
            case "error":
                throw new Exception(response.optString("data", "Captcha Error"));
            default: throw new Exception("Unknown Error");
        }
    }

    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (!captchas.isEmpty()) {
            String captchaId = captchas.keySet().iterator().next();
            String captchaAnswer = captchas.remove(captchaId);
            if (captchaAnswer == null) captchaAnswer = "";
            renewBypass(captchaAnswer, listener, task);
            if (model.captchaAnswer.length() > 0) {
                throw new Exception("You have a valid block bypass.");
            }
        }

        boolean captchaSolved = false;
        if (model.captchaAnswer.length() > 0) {
            captchaSolved = validateCaptcha(model.captchaAnswer, listener, task);
        }
        
        String url = getUsingUrl() + (model.threadNumber == null ? "newThread.js?json=1" : "replyThread.js?json=1");
        
        if (model.password.length() > MAX_PASSWORD_LENGTH) {
            model.password = model.password.substring(0, MAX_PASSWORD_LENGTH);
        }
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                setCharset(Charset.forName("UTF-8")).
                addString("name", model.name).
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("password", model.password).
                addString("boardUri", model.boardName);
        if (model.sage) postEntityBuilder.addString("sage", "true");
        if (model.threadNumber != null) postEntityBuilder.addString("threadId", model.threadNumber);
        if (captchaSolved) postEntityBuilder.addString("captcha", model.captchaAnswer);
        if (model.custommark) postEntityBuilder.addString("spoiler", "true");
        if (model.attachments != null && model.attachments.length > 0) {
            for (int i = 0; i < model.attachments.length; ++i) {
                String name = model.attachments[i].getName();
                String mime = MimeTypes.forExtension(name.substring(name.lastIndexOf('.') + 1));
                if (mime == null) throw new Exception("Unknown file type of " + name);
                String md5;
                try {
                    md5 = computeFileMD5(model.attachments[i]);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new Exception("Cannot attach file " + name);
                }
                postEntityBuilder.
                        addString("fileMd5", md5).
                        addString("fileMime", mime).
                        addString("fileSpoiler", "").
                        addString("fileName", name);
                boolean fileExists = checkFileIdentifier(md5, mime, listener, task);
                if (!fileExists) {
                    postEntityBuilder.addFile("files", model.attachments[i], mime, model.randomHash);
                }
            }
        }
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        JSONObject result = new JSONObject(response);
        String status = result.optString("status");
        if ("ok".equals(status)) {
            UrlPageModel urlPageModel = new UrlPageModel();
            urlPageModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlPageModel.chanName = getChanName();
            urlPageModel.boardName = model.boardName;
            urlPageModel.threadNumber = model.threadNumber;
            if (model.threadNumber == null) {
                urlPageModel.threadNumber = Integer.toString(result.optInt("data"));
            } else {
                urlPageModel.postNumber = Integer.toString(result.optInt("data"));
            }
            return buildUrl(urlPageModel);
        } else if (status.contains("error") || status.contains("blank")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        } else if ("bypassable".equals(status)) {
            throw new KohlchanCaptchaException();
        } else if("banned".equals(status)) {
            String banMessage = "You have been banned!";
            try {
                banMessage += "\nReason: " + result.getJSONObject("data").getString("reason");
            } catch (Exception e) { }
            throw new Exception(banMessage);
        }
        throw new Exception("Unknown Error");
    }

    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "contentActions.js?json=1";

        if (model.password.length() > MAX_PASSWORD_LENGTH) {
            model.password = model.password.substring(0, MAX_PASSWORD_LENGTH);
        }
        ExtendedMultipartBuilder multipartBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("action", "delete").
                addString("password", model.password).
                addString("deleteMedia", "true"). /* Unnecessary. Only mods can remove files from server */
                addString(model.boardName + "-" + model.threadNumber
                        + (model.threadNumber.equals(model.postNumber) ? "" : ("-" + model.postNumber)), "true");
        if (model.onlyFiles) {
            multipartBuilder.addString("deleteUploads", "true");
        }
        HttpRequestModel request = HttpRequestModel.builder().setPOST(multipartBuilder.build()).build();
        String response;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        JSONObject result = new JSONObject(response);
        String status = result.optString("status");
        if ("ok".equals(status)) {
            try {
                JSONObject data = result.getJSONObject("data");
                int removedCount = data.getInt("removedPosts") + data.getInt("removedThreads");
                if (removedCount == 0) throw new Exception("Nothing was removed");
                else if (removedCount > 0) return null;
            } catch (JSONException e) {
                Logger.e(TAG, "Incorrect delete content result");
            }
        } else if (status.contains("error")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        }
        throw new Exception("Unknown Error");
    }

    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (reportCaptchaAnswer == null) {
            throw new KohlchanCaptchaException() {
                @Override
                protected void storeResponse(String response) {
                    reportCaptchaAnswer = response;
                }
            };
        }

        String url = getUsingUrl() + "contentActions.js?json=1";

        ExtendedMultipartBuilder multipartBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("action", "report").
                addString("reason", model.reportReason).
                addString("captcha", reportCaptchaAnswer).
                addString(model.boardName + "-" + model.threadNumber
                        + (model.threadNumber.equals(model.postNumber) ? "" : "-" + model.postNumber), "true");
        reportCaptchaAnswer = null;
        HttpRequestModel request = HttpRequestModel.builder().setPOST(multipartBuilder.build()).build();
        String response;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        JSONObject result = new JSONObject(response);
        String status = result.optString("status");
        if ("ok".equals(status)) {
            return null;
        } else if (status.equals("error")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        }
        throw new Exception("Unknown Error");
    }
}

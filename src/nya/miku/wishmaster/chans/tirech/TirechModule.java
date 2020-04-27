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

package nya.miku.wishmaster.chans.tirech;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractWakabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class TirechModule extends AbstractWakabaModule {
    private static final String CHAN_NAME = "2--ch.ru";
    private static final String DEFAULT_DOMAIN = "2--ch.ru";
    private static final String DOMAINS_HINT = "2--ch.ru, 2-ch.su";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, "2-ch.su" };
    
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Работа сайта", "Обсуждения", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бред", "Обсуждения", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bg", "Настольные игры", "Общее", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hb", "Хобби", "Общее", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wr", "Графомания", "Общее", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме", "Японская культура", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "to", "Touhou", "Японская культура", false)
    };
    
    public TirechModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Тире.ч";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_dvach, null);
    }
    
    @Override
    protected String getUsingDomain() {
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        String domain = getUsingDomain();
        for (String d : DOMAINS) if (domain.equals(d)) return DOMAINS;
        String[] domains = new String[DOMAINS.length + 1];
        for (int i=0; i<DOMAINS.length; ++i) domains[i] = DOMAINS[i];
        domains[DOMAINS.length] = domain;
        return domains;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected boolean wakabaNoRedirect() {
        return true;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        preferenceGroup.addPreference(domainPref);
        addHttpsPreference(preferenceGroup, true);
        addPasswordPreference(preferenceGroup);
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId =  "GMT+3";
        model.defaultUserName = "Аноним";
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = false;
        model.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.customMarkDescription = "Прикрепить капчу к посту";
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new TirechReader(stream, canCloudflare());
    }
    
    @Override
    protected ThreadModel[] readWakabaPage(String url, ProgressListener listener, CancellableTask task, boolean checkModified, UrlPageModel urlModel)
            throws Exception {
        try {
            return super.readWakabaPage(url, listener, task, checkModified, urlModel);
        } catch (HttpWrongStatusCodeException e) {
            if (e.getStatusCode() == 302) throw new HttpWrongStatusCodeException(404, "404 - Not Found");
            throw e;
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + boardName + "/captcha.fpl?" + Long.toString(Math.round(Math.random() * 1000000));
        HttpRequestModel request = HttpRequestModel.builder().setGET().setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(captchaUrl, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        if (header.getValue().contains("/nocap")) {
                            return null;
                        } else {
                            return downloadCaptcha(fixRelativeUrl(header.getValue()), listener, task);
                        }
                    }
                }
            }
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/post.fpl";
        
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task);
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        postEntityBuilder.
                addString("task", "post").
                addString("name", model.name).
                addString("email", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("comment", model.comment).
                addString("captcha", model.captchaAnswer).
                addString("password", model.password);
        if (model.custommark) postEntityBuilder.addString("addcaptcha", "on");
        if (model.attachments != null && model.attachments.length > 0) {
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        }
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 303) {
                return null;
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                
                JSONObject jsonResponse = new JSONObject(htmlResponse);
                if (jsonResponse.has("error")) {
                    String errorMessage = jsonResponse.getJSONObject("error").optString("text", "Ошибка");
                    throw new Exception(errorMessage.replace("<br/?>", "\n"));
                }
            } else {
                throw new Exception(response.statusCode + " - " + response.statusReason);
            }
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/delete.fpl";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("delete", model.postNumber));
        pairs.add(new BasicNameValuePair("password", model.password));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        try {
            String htmlResponse = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
            if (htmlResponse.contains("Неверный пароль")) {
                throw new Exception("Неверный пароль");
            }
        } catch (HttpWrongStatusCodeException e) {
            if (e.getStatusCode() == 302) {
                return null;
            }
            throw e;
        }
        return null;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        String url = super.buildUrl(model);
        if (model.type == UrlPageModel.TYPE_BOARDPAGE) {
            if (model.boardPage == 0) url += "0.html";
            url = url.replace(".html", ".memhtml");
        }
        return url;
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return super.parseUrl(url.replace(".memhtml", ".html"));
    }
    
}

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

package nya.miku.wishmaster.chans.synch;

import java.util.List;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.Charset;
import java.io.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.ListPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.entity.mime.content.ByteArrayBody;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public class SynchModule extends AbstractVichanModule {
    
    private static final String TAG = "SynchModule";
    private static final String CHAN_NAME = "syn-ch";
    private static final String DOMAINS_HINT = "syn-ch.com, syn-ch.org, syn-ch.ru, syn-ch.com.ua";
    private static final String[] DOMAINS = new String[] { "syn-ch.com", "syn-ch.org", "syn-ch.ru", "syn-ch.com.ua" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бардак", "Основные", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Майдан", "Основные", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аниме", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "anon", "Anonymous", "Тематические", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mlp", "My Little Pony", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Музыка", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "po", "Politics", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r34", "Rule 34", "Тематические", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Кино и сериалы", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Видеоигры", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "diy", "Хобби", "Тематические", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "old", "Чулан", "Остальные", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "test", "Старая школа", "Остальные", false),
    };
    private static final String[] BOARDS_WITH_CAPTCHA = new String[] {};
    private static final String[] ATTACHMENT_FORMATS = new String[] {
        "jpg", "png", "bmp", "svg", "swf", "mp3", "m4a", "flac", "zip", "rar", "tar", "gz", "txt", "pdf", "torrent", "webm"
    };
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    private static final String[] ATTACHMENT_KEYS = new String[] { "file", "file2", "file3", "file4", "file5" };
    private static final String RECAPTCHA_PUBLIC_KEY = "6LfDqP4SAAAAAH8k-y82VXfSMfFF8pfU9sasuR5I";
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");
    
    public SynchModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Син.ч";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_synch, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DOMAINS[0]);
    }
    
    @Override
    protected String[] getAllDomains() {
        return DOMAINS;
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
    protected boolean useHttpsDefaultValue() {
        return true;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        ListPreference domainPref = new LazyPreferences.ListPreference(context);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setEntries(DOMAINS);
        domainPref.setEntryValues(DOMAINS);
        domainPref.setDefaultValue(DOMAINS[0]);
        preferenceGroup.addPreference(domainPref);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Аноним";
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel model = super.mapAttachment(object, boardName, isSpoiler);
        if (model != null) {
            if (model.type == AttachmentModel.TYPE_VIDEO) model.thumbnail = null;
            if (model.thumbnail != null) model.thumbnail = model.thumbnail.replace("//", "/").replaceAll("^/\\w+", "");
            if (model.path != null) model.path = model.path.replace("//", "/").replaceAll("^/\\w+", "");
        }
        return model;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Пожаловаться";
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("/src/") | url.startsWith("/thumb/")) return (useHttps() ? "https://" : "http://") + "cdn." + getUsingDomain() + url;
        return super.fixRelativeUrl(url);
    }

    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        return null;
    }

    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = getChanName();
        urlModel.boardName = model.boardName;
        if (model.threadNumber == null) {
            urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
            urlModel.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
        } else {
            urlModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlModel.threadNumber = model.threadNumber;
        }
        String referer = buildUrl(urlModel);
        List<Pair<String, String>> fields = AbstractVichanModule.VichanAntiBot.getFormValues(referer, task, httpClient);

        if (task != null && task.isCancelled()) throw new Exception("interrupted");

        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().
                setCharset(Charset.forName("UTF-8")).setDelegates(listener, task);

        if (Arrays.asList(BOARDS_WITH_CAPTCHA).contains(model.boardName)) {
            String recaptchaResponse = Recaptcha2solved.pop(RECAPTCHA_PUBLIC_KEY);
            if (recaptchaResponse == null) {
                throw Recaptcha2.obtain(getUsingUrl(), RECAPTCHA_PUBLIC_KEY, null, CHAN_NAME, false);
            }
            postEntityBuilder.addString("g-recaptcha-response", recaptchaResponse);
        }

        for (Pair<String, String> pair : fields) {
            if (pair.getKey().equals("spoiler") && !model.custommark) continue;
            String val;
            switch (pair.getKey()) {
                case "name": val = model.name; break;
                case "email": val = getSendPostEmail(model); break;
                case "subject": val = model.subject; break;
                case "body": val = model.comment; break;
                case "password": val = model.password; break;
                case "spoiler": val = "on"; break;
                default: val = pair.getValue();
            }
            if (pair.getKey().equals("file")) {
                if (model.attachments != null && model.attachments.length > 0) {
                    for (int i=0; i<model.attachments.length && i<ATTACHMENT_KEYS.length; ++i) {
                        postEntityBuilder.addFile(ATTACHMENT_KEYS[i], model.attachments[i], model.randomHash);
                    }
                } else {
                    postEntityBuilder.addPart(pair.getKey(), new ByteArrayBody(new byte[0], ""));
                }
            } else {
                postEntityBuilder.addString(pair.getKey(), val);
            }
        }

        String url = getUsingUrl() + "post.php";
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, referer) };
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, listener, task);
            if (response.statusCode == 200 || response.statusCode == 400) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
            } else if (response.statusCode == 303) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
}

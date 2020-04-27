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

package nya.miku.wishmaster.chans.kropyvach;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;

import org.apache.commons.lang3.tuple.Pair;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.entity.mime.content.ByteArrayBody;
import cz.msebera.android.httpclient.message.BasicHeader;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class KropyvachModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "kropyva.ch";
    private static final String DEFAULT_DOMAIN = "www.kropyva.ch";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, "kropyva.ch" };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аніме", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Балачки", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bugs", "Зауваження", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Кіно", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Фап", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "Відеоігри", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "i", "International", "", false), 
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "k", "Кропиватика", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "l", "Література", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "m", "Музика", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "p", "Політика", "", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Технології", "", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "u", "Українська мова", "", false),
    };
    
    private static final String[] ATTACHMENT_KEYS = new String[] { "file", "file2", "file3", "file4", "file5" };
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    public KropyvachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Кропивач";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_uchan, null);
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
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
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        preferenceGroup.addPreference(domainPref);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.bumpLimit = 250;
        model.allowCustomMark = true;
        model.attachmentsMaxCount = 4;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel attachment = super.mapAttachment(object, boardName, isSpoiler);
        if (attachment != null && attachment.thumbnail != null) {
            switch (attachment.type) {
                case AttachmentModel.TYPE_VIDEO:
                case AttachmentModel.TYPE_IMAGE_STATIC:
                case AttachmentModel.TYPE_IMAGE_GIF:
                case AttachmentModel.TYPE_IMAGE_SVG:
                attachment.thumbnail = attachment.thumbnail.substring(0, attachment.thumbnail.lastIndexOf('.')) + ".png";
                break;
            }
        }
        return attachment;
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
        String url = buildUrl(urlModel);
        List<Pair<String, String>> fields = VichanAntiBot.getFormValues(url, task, httpClient);

        if (task != null && task.isCancelled()) throw new Exception("interrupted");

        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().
                setCharset(Charset.forName("UTF-8")).setDelegates(listener, task);

        String key;
        for (Pair<String, String> pair : fields) {
            key = pair.getKey();
            if (key.equals("op_stickers") && !model.custommark) continue;
            String val;
            switch (key) {
                case "name": val = model.name; break;
                case "email": val = getSendPostEmail(model); break;
                case "subject": val = model.subject; break;
                case "body": val = model.comment; break;
                case "password": val = model.password; break;
                default: val = pair.getValue();
            }
            if (key.equals("file")) {
                if (model.attachments != null && model.attachments.length > 0) {
                    for (int i=0; i<model.attachments.length; ++i) {
                        postEntityBuilder.addFile(ATTACHMENT_KEYS[i], model.attachments[i], model.randomHash);
                    }
                } else {
                    postEntityBuilder.addPart(key, new ByteArrayBody(new byte[0], ""));
                }
            } else {
                postEntityBuilder.addString(key, val);
            }
        }
        postEntityBuilder.addString("json_response", "1");

        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, url) };
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        JSONObject json = HttpStreamer.getInstance().getJSONObjectFromUrl(url, request, httpClient, listener, task, false);
        if (json.has("error")) {
            String errorMessage = json.optString("error", "Unknown Error");
            throw new Exception(errorMessage);
        } else {
            String id = json.optString("id", "");
            if (!id.equals("")) {
                urlModel = new UrlPageModel();
                urlModel.chanName = getChanName();
                urlModel.type = UrlPageModel.TYPE_THREADPAGE;
                urlModel.boardName = model.boardName;
                if (model.threadNumber == null) {
                    urlModel.threadNumber = id;
                } else {
                    urlModel.threadNumber = model.threadNumber;
                    urlModel.postNumber = id;
                }
                return buildUrl(urlModel);
            }
        }
        return null;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Видалити";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Поскаржитися";
    }

    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList) throws Exception {
        PostModel[] result = super.getPostsList(boardName, threadNumber, listener, task, oldList);
        Arrays.sort(result, new Comparator<PostModel>() {
            @Override
            public int compare(PostModel p1, PostModel p2) {
                int n1 = Integer.parseInt(p1.number);
                int n2 = Integer.parseInt(p2.number);
                return (n1 == n2) ? 0 : (n1 > n2) ? 1 : -1;
            }
        });
        return result;
    }

    @Override
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel result = super.mapPostModel(object, boardName);
        result.comment = result.comment.replaceAll("(<a [^>]*href=\"[^#\"]*)(\"[^>]*>&gt;&gt;([0-9]*)</a>)", "$1#$3$2");
        return result;
    }
}

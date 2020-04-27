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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.text.InputFilter;
import android.text.InputType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.MimeTypes;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public abstract class AbstractLynxChanModule extends AbstractWakabaModule {
    private static final String TAG = "AbstractLynxChanModule";
    private static final DateFormat CHAN_DATEFORMAT;
    static {
        CHAN_DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        CHAN_DATEFORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    private static DateFormat COOKIE_DATEFORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    private static final Pattern MIME_TYPE_PATTERN = Pattern.compile("-(application|audio|image|text|video)(.+)");
    private static final Pattern RED_TEXT_MARK_PATTERN = Pattern.compile("<span class=\"redText\">(.*?)</span>");
    private static final Pattern ORANGE_TEXT_MARK_PATTERN = Pattern.compile("<span class=\"orangeText\">(.*?)</span>");
    private static final Pattern GREEN_TEXT_MARK_PATTERN = Pattern.compile("<span class=\"greenText\">(.*?)</span>");
    private static final Pattern REPLY_NUMBER_PATTERN = Pattern.compile("&gt&gt(\\d+)");
    
    private static final String CAPTCHAID_COOKIE_NAME = "captchaid";
    private static final String CAPTCHAEXPIRATION_COOKIE_NAME = "captchaexpiration";
    
    protected static final String PREF_KEY_BYPASS_COOKIE = "PREF_KEY_BYPASS_COOKIE";
    protected static final String BYPASS_COOKIE_NAME = "bypass";
    
    private static final int MODE_NO_CAPTCHA = 0;
    private static final int MODE_THREAD_CAPTCHA = 1;
    private static final int MODE_POST_CAPTCHA = 2;
    
    public static final int MAX_PASSWORD_LENGTH = 8;
    
    protected Map<String, BoardModel> boardsMap = null;
    protected Map<String, Integer> captchaModesMap = null;
    protected Map<String, ArrayList<String>> flagsMap = null;
    protected String lastCaptchaId = null;
    protected String lastCaptchaAnswer = null;

    public AbstractLynxChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    public String getDefaultPassword() {
        String curPass;
        if (!preferences.contains(getSharedKey(PREF_KEY_PASSWORD))) {
            curPass = CryptoUtils.genPassword(MAX_PASSWORD_LENGTH);
        } else {
            curPass = preferences.getString(getSharedKey(PREF_KEY_PASSWORD), "");
            if (curPass.length() > MAX_PASSWORD_LENGTH) {
                curPass = curPass.substring(0, MAX_PASSWORD_LENGTH);
            }
        }
        preferences.edit().putString(getSharedKey(PREF_KEY_PASSWORD), curPass).commit();
        return curPass;
    }

    @Override
    protected void addPasswordPreference(PreferenceGroup group) {
        final Context context = group.getContext();
        EditTextPreference passwordPref = new EditTextPreference(context) {
            @Override
            protected void showDialog(Bundle state) {
                setText(getDefaultPassword());
                super.showDialog(state);
            }
        };
        passwordPref.setTitle(R.string.pref_password_title);
        passwordPref.setDialogTitle(R.string.pref_password_title);
        passwordPref.setSummary(R.string.pref_password_summary);
        passwordPref.setKey(getSharedKey(PREF_KEY_PASSWORD));
        passwordPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        passwordPref.getEditText().setSingleLine();
        passwordPref.getEditText().setFilters(new InputFilter[] { new InputFilter.LengthFilter(MAX_PASSWORD_LENGTH) });
        group.addPreference(passwordPref);
    }

    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "boards.js?json=1";
        List<SimpleBoardModel> list = new ArrayList<SimpleBoardModel>();
        JSONObject response = downloadJSONObject(url, (oldBoardsList != null && boardsMap != null), listener, task);
        if (response == null) return oldBoardsList;
        String status = response.optString("status");  //since Lynxchan v2.2
        if ("ok".equals(status)) {
            response = response.getJSONObject("data");
        } else if ("error".equals(status)) {
            throw new Exception(response.optString("data"));
        }
        JSONArray boards = response.getJSONArray("boards");
        SimpleBoardModel model;
        for (int i = 0, len = boards.length(); i < len; ++i) {
            try {
                model = new SimpleBoardModel();
                model.chan = getChanName();
                model.boardName = boards.getJSONObject(i).getString("boardUri");
                model.boardDescription = boards.getJSONObject(i).optString("boardName", model.boardName);
                try {
                    String settings = boards.getJSONObject(i).getJSONArray("specialSettings").toString();
                    model.nsfw = !settings.contains("\"sfw\"");
                } catch (Exception e) {
                    model.nsfw = true;
                }
                list.add(model);
            } catch (Exception e) {
                Logger.e(TAG, "Incorrect element in boards list");
            }
        }
        return list.toArray(new SimpleBoardModel[list.size()]);
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap == null) {
            boardsMap = new HashMap<>();
        }
        if (captchaModesMap == null) {
            captchaModesMap = new HashMap<>();
        }
        if (boardsMap.containsKey(shortName) && captchaModesMap.containsKey(shortName)) {
            return boardsMap.get(shortName);
        } else {
            String url = getUsingUrl() + shortName + "/1.json";
            JSONObject boardJson;
            try {
                boardJson = downloadJSONObject(url, false, listener, task);
            } catch (Exception e) {
                boardJson = new JSONObject();
            }
            BoardModel model = mapBoardModel(boardJson, shortName);
            boardsMap.put(model.boardName, model);
            return model;
        }
    }

    protected BoardModel mapBoardModel(JSONObject json, String shortName) {
        BoardModel model = getDefaultBoardModel(shortName);
        model.boardDescription = json.optString("boardName", model.boardName);
        model.attachmentsMaxCount = json.optInt("maxFileCount", 5);
        model.lastPage = json.optInt("pageCount", BoardModel.LAST_PAGE_UNDEFINED);
        
        JSONArray settingsJson = json.optJSONArray("settings");
        ArrayList<String> settings = new ArrayList<String>();
        if (settingsJson != null) {
            for(int i = 0, len = settingsJson.length(); i < len; ++i) settings.add(settingsJson.getString(i));
        }
        model.allowNames = !settings.contains("forceAnonymity");
        model.allowDeletePosts = !settings.contains("blockDeletion");
        model.allowDeleteFiles = model.allowDeletePosts;
        model.requiredFileForNewThread = settings.contains("requireThreadFile");
        model.allowRandomHash = settings.contains("uniqueFiles");
        model.uniqueAttachmentNames = false;  //spoiler thumbs have same urls in different imageboards
        model.attachmentsMaxCount = settings.contains("textBoard") ? 0 : model.attachmentsMaxCount;
        try {
            JSONArray flags = json.getJSONArray("flagData");
            if (flags.length() > 0) {
                String[] icons = new String[flags.length() + 1];
                icons[0] = "No flag";
                if (flagsMap == null) flagsMap = new HashMap<String, ArrayList<String>>();
                ArrayList<String> boardFlagIds = new ArrayList<String>();
                for(int i = 0, len = flags.length(); i < len; ++i) {
                    boardFlagIds.add(flags.getJSONObject(i).getString("_id"));
                    icons[i + 1] = flags.getJSONObject(i).getString("name");
                }
                flagsMap.put(model.boardName, boardFlagIds);
                model.allowIcons = true;
                model.iconDescriptions = icons;
            }
        } catch (Exception e) {}
        if (captchaModesMap == null) {
            captchaModesMap = new HashMap<>();
        }
        switch (json.optInt("captchaMode", -1)) {
            case 0: captchaModesMap.put(model.boardName, MODE_NO_CAPTCHA); break;
            case 1: captchaModesMap.put(model.boardName, MODE_THREAD_CAPTCHA); break;
            case 2: captchaModesMap.put(model.boardName, MODE_POST_CAPTCHA); break;
            default: captchaModesMap.put(model.boardName, MODE_NO_CAPTCHA);
        }
        return model;
    }

    private BoardModel getDefaultBoardModel(String shortName) {
        BoardModel board = new BoardModel();
        board.timeZoneId = "UTC";
        board.defaultUserName = "Anonymous";
        board.readonlyBoard = false;
        board.requiredFileForNewThread = true;
        board.allowDeletePosts = true;
        board.allowDeleteFiles = true;
        board.allowReport = BoardModel.REPORT_WITH_COMMENT;
        board.allowNames = true;
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowCustomMark = true;
        board.customMarkDescription = "Spoiler";
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = null;
        board.markType = BoardModel.MARK_INFINITY;
        board.firstPage = 1;
        board.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        board.catalogAllowed = true;
        board.boardName = shortName;
        board.bumpLimit = 500;
        board.chan = getChanName();
        return board;
    }

    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/" + page + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        try {
            BoardModel board = mapBoardModel(response, boardName);
            if (boardsMap == null) boardsMap = new HashMap<>();
            boardsMap.put(boardName, board);
        } catch (Exception e) {}
        JSONArray threads = response.getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i = 0, len = threads.length(); i < len; ++i) {
            JSONArray posts = threads.getJSONObject(i).getJSONArray("posts");
            JSONObject thread = threads.getJSONObject(i);
            ThreadModel curThread = mapThreadModel(thread);
            curThread.posts = new PostModel[posts.length() + 1];
            curThread.postsCount += curThread.posts.length;
            curThread.posts[0] = mapPostModel(thread);
            curThread.threadNumber = curThread.posts[0].number;
            curThread.posts[0].parentThread = curThread.threadNumber;
            for (int j = 0, plen = posts.length(); j < plen; ++j) {
                curThread.posts[j + 1] = mapPostModel(posts.getJSONObject(j));
                curThread.posts[j + 1].parentThread = curThread.threadNumber;
            }
            result[i] = curThread;
        }
        return result;
    }

    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/res/" + threadNumber + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        JSONArray posts = response.getJSONArray("posts");
        PostModel[] result = new PostModel[posts.length() + 1];
        result[0] = mapPostModel(response);
        for (int i = 0, len = posts.length(); i < len; ++i) {
            result[i + 1] = mapPostModel(posts.getJSONObject(i));
            result[i + 1].parentThread = result[0].number;
        }
        if (oldList != null) {
            result = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(result));
        }
        return result;
    }
    
    @Override
    public ThreadModel[] getCatalog(
            String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception {
        String url = getUsingUrl() + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList;
        ThreadModel[] result = new ThreadModel[response.length()];
        for (int i = 0, len = response.length(); i < len; ++i) {
            result[i] = mapCatalogThreadModel(response.getJSONObject(i));
        }
        return result;
    }

    private ThreadModel mapCatalogThreadModel(JSONObject object) {
        ThreadModel model = mapThreadModel(object);
        model.postsCount = object.optInt("postCount") + 1;
        model.attachmentsCount = object.optInt("fileCount", -1);
        PostModel post = mapPostModel(object);
        post.number = model.threadNumber;
        post.parentThread = model.threadNumber;
        String thumb = object.optString("thumb", "");
        if (thumb.length() > 0) {
            AttachmentModel attachment = new AttachmentModel();
            attachment.thumbnail = thumb;
            Matcher mimeMatcher = MIME_TYPE_PATTERN.matcher(thumb);
            if (mimeMatcher.find()) {
                String mime = mimeMatcher.group(1) + "/" + mimeMatcher.group(2);
                attachment.type = getAttachmentType(mime);
                String ext = MimeTypes.toExtension(mime);
                attachment.path = thumb.replace("t_", "")
                        + (ext != null ? "." + ext : "");
            } else { //Internal image (e.g. spoiler)
                attachment.thumbnail = fixRelativeUrl(thumb); //fix equal hashes in cache
                attachment.path = attachment.thumbnail;
            }
            attachment.height = -1;
            attachment.width = -1;
            attachment.size = -1;
            post.attachments = new AttachmentModel[] { attachment };
        }
        model.posts = new PostModel[] { post };
        return model;
    }
    
    private ThreadModel mapThreadModel(JSONObject object) {
        ThreadModel model = new ThreadModel();
        model.threadNumber = Integer.toString(object.optInt("threadId"));
        model.isSticky = object.optBoolean("pinned", false);
        model.isClosed = object.optBoolean("locked", false);
        model.isCyclical = object.optBoolean("cyclic", false);
        model.postsCount = object.optInt("ommitedPosts");
        return model;
    }

    protected PostModel mapPostModel(JSONObject object) {
        PostModel model = new PostModel();
        try {
            model.timestamp = CHAN_DATEFORMAT.parse(object.optString("creation")).getTime();
        } catch (ParseException e) {
            Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
        }
        model.name = StringEscapeUtils.unescapeHtml4(object.optString("name")).replace("&apos;", "'");
        model.email = StringEscapeUtils.unescapeHtml4(object.optString("email")).replace("&apos;", "'");
        model.subject = StringEscapeUtils.unescapeHtml4(object.optString("subject")).replace("&apos;", "'");
        model.comment = object.optString("markdown", object.optString("message"));
        model.comment = RegexUtils.replaceAll(model.comment, RED_TEXT_MARK_PATTERN, "<font color=\"red\"><b>$1</b></font>");
        model.comment = RegexUtils.replaceAll(model.comment, ORANGE_TEXT_MARK_PATTERN, "<font color=\"#FFA500\">$1</font>");
        model.comment = RegexUtils.replaceAll(model.comment, GREEN_TEXT_MARK_PATTERN, "<span class=\"quote\">$1</span>");
        model.comment = RegexUtils.replaceAll(model.comment, REPLY_NUMBER_PATTERN, "&gt;&gt;$1");
        model.comment = RegexUtils.linkify(model.comment.replace("&#58;//", "://"));
        
        String banMessage = object.optString("banMessage", "");
        if (!banMessage.equals(""))
            model.comment += "<br/><br/><em><font color=\"red\">" + banMessage + "</font></em>";
        String flag = object.optString("flag", "");
        if (!flag.equals("")) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.description = object.optString("flagName");
            icon.source = flag;
            model.icons = new BadgeIconModel[] { icon };
        }
        int post_number = object.optInt("postId", -1);
        model.number = post_number == -1 ? null : Integer.toString(post_number);
        if (model.number == null) {
            int thread_number = object.optInt("threadId", -1);
            model.number = thread_number == -1 ? "" : Integer.toString(thread_number);
        }
        String signedRole = object.optString("signedRole", "");
        if (!signedRole.equals("")) model.trip = "##" + signedRole;
        String id = object.optString("id", "");
        model.sage = model.email.toLowerCase(Locale.US).equals("sage");
        if (!id.equals("")) {
            model.name += (" ID:" + id);
            model.color = CryptoUtils.hashIdColor(id);
        }
        JSONArray files = object.optJSONArray("files");
        if (files != null && files.length() > 0) {
            model.attachments = new AttachmentModel[files.length()];
            for (int i = 0, len = files.length(); i < len; ++i) {
                model.attachments[i] = mapAttachment(files.getJSONObject(i));
            }
        }
        return model;
    }

    private AttachmentModel mapAttachment(JSONObject object) {
        AttachmentModel model = new AttachmentModel();
        model.originalName = StringEscapeUtils.unescapeHtml4(object.optString("originalName")).replace("&apos;", "'");
        model.thumbnail = object.optString("thumb");
        model.path = object.optString("path");
        model.height = object.optInt("height", -1);
        model.width = object.optInt("width", -1);
        model.size = object.optInt("size", -1) / 1024;
        model.size = model.size < 0 ? -1 : model.size;
        model.type = getAttachmentType(object.optString("mime"));
        return model;
    }

    private int getAttachmentType(String mimeType) {
        if (mimeType.startsWith("image/")) {
            if (mimeType.contains("gif")) return AttachmentModel.TYPE_IMAGE_GIF;
            if (mimeType.contains("svg")) return AttachmentModel.TYPE_IMAGE_SVG;
            return AttachmentModel.TYPE_IMAGE_STATIC;
        } else if (mimeType.startsWith("audio/")) {
            return AttachmentModel.TYPE_AUDIO;
        } else if (mimeType.startsWith("video/")) {
            return AttachmentModel.TYPE_VIDEO;
        } else {
            return AttachmentModel.TYPE_OTHER_FILE;
        }
    }

    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        if (model.type == UrlPageModel.TYPE_CATALOGPAGE)
            return getUsingUrl() + model.boardName + "/catalog.html";
        if (model.type == UrlPageModel.TYPE_BOARDPAGE && model.boardPage == 1)
            return (getUsingUrl() + model.boardName + "/");
        String url = WakabaUtils.buildUrl(model, getUsingUrl());
        return url;
    }

    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath = UrlPathUtils.getUrlPath(url, getAllDomains());
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        if (url.contains("/catalog.html")) {
            try {
                int index = url.indexOf("/catalog.html");
                String left = url.substring(0, index);
                UrlPageModel model = new UrlPageModel();
                model.chanName = getChanName();
                model.type = UrlPageModel.TYPE_CATALOGPAGE;
                model.boardName = left.substring(left.lastIndexOf('/') + 1);
                model.catalogType = 0;
                return model;
            } catch (Exception e) {
            }
        }
        UrlPageModel model = WakabaUtils.parseUrlPath(urlPath, getChanName(), false);
        if ((model.type == UrlPageModel.TYPE_BOARDPAGE) && (model.boardPage < 1)) {
            model.boardPage = 1;
        }
        return model;
    }

    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("?/")) url = url.substring(1);
        return super.fixRelativeUrl(url);
    }

    @Override
    public ExtendedCaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        List<Cookie> cookies = httpClient.getCookieStore().getCookies();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(CAPTCHAEXPIRATION_COOKIE_NAME) && cookie.getDomain().contains(getUsingDomain())) {
                long delta = 0;
                try {
                    delta = (COOKIE_DATEFORMAT.parse(cookie.getValue()).getTime() - System.currentTimeMillis()) / 1000;
                } catch (Exception e) {
                    Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
                }
                if (delta <= 0) {
                    BasicClientCookie c = new BasicClientCookie(CAPTCHAID_COOKIE_NAME, "");
                    c.setDomain(getUsingDomain());
                    c.setPath("/");
                    httpClient.getCookieStore().addCookie(c);
                }
                break;
            }
        }
        
        String captchaUrl = getUsingUrl() + "captcha.js?d=" + Math.random();
        ExtendedCaptchaModel captcha = downloadCaptcha(captchaUrl, listener, task);
        lastCaptchaId = captcha.captchaID;
        
        if (boardName == null) {
            return captcha;
        }
        if (captchaModesMap == null || !captchaModesMap.containsKey(boardName)) {
            try {
                getBoard(boardName, listener, task);
            } catch (Exception e) {
                return null;
            }
        }
        int captchaMode = captchaModesMap.get(boardName);
        if ((captchaMode == MODE_THREAD_CAPTCHA && threadNumber == null) || captchaMode == MODE_POST_CAPTCHA) {
            return captcha;
        } else {
            return null;
        }
    }

    protected ExtendedCaptchaModel downloadCaptcha(String captchaUrl, ProgressListener listener, CancellableTask task) throws Exception {
        Bitmap captchaBitmap = null;
        String captchaId = null;
        HttpRequestModel requestModel = HttpRequestModel.builder().setGET().setNoRedirect(true).build();
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
        try {
            for (Header header : responseModel.headers) {
                if (header != null && "Set-Cookie".equalsIgnoreCase(header.getName())) {
                    String cookie = header.getValue();
                    if (cookie.contains(CAPTCHAID_COOKIE_NAME)) {
                        try {
                            captchaId = cookie.split(";")[0].split("=")[1];
                        } catch (Exception e) {
                        }
                    }
                    if (captchaId != null) break;
                }
            }
            if (responseModel.statusCode == 301 || responseModel.statusCode == 302) {
                captchaUrl = fixRelativeUrl(responseModel.locationHeader);
                responseModel.release();
                responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
            }
            InputStream imageStream = responseModel.stream;
            captchaBitmap = BitmapFactory.decodeStream(imageStream);
        } finally {
            responseModel.release();
        }
        ExtendedCaptchaModel captchaModel = new ExtendedCaptchaModel();
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        captchaModel.bitmap = captchaBitmap;
        captchaModel.captchaID = captchaId;
        return captchaModel;
    }

    public static String computeFileMD5(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.reset();

        FileInputStream fis = new FileInputStream(file);
        byte[] byteArray = new byte[1024];
        int bytesCount;
        while ((bytesCount = fis.read(byteArray)) != -1) {
            messageDigest.update(byteArray, 0, bytesCount);
        }
        fis.close();

        byte[] digest = messageDigest.digest();
        BigInteger bigInt = new BigInteger(1, digest);
        String md5Hex = bigInt.toString(16);
        if (md5Hex.length() < 32) {
            char[] head = new char[32 - md5Hex.length()];
            Arrays.fill(head, '0');
            md5Hex = new StringBuilder(32).append(head).append(md5Hex).toString();
        }
        return md5Hex;
    }

    protected boolean checkFileIdentifier(String hash, String mime, ProgressListener listener, CancellableTask task) {
        if (hash == null || mime == null) return false;
        String identifier = hash + "-" + mime.replace("/", "");
        String url = getUsingUrl() + "checkFileIdentifier.js?json=1&identifier=" + identifier;
        String response;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, null, httpClient, listener, task, false);
            return response.contains("true");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public class ExtendedCaptchaModel extends CaptchaModel {
        public String captchaID = "";
    }
}

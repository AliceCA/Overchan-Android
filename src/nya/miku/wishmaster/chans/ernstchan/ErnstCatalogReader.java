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

package nya.miku.wishmaster.chans.ernstchan;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.RegexUtils;

public class ErnstCatalogReader implements Closeable {
    private static final char[] CATALOG_START = "</header>".toCharArray();
    private static final char[] TAG_CLOSE = ">".toCharArray();
    
    private static final Pattern PATTERN_OMITTED = Pattern.compile("\\D+(\\d+)(?:\\D+(\\d+))?");
    
    private static final int FILTER_THREAD_NUMBER = 0;
    private static final int FILTER_SUBJECT = 1;
    private static final int FILTER_COMMENT = 2;
    private static final int FILTER_THUMBNAIL = 3;
    private static final int FILTER_OMITTED = 4;
    private static final int FILTER_THREAD_END = 5;
    private static final int FILTER_CATALOG_END = 6;
    
    public static final char[][] FILTERS_OPEN = {
            "<a target=\"_blank\" href=\"".toCharArray(),
            "<div class=\"subject\"".toCharArray(),
            "<div class=\"post_body\"".toCharArray(),
            "<img src=\"".toCharArray(),
            "<span class=\"notice\"".toCharArray(),
            "class=\"catalog post\"".toCharArray(),
            "<footer>".toCharArray(),
    };
    
    private static final char[][] FILTERS_CLOSE = {
            "\"".toCharArray(),
            "</div>".toCharArray(),
            "</div>".toCharArray(),
            "\"".toCharArray(),
            "</span>".toCharArray(),
            null,
            null
    };
    
    private final Reader _in;
    
    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;

    private List<AttachmentModel> currentAttachments;
    
    public ErnstCatalogReader(Reader reader) {
        _in = reader;
    }
    
    public ErnstCatalogReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }
    
    public ThreadModel[] readPage() throws IOException {
        threads = new ArrayList<ThreadModel>();
        initThreadModel();
        skipUntilSequence(CATALOG_START);
        readData();
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    private void readData() throws IOException {
        int filtersCount = FILTERS_OPEN.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS_OPEN[i].length;
        
        int curChar;
        while ((curChar = _in.read()) != -1) {
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS_OPEN[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        handleFilter(i);
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS_OPEN[i][0] ? 1 : 0;
                }
            }
        }
        finalizeThread();
    }
    
    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = 0;
        currentThread.posts = new PostModel[1];
        currentThread.posts[0] = new PostModel();
        currentThread.posts[0].email = "";
        currentThread.posts[0].trip = "";
        currentThread.posts[0].name = "";
        currentAttachments = new ArrayList<AttachmentModel>();
    }
    
    private void finalizeThread() {
        if (currentThread.posts[0].number != null && currentThread.posts[0].number.length() > 0) {
            currentThread.threadNumber = currentThread.posts[0].number;
            currentThread.posts[0].parentThread = currentThread.posts[0].number;
            currentThread.posts[0].attachments = currentAttachments.toArray(new AttachmentModel[currentAttachments.size()]);
            if (currentThread.posts[0].subject == null) currentThread.posts[0].subject = "";
            if (currentThread.posts[0].comment == null) currentThread.posts[0].comment = "";
            threads.add(currentThread);
        }
        initThreadModel();
    }
    
    private void handleFilter(int filterIndex) throws IOException {
        switch (filterIndex) {
            case FILTER_THREAD_NUMBER:
                skipUntilSequence("/thread/".toCharArray());
                currentThread.posts[0].number = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_SUBJECT:
                skipUntilSequence(TAG_CLOSE);
                String subject = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                currentThread.posts[0].subject = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlTags(subject).trim());
                break;
            case FILTER_COMMENT:
                skipUntilSequence(TAG_CLOSE);
                currentThread.posts[0].comment = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_THUMBNAIL:
                String thumbPath = readUntilSequence("\"".toCharArray());
                if (thumbPath.contains("/thumb/")) {
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    attachment.size = -1;
                    attachment.width = -1;
                    attachment.height = -1;
                    attachment.thumbnail = thumbPath;
                    attachment.path = thumbPath.replace("/thumb/", "/src/").replaceFirst("(\\d+)s\\.", "$1.");
                    currentAttachments.add(attachment);
                }
                break;
            case FILTER_OMITTED:
                skipUntilSequence(TAG_CLOSE);
                String noticeHtml = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                parseCountryball(noticeHtml);
                parseOmittedString(RegexUtils.removeHtmlTags(noticeHtml));
                break;
            case FILTER_THREAD_END:
            case FILTER_CATALOG_END:
                finalizeThread();
                break;
        }
    }
    
    private void parseCountryball(String html) {
        int countryBallIndex = html.indexOf("<img src=\"/img/balls/");
        if (countryBallIndex != -1) {
            int start = countryBallIndex + 11;
            int end = html.indexOf('\"', start);
            if (end != -1) {
                BadgeIconModel icon = new BadgeIconModel();
                icon.source = html.substring(start, end);
                int titleIndex = html.indexOf("title=\"");
                if (titleIndex != -1) {
                    int startTitle = titleIndex + 7;
                    int endTitle = html.indexOf("\"", startTitle);
                    icon.description = html.substring(startTitle, endTitle);
                }
                currentThread.posts[0].icons = new BadgeIconModel[] { icon };
            }
        }
    }
    
    private void parseOmittedString(String omitted) {
        Matcher matcher = PATTERN_OMITTED.matcher(omitted);
        if (matcher.find()) {
            currentThread.postsCount = 1 + Integer.parseInt(matcher.group(1));
            if (matcher.group(2) != null) {
                currentThread.attachmentsCount = Integer.parseInt(matcher.group(2));
            }
        }
    }
    
    private void skipUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return;
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
    }
    
    private String readUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return "";
        readBuffer.setLength(0);
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
            readBuffer.append((char) curChar);
            if (curChar == sequence[pos]) {
                ++pos;
                if (pos == len) break;
            } else {
                if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
            }
        }
        int buflen = readBuffer.length();
        if (buflen >= len) {
            readBuffer.setLength(buflen - len);
            return readBuffer.toString();
        } else {
            return "";
        }
    }
    
    @Override
    public void close() throws IOException {
        _in.close();
    }
}

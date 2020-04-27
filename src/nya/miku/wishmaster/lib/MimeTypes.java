package nya.miku.wishmaster.lib;

import android.webkit.MimeTypeMap;

import java.util.HashMap;
import java.util.Locale;

/**
 * Workaround for MimeTypeMap.
 * Contains mime types of most popular file formats.
 * Handles them even if the internal implementation fails.
 * @author Fukurou Mishiranu
 */
public class MimeTypes {

    private static final HashMap<String, String> MIME_TYPE_MAP = new HashMap<>();
    private static final HashMap<String, String> EXTENSION_MAP = new HashMap<>();
    static {
        addMime("epub", "application/epub+zip");
        addMime("ogg", "application/ogg");
        addMime("pdf", "application/pdf");
        addMime("apk", "application/vnd.android.package-archive");
        addMime("xhtml", "application/xhtml+xml");
        addMime("7z", "application/x-7z-compressed");
        addMime("swf", "application/x-shockwave-flash");
        addMime("tar", "application/x-tar");
        addMime("zip", "application/zip");
        addMime("aac", "audio/aac");
        addMime("flac", "audio/flac");
        addMime("midi", "audio/midi");
        addMime("mid", "audio/midi");
        addMime("mp3", "audio/mpeg");
        addMime("opus", "audio/opus");
        addMime("wav", "audio/x-wav");
        addMime("gif", "image/gif");
        addMime("jpe", "image/jpeg");
        addMime("jpeg", "image/jpeg");
        addMime("jpg", "image/jpeg");
        addMime("apng", "image/png");
        addMime("png", "image/png");
        addMime("svgz", "image/svg+xml");
        addMime("svg", "image/svg+xml");
        addMime("webp", "image/webp");
        addMime("ico", "image/x-icon");
        addMime("bmp", "image/x-ms-bmp");
        addMime("css", "text/css");
        addMime("html", "text/html");
        addMime("htm", "text/html");
        addMime("txt", "text/plain");
        addMime("xml", "text/xml");
        addMime("mp4", "video/mp4");
        addMime("webm", "video/webm");
    }
    
    public static String forExtension(String extension, String defaultMimeType) {
        if (extension != null && extension.length() != 0) {
            extension = extension.toLowerCase(Locale.US);
            String mimeType = MIME_TYPE_MAP.get(extension);
            if (mimeType == null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            if (mimeType != null) {
                return mimeType;
            }
        }
        return defaultMimeType;
    }

    public static String forExtension(String extension) {
        return forExtension(extension, null);
    }

    public static String toExtension(String mimeType, String defaultExtension) {
        if (mimeType != null && mimeType.length() != 0) {
            String extension = EXTENSION_MAP.get(mimeType);
            if (extension == null) {
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            }
            if (extension != null) {
                return extension;
            }
        }
        return defaultExtension;
    }

    public static String toExtension(String mimeType) {
        return toExtension(mimeType, null);
    }

    private static void addMime(String extension, String mimeType) {
        MIME_TYPE_MAP.put(extension, mimeType);
        EXTENSION_MAP.put(mimeType, extension);
    }
}

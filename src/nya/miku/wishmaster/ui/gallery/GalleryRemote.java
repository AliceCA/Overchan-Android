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

package nya.miku.wishmaster.ui.gallery;

import java.io.File;
import java.util.ArrayList;

import android.graphics.Bitmap;
import nya.miku.wishmaster.common.Logger;

public class GalleryRemote {
    private static final String TAG = "GalleryContextBinder";
    
    public final GalleryBinder binder;
    public final int contextId;
    
    public GalleryRemote(GalleryBinder binder, int contextId) {
        this.binder = binder;
        this.contextId = contextId;
    }
    
    public GalleryInitResult getInitResult() {
        try {
            GalleryInitResult part = binder.getInitResult(contextId);
            if (part == null || part.attachments == null) {
                Logger.e(TAG, "returned null");
                return null;
            }
            if (part.hasMoreAttachments == 0)
                return part;
            GalleryInitResult result = new GalleryInitResult();
            result.initPosition = part.initPosition;
            result.shouldWaitForPageLoaded = part.shouldWaitForPageLoaded;
            result.attachments = new ArrayList<>(part.attachments.size() + part.hasMoreAttachments);
            result.attachments.addAll(part.attachments);
            do {
                part = binder.getInitResult(contextId);
                if (part == null || part.attachments == null) {
                    Logger.e(TAG, "returned null");
                    return null;
                }
                result.attachments.addAll(part.attachments);
            } while (part.hasMoreAttachments > 0);
            return result;
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public boolean isPageLoaded(String pagehash) {
        try {
            return binder.isPageLoaded(pagehash);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return false;
        }
    }
    
    public Bitmap getBitmapFromMemory(String hash) {
        try {
            return binder.getBitmapFromMemory(contextId, hash);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public Bitmap getBitmap(String hash, String url) {
        try {
            return binder.getBitmap(contextId, hash, url);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public File getAttachment(GalleryAttachmentInfo attachment, boolean localOnly, GalleryGetterCallback callback) {
        try {
            String path = binder.getAttachment(contextId, attachment, localOnly, callback);
            if (path == null) return null;
            return new File(path);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public String getAbsoluteUrl(String url) {
        try {
            return binder.getAbsoluteUrl(contextId, url);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }
    
    public String getAttachmentInfoString(GalleryAttachmentInfo attachment) {
        try {
            return binder.getAttachmentInfoString(contextId, attachment);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        }
    }

    public void tryScrollParent(String postNumber, boolean closeDialogs) {
        try {
            binder.tryScrollParent(contextId, postNumber, closeDialogs);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
}

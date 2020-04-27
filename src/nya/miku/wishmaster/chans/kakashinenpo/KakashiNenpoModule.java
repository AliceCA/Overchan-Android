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

package nya.miku.wishmaster.chans.kakashinenpo;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class KakashiNenpoModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "kakashi-nenpo.com";
    private static final String CHAN_DOMAIN = "kakashi-nenpo.com";
    private static final String DISPLAYING_NAME = "Kakashi Nenpo";
    private static final String[] ATTACHMENT_FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm", "mp3", "ogg", "mid", "midi" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "jp", "Mysterious Thoughtography Collection", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "snow", "Eternal Spirit of Winter", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tan", "Moe personifications", "", false),
    };

    public KakashiNenpoModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return DISPLAYING_NAME;
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_kakashinenpo, null);
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected String getUsingDomain() {
        return CHAN_DOMAIN;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.allowCustomMark = true;
        model.customMarkDescription = "NSFW/Spoiler Image";
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        AttachmentModel attachment = super.mapAttachment(object, boardName, isSpoiler);
        if (attachment != null && attachment.type == AttachmentModel.TYPE_VIDEO) {
            attachment.thumbnail = null;
        }
        return attachment;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        super.sendPost(model, listener, task);
        return null;
    }

}

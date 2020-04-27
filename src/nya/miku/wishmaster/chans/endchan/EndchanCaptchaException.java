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

package nya.miku.wishmaster.chans.endchan;

import android.graphics.Bitmap;

import nya.miku.wishmaster.api.AbstractLynxChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.SimpleCaptchaException;

public class EndchanCaptchaException extends SimpleCaptchaException {
    private static final long serialVersionUID = 1L;

    private String captchaId = "";

    protected Bitmap getNewCaptcha() throws Exception {

        AbstractLynxChanModule.ExtendedCaptchaModel captcha =
                ((EndChanModule) MainApplication.getInstance()
                        .getChanModule(EndChanModule.CHAN_NAME))
                        .getNewCaptcha(null, null, null, new CancellableTask() {
                            @Override
                            public void cancel() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        });
        captchaId = captcha.captchaID;
        return captcha.bitmap;
    }

    protected void storeResponse(String response) {
        ((EndChanModule) MainApplication.getInstance().getChanModule(EndChanModule.CHAN_NAME))
                .putCaptcha(captchaId, response);
    }
}

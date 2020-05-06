package nya.miku.wishmaster.http.stormwall;

import android.app.Activity;

import cz.msebera.android.httpclient.cookie.Cookie;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;

public class StormwallUIHandler {
    private StormwallUIHandler() {}
        
    /**
     * Обработать исключение-запрос проверки Stormwall.
     * Вызывать из фонового потока
     * @param e исключение {@link StormwallException}
     * @param chan модуль чана
     * @param activity активность, в которой будет создан диалог (в случае проверки с капчей),
     * или в контексте которой будет создан WebView для Anti DDOS проверки с javascript.
     * Используется как доступ к UI потоку ({@link Activity#runOnUiThread(Runnable)})
     * @param cfTask отменяемая задача
     * @param callback интерфейс {@link InteractiveException.Callback}
     */
    static void handleStormwall(final StormwallException e, final HttpChanModule chan, final Activity activity, final CancellableTask cfTask,
                                final InteractiveException.Callback callback) {
        if (cfTask.isCancelled()) return;
        if (e.isRecaptcha()) {
            Recaptcha2.obtain(e.getUrl(), e.getSiteKey(), null, chan.getChanName(), false).
                    handle(activity, cfTask, new InteractiveException.Callback() {
                        @Override
                        public void onSuccess() {
                            Async.runAsync(new Runnable() {
                                @Override
                                public void run() {
                                    String RecaptchaAnswer = Recaptcha2solved.pop(e.getSiteKey());
                                    Cookie swpCookie = StormwallChecker.getInstance().
                                            checkRecaptcha(e, (ExtendedHttpClient) chan.getHttpClient(), cfTask, RecaptchaAnswer);
                                    if (swpCookie != null) {
                                        chan.saveCookie(swpCookie);
                                        if (!cfTask.isCancelled()) {
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    callback.onSuccess();
                                                }
                                            });
                                        }
                                    } else {
                                        //печенька не получена (вероятно, ответ неверный, загружаем капчу еще раз)
                                        handleStormwall(e, chan, activity, cfTask, callback);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            if (!cfTask.isCancelled()) callback.onError(message);
                        }
                    });
        } else {
            if (!StormwallChecker.getInstance().isAvaibleAntiDDOS()) {
                //если анти ддос проверка уже проводится другим потоком, тогда подождем завершения и объявим успех
                //в случае, если проверка была по тому же ChanModule, проверка уже будет пройдена
                //в противном случае на следующей попытке (закачки) cloudflare выкинет исключение снова
                //и мы сможем обработать исключение для этого чана на свободном CloudflareChecker
                while (!StormwallChecker.getInstance().isAvaibleAntiDDOS()) Thread.yield();
                if (!cfTask.isCancelled()) activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
                return;
            }

            Cookie cfCookie = StormwallChecker.getInstance().checkAntiDDOS(e, chan.getHttpClient(), cfTask, activity);
            if (cfCookie != null) {
                chan.saveCookie(cfCookie);
                if (!cfTask.isCancelled()) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess();
                        }
                    });
                }
            } else if (!cfTask.isCancelled()) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(activity.getString(R.string.error_cloudflare_antiddos));
                    }
                });
            }
        }
    }
}

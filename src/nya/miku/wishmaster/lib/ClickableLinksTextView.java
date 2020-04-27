package nya.miku.wishmaster.lib;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.lib.ExtendedClickableSpan;
import nya.miku.wishmaster.ui.CompatibilityImpl;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Исправленный TextView, корректно работающий с Spanned текстом со ссылками и поддерживает выделение текста.
 * Класс взят из проекта 2ch Browser, с минимальными изменениями.
 * + исправлена работа с Android ICS (API <= 15) и Android 6.0 (API 23)
 * см. также http://stackoverflow.com/questions/14862750/textview-that-is-linkified-and-selectable
 * 
 */
//The TextView that handles correctly clickable spans.
public class ClickableLinksTextView extends JellyBeanSpanFixTextView {
    public static final String TAG = "ClickableLinksTextView";

    private boolean mBaseEditorCopied = false;
    private Object mBaseEditor = null;
    private Field mDiscardNextActionUpField = null;
    private Field mIgnoreActionUpEventField = null;

    public ClickableLinksTextView(Context context) {
        super(context);
    }

    public ClickableLinksTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ClickableLinksTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onStartTemporaryDetach() {
        super.onStartTemporaryDetach();

        // listview scrolling behaves incorrectly after you select and copy some text, so I've added this code
        if (this.isFocused()) {
            Logger.d(TAG, "clear focus");
            this.clearFocus();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // the base TextView class checks if getAutoLinkMask != 0, so I added a similar code for == 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && CompatibilityImpl.isTextSelectable(this) &&
                this.getText() instanceof Spannable && this.getAutoLinkMask() == 0 && this.getLinksClickable() &&
                this.isEnabled() && this.getLayout() != null) {
            return this.checkLinksOnTouch(event);
        }

        return super.onTouchEvent(event);
    }

    public void startSelection() {
        if (this.getText() == null || this.getText().equals("")) {
            return;
        }

        this.copyBaseEditorIfNecessary();

        Selection.setSelection((Spannable) this.getText(), 0, this.getText().length());

        try {
            Method performLongClick = this.mBaseEditor.getClass().getMethod("performLongClick", Boolean.TYPE);
            performLongClick.invoke(this.mBaseEditor, false);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Method startSelectionActionMode = this.mBaseEditor.getClass().getDeclaredMethod("startSelectionActionMode");
                startSelectionActionMode.setAccessible(true);
                startSelectionActionMode.invoke(this.mBaseEditor);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            try {
                Method startSelectionActionMode = TextView.class.getDeclaredMethod("startSelectionActionMode");
                startSelectionActionMode.setAccessible(true);
                startSelectionActionMode.invoke(this);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }

    private final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
        private ExtendedClickableSpan[] getSpans(MotionEvent event, Spannable spannable) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            x -= ClickableLinksTextView.this.getTotalPaddingLeft();
            y -= ClickableLinksTextView.this.getTotalPaddingTop();
            x += ClickableLinksTextView.this.getScrollX();
            y += ClickableLinksTextView.this.getScrollY();
            Layout layout = ClickableLinksTextView.this.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);
            ExtendedClickableSpan[] link = spannable.getSpans(off, off, ExtendedClickableSpan.class);
            return link;
        }

        @Override
        public boolean onDown(MotionEvent event) {
            Spannable spannable = (Spannable)ClickableLinksTextView.this.getText();
            ExtendedClickableSpan[] link = getSpans(event, spannable);
            if (link.length > 0) {
                return link[0].onClickDown(ClickableLinksTextView.this);
            } else {
                return false;
            }
        }

        @Override
        public void onLongPress(MotionEvent event) {
            Spannable spannable = (Spannable)ClickableLinksTextView.this.getText();
            ExtendedClickableSpan[] link = getSpans(event, spannable);
            if (link.length > 0) {
                link[0].onLongClick(ClickableLinksTextView.this);
            }
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            boolean discardNextActionUp = ClickableLinksTextView.this.getDiscardNextActionUp();
            ClickableLinksTextView.super.onTouchEvent(event);
            if (discardNextActionUp) {
                return true;
            }
            Spannable spannable = (Spannable)ClickableLinksTextView.this.getText();
            ExtendedClickableSpan[] link = getSpans(event, spannable);
            if (link.length > 0) {
                return link[0].onClickUp(ClickableLinksTextView.this);
            } else {
                return false;
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return onSingleTapUp(e2);
        }
    });

    private boolean checkLinksOnTouch(MotionEvent event) {
        this.copyBaseEditorIfNecessary();
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    private void copyBaseEditorIfNecessary() {
        if (this.mBaseEditorCopied) {
            return;
        }

        try {
            Field field = TextView.class.getDeclaredField("mEditor");
            field.setAccessible(true);
            this.mBaseEditor = field.get(this);

            if (this.mBaseEditor != null) {
                Class<? extends Object> editorClass = this.mBaseEditor.getClass();
                this.mDiscardNextActionUpField = editorClass.getDeclaredField("mDiscardNextActionUp");
                this.mDiscardNextActionUpField.setAccessible(true);

                this.mIgnoreActionUpEventField = editorClass.getDeclaredField("mIgnoreActionUpEvent");
                this.mIgnoreActionUpEventField.setAccessible(true);
            }

        } catch (Exception e) {
            Logger.e(TAG, e);
        } finally {
            this.mBaseEditorCopied = true;
        }
    }

    private boolean getDiscardNextActionUp() {
        if (this.mBaseEditor == null) {
            return false;
        }

        try {
            return this.mDiscardNextActionUpField.getBoolean(this.mBaseEditor);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean getIgnoreActionUpEvent() {
        if (this.mBaseEditor == null) {
            return false;
        }

        try {
            return this.mIgnoreActionUpEventField.getBoolean(this.mBaseEditor);
        } catch (Exception e) {
            return false;
        }
    }
}

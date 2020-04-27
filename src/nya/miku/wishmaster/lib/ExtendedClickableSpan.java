package nya.miku.wishmaster.lib;

import android.text.style.ClickableSpan;
import android.view.View;

public class ExtendedClickableSpan extends ClickableSpan {
    @Override
    public void onClick(View widget) {
        onClickUp(widget);
    }
    public boolean onClickDown(View widget) {
        return false;
    }
    public boolean onClickUp(View widget) {
        return false;
    }
    public void onLongClick(View widget) {
        return;
    }
}

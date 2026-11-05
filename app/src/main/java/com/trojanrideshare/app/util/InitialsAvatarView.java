package com.trojanrideshare.app.util;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import com.trojanrideshare.app.model.User;

/** A circle with someone's initials, styled by {@code @style/InitialsAvatar}. */
public class InitialsAvatarView extends AppCompatTextView {
    public InitialsAvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setText(Initials.UNKNOWN);
    }

    /** A null user (not found, or still loading) shows "?". */
    public void setUser(User user) {
        setText(user == null ? Initials.UNKNOWN : Initials.of(user.fullName, user.nickname));
    }

    /**
     * Measured a second time as a square on the longer side, so large text grows the circle
     * instead of clipping or stretching it into an oval.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int side = Math.max(getMeasuredWidth(), getMeasuredHeight());
        int square = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
        super.onMeasure(square, square);
    }
}

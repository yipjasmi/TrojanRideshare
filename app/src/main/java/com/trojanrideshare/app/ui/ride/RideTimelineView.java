package com.trojanrideshare.app.ui.ride;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.color.MaterialColors;
import com.trojanrideshare.app.R;
import com.trojanrideshare.app.service.ride.RideTimelineService;

import java.util.List;

/**
 * Ride lifecycle steps joined by a line. The line fills with the primary color down to the
 * current step, or to the last step once every step is done.
 */
public class RideTimelineView extends LinearLayout {
    private static final long FILL_DURATION_MILLIS = 400;

    public static final class Row {
        final String title;
        final CharSequence detail;
        final String spokenDetail;
        final RideTimelineService.State state;

        public Row(
                String title,
                CharSequence detail,
                String spokenDetail,
                RideTimelineService.State state
        ) {
            this.title = title;
            this.detail = detail;
            this.spokenDetail = spokenDetail;
            this.state = state;
        }
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int fillIndex;
    private float fillFraction = 1f;
    private boolean filledOnce;
    private ValueAnimator fillAnimator;

    public RideTimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        float lineWidth = 2 * getResources().getDisplayMetrics().density;
        trackPaint.setStrokeWidth(lineWidth);
        trackPaint.setColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline));
        fillPaint.setStrokeWidth(lineWidth);
        fillPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
    }

    /** Replaces the steps. Only the first call animates the fill. */
    public void setRows(List<Row> rows) {
        removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int index = 0; index < rows.size(); index++) {
            View item = inflater.inflate(R.layout.item_timeline_step, this, false);
            bindRow(item, rows.get(index), index, rows.size());
            if (index == rows.size() - 1) {
                item.setPadding(item.getPaddingLeft(), item.getPaddingTop(),
                        item.getPaddingRight(), 0);
            }
            addView(item);
        }
        fillIndex = fillIndex(rows);
        if (!filledOnce) {
            filledOnce = true;
            animateFill();
        }
        invalidate();
    }

    private void bindRow(View item, Row row, int index, int count) {
        View dot = item.findViewById(R.id.timelineDot);
        TextView title = item.findViewById(R.id.timelineTitle);
        TextView detail = item.findViewById(R.id.timelineDetail);
        int titleColorAttr;
        int stateLabelRes;
        switch (row.state) {
            case DONE:
                dot.setBackgroundResource(R.drawable.bg_timeline_dot_done);
                titleColorAttr = com.google.android.material.R.attr.colorOnSurface;
                stateLabelRes = R.string.ride_timeline_done;
                break;
            case CURRENT:
                dot.setBackgroundResource(R.drawable.bg_timeline_dot_current);
                titleColorAttr = androidx.appcompat.R.attr.colorPrimary;
                stateLabelRes = R.string.ride_timeline_current;
                break;
            case UPCOMING:
            default:
                dot.setBackgroundResource(R.drawable.bg_timeline_dot_upcoming);
                titleColorAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
                stateLabelRes = R.string.ride_timeline_upcoming;
                break;
        }
        title.setText(row.title);
        title.setTextColor(MaterialColors.getColor(title, titleColorAttr));
        detail.setText(row.detail);
        detail.setVisibility(row.detail.length() == 0 ? GONE : VISIBLE);

        String description = getContext().getString(R.string.ride_timeline_step_description,
                index + 1, count, row.title, getContext().getString(stateLabelRes));
        if (!row.spokenDetail.isEmpty()) {
            description += ". " + row.spokenDetail;
        }
        item.setContentDescription(description);
        ViewCompat.setScreenReaderFocusable(item, true);
    }

    private static int fillIndex(List<Row> rows) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).state == RideTimelineService.State.CURRENT) {
                return index;
            }
        }
        return rows.size() - 1;
    }

    private void animateFill() {
        boolean animationsOff = Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        if (animationsOff) {
            return;
        }
        fillFraction = 0f;
        fillAnimator = ValueAnimator.ofFloat(0f, 1f);
        fillAnimator.setDuration(FILL_DURATION_MILLIS);
        fillAnimator.setInterpolator(new DecelerateInterpolator());
        fillAnimator.addUpdateListener(animation -> {
            fillFraction = animation.getAnimatedFraction();
            invalidate();
        });
        fillAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fillAnimator != null) {
            fillAnimator.end();
        }
        super.onDetachedFromWindow();
    }

    // Children draw after this, so the dots sit on top of the line.
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (getChildCount() > 1) {
            float x = dotCenterX(0);
            float top = dotCenterY(0);
            float bottom = dotCenterY(getChildCount() - 1);
            float fillEnd = top + (dotCenterY(fillIndex) - top) * fillFraction;
            canvas.drawLine(x, top, x, bottom, trackPaint);
            canvas.drawLine(x, top, x, fillEnd, fillPaint);
        }
        super.dispatchDraw(canvas);
    }

    private float dotCenterX(int index) {
        View item = getChildAt(index);
        View dot = item.findViewById(R.id.timelineDot);
        return item.getLeft() + dot.getLeft() + dot.getWidth() / 2f;
    }

    private float dotCenterY(int index) {
        View item = getChildAt(index);
        View dot = item.findViewById(R.id.timelineDot);
        return item.getTop() + dot.getTop() + dot.getHeight() / 2f;
    }
}

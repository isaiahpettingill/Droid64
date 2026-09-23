package ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import emu.Control;
import emu.KeyCode;

/** A fixed, multi-touch joystick and fire button over the C64 screen. */
public class TouchControlsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int directionPointer = -1;
    private int firePointer = -1;
    private int direction;

    public TouchControlsView(Context context) {
        super(context);
        setContentDescription("Touch joystick on left; fire button on right");
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(dp(68), getHeight() * .42f);
        float leftX = radius + dp(18), rightX = getWidth() - radius - dp(18);
        float centerY = getHeight() / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(115, 30, 35, 45));
        canvas.drawCircle(leftX, centerY, radius, paint);
        canvas.drawCircle(rightX, centerY, radius * .82f, paint);
        paint.setColor(Color.argb(220, 255, 255, 255));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(25));
        canvas.drawText("↑", leftX, centerY - radius * .42f, paint);
        canvas.drawText("↓", leftX, centerY + radius * .7f, paint);
        canvas.drawText("←", leftX - radius * .56f, centerY + dp(8), paint);
        canvas.drawText("→", leftX + radius * .56f, centerY + dp(8), paint);
        paint.setTextSize(dp(17));
        canvas.drawText("FIRE", rightX, centerY + dp(6), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(Color.argb(235, 112, 230, 255));
        if (direction != 0) canvas.drawCircle(leftX, centerY, radius, paint);
        if (firePointer != -1) canvas.drawCircle(rightX, centerY, radius * .82f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = event.getPointerId(index);
            if (event.getX(index) < getWidth() * .6f && directionPointer == -1)
                directionPointer = id;
            else if (firePointer == -1)
                firePointer = id;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = event.getPointerId(index);
            if (id == directionPointer) directionPointer = -1;
            if (id == firePointer) firePointer = -1;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            clearInput();
            return true;
        }

        direction = 0;
        int indexOfDirection = event.findPointerIndex(directionPointer);
        if (indexOfDirection >= 0) {
            float radius = Math.min(dp(68), getHeight() * .42f);
            float dx = event.getX(indexOfDirection) - radius - dp(18);
            float dy = event.getY(indexOfDirection) - getHeight() / 2f;
            if (dx < -radius * .3f) direction |= KeyCode.C64STICK_LEFT;
            if (dx > radius * .3f) direction |= KeyCode.C64STICK_RIGHT;
            if (dy < -radius * .3f) direction |= KeyCode.C64STICK_UP;
            if (dy > radius * .3f) direction |= KeyCode.C64STICK_DOWN;
        }
        Control control = Control.instance();
        if (control != null) control.setStick(direction | (firePointer != -1 ? KeyCode.C64STICK_FIRE : 0));
        invalidate();
        return true;
    }

    public void clearInput() {
        directionPointer = firePointer = -1;
        direction = 0;
        Control control = Control.instance();
        if (control != null) control.setStick(0);
        invalidate();
    }
}

package ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;

/** Per-controller button mappings; analog sticks retain Android's normal axes. */
public class ControllerBindings {
    public static final String[] ACTIONS = {"Up", "Down", "Left", "Right", "Fire", "Menu", "Keyboard"};
    public static final int[] DEFAULT_KEYS = {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_THUMBR
    };
    private final SharedPreferences preferences;

    public ControllerBindings(Context context) {
        preferences = context.getSharedPreferences("controller_bindings", Context.MODE_PRIVATE);
    }

    private String key(InputDevice device, int action) {
        return device.getDescriptor() + ":" + action;
    }

    public boolean isCustomized(InputDevice device) {
        for (int i = 0; i < ACTIONS.length; i++) {
            if (preferences.contains(key(device, i))) return true;
        }
        return false;
    }

    public int get(InputDevice device, int action) {
        return preferences.getInt(key(device, action), DEFAULT_KEYS[action]);
    }

    public void set(InputDevice device, int action, int keyCode) {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < ACTIONS.length; i++) {
            if (i != action && get(device, i) == keyCode)
                editor.putInt(key(device, i), KeyEvent.KEYCODE_UNKNOWN);
        }
        editor.putInt(key(device, action), keyCode).apply();
    }

    public void clear(InputDevice device) {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < ACTIONS.length; i++) editor.remove(key(device, i));
        editor.apply();
    }
}

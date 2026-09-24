package ui;

import android.content.Intent;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.codewiz.droid64.R;

import emu.ImageFilter;
import emu.Image;
import emu.ImageManager;
import emu.Control;
import system.Preferences;
import system.GameController;
import system.GameControllerListener;
import emu.KeyCode;
import emu.KeyMap;
import emu.KeyMapEntry;
import emu.KeySequence;
import util.LogManager;
import util.Logger;
import util.SystemUiHider;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class FullscreenActivity extends FragmentActivity implements FileDialog.OnDiskSelectHandler, EmuControlFragment.OnFragmentInteractionListener, EmuViewFragment.OnFragmentInteractionListener, InputManager.InputDeviceListener {

    private final static Logger logger = LogManager.getLogger(FullscreenActivity.class.getName());

    private static final int AREA_TOP = 0x1;
    private static final int AREA_MIDDLE = 0x2;
    private static final int AREA_BOTTOM = 0x4;
    private static final int AREA_LEFT = 0x10;
    private static final int AREA_CENTER = 0x20;
    private static final int AREA_RIGHT = 0x40;

    private static final int COMMAND_CLICK_AREA = 15;
    private static final boolean EMU_PAUSED_WHILE_CONTROL = false;

    private Preferences emuPrefs;
    private Control emuControl;

    private View emuView;
    private View controlsView;
    private android.app.DialogFragment settingsDialog;
    private android.app.DialogFragment fileDialog;

    private int eventSourceViewId = 0;

    private float mouseX = 0.0f;
    private float mouseY = 0.0f;

    private float mouseDownX = 0.0f;
    private float mouseDownY = 0.0f;

    private float screenWidth = 0.0f;
    private float screenHeight = 0.0f;

    private boolean mouseDown = false;
    private boolean keyboardVisible = false;

    private GameController gameController;
    private ImageManager diskManager;

    private class StableArrayAdapter extends ArrayAdapter<String> {

        HashMap<String, Integer> mIdMap = new HashMap<String, Integer>();

        public StableArrayAdapter(Context context, int textViewResourceId, List<String> objects) {
            super(context, textViewResourceId, objects);
            
            for (int i = 0; i < objects.size(); ++i) {
                mIdMap.put(objects.get(i), i);
            }
        }

        @Override
        public long getItemId(int position) {
            String item = getItem(position);
            Integer itemId = mIdMap.get(item);
            return itemId;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

    }

    private static final int REQUEST_IMPORT_DISK = 1234;
    private static final int REQUEST_INSERT_MEDIA = 1235;
    private int requestedMediaType;
    private FrameLayout contentFrame;
    private View swipeMenu;
    private TouchControlsView touchControls;
    private EditText keyboardInput;
    private View keyboardRow;
    private boolean clearingKeyboardInput;
    private boolean touchControlsEnabled;
    private ControllerBindings controllerBindings;
    private InputManager inputManager;
    private int mappedStickMask;
    private float swipeStartY;
    private boolean swipeFromTop;
    private boolean swipeConsumed;

    public FullscreenActivity() {
        instantiateEmu();
    }

    private void instantiateEmu() {
        emuPrefs = Preferences.instance();
        if (null == emuPrefs) emuPrefs = new Preferences();

        diskManager = new ImageManager();

        emuControl = Control.instance();
        if (null == emuControl) emuControl = new Control();

        emuPrefs.init(this);
        emuControl.init();
    }

    public void importDisk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMPORT_DISK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_DISK && resultCode == RESULT_OK && data != null) {
            boolean imported = diskManager.importDocument(data.getData());
            Toast.makeText(this, imported ? "Disk imported; tap Re-Scan Disks" : "Could not import disk image", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_INSERT_MEDIA && resultCode == RESULT_OK && data != null) {
            Image image = diskManager.importDocumentImage(data.getData());
            if (image == null || image.getType() != requestedMediaType) {
                Toast.makeText(this, "Select a " + mediaExtension(requestedMediaType) + " file", Toast.LENGTH_LONG).show();
            } else {
                boolean inserted = emuControl.attachDisk(image);
                Toast.makeText(this, inserted ? "Inserted " + image.getName() :
                        "Unsupported cartridge (supports standard, Ocean, EasyFlash CRT)", Toast.LENGTH_LONG).show();
            }
        }
    }

    boolean isEmuView(View v) {
        if (null == v) {
            return false;
        }

        int id = v.getId();

        if (id == eventSourceViewId) {
            return true;
        }

        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logger.info("Activity.onCreate()");

        diskManager.bindContext(this.getApplicationContext());

        keyboardVisible = false;

        checkOpenGL();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(R.layout.activity_fullscreen);

        emuPrefs.load();

        emuView = findViewById(R.id.view_fragment);
        eventSourceViewId = R.id.view_fragment;

        controlsView = findViewById(R.id.controls_fragment);

        createKeyButtons();
        controllerBindings = new ControllerBindings(this);
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        touchControlsEnabled = getPreferences(MODE_PRIVATE).getBoolean("touch_controls", true);
        setupOverlay();

        emuView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                if (0.0f == screenWidth && 0.0f == screenHeight) {
                    updateScreenSize();
                }
            }

        });

        emuView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent e) {

                int action = e.getActionMasked();
                if (MotionEvent.ACTION_DOWN == action || MotionEvent.ACTION_POINTER_DOWN == action) {

                    if (isControlsVisible()) {
                        logger.info("Disabled controls");
                        setControlsVisible(false);
                        return true;
                    }

                    mouseDown = true;

                    mouseX = e.getX();
                    mouseY = e.getY();

                    mouseDownX = mouseX;
                    mouseDownY = mouseY;

                    int area = getTouchArea(mouseX, mouseY);
                    // Touch joystick is handled by the dedicated overlay.

                } else if (MotionEvent.ACTION_UP == action || MotionEvent.ACTION_POINTER_UP == action) {

                    if (isControlsVisible()) {
                        logger.info("Disabled controls");
                        setControlsVisible(false);
                        return false;
                    }

                    if (!mouseDown) {
                        return false;
                    }

                    mouseDown = false;
                    mouseX = e.getX();
                    mouseY = e.getY();

                    int area = getTouchArea(mouseX, mouseY);
                    if (0 != (area & AREA_MIDDLE)) {
                        return true;
                    } else {

                        // logger.info("H DISTANCE: " + Math.abs(mouseX - mouseDownX));
                        // logger.info("V DISTANCE: " + Math.abs(mouseY - mouseDownY));

                        if (Math.abs(mouseX - mouseDownX) < (float) COMMAND_CLICK_AREA
                                && Math.abs(mouseY - mouseDownY) < (float) COMMAND_CLICK_AREA) {
                            handleCommandClick(area);
                        }
                    }

                } else if (MotionEvent.ACTION_MOVE == action) {

                    mouseX = e.getX();
                    mouseY = e.getY();

                    if (isControlsVisible()) {
                        return true;
                    }

                    int area = getTouchArea(mouseX, mouseY);
                    if (0 != (area & AREA_MIDDLE)) {
                        return true;
                    }

                } else {
                    return false;
                }

                return true;
            }
        });

        emuView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                //logger.info("focus change: " + v.getId() + " status: " + hasFocus);

                if (isEmuView(v)) {
                    if (hasFocus && isControlsVisible()) {
                        setControlsVisible(false);
                    }
                }
            }
        });

        setControlsVisible(false);

        gameController = new GameController();
        gameController.addListener(new GameControllerListener() {
            @Override
            public void onButtonDown(int buttonId) {

                logger.info("game controller button down: 0x" + Integer.toHexString(buttonId));

                if (buttonId == GameController.ID_BUTTON_L1) { // F1

                    emuControl.keyInput(KeyCode.C64KEY_F1F2 | KeyCode.KEYFLAG_PRESSED);
                    emuControl.keyInput(KeyCode.C64KEY_F1F2 | KeyCode.KEYFLAG_RELEASED);

                } else if (buttonId == GameController.ID_BUTTON_L2) { // F2

                    emuControl.keyInput(KeyCode.C64KEY_F1F2 | KeyCode.C64KEY_FLAG_SHIFT | KeyCode.KEYFLAG_PRESSED);
                    emuControl.keyInput(KeyCode.C64KEY_F1F2 | KeyCode.C64KEY_FLAG_SHIFT | KeyCode.KEYFLAG_RELEASED);

                } else if (buttonId == GameController.ID_BUTTON_R1) { // F3

                    emuControl.keyInput(KeyCode.C64KEY_F3F4 | KeyCode.KEYFLAG_PRESSED);
                    emuControl.keyInput(KeyCode.C64KEY_F3F4 | KeyCode.KEYFLAG_RELEASED);

                } else if (buttonId == GameController.ID_BUTTON_R2) { // F4

                    emuControl.keyInput(KeyCode.C64KEY_F3F4 | KeyCode.C64KEY_FLAG_SHIFT | KeyCode.KEYFLAG_PRESSED);
                    emuControl.keyInput(KeyCode.C64KEY_F3F4 | KeyCode.C64KEY_FLAG_SHIFT | KeyCode.KEYFLAG_RELEASED);

                } else if (buttonId == GameController.ID_BUTTON_Y) { // SPACE key

                    emuControl.keyInput(KeyCode.C64KEY_SPACE | KeyCode.KEYFLAG_PRESSED);
                    emuControl.keyInput(KeyCode.C64KEY_SPACE | KeyCode.KEYFLAG_RELEASED);

                } else if (buttonId == GameController.ID_BUTTON_START) {

                    emuControl.keyInput(KeySequence.sequenceAllKeys);

                } else if (buttonId == GameController.ID_BUTTON_SELECT || buttonId == GameController.ID_BUTTON_MENU) {

                    setMenuVisible(swipeMenu.getVisibility() != View.VISIBLE);

                } else if (buttonId == GameController.ID_BUTTON_PLAY_PAUSE) {

                    if (emuControl.isPaused()) {
                        emuControl.resume();
                    } else {
                        emuControl.pause();
                    }

                } else if (buttonId == GameController.ID_BUTTON_REWIND) {

                    if (emuPrefs.isWarpEnabled()) {
                        emuPrefs.setWarpEnabled(false);
                    }

                } else if (buttonId == GameController.ID_BUTTON_FAST_FORWARD) {

                    if (emuControl.isPaused()) {
                        emuControl.resume();
                    }

                    emuPrefs.setWarpEnabled(!emuPrefs.isWarpEnabled());

                } else if (buttonId == GameController.ID_BUTTON_THUMB_RIGHT) {

                    setKeyboardVisible(!isKeyboardVisible());

                }
            }

            @Override
            public void onButtonUp(int buttonId) {
                ;
            }

        });

        VirtualGamepad.instance().init(this, emuView);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addMenuButton(LinearLayout menu, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        menu.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void setupOverlay() {
        contentFrame = findViewById(android.R.id.content);
        if (Build.VERSION.SDK_INT >= 30) {
            contentFrame.setOnApplyWindowInsetsListener((view, insets) -> {
                boolean shown = insets.isVisible(WindowInsets.Type.ime());
                if (keyboardVisible != shown) {
                    keyboardVisible = shown;
                    updateTouchControls();
                }
                if (shown) view.post(this::positionKeyboardRow);
                return view.onApplyWindowInsets(insets);
            });
        }
        FrameLayout viewFrame = (FrameLayout) emuView;

        keyboardInput = new EditText(this) {
            @Override public InputConnection onCreateInputConnection(EditorInfo info) {
                InputConnection connection = super.onCreateInputConnection(info);
                if (connection == null) return null;
                return new InputConnectionWrapper(connection, false) {
                    @Override public boolean deleteSurroundingText(int before, int after) {
                        if (before > 0) {
                            for (int i = 0; i < Math.min(before, 32); i++) sendC64Key(KeyCode.C64KEY_INSTDEL);
                            return true;
                        }
                        return super.deleteSurroundingText(before, after);
                    }
                    @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                        if (before > 0) {
                            for (int i = 0; i < Math.min(before, 32); i++) sendC64Key(KeyCode.C64KEY_INSTDEL);
                            return true;
                        }
                        return super.deleteSurroundingTextInCodePoints(before, after);
                    }
                    @Override public boolean sendKeyEvent(KeyEvent event) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) sendC64Key(KeyCode.C64KEY_INSTDEL);
                            return true;
                        }
                        return super.sendKeyEvent(event);
                    }
                };
            }
        };
        keyboardInput.setAlpha(0);
        keyboardInput.setSingleLine(true);
        keyboardInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyboardInput.setImeOptions(EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        viewFrame.addView(keyboardInput, new FrameLayout.LayoutParams(dp(2), dp(2), Gravity.TOP | Gravity.LEFT));
        keyboardInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (clearingKeyboardInput) return;
                if (count == 0 && before > 0) sendC64Key(KeyCode.C64KEY_INSTDEL);
                for (int i = start; i < start + count; i++) sendTypedCharacter(s.charAt(i));
            }
            @Override public void afterTextChanged(Editable s) {
                if (!clearingKeyboardInput && s.length() > 0) {
                    clearingKeyboardInput = true;
                    s.clear();
                    clearingKeyboardInput = false;
                }
            }
        });
        keyboardInput.setOnEditorActionListener((v, actionId, event) -> {
            sendC64Key(KeyCode.C64KEY_RETURN);
            return true;
        });

        touchControls = new TouchControlsView(this);
        viewFrame.addView(touchControls, new FrameLayout.LayoutParams(-1, dp(160), Gravity.BOTTOM));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(25, 29, 39));
        scroll.setVisibility(View.GONE);
        swipeMenu = scroll;
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(12), dp(16), dp(12), dp(12));
        scroll.addView(menu);
        addMenuButton(menu, "Insert disk (.d64)", v -> chooseMedia(Image.TYPE_DISK));
        addMenuButton(menu, "Insert tape (.t64)", v -> chooseMedia(Image.TYPE_TAPE));
        addMenuButton(menu, "Insert cartridge (.crt)", v -> chooseMedia(Image.TYPE_CARTRIDGE));
        addMenuButton(menu, "Eject cartridge", v -> {
            setMenuVisible(false);
            emuControl.ejectCartridge();
        });
        addMenuButton(menu, "Show keyboard", v -> { setMenuVisible(false); setKeyboardVisible(true); });
        addMenuButton(menu, "Load and run", v -> {
            setMenuVisible(false);
            emuControl.keyInput(KeySequence.sequence_Load_Asterisk_8_1_Run);
        });
        addMenuButton(menu, "Touch controls on/off", v -> {
            touchControlsEnabled = !touchControlsEnabled;
            getPreferences(MODE_PRIVATE).edit().putBoolean("touch_controls", touchControlsEnabled).apply();
            setMenuVisible(false);
        });
        addMenuButton(menu, "Connect / map controller", v -> { setMenuVisible(false); showControllerDialog(); });
        addMenuButton(menu, "More controls", v -> { setMenuVisible(false); setControlsVisible(true); });
        addMenuButton(menu, "Close menu", v -> setMenuVisible(false));
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(300), -2, Gravity.TOP | Gravity.LEFT);
        menuParams.bottomMargin = dp(50);
        contentFrame.addView(scroll, menuParams);

        HorizontalScrollView row = new HorizontalScrollView(this);
        row.setBackgroundColor(Color.rgb(25, 29, 39));
        row.setHorizontalScrollBarEnabled(false);
        row.setVisibility(View.GONE);
        keyboardRow = row;
        LinearLayout keys = new LinearLayout(this);
        row.addView(keys);
        addKeyboardKey(keys, "RUN/STOP", KeyCode.C64KEY_RUNSTOP);
        addKeyboardKey(keys, "C=", KeyCode.C64KEY_COMMODORE);
        addKeyboardKey(keys, "←", KeyCode.C64KEY_CRSR_LEFTRIGHT | KeyCode.C64KEY_FLAG_SHIFT);
        addKeyboardKey(keys, "↑", KeyCode.C64KEY_CRSR_UPDOWN | KeyCode.C64KEY_FLAG_SHIFT);
        addKeyboardKey(keys, "↓", KeyCode.C64KEY_CRSR_UPDOWN);
        addKeyboardKey(keys, "→", KeyCode.C64KEY_CRSR_LEFTRIGHT);
        addKeyboardKey(keys, "DEL", KeyCode.C64KEY_INSTDEL);
        addKeyboardKey(keys, "RETURN", KeyCode.C64KEY_RETURN);
        addKeyboardKey(keys, "F1", KeyCode.C64KEY_F1F2);
        addKeyboardKey(keys, "F3", KeyCode.C64KEY_F3F4);
        addKeyboardKey(keys, "F5", KeyCode.C64KEY_F5F6);
        addKeyboardKey(keys, "F7", KeyCode.C64KEY_F7F8);
        contentFrame.addView(row, new FrameLayout.LayoutParams(-1, dp(44), Gravity.BOTTOM));
        updateTouchControls();
    }

    private void addKeyboardKey(LinearLayout row, String label, int code) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setOnClickListener(v -> sendC64Key(code));
        row.addView(button, new LinearLayout.LayoutParams(-2, dp(44)));
    }

    private void positionKeyboardRow() {
        if (keyboardRow == null || !keyboardVisible) return;
        Rect visible = new Rect();
        contentFrame.getWindowVisibleDisplayFrame(visible);
        int[] origin = new int[2];
        contentFrame.getLocationOnScreen(origin);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) keyboardRow.getLayoutParams();
        int margin = Math.max(0, origin[1] + contentFrame.getHeight() - visible.bottom);
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin;
            keyboardRow.setLayoutParams(params);
        }
    }

    private void setMenuVisible(boolean visible) {
        swipeMenu.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            setKeyboardVisible(false);
            touchControls.clearInput();
        }
        updateTouchControls();
    }

    private void updateTouchControls() {
        if (touchControls == null) return;
        boolean visible = touchControlsEnabled && !keyboardVisible &&
                swipeMenu.getVisibility() != View.VISIBLE && !hasConnectedController() && !isControlsVisible();
        if (!visible) touchControls.clearInput();
        touchControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (keyboardRow != null) {
            keyboardRow.setVisibility(keyboardVisible ? View.VISIBLE : View.GONE);
            if (keyboardVisible) keyboardRow.post(this::positionKeyboardRow);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && contentFrame != null) {
            int[] origin = new int[2];
            contentFrame.getLocationOnScreen(origin);
            float y = event.getRawY() - origin[1];
            float x = event.getRawX() - origin[0];
            swipeStartY = event.getRawY();
            swipeFromTop = x >= 0 && x < dp(120) && y >= 0 && y < dp(72) &&
                    swipeMenu.getVisibility() != View.VISIBLE;
            swipeConsumed = false;
        } else if (action == MotionEvent.ACTION_MOVE && swipeFromTop &&
                event.getRawY() - swipeStartY > dp(55)) {
            swipeFromTop = false;
            swipeConsumed = true;
            mouseDown = false;
            setMenuVisible(true);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            swipeFromTop = false;
            if (swipeConsumed) {
                swipeConsumed = false;
                return true;
            }
        }
        return swipeConsumed || super.dispatchTouchEvent(event);
    }

    private String mediaExtension(int type) {
        return type == Image.TYPE_TAPE ? ".t64" : type == Image.TYPE_CARTRIDGE ? ".crt" : ".d64";
    }

    private void chooseMedia(int type) {
        setMenuVisible(false);
        requestedMediaType = type;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_INSERT_MEDIA);
    }

    private void sendC64Key(int code) {
        emuControl.keyInput(code | KeyCode.KEYFLAG_PRESSED);
        emuControl.keyInput(code | KeyCode.KEYFLAG_RELEASED);
    }

    private void sendTypedCharacter(char character) {
        int key;
        if (character >= 'a' && character <= 'z') key = KeyEvent.KEYCODE_A + character - 'a';
        else if (character >= 'A' && character <= 'Z') key = KeyEvent.KEYCODE_A + character - 'A';
        else if (character >= '0' && character <= '9') key = KeyEvent.KEYCODE_0 + character - '0';
        else {
            switch (character) {
                case ' ': key = KeyEvent.KEYCODE_SPACE; break;
                case '\n': key = KeyEvent.KEYCODE_ENTER; break;
                case '.': key = KeyEvent.KEYCODE_PERIOD; break;
                case ',': key = KeyEvent.KEYCODE_COMMA; break;
                case ':': sendC64Key(KeyCode.C64KEY_COLON); return;
                case ';': key = KeyEvent.KEYCODE_SEMICOLON; break;
                case '"': key = KeyEvent.KEYCODE_APOSTROPHE; break;
                case '*': key = KeyEvent.KEYCODE_STAR; break;
                case '/': key = KeyEvent.KEYCODE_SLASH; break;
                case '+': key = KeyEvent.KEYCODE_PLUS; break;
                case '-': key = KeyEvent.KEYCODE_MINUS; break;
                case '=': key = KeyEvent.KEYCODE_EQUALS; break;
                default: return;
            }
        }
        int code = KeyMap.translate(key);
        if (code != -1) sendC64Key(code);
    }

    private List<InputDevice> connectedControllers() {
        List<InputDevice> devices = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
                devices.add(device);
        }
        return devices;
    }

    private boolean hasConnectedController() {
        return !connectedControllers().isEmpty();
    }

    private void showControllerDialog() {
        List<InputDevice> devices = connectedControllers();
        if (devices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("No controller connected")
                    .setMessage("Pair a Bluetooth controller or connect a USB controller, then return here to map its buttons.")
                    .setPositiveButton("Bluetooth settings", (dialog, which) ->
                            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                    .setNegativeButton("Close", null).show();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < names.length; i++) names[i] = devices.get(i).getName();
        new AlertDialog.Builder(this).setTitle("Select controller")
                .setItems(names, (dialog, which) -> showMappingDialog(devices.get(which)))
                .setNegativeButton("Close", null).show();
    }

    private void showMappingDialog(InputDevice device) {
        String[] labels = new String[ControllerBindings.ACTIONS.length];
        for (int i = 0; i < labels.length; i++) {
            int code = controllerBindings.get(device, i);
            labels[i] = ControllerBindings.ACTIONS[i] + ": " +
                    (code == KeyEvent.KEYCODE_UNKNOWN ? "Unassigned" : KeyEvent.keyCodeToString(code).replace("KEYCODE_", ""));
        }
        new AlertDialog.Builder(this).setTitle(device.getName() + " buttons")
                .setItems(labels, (dialog, action) -> captureButton(device, action))
                .setNeutralButton("Reset defaults", (dialog, which) -> {
                    controllerBindings.clear(device);
                    mappedStickMask = 0;
                    gameController.init();
                    emuControl.setStick(0);
                    showMappingDialog(device);
                }).setNegativeButton("Done", null).show();
    }

    private void captureButton(InputDevice device, int action) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Map " + ControllerBindings.ACTIONS[action])
                .setMessage("Press a button on " + device.getName())
                .setNegativeButton("Cancel", null).create();
        dialog.setOnKeyListener((dismiss, code, event) -> {
            if (event.getDeviceId() != device.getId() || event.getAction() != KeyEvent.ACTION_DOWN)
                return false;
            controllerBindings.set(device, action, code);
            mappedStickMask = 0;
            gameController.init();
            emuControl.setStick(0);
            dialog.dismiss();
            showMappingDialog(device);
            return true;
        });
        dialog.show();
    }

    private boolean handleMappedButton(KeyEvent event, InputDevice device) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int[] masks = {KeyCode.C64STICK_UP, KeyCode.C64STICK_DOWN,
                KeyCode.C64STICK_LEFT, KeyCode.C64STICK_RIGHT, KeyCode.C64STICK_FIRE};
        for (int i = 0; i < ControllerBindings.ACTIONS.length; i++) {
            if (controllerBindings.get(device, i) != code) continue;
            if (i < masks.length) {
                if (down) mappedStickMask |= masks[i];
                else mappedStickMask &= ~masks[i];
                emuControl.setStick(mappedStickMask | (gameController.getState() & 0xff));
            } else if (down) {
                if (i == 5) setMenuVisible(swipeMenu.getVisibility() != View.VISIBLE);
                if (i == 6) setKeyboardVisible(!isKeyboardVisible());
            }
            return true;
        }
        return true; // unmapped gamepad buttons must not type into the C64
    }

    @Override public void onInputDeviceAdded(int id) { updateTouchControls(); }
    @Override public void onInputDeviceRemoved(int id) {
        mappedStickMask = 0;
        gameController.init();
        emuControl.setStick(0);
        updateTouchControls();
    }
    @Override public void onInputDeviceChanged(int id) { updateTouchControls(); }

    private void createKeyButtons() {

        ViewGroup view = (ViewGroup) findViewById(R.id.keyButtons);
        if (null == view) {
            logger.warning("Key button view not found");
            return;
        }

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                Button button = (Button) v;
                KeyMapEntry entry = (KeyMapEntry) button.getTag();
                if (null != entry) {
                    int c64Code = entry.getC64Code();
                    enterKey(c64Code);
                }
            }
        };

        int buttonStyle = android.R.attr.buttonStyleSmall;

        int[] shownC64Keys = {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,

            KeyEvent.KEYCODE_F1,
            KeyEvent.KEYCODE_F2,
            KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5,
            KeyEvent.KEYCODE_F6,
            KeyEvent.KEYCODE_F7,
            KeyEvent.KEYCODE_F8,

            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9,

            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_Z,
        };

        for (int k: shownC64Keys) {
            KeyMapEntry entry = KeyMap.getMap().get(k);
            if (null == entry) {
                continue;
            }

            Button button = new Button(this, null, buttonStyle);
            button.setText(entry.getKeyName().toUpperCase());
            button.setOnClickListener(clickListener);
            button.setFocusable(true);
            button.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            button.setTag(entry);

            view.addView(button);
        }

    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {

        /*
        if (isControlsVisible()) {
            return super.dispatchGenericMotionEvent(event);
        }
        */

        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!isControlsVisible() && swipeMenu.getVisibility() != View.VISIBLE) {
                if (gameController.handleEvent(event)) {
                    logger.info("update emu stick");
                    emuControl.setStick((gameController.getState() & 0xff) | mappedStickMask);
                }
                return true;
            }
        }

        return super.dispatchGenericMotionEvent(event);
    }

    private void updateScreenSize() {

        if (null == emuView) {
            return;
        }

        int receivedWidth = emuView.getWidth();
        int receivedHeight = emuView.getHeight();

        if (0 != receivedWidth && 0 != receivedHeight) {

            screenWidth = receivedWidth;
            screenHeight = receivedHeight;
        }

    }

    private boolean handleKeyEvent(KeyEvent event) {

        boolean keyUp = event.getAction()== KeyEvent.ACTION_UP;
        boolean keyDown = event.getAction()== KeyEvent.ACTION_DOWN;

        logger.info("KEY: " + event.toString());

        int keyCode = event.getKeyCode();
        char keyChar = (char) event.getUnicodeChar();

        boolean handled = true;

        if (keyCode == KeyEvent.KEYCODE_ENTER && keyDown) {
            setKeyboardVisible(false);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_UNKNOWN: {
                handled = false;
                break;
            }
            case KeyEvent.KEYCODE_MENU: {
                if (keyDown) {
                    setControlsVisible(!isControlsVisible());
                }
                break;
            }
            default: {
                int c64Code = KeyMap.translate(keyCode);
                if (-1 != c64Code) {
                    logger.info("c64 key: " + keyCode + " -> 0x" + Integer.toHexString(c64Code));
                    emuControl.keyInput(c64Code | (keyDown ? KeyCode.KEYFLAG_PRESSED : KeyCode.KEYFLAG_RELEASED));
                } else {
                    logger.info("no c64 key: " + keyCode);
                    handled = false;
                }

                break;
            }
        }

        return handled;

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (isControlsVisible()) {
            return super.onKeyDown(keyCode, event);
        }

        if (!GameController.isGameControllerEvent(event)) {
            if (keyCode != KeyEvent.KEYCODE_ENTER && handleKeyEvent(event) == true) {
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        if (isControlsVisible()) {
            return super.onKeyUp(keyCode, event);
        }

        if (!GameController.isGameControllerEvent(event)) {
            if (keyCode != KeyEvent.KEYCODE_ENTER && handleKeyEvent(event) == true) {
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (swipeMenu != null && swipeMenu.getVisibility() == View.VISIBLE &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) setMenuVisible(false);
            return true;
        }
        InputDevice device = event.getDevice();
        if (device != null && controllerBindings != null && controllerBindings.isCustomized(device)
                && (((device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
                    ((device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK))
                && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)) {
            if (event.getRepeatCount() == 0) return handleMappedButton(event, device);
            return true;
        }

        if (isControlsVisible()) {

            if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() != KeyEvent.ACTION_UP) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        setControlsVisible(false);
                    }
                    return true;
                }
            }

            return super.dispatchKeyEvent(event);
        }

        if (null != gameController) {
            if (((event.getSource() & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) ||
                    ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)){
                if (event.getRepeatCount() == 0) {
                    if (gameController.handleEvent(event)) {
                        int stickMask = gameController.getState()&0xff;
                        logger.info("update emu stick mask: " + stickMask);
                        emuControl.setStick(stickMask | mappedStickMask);
                    }
                }
                return true;
            }
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                return super.dispatchKeyEvent(event);
            }
        }

        if (handleKeyEvent(event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);

    }

    private void setControlsVisible(boolean show) {

        logger.info("COMMAND: setControlsVisible(" + show + ")");

        if (EMU_PAUSED_WHILE_CONTROL) {
            if (show) {
                emuControl.pause();
            } else {
                emuControl.resume();
            }
        }

        if (show) {
            emuControl.setStick(0x0); // clear stick input
        }
        
        controlsView.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            controlsView.requestFocus();
        } else {
            emuView.requestFocus();
        }

        updateScreenSize();
        updateTouchControls();

    }

    private boolean isControlsVisible() {
        return (View.VISIBLE == controlsView.getVisibility());
    }

    private int getTouchArea(float x, float y) {

        updateScreenSize();

        if (screenWidth < 1.0f || screenHeight < 1.0f)
            return 0x0;

        float xPercent = (x * 100.0f) / screenWidth;
        float yPercent = (y * 100.0f) / screenHeight;

        int area = 0x0;

        if (yPercent <= 10.0f) {
            area |= AREA_TOP;
        } else if (yPercent >= 20.0f && yPercent <= 80.0f) {
            area |= AREA_MIDDLE;
        } else if (yPercent >= 90.0f) {
            area |= AREA_BOTTOM;
        }

        if (xPercent <= 10.0f) {
            area |= AREA_LEFT;
        } else if (xPercent >= 20.0f && xPercent <= 80.0f) {
            area |= AREA_CENTER;
        } else if (xPercent >= 90.0f) {
            area |= AREA_RIGHT;
        }

        // logger.info("MOUSE: " + (int) x + "/" + (int) screenWidth + "  -  "
        // + (int) y + "/" + (int) screenHeight);
        // displayArea(area);

        return area;
    }

    @SuppressWarnings("unused")
    private void displayArea(int area) {
        if ((area & AREA_TOP) != 0)
            logger.info("AREA_TOP");
        if ((area & AREA_MIDDLE) != 0)
            logger.info("AREA_MIDDLE");
        if ((area & AREA_BOTTOM) != 0)
            logger.info("AREA_BOTTOM");
        if ((area & AREA_LEFT) != 0)
            logger.info("AREA_LEFT");
        if ((area & AREA_CENTER) != 0)
            logger.info("AREA_CENTER");
        if ((area & AREA_RIGHT) != 0)
            logger.info("AREA_RIGHT");
    }

    private void handleCommandClick(int area) {

        logger.info(System.currentTimeMillis() + "Handle command click: 0x" + Long.toHexString(area));

        if (0x0 == area) {
            return;
        }

        if (isControlsVisible() && (AREA_BOTTOM + AREA_RIGHT) != area) {
            logger.info("Disable control panel");
            setControlsVisible(false);
        }

        if (isKeyboardVisible() && (AREA_BOTTOM + AREA_LEFT) != area) {
            logger.info("Disable virtual keyboard");
            setKeyboardVisible(false);
        }

        if (AREA_TOP + AREA_RIGHT == area) {

            emuControl.keyInput(KeySequence.sequence_Load_Asterisk_8_1_Run);

        } else if (AREA_BOTTOM + AREA_CENTER == area) {

            emuControl.keyInput(KeyCode.C64KEY_SPACE | KeyCode.KEYFLAG_PRESSED);
            emuControl.keyInput(KeyCode.C64KEY_SPACE | KeyCode.KEYFLAG_RELEASED);

        } else if ((AREA_BOTTOM + AREA_LEFT) == area) {
            //if (false == isKeyboardVisible()) {
                logger.info("Enable virtual keyboard");
                setKeyboardVisible(true);
            //}
        } else if ((AREA_BOTTOM + AREA_RIGHT) == area) {
            if (false == isControlsVisible()) {
                logger.info("Enable control panel");
                setControlsVisible(true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void removeLayoutListenerPre16(OnGlobalLayoutListener listener) {
        emuView.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
    }

    @TargetApi(16)
    private void removeLayoutListenerPost16(OnGlobalLayoutListener listener) {
        emuView.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
    }

    public void onClickReset(View v) {
        logger.info("command: reset");
        setControlsVisible(false);
        //emuControl.softReset();
        emuControl.hardReset(null);
    }

    public void onClickShowSettings(View v) {
        setControlsVisible(false);
        if (null == settingsDialog) {
            settingsDialog = new SettingsDialog();
        }
        settingsDialog.show(getFragmentManager(), "emu_settings_dialog");
    }

    public void onClickSelectDisk(View v) {
        setControlsVisible(false);
        if (null == fileDialog) {
            fileDialog = new FileDialog();
        }
        fileDialog.show(getFragmentManager(), "emu_file_dialog");
    }

    @Override
    public void onDiskSelect(Image diskImage, ImageFilter diskFilter, boolean longClick) {

        Control emuControl = Control.instance();

        boolean status = true;
        if (!longClick) {
            status = emuControl.attachDisk(diskImage); // just insert disk
        } else {
            emuControl.hardReset(diskImage); // reset and autostart
        }

        gameController.init();
        emuControl.setStick(0x0); // clear stick input

        if (diskImage.getType() == Image.TYPE_SNAPSHOT) {
            setControlsVisible(false);
            Toast.makeText(getApplicationContext(), "Restore snapshot: " + diskImage.getName(), Toast.LENGTH_LONG).show();
            return;
        }

        if (diskImage.getType() == Image.TYPE_CARTRIDGE) {
            setControlsVisible(false);
            Toast.makeText(this, status ? "Inserted cartridge: " + diskImage.getName() :
                    "Unsupported CRT cartridge (standard 8K/16K only)", Toast.LENGTH_LONG).show();
            return;
        }

        emuControl.keyInputDelay(20);
        emuControl.keyInput(KeySequence.sequence_Load_Asterisk_8_1_Run);
        setControlsVisible(false);

        if (!longClick) {
            if (status) {
                Toast.makeText(getApplicationContext(), "Selected image: " + diskImage.getName(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "Failed to select image: " + diskImage.getName(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Quick start: " + diskImage.getName(), Toast.LENGTH_LONG).show();
        }

    }

    public void onClickExitApp(View v) {
        finish();
    }

    public void onClickPauseResume(View v) {
        if (!emuControl.isPaused()) {
            emuControl.pause();
        } else {
            emuControl.resume();
        }
    }

    public void onClickAnyKey(View v) {
        setControlsVisible(false);
        emuControl.keyInput(KeySequence.sequenceAllKeys);
    }

    private void enterKey(int key) {
        enterKey(key, false);
    }

    private void enterKey(int key, boolean shift) {
        setControlsVisible(false);
        if (shift) key |= KeyCode.C64KEY_FLAG_SHIFT;
        emuControl.keyInput(key | KeyCode.KEYFLAG_PRESSED);
        emuControl.keyInput(key | KeyCode.KEYFLAG_RELEASED);
    }

    private void setKeyboardVisible(boolean visible) {
        keyboardVisible = visible;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (visible) {
            keyboardInput.requestFocus();
            keyboardInput.post(() -> imm.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT));
        } else {
            imm.hideSoftInputFromWindow(keyboardInput.getWindowToken(), 0);
            keyboardInput.clearFocus();
            emuView.requestFocus();
        }
        updateTouchControls();
    }

    private boolean isKeyboardVisible() {

        return keyboardVisible;

    }

    public void onClickLoad(View v) {
        setControlsVisible(false);
        emuControl.keyInput(KeySequence.sequence_Load_Asterisk_8_1);
    }

    public void onClickRun(View v) {
        setControlsVisible(false);
        emuControl.keyInput(KeySequence.sequence_Run);
    }

    public void onClickRunStop(View v) {
        enterKey(KeyCode.C64KEY_RUNSTOP);
    }

    public void onClickRunStopRestore(View v) {
        setControlsVisible(false);
        emuControl.keyInput(KeyCode.C64KEY_RUNSTOP | KeyCode.KEYFLAG_PRESSED);
        emuControl.keyInput(KeyCode.C64KEY_RESTORE | KeyCode.KEYFLAG_PRESSED);
        emuControl.keyInput(KeyCode.C64KEY_RESTORE | KeyCode.KEYFLAG_RELEASED);
        emuControl.keyInput(KeyCode.C64KEY_RUNSTOP | KeyCode.KEYFLAG_RELEASED);
    }

    public void onClickShiftRunStop(View v) {
        enterKey(KeyCode.C64KEY_RUNSTOP, true);
    }

    private void checkOpenGL() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        logger.info("GLES version: 0x" + Integer.toHexString(info.reqGlEsVersion)); // >= 0x20000;
    }

    @Override
    protected void onStart() {
        super.onStart();
        logger.info("Activity.onStart()");
        emuControl.start();
        if (isControlsVisible()) {
            emuControl.pause();
        }
    }

    @Override
    protected void onPause() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        super.onPause();
        logger.info("Activity.onPause()");
        emuControl.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inputManager != null) inputManager.registerInputDeviceListener(this, null);
        updateTouchControls();
        logger.info("Activity.onResume()");
        if (isControlsVisible()) {
            emuControl.pause();
        } else {
            emuControl.resume();
        }
    }

    @Override
    protected void onRestart() {
        logger.info("Activity.onRestart()");
        emuControl.stop();
        emuControl.start();
        if (isControlsVisible()) {
            emuControl.pause();
        }
        super.onRestart();
    }

    @Override
    protected void onStop() {
        logger.info("Activity.onStop()");
        emuControl.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        logger.info("Activity.onDestroy()");

        if (null != emuControl) {
            emuControl.stop();
            emuControl = null;
        }

        super.onDestroy();
    }

    public void onClickStoreSnapshot(View v) {
        logger.info("command: store snapshot");
        setControlsVisible(false);
        emuControl.storeSnapshot();
    }

    public void onClickRestoreSnapshot(View v) {
        logger.info("command: restore snapshot");
        setControlsVisible(false);
        emuControl.restoreSnapshot();
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}

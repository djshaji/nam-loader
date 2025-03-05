package org.acoustixaudio.namloader;

import static android.os.Environment.DIRECTORY_MUSIC;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ComponentCaller;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.browse.MediaBrowser;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.material.slider.Slider;
import com.shajikhan.ladspa.amprack.AudioEngine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {
    private static final int AUDIO_EFFECT_REQUEST = 0;
    private static final String TAG = MainActivity.class.getName();
    static SharedPreferences defaultSharedPreferences ;

    static {
        System.loadLibrary("amprack");
    }

    private boolean running = false;
    private static Context context;
    private String dir = null;
    MainActivity mainActivity ;
    ToggleButton record ;
    private int REQUEST_CODE_NAM = 1;
    Spinner namSpinner, irSpinner ;
    public static boolean proVersion = false ;
    private String filename, basename;
    LinearLayout lastRecordedBox;
    TextView lastFilename;
    private ExoPlayer mediaPlayer;
    String [] fileTypes = {
            ".wav",
            ".ogg",
            ".mp3"
    } ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this ;
        mainActivity = this;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mediaPlayer = new ExoPlayer.Builder(context).build();

        ToggleButton onoff = findViewById(R.id.onoff);
        onoff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    startEffect();
                } else {
                    stopEffect();
                }
            }
        });

        dir = getExternalFilesDir(DIRECTORY_MUSIC).getPath();
        File folder = getExternalFilesDir(DIRECTORY_MUSIC);
        if (! folder.exists()) {
            if (!folder.mkdirs()) {
                Toast.makeText(context, "Unable to create recording files directory", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, String.format("created folder: %s", folder.getAbsolutePath()));
            }
        } else {
            Log.d(TAG, String.format ("folder exists: %s", folder.getAbsolutePath()));
        }

        ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        // Handle the returned Uri
                        Log.i(TAG, "onActivityResult: " + uri);
                        loadNAMModel(uri, "nam", namSpinner, 1);
                    }
                });

        ActivityResultLauncher<String> mGetIR = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        // Handle the returned Uri
                        Log.i(TAG, "onActivityResult: " + uri);
                        loadNAMModel(uri, "ir", irSpinner, 13);
                    }
                });

        lastRecordedBox = findViewById(R.id.last_recorded_box);

        Button loadModel = findViewById(R.id.load_nam);
        loadModel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent_upload = new Intent();
                intent_upload.setType("application/json");
                String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension("json");
                String [] mimeTypes = {
                        "*/*",
                        "text/*",
                        "application/x-zip",
                        "application/ld-json",
                        "application/json",
                        mimetype
                };


                intent_upload.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

                intent_upload.setAction(Intent.ACTION_OPEN_DOCUMENT);
                intent_upload.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent_upload.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                int requestCode = REQUEST_CODE_NAM;
//                startActivityForResult(intent_upload,requestCode);
                mGetContent.launch("*/*");
            }
        });

        namSpinner = findViewById(R.id.nam_spinner);
        irSpinner = findViewById(R.id.ir_spinner);
        Button namPre = findViewById(R.id.nam_pre);
        namPre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = namSpinner.getSelectedItemPosition();
                pos -- ;
                if (pos >= 0)
                    namSpinner.setSelection(pos);
            }
        });

        Button namNext = findViewById(R.id.nam_next);
        namNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = namSpinner.getSelectedItemPosition();
                pos ++ ;
                if (pos < namSpinner.getAdapter().getCount())
                    namSpinner.setSelection(pos);
            }
        });

        setSpinnerFromDir(namSpinner, context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/nam", null);
        namSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String m = namSpinner.getAdapter().getItem(i).toString();
                if (m.isEmpty())
                    return;

                String dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/nam/" ;
                Uri ri = Uri.parse("file://" + dir + m);
                Log.d(TAG, String.format ("sending filename: %s", ri.getPath()));
                for (int s = 0 ; s < 21 ; s ++) {
                    Log.i(TAG, "onItemSelected: port " + s + ' ' + AudioEngine.getControlName(0, s));
                }

                AudioEngine.setAtomPort(0, 17, ri.getPath());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Slider gainIn = findViewById(R.id.slider_input);
        gainIn.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                AudioEngine.setPluginControl(0, 9, value);
            }
        });

        Slider gainOut = findViewById(R.id.slider_output);
        gainOut.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                AudioEngine.setPluginControl(0, 15, value);
            }
        });

        Slider mix = findViewById(R.id.slider_mix);
        mix.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                AudioEngine.setPluginControl(0, 18, value);
            }
        });

        Button settingsBtn = findViewById(R.id.settings_btn);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(context, SettingsActivity.class));
            }
        });

        irSpinner = findViewById(R.id.ir_spinner);
        setSpinnerFromDir(irSpinner, context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/ir", null);

        Button irPre = findViewById(R.id.ir_pre);
        Button irNext = findViewById(R.id.ir_next);

        irPre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = irSpinner.getSelectedItemPosition();
                pos -- ;
                if (pos >= 0)
                    irSpinner.setSelection(pos);

            }
        });


        Button lastShare = findViewById(R.id.share_last);

        lastShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (filename != null)
                    shareFile(new File(filename + ".mp3"));
            }
        });

        irNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = irSpinner.getSelectedItemPosition();
                pos ++ ;
                if (pos < irSpinner.getAdapter().getCount())
                    irSpinner.setSelection(pos);

            }
        });

        irSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String m = irSpinner.getAdapter().getItem(i).toString();
                if (m.isEmpty())
                    return;

                String dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/ir/" ;
                Uri ri = Uri.parse("file://" + dir + m);
                Log.d(TAG, String.format ("sending filename: %s", ri.getPath()));
//                for (int s = 0 ; s < 19 ; s ++) {
//                    Log.i(TAG, "onItemSelected: port " + s + ' ' + AudioEngine.getControlName(0, s));
//                }

                AudioEngine.setAtomPort(0, 13, ri.getPath());

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Button irLoad = findViewById(R.id.ir_load);
        irLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent_upload = new Intent();
                intent_upload.setType("application/json");
                String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension("json");
                String [] mimeTypes = {
                        "*/*",
                        "text/*",
                        "application/x-zip",
                        "application/ld-json",
                        "application/json",
                        mimetype
                };


                intent_upload.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

                intent_upload.setAction(Intent.ACTION_OPEN_DOCUMENT);
                intent_upload.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent_upload.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                int requestCode = REQUEST_CODE_NAM;
//                startActivityForResult(intent_upload,requestCode);
                mGetIR.launch("*/*");

            }
        });

        ((Button) findViewById(R.id.recordings)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(context, Recordings.class));
            }
        });

        lastFilename = findViewById(R.id.last_filename);
        ToggleButton lastPlayPause = findViewById(R.id.last_play);

        lastFilename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastPlayPause.performClick();
            }
        });

        lastPlayPause.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "onCheckedChanged: playing " + filename);
                    mediaPlayer.play();
                    buttonView.setBackground(getResources().getDrawable(R.drawable.baseline_pause_24));
                } else {
                    Log.i(TAG, "onCheckedChanged: pause");
                    mediaPlayer.pause();
                    buttonView.setBackground(getResources().getDrawable(R.drawable.baseline_play_arrow_24));
                }
            }
        });

        mediaPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.i(TAG, "onIsPlayingChanged: " + isPlaying);
                Player.Listener.super.onIsPlayingChanged(isPlaying);
                if (! isPlaying) {
                    lastPlayPause.setChecked(false);
                    MediaItem mediaItem = MediaItem.fromUri(filename +
                            fileTypes [Integer.parseInt(defaultSharedPreferences.getString("format", "2"))]);
                    mediaPlayer.setMediaItem(mediaItem);
                    mediaPlayer.prepare();
                }
            }
        });

        mediaPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                Log.e(TAG, "onPlayerError: ", error);
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        mediaPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerErrorChanged(@Nullable PlaybackException error) {
                Player.Listener.super.onPlayerErrorChanged(error);
                if (error != null)
                    Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "onPlayerErrorChanged: ", error);
            }
        });

        Button renameLast = findViewById(R.id.last_edit);
        renameLast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renameFile(basename, -1);
            }
        });


        record = findViewById(R.id.rec);
        record.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    lastPlayPause.setChecked(false);
//                    buttonView.setCompoundDrawablesWithIntrinsicBounds(null,getResources().getDrawable(R.drawable.stop1),null,null);

                    SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy_HH.mm.ss");
                    Date date = new Date();
                    basename = formatter.format(date);
                    filename = new StringJoiner("/").add (dir).add (basename).toString();
                    applySettings();
                    AudioEngine.setFileName(filename);
                    Log.d(TAG, String.format ("[filename]: %s", filename));

//                    if (! running)
//                        startEffect();


                    if (! onoff.isChecked())
                        onoff.setChecked(true);

                    AudioEngine.toggleRecording(true);
                    lastRecordedBox.setVisibility(View.GONE);
                } else {
//                    buttonView.setCompoundDrawablesWithIntrinsicBounds(null,getResources().getDrawable(R.drawable.record),null,null);

                    if (! onoff.isChecked ())
                        stopEffect();

                    lastFilename.setText(new File (filename).getName());
                    lastRecordedBox.setVisibility(View.VISIBLE);

                    mediaPlayer = new ExoPlayer.Builder(context).build();
                    MediaItem mediaItem = MediaItem.fromUri(filename +
                            fileTypes [Integer.parseInt(defaultSharedPreferences.getString("format", "2"))]);
                    mediaPlayer.setMediaItem(mediaItem);
                    mediaPlayer.prepare();

                    Log.i(TAG, "onCheckedChanged: set media item " + filename);
                }
            }
        });

        AudioEngine.create();
        AudioEngine.setExportFormat(2);
        AudioEngine.popFunction(); // this disables the meter output
        AudioEngine.setLibraryPath(getApplicationInfo().nativeLibraryDir);
        AudioEngine.setLazyLoad(true);
        AudioEngine.pushToLockFreeBeforeOutputVolumeAaaaaargh(true);

        AudioEngine.setInputVolume(1f);
        AudioEngine.setOutputVolume(1f);

        AudioEngine.setMainActivityClassName("org/acoustixaudio/namloader/MainActivity");
        AudioEngine.addPluginLazyLV2("libRatatouille.so", 0);
    }

    private void startEffect() {
        Log.d(TAG, "Attempting to start");
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy_HH.mm.ss");
        Date date = new Date();
        basename = formatter.format(date);
        filename = new StringJoiner("/").add (getExternalFilesDir(DIRECTORY_MUSIC).getPath()).add (basename).toString();

        AudioEngine.setFileName(filename);
        applySettings();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!isRecordPermissionGranted()) {
            requestRecordPermission();
            return;
        }

        running = AudioEngine.setEffectOn(true);
    }

    private void stopEffect() {
        if (!running) return;

        if (record.isChecked()) {
            record.setChecked(false);
        }

        Log.d(TAG, "Playing, attempting to stop, state: " + running);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        running = !AudioEngine.setEffectOn(false);
    }

    private boolean isRecordPermissionGranted() {
        return (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED);
    }

    private void requestRecordPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.RECORD_AUDIO},
                AUDIO_EFFECT_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == AUDIO_EFFECT_REQUEST) {
            int x = 0;
            for (String p : permissions) {
                if (p.equals(Manifest.permission.RECORD_AUDIO)) {
                    if (grantResults[x] == PackageManager.PERMISSION_GRANTED) {
                        startEffect();
                    }
                }

                x++;
            }

            return;
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data, @NonNull ComponentCaller caller) {
        Log.i(TAG, "onActivityResult: " + requestCode + " " + resultCode);
        super.onActivityResult(requestCode, resultCode, data, caller);

        if (requestCode == REQUEST_CODE_NAM && resultCode == RESULT_OK) {

        }
    }

    static void setMixerMeterSwitch(float inputValue, boolean isInput) {

    }

    static void setTuner (float [] data, int size) {

    }

    public static void pushToVideo (float [] data, int nframes) {

    }

    public static void setSampleRateDisplay (int sampleRateDisplay, boolean lowLatency) {

    }

    public void alert(String title, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(text)
                .setTitle(title)
                .setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void unzipNAMModel (String dir, Uri uri) {
        InputStream inputStream = null;
        String basename = null ;
        try {
            inputStream = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            alert("Cannot load model", e.getMessage());
            Log.e(TAG, "onActivityResult: ", e);
        }
        try {
            basename = unzip(inputStream, dir);
        } catch (IOException e) {
            alert("Cannot unzip model", e.getMessage());
            Log.e(TAG, "onActivityResult: ", e);
        }

        toast("Successfully extracted Model " + basename);
    }

    public void toast(String text) {
        Toast.makeText(this,
                        text,
                        Toast.LENGTH_LONG)
                .show();

    }

    public int setSpinnerFromDir (Spinner spinner, String dir, String toSelect) {
        if (spinner == null) {
            return 0 ;
        }

        int selection = 0 ;
        ArrayList<String> models = new ArrayList<>();
        models.add ("");

        DocumentFile root = DocumentFile.fromFile(new File(dir));
        DocumentFile [] files = root.listFiles() ;
        int counter = 0 ;
        for (DocumentFile file: files) {
            Log.d(TAG, String.format ("%s: %s", file.getName(), file.getUri()));
            models.add(file.getName());
            if (toSelect != null && file.getName().equals(toSelect))
                selection = counter ;
            counter ++ ;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mainActivity,
                android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return selection;
    }


    private void copyFile(Uri pathFrom, Uri pathTo) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(pathFrom)) {
            if(in == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(pathTo)) {
                if(out == null) return;
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    /**
     * Extracts a zip file specified by the zipFilePath to a directory specified by
     * destDirectory (will be created if does not exists)
     * @param zipFilePath
     * @param destDirectory
     * @throws IOException
     */
    public static String unzip(InputStream zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        String dirname = null ;
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(zipFilePath);
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            Log.d("SkinEngine", String.format ("[unzip] %s: %s", destDirectory, entry.getName()));
            String filePath = destDirectory + File.separator + entry.getName();
            if (dirname == null)
                dirname = entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
//                Log.d("Skin Engine", String.format ("%s: %s", destDirectory, entry.getName()));
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdirs();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
        return dirname;
    }

    /**
     * Extracts a zip entry (file entry)
     * @param zipIn
     * @param filePath
     * @throws IOException
     */
    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    public static String getLV2Info (String libraryName, String plugin) {
        String pluginName ;
        if (plugin.indexOf("#") != -1)
            pluginName = plugin.split("#")[1];
        else {
            String [] p = plugin.split("/");
            pluginName = p [p.length -1];
        }

        Log.d(TAG, "getLV2Info: lv2/" + libraryName + "/" + pluginName + ".json");
        JSONObject jsonObject = loadJSONFromAssetFile(context, "lv2/" + libraryName + "/" + pluginName + ".json");
        return jsonObject.toString();
    }

    static public JSONObject loadJSONFromAssetFile(Context context, String filename) {
        String json = null;
        try {
            InputStream is = context.getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            Log.e(TAG, "loadJSONFromAsset: unable to parse json " + filename, ex);
            return null;
        }

        JSONObject jsonObject = null ;
        try {
            jsonObject = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "loadJSONFromAsset: cannot parse json " + filename, e);
        }

        return jsonObject;
    }

    void loadNAMModel (Uri returnUri, String what, Spinner spinner, int control) {
        if (returnUri == null)
            return;

        int plugin = 0;
        DocumentFile file = DocumentFile.fromSingleUri(mainActivity, returnUri);
        Log.d(TAG, String.format("ayyo filename: %s [%s]", file.getName(), file.getUri()));
        String dir = context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS) + "/" + what ;

//                String basename = file.getName() ; //returnUri.getLastPathSegment();
//                basename = basename.substring(basename.lastIndexOf(":") + 1);
//                Log.d(TAG, String.format("[basename]: %s", basename));
        String dest = dir + "/" + file.getName();
        File fDir = new File(dir);
        if (!fDir.exists()) {
            if (!fDir.mkdirs()) {
                alert("Cannot create directory", "Error loading model: " + dir);
                return;
            }
        }

        String path = file.getName();
        String ext = path.substring(path.toString().lastIndexOf('.') + 1);

        Log.d(TAG, String.format("extension: %s", ext));
        if (ext.equalsIgnoreCase("zip")) {
            unzipNAMModel(dir, returnUri);
            if (spinner == null) {
                Log.d(TAG, "onActivityResult: spinner is null! why???");
            } else
                setSpinnerFromDir(spinner, dir, null);
            return;
        }

        try {
//                    copy (new File(file.getUri().getPath()), new File(dest));
            copyFile(returnUri, Uri.parse("file://" + dest));
        } catch (IOException e) {
            alert("Error loading file", e.getMessage());
            Log.e(TAG, "onActivityResult: ", e);
            return;
        }

        Log.d(TAG, String.format("[copy file]: %s -> %s", returnUri.getPath(), dest));
        Log.d(TAG, String.format("[load atom]: got filename %s", dest));
        int selection = setSpinnerFromDir(spinner, dir, file.getName());
        spinner.setSelection(selection);
        AudioEngine.setAtomPort(plugin, control, dest);

    }


    public static void applySettings () {
        String input = defaultSharedPreferences.getString("input", "-1");
        String output = defaultSharedPreferences.getString("output", "-1");
        Log.d(TAG, "applyPreferences: [devices] " + String.format("input: %s, output: %s", input, output));

        Log.d(TAG, "applyPreferencesDevices: " + String.format(
                "[preferences] playback device: %s, recording device: %s",
                output, input
        ));

        try {
            if (!input.equals("default") && ! input.equals("-1"))
                AudioEngine.setRecordingDeviceId(new Integer(input));
            if (!output.equals("default") && ! input.equals("-1"))
                AudioEngine.setPlaybackDeviceId(new Integer(output));

//            AudioEngine.setLamePreset(Integer.parseInt(defaultSharedPreferences.getString("lame_preset", "1001")));
            AudioEngine.setExportFormat(Integer.parseInt(defaultSharedPreferences.getString("format", "2")));
        } catch (NumberFormatException e) {
            Log.e(TAG, "applySettings: ", e);
        }
    }

    public void renameFile (String oldName, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = mainActivity.getLayoutInflater();
        LinearLayout linearLayout = (LinearLayout) inflater.inflate(R.layout.get_filename, null);
        EditText textView = linearLayout.findViewById(R.id.filename);
        TextView title = linearLayout.findViewById(R.id.preset_name);
        title.setText("Enter filename");
        textView.setText(oldName);


        builder.setView(linearLayout)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        CharSequence filename = textView.getText() ;
                        if (filename.equals("") || filename == null || filename.equals(oldName))
                            return;

                        File file = new File(new StringJoiner("/").add (mainActivity.getExternalFilesDir(DIRECTORY_MUSIC).getAbsolutePath()).add (oldName).toString() + ".mp3");
                        file.renameTo(new File(new StringJoiner("/").add (mainActivity.getExternalFilesDir(DIRECTORY_MUSIC).getAbsolutePath()).add (filename).toString() + ".mp3"))  ;
                        mainActivity.lastFilename.setText(filename);
                    }
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AlertDialog.Builder b = new AlertDialog.Builder(context);
                        b.setMessage("you want to delete this recording?")
                                .setTitle("Are you sure")
                                .setIcon(R.drawable.baseline_delete_24)
                                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        File f = new File (filename + ".mp3");
                                        if (f.delete()) {
                                            Toast.makeText(MainActivity.this, "Recording deleted", Toast.LENGTH_SHORT).show();
                                            lastRecordedBox.setVisibility(View.GONE);
                                        } else {
                                            Toast.makeText(MainActivity.this, "Cannot delete file", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }).setNegativeButton("Cancel", null);
                        b.show();
                    }
                });

        builder.show();
    }

    public static void shareFile(File file) {
        Intent intentShareFile = new Intent(Intent.ACTION_SEND);
        Uri contentUri = null;
        try {
            contentUri = FileProvider.getUriForFile(context, "org.acoustixaudio.tonesmith.fileprovider", file);
        } catch (IllegalArgumentException illegalArgumentException) {
            Log.e(TAG, "shareFile: ", illegalArgumentException);
            Toast.makeText(context, illegalArgumentException.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        intentShareFile.setType("audio/*");
        intentShareFile.putExtra(Intent.EXTRA_STREAM, contentUri);

        intentShareFile.putExtra(Intent.EXTRA_SUBJECT,
                "Sharing Audio File...");
        intentShareFile.putExtra(Intent.EXTRA_TEXT, context.getResources().getString(R.string.app_name) + " recorded audio ...");

        intentShareFile.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intentShareFile, "Share Audio File"));

    }


    @Override
    protected void onStop() {
        super.onStop();
        mediaPlayer.stop();
    }

}
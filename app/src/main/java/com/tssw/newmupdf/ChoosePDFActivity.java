package com.tssw.newmupdf;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ListView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.facebook.stetho.Stetho;
import com.tssw.newmupdf.R;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

enum Purpose {
    PickPDF,
    PickKeyFile
}

public class ChoosePDFActivity extends ListActivity {
    static public final String PICK_KEY_FILE = "com.artifex.mupdfdemo.PICK_KEY_FILE";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_CODE = 101;
    private static final String TAG = "MYTAG";
    static private File mDirectory;
    static private Map<String, Integer> mPositions = new HashMap<String, Integer>();
    private File mTopDirectory;
    private File mParent;
    private File[] mDirs;
    private File[] mFiles;
    private Handler mHandler;
    private Runnable mUpdateFiles;
    private ChoosePDFAdapter adapter;
    private Purpose mPurpose;
    private Button chooseDocument;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Stetho.initializeWithDefaults(this);

        if (!checkPermission()) {
            requestPermission();
        }

        mPurpose = PICK_KEY_FILE.equals(getIntent().getAction()) ? Purpose.PickKeyFile : Purpose.PickPDF;

        String storageState = Environment.getExternalStorageState();

        if (!Environment.MEDIA_MOUNTED.equals(storageState)
                && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.no_media_warning);
            builder.setMessage(R.string.no_media_hint);
            AlertDialog alert = builder.create();
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                    new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
            alert.show();
            return;
        }

        mDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        mTopDirectory = mDirectory.getParentFile();

        // update dynamically list adapter when files are scanned
        mHandler = new Handler();
        mUpdateFiles = new Runnable() {
            public void run() {
                Resources res = getResources();

                String appName = res.getString(R.string.app_name);
                String version = res.getString(R.string.version);
                String title = res.getString(R.string.picker_title_App_Ver_Dir);
                setTitle(String.format(title, appName, version, mDirectory));

                setTitle(mDirectory.toString());

                mParent = null;
                if (!mDirectory.equals(mTopDirectory))
                    mParent = mDirectory.getParentFile();

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

                    // Create a list adapter...
                    adapter = new ChoosePDFAdapter(getLayoutInflater());
                    setListAdapter(adapter);

                    mDirs = mDirectory.listFiles(new FileFilter() {
                        public boolean accept(File file) {
                            return file.isDirectory();
                        }
                    });
                    if (mDirs == null)
                        mDirs = new File[0];

                    mFiles = mDirectory.listFiles(new FileFilter() {
                        public boolean accept(File file) {
                            if (file.isDirectory())
                                return false;
                            String fname = file.getName().toLowerCase();
                            switch (mPurpose) {
                                case PickPDF:
                                    if (fname.endsWith(".pdf"))
                                        return true;
                                    if (fname.endsWith(".xps"))
                                        return true;
                                    if (fname.endsWith(".cbz"))
                                        return true;
                                    if (fname.endsWith(".epub"))
                                        return true;
                                    if (fname.endsWith(".fb2"))
                                        return true;
                                    if (fname.endsWith(".png"))
                                        return true;
                                    if (fname.endsWith(".jpe"))
                                        return true;
                                    if (fname.endsWith(".jpeg"))
                                        return true;
                                    if (fname.endsWith(".jpg"))
                                        return true;
                                    if (fname.endsWith(".jfif"))
                                        return true;
                                    if (fname.endsWith(".jfif-tbnl"))
                                        return true;
                                    if (fname.endsWith(".tif"))
                                        return true;
                                    if (fname.endsWith(".tiff"))
                                        return true;
                                    return false;
                                case PickKeyFile:
                                    if (fname.endsWith(".pfx"))
                                        return true;
                                    return false;
                                default:
                                    return false;
                            }
                        }
                    });


                    if (mFiles == null)
                        mFiles = new File[0];

                    Arrays.sort(mFiles, new Comparator<File>() {
                        public int compare(File arg0, File arg1) {
                            return arg0.getName().compareToIgnoreCase(arg1.getName());
                        }
                    });

                    Arrays.sort(mDirs, new Comparator<File>() {
                        public int compare(File arg0, File arg1) {
                            return arg0.getName().compareToIgnoreCase(arg1.getName());
                        }
                    });

                    adapter.clear();
                    if (mParent != null)
                        adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.PARENT, getString(R.string.parent_directory)));
                    for (File f : mDirs)
                        adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DIR, f.getName()));
                    for (File f : mFiles)
                        adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DOC, f.getName()));

                    lastPosition();

                } else {
                    setContentView(R.layout.choose_pdf_document);

                    chooseDocument = findViewById(R.id.searchDocsButton);
                    chooseDocument.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String pdf = MimeTypeMap.getSingleton().getMimeTypeFromExtension("pdf");
                            String doc = MimeTypeMap.getSingleton().getMimeTypeFromExtension("doc");
                            String docx = MimeTypeMap.getSingleton().getMimeTypeFromExtension("docx");
                            String xls = MimeTypeMap.getSingleton().getMimeTypeFromExtension("xls");
                            String xlsx = MimeTypeMap.getSingleton().getMimeTypeFromExtension("xlsx");
                            String ppt = MimeTypeMap.getSingleton().getMimeTypeFromExtension("ppt");
                            String pptx = MimeTypeMap.getSingleton().getMimeTypeFromExtension("pptx");
                            String txt = MimeTypeMap.getSingleton().getMimeTypeFromExtension("txt");
                            String rtx = MimeTypeMap.getSingleton().getMimeTypeFromExtension("rtx");
                            String rtf = MimeTypeMap.getSingleton().getMimeTypeFromExtension("rtf");
                            String html = MimeTypeMap.getSingleton().getMimeTypeFromExtension("html");

                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setFlags((Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
                            intent.setType("*/*");
                            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{pdf, doc, docx, xls, xlsx, ppt, pptx, txt, rtx, rtf, html});
                            startActivityForResult(Intent.createChooser(intent, "Pick a file"), REQUEST_CODE);
                        }
                    });
                }
            }
        };

        // Start initial file scan...
        mHandler.post(mUpdateFiles);

        // ...and observe the directory and scan files upon changes.
        FileObserver observer = new FileObserver(mDirectory.getPath(), FileObserver.CREATE | FileObserver.DELETE) {
            public void onEvent(int event, String path) {
                mHandler.post(mUpdateFiles);
            }
        };
        observer.startWatching();
    }


    private void lastPosition() {
        String p = mDirectory.getAbsolutePath();
        if (mPositions.containsKey(p))
            getListView().setSelection(mPositions.get(p));
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        ChoosePDFItem item = (ChoosePDFItem) adapter.getItem(position);
        String name = item.name;

        mPositions.put(mDirectory.getAbsolutePath(), getListView().getFirstVisiblePosition());

        if (position < (mParent == null ? 0 : 1)) {
            mDirectory = mParent;
            mHandler.post(mUpdateFiles);
            return;
        }

        position -= (mParent == null ? 0 : 1);

        if (position < mDirs.length) {
            mDirectory = mDirs[position];
            mHandler.post(mUpdateFiles);
            return;
        }

        position -= mDirs.length;

        Uri uri = Uri.fromFile(mFiles[position]);
        Intent intent = new Intent(this, MuPDFActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        switch (mPurpose) {
            case PickPDF:
                // Start an activity to display the PDF file
                startActivity(intent);
                break;
            case PickKeyFile:
                // Return the uri to the caller
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                if (data != null && data.getData() != null) {
                    Log.d(TAG, "URI:" + data.getData());
                    Uri uri = data.getData();
                    Intent intentmupdf = new Intent(this, MuPDFActivity.class);
                    intentmupdf.setAction(Intent.ACTION_VIEW);
                    intentmupdf.setData(uri);
                    startActivity(intentmupdf);
                } else {
                    Log.d("TAG", "File uri not found");
                }
            } else {
                Log.d(TAG, "User cancelled file browsing");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mDirectory != null)
            mPositions.put(mDirectory.getAbsolutePath(), getListView().getFirstVisiblePosition());
    }

    private boolean checkPermission() {
        int resultWrite = ContextCompat.checkSelfPermission(ChoosePDFActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int resultRead = ContextCompat.checkSelfPermission(ChoosePDFActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE);
        return (resultWrite == PackageManager.PERMISSION_GRANTED && resultRead == PackageManager.PERMISSION_GRANTED);
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:

                if (grantResults.length > 0) {
                    boolean readStorageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean writeStorageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if (readStorageAccepted && writeStorageAccepted)
                        Log.i(TAG, "Permission Granted, Now you can use local drive.");
                    else {
                        Log.i(TAG, "Permission Denied, You cannot use local drive.");

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                                showMessageOKCancel("You need to allow access to storage to continue",
                                        new OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                                            PERMISSION_REQUEST_CODE);
                                                }
                                            }
                                        });
                                return;
                            }
                        }

                    }

                }
                break;
        }
    }

    private void showMessageOKCancel(String message, OnClickListener okListener) {
        new AlertDialog.Builder(ChoosePDFActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

}

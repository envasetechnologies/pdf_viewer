package com.tssw.newmupdf;

import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tssw.newmupdf.BuildConfig;
import com.tssw.newmupdf.R;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Resources res = getResources();
        setTitle(res.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        TextView aboutTextLicense = findViewById(R.id.aboutTextLicense);
        TextView aboutTextSourceCode = findViewById(R.id.aboutTextSourceCode);

        aboutTextLicense.setText(res.getString(R.string.license));
        aboutTextSourceCode.setText(res.getString(R.string.source_code));
    }
}
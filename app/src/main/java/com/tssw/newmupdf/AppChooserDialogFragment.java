package com.tssw.newmupdf;


import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.tssw.newmupdf.R;

import java.util.ArrayList;
import java.util.List;

public class AppChooserDialogFragment extends BottomSheetDialogFragment implements GenericAndroidAppAdapter.OnItemClickListener {
    private static final String KEY_APPS = "KEY_APPS";
    private static final String KEY_TITLE = "KEY_TITLE";
    private static final String KEY_REQUEST_CODE = "KEY_REQUEST_CODE";

    public static void show(FragmentActivity activity, ArrayList<Intent> targets, String title, int requestCode) {
        PackageManager packageManager = activity.getPackageManager();
        ArrayList<GenericAndroidApp> apps = new ArrayList<>();
        for (Intent intent : targets) {
            List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : activities) {
                Intent targetIntent = new Intent(intent);
                apps.add(new GenericAndroidApp(targetIntent, resolveInfo));
            }
        }

        if (apps.size() > 0) {
            if (apps.size() == 0) {
                activity.startActivityForResult(apps.get(0).intent, requestCode);
            } else {
                DialogFragment appChooserDialog = new AppChooserDialogFragment();
                Bundle data = new Bundle();
                data.putParcelableArrayList(KEY_APPS, apps);
                data.putString(KEY_TITLE, title);
                data.putInt(KEY_REQUEST_CODE, requestCode);
                appChooserDialog.setArguments(data);
                appChooserDialog.show(activity.getSupportFragmentManager(), "AppChooserDialog");
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.dialog_app_chooser, container, false);

        TextView titleTextView = rootView.findViewById(R.id.text_view_title);
        RecyclerView appsRecyclerView = rootView.findViewById(R.id.recycler_view_apps);

        String title = getArguments().getString(KEY_TITLE);
        if (!TextUtils.isEmpty(title)) {
            titleTextView.setText(title);
        }

        List<GenericAndroidApp> apps = getArguments().getParcelableArrayList(KEY_APPS);
        appsRecyclerView.setAdapter(new GenericAndroidAppAdapter(apps, this));

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float screenWidthInDp = displayMetrics.widthPixels / displayMetrics.density;
        int recyclerViewItemWidthInDp = 72;
        int recyclerViewStartEndPadding = 32;
        int numberOfColumns = (int) ((screenWidthInDp - recyclerViewStartEndPadding) / recyclerViewItemWidthInDp);
        int spanCount = (apps.size() < numberOfColumns) ? apps.size() : numberOfColumns;
        appsRecyclerView.setLayoutManager(new GridLayoutManager(requireActivity(), spanCount));

        return rootView;
    }


    @Override
    public void onItemClick(GenericAndroidApp app) {
        ActivityInfo activity = app.resolveInfo.activityInfo;
        String packageName = activity.applicationInfo.packageName;
        ComponentName component = new ComponentName(packageName, activity.name);

        Intent intent = new Intent(app.intent);
        intent.setComponent(component);

        Uri uri = app.intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
        if (uri != null) {
            requireActivity().grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        OnAppSelectedListener listener = null;
        try {
            listener = (OnAppSelectedListener) requireActivity();
        } catch (Exception e) {
            // Ignore exception
        }
        if (listener != null) {
            listener.onAppSelected(intent);
        }

        requireActivity().startActivityForResult(intent, getArguments().getInt(KEY_REQUEST_CODE));
        dismiss();
    }

    public interface OnAppSelectedListener {
        void onAppSelected(Intent intent);
    }
}
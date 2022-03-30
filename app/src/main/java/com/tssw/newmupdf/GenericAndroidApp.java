package com.tssw.newmupdf;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Parcel;
import android.os.Parcelable;

public class GenericAndroidApp implements Parcelable {
    public Intent intent;
    public ResolveInfo resolveInfo;

    public GenericAndroidApp(Intent intent, ResolveInfo resolveInfo) {
        this.intent = intent;
        this.resolveInfo = resolveInfo;
    }

    protected GenericAndroidApp(Parcel in) {
        intent = in.readParcelable(Intent.class.getClassLoader());
        resolveInfo = in.readParcelable(ResolveInfo.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(intent, flags);
        dest.writeParcelable(resolveInfo, flags);
    }


    public static final Creator<GenericAndroidApp> CREATOR = new Creator<GenericAndroidApp>() {
        @Override
        public GenericAndroidApp createFromParcel(Parcel in) {
            return new GenericAndroidApp(in);
        }

        @Override
        public GenericAndroidApp[] newArray(int size) {
            return new GenericAndroidApp[size];
        }
    };
}
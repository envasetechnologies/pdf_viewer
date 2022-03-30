# pdf_viewer

## How to Build

In Linux, go to folder: app/src/main/mupdf_library/libmupdf
```
make generate
make android
```
Step up 1 folder (app/src/main/mupdf_library) then run the Android ndk builder that should pick up the content in **jni** folder.
```
ndk-build -j8
```

Next, the pre-built libraries can be used in Android Studio as:
```
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/mupdf_library/libs']
        }
    }
```

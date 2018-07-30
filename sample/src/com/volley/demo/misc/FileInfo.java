package com.volley.demo.misc;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class FileInfo {
    private static final String TAG = FileInfo.class.getSimpleName();
    private static List<String> validExtensions;

    static {
        validExtensions = new ArrayList<>();
        validExtensions.add("pdf");
        validExtensions.add("jpeg");
        validExtensions.add("jpg");
        validExtensions.add("png");
        validExtensions.add("xls");
        validExtensions.add("xlsx");
        validExtensions.add("doc");
        validExtensions.add("docs");
        validExtensions.add("bmp");
        validExtensions.add("gif");
        validExtensions.add("txt");
    }

    public Uri uri;
    public String name;
    public long size;
    public String extension;
    public String contentType;

    public FileInfo(Context context, Uri uri) {
        this.uri = uri;
        name = "";
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                size = -1;
                if (!cursor.isNull(sizeIndex)) {
                    String sizeStr = cursor.getString(sizeIndex);
                    this.size = Long.parseLong(sizeStr);
                }
            }
            contentType = context.getContentResolver().getType(this.uri);
        } finally {
            cursor.close();
        }
        if (!TextUtils.isEmpty(name)) {
            this.extension = name.substring(name.lastIndexOf(".")).replace(".", "").trim();
        }

    }

    public boolean isValidExtension() {
        if (!TextUtils.isEmpty(extension)) {
            return validExtensions.contains(extension.toLowerCase());
        }
        return false;
    }

    public boolean isValidSize() {
        return size > 0 && size < 5242880;
    }
}


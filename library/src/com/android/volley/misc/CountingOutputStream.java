package com.android.volley.misc;

//import android.util.Log;

import com.android.volley.Response.ProgressListener;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends DataOutputStream {
    //private final static String TAG = "CountingOutputStream";
    private final ProgressListener progressListener;
    private long bytesWritten = 0;
    private long totalSize = 0;

    public CountingOutputStream(final OutputStream out, long length,
                                final ProgressListener listener) {
        super(out);
        //Log.d(TAG, "======== coming in =====" + Boolean.toString(listener != null));
        totalSize = length;
        progressListener = listener;
        //Log.d(TAG, "======== coming out =====");
    }

    @Override
    public void write(int b) throws IOException {
        //Log.d(TAG, "======== coming in write===== " + Boolean.toString(progressListener != null));
        super.write(b);
        //Log.d(TAG, "before call onProgress");
        if (null != progressListener) {
            bytesWritten += 1;
            progressListener.onProgress(bytesWritten, totalSize);
        }
        //Log.d(TAG, "after call onProgress: prog=");
    }

//    @Override
//    public void write(byte[] b) throws IOException {
//        Log.d(TAG, "======== coming in write(byte[] b)===== " + Boolean.toString(progressListener != null));
//        super.write(b);
//        if (null != progressListener) {
//            Log.d(TAG, "before call onProgress in write(byte[] b)");
//            bytesWritten += b.length;
//            progressListener.onProgress(bytesWritten, totalSize);
//        }
//    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        //Log.d(TAG, "======== coming in write(byte[] b, int off, int len)===== " + Boolean.toString(progressListener != null));
        super.write(b, off, len);
        if (null != progressListener) {
            //Log.d(TAG, "before call onProgress in write(byte[] b, int off, int len)");
            bytesWritten += len;
            progressListener.onProgress(bytesWritten, totalSize);
        }
    }

//    @Override
//    public void flush() throws IOException {
//        Log.d(TAG, "======== coming in flush===== written: = " + written + " size= " + size());
//        super.flush();
//        Log.d(TAG, "======== coming out flush===== written: = " + written + " size= " +size());
//
//    }
}
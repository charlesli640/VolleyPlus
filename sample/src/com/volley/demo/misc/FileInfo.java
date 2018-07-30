package com.volley.demo.misc;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.error.VolleyError;
import com.android.volley.misc.MultiPartData;
import com.android.volley.request.SimpleMultiPartRequest;
import com.android.volley.toolbox.RequestFuture;
import com.volley.demo.util.MyVolley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public class FileInfo {

    private static final String TAG = FileInfo.class.getSimpleName();
    public static final int INVALID_SIZE = -1;
    public static final int INVALID_TYPE = -2;
    public static final int FILE_UPLOAD_UNKNOWN = -3;
    public static final int FILE_UPLOAD_SUCCESS = 0;
    public static final int FILE_UPLOAD_FAILURE = 1;
    public static final int FILE_UPLOAD_ONGOING = 2;
    public static final int FILE_UPLOAD_CONFIRM_SUCCESS = 2;
    public static final int FILE_UPLOAD_CONFIRM_FAILEURE = 3;


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
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
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


    public interface ProgressListener {
        void updateProgress(int progress);
    }

    private static class CountingRequestBody extends RequestBody {

        protected RequestBody delegate;
        private final ProgressListener listener;

        protected CountingSink countingSink;

        public CountingRequestBody(RequestBody delegate, ProgressListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() {
            try {
                return delegate.contentLength();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return -1;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            countingSink = new CountingSink(sink);
            BufferedSink bufferedSink = Okio.buffer(countingSink);
            delegate.writeTo(bufferedSink);

            bufferedSink.flush();
        }

        protected final class CountingSink extends ForwardingSink {
            private long bytesWritten = 0;

            public CountingSink(Sink delegate) {
                super(delegate);
            }

            @Override
            public void write(Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                bytesWritten += byteCount;
                int prg = (int) (bytesWritten * 100 / contentLength());
                listener.updateProgress(prg);
            }
        }
    }

    // Sync request
    public int uploadFile_Okhttp(final Context ctx, String url, ProgressListener progressListener) {
        int result = FileInfo.FILE_UPLOAD_UNKNOWN;
        final ContentResolver contentResolver = ctx.getContentResolver();
        RequestBody fileReq = new RequestBody() {
            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public MediaType contentType() {
                return MediaType.parse(contentType);
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                //Log.d("RequestBodyInner", "writeTo");
                InputStream is = null;
                try {
                    is = contentResolver.openInputStream(uri);
                    //Log.d("RequestBodyInner", "sink.writeAll");
                    sink.writeAll(Okio.buffer(Okio.source(is)));
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception e) {
                        }
                    }
                }
            }
        };

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                //.addFormDataPart("siteId", NuanMessaging.getInstance().getSiteID())
                .addFormDataPart("file", name,
                        new CountingRequestBody(fileReq, progressListener))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String data = response.body().string();
                Log.d(TAG, "OKHttp call response:" + data);

                try {
                    JSONObject jo = new JSONObject(data);
                    String filename = jo.getString("fileName");
                } catch (JSONException e) {
                    Log.e(TAG, "onResponse json error:" + e.getMessage());
                }
                result = FileInfo.FILE_UPLOAD_SUCCESS;
            } else {
                // retry
                //String err = getString(R.string.file_upload_error_text);
                Log.i(TAG, "OKHttp call Failed");
                result = FileInfo.FILE_UPLOAD_FAILURE;
            }
        } catch (IOException e) {
            result = FileInfo.FILE_UPLOAD_FAILURE;
            Log.i(TAG, "OKHttp call Failed");
            e.printStackTrace();
        } catch (Exception e) {
            result = FileInfo.FILE_UPLOAD_FAILURE;
            Log.i(TAG, "OKHttp call Failed");
            e.printStackTrace();
        }
        Log.d(TAG, "return: " + result);

        return result;
    }

    // Async request
    public int uploadFile_Okhttp(final Context ctx, String url, ProgressListener progressListener, Callback callback) {

        final ContentResolver contentResolver = ctx.getContentResolver();
        RequestBody fileReq = new RequestBody() {
            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public MediaType contentType() {
                return MediaType.parse(contentType);
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                //Log.d("RequestBodyInner", "writeTo");
                InputStream is = null;
                try {
                    is = contentResolver.openInputStream(uri);
                    //Log.d("RequestBodyInner", "sink.writeAll");
                    sink.writeAll(Okio.buffer(Okio.source(is)));
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception e) {
                        }
                    }
                }
            }
        };

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                //.addFormDataPart("siteId", NuanMessaging.getInstance().getSiteID())
                .addFormDataPart("file", name,
                        new CountingRequestBody(fileReq, progressListener))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        client.newCall(request).enqueue(callback);

        return FileInfo.FILE_UPLOAD_ONGOING;
    }
}


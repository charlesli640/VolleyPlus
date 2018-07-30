package com.volley.demo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.error.AuthFailureError;
import com.android.volley.error.VolleyError;

import com.android.volley.misc.MultiPartData;
import com.android.volley.request.SimpleMultiPartRequest;
import com.volley.demo.util.MyVolley;
import com.volley.demo.util.Utils;
import com.volley.demo.misc.FileInfo;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
//import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

import org.json.JSONException;
import org.json.JSONObject;

import static java.lang.String.format;


public class FileUploadActivity extends AppCompatActivity {

    public static final String TAG = FileUploadActivity.class.getSimpleName();

    private static final int READ_REQUEST_CODE = 42;

    private TextView lblLog;
    private EditText etUrl;
    private ProgressDialog progressDialog;
    private Button btnFileUpload;
    private Button btnClose;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.DEBUG) {
            Utils.enableStrictMode();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_upload);

        lblLog = (TextView) findViewById(R.id.lbl_file_upload_log);
        btnFileUpload = (Button) findViewById(R.id.btn_file_upload);
        btnClose = (Button) findViewById(R.id.btn_file_close);
        etUrl = findViewById(R.id.url);
        progressDialog = new ProgressDialog(this);

        btnFileUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = getFileChooserIntent();
                startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private Intent getFileChooserIntent() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        intent.setType("*/*");
        return intent;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (data != null) {
                uri = data.getData();
                //final FileInfo fileInfo = new FileInfo(this, uri);
                //uploadFile_Okhttp(fileInfo);
                uploadFileTask(this, uri);
            }
        }
    }

    private void uploadFileTask(final Context ctx, final Uri uri) {

        class LongOperation extends AsyncTask<String, Void, String> {
            @Override
            protected String doInBackground(String... params) {
                final FileInfo fileInfo = new FileInfo(ctx, uri);
                uploadFile_Okhttp(fileInfo);
                //uploadFile_Volleyplus(fileInfo);
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
            }

            @Override
            protected void onPreExecute() {
            }

            @Override
            protected void onProgressUpdate(Void... values) {
            }
        }

        LongOperation uploadfile = new LongOperation();
        uploadfile.execute();
    }

    private void uploadFile_Okhttp(final FileInfo fileInfo) {
        final ProgressListener progressListener = new ProgressListener() {
            boolean firstUpdate = true;

            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
                if (done) {
                    Log.d(TAG, "completed");
                } else {
                    if (firstUpdate) {
                        firstUpdate = false;
                        if (contentLength == -1) {
                            Log.d(TAG, "content-length: unknown");
                        } else {
                            Log.d(TAG, format("content-length: %d\n", contentLength));
                            final long trans = bytesRead;
                            final long total = contentLength;
                            setProgress(trans, total);
                        }
                    }
                    //System.out.println(bytesRead);

                    if (contentLength != -1) {
                        Log.d(TAG, format("%d%% done\n", (100 * bytesRead) / contentLength));
                        final long trans = bytesRead;
                        final long total = contentLength;
                        setProgress(trans, total);
                    }
                }
            }
        };

        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                hideSpinner();
                String err = getString(R.string.file_upload_error_text);
                //lblLog.setText(getString(R.string.file_upload_error_text));
                //sendSystemMessageToAgent(err);
                Log.i(TAG, "OKHttp call:" + call.toString() + " Callback @@@" + e.toString());
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                hideSpinner();
                String data = response.body().string();
                Log.d(TAG, "OKHttp call response:" + data);
                try {
                    JSONObject jo = new JSONObject(data);
                    String filename = jo.getString("fileName");
                    //sendCustomerMessage(generateFileUrl(filename));
                } catch (JSONException e) {
                    Log.e(TAG, "onResponse json error:" + e.getMessage());
                }
            }
        };

        String url = "http://192.168.1.100:5000/";
        //String url = "http://10.0.2.2:5000/";
        //String url = "https://ft-west.touchcommerce.com/filetransfer/rest/cont/uploadFile";

        final ContentResolver contentResolver = getContentResolver();
        //final String contentType = contentResolver.getType(fileInfo.uri);
        //Log.d(TAG, "File path uri.getPath()=" + uri.getPath());
        //Log.d(TAG, "getUriRealPath = " + getUriRealPath(context, finfo.uri));
        //final AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(finfo.uri, "r");
        //if (fd == null) {
        //    throw new FileNotFoundException("could not open file descriptor");
        //}
        RequestBody fileReq = new RequestBody() {
            @Override
            public long contentLength() {
                return fileInfo.size;
            }

            @Override
            public MediaType contentType() {
                return MediaType.parse(fileInfo.contentType);
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {

                Log.d("RequestBodyInner", "writeTo");
                InputStream is = null;
                try {
                    is = contentResolver.openInputStream(fileInfo.uri); //fd.createInputStream();
                    Log.d("RequestBodyInner", "sink.writeAll");
                    sink.writeAll(Okio.buffer(Okio.source(is)));
                    //is.close();
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
                .addFormDataPart("file", fileInfo.name,
                        new CountingRequestBody(fileReq, progressListener))
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        client.newCall(request).enqueue(callback);
        //client.newCall(request).execute();//(callback);
        Log.d(TAG, "request of file upload enqueued");
        showSpinner();

    }

    private void uploadFile_Volleyplus(final FileInfo fileInfo) {
        RequestQueue queue = MyVolley.getRequestQueue();
        Log.d(TAG, "coming uploadFile");
        String BASE_URL = "http://192.168.1.9:5000";
        if (!TextUtils.isEmpty(etUrl.getText())) {
            BASE_URL = etUrl.getText().toString();
        }
        //final Uri upload_file_uri = uri;

        SimpleMultiPartRequest smr = new SimpleMultiPartRequest(Request.Method.POST, BASE_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("Response", response);
                        hideSpinner();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //Log.d(TAG, error.getMessage());
                hideSpinner();
                if (!TextUtils.isEmpty(error.getMessage()))
                    Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }) {

            @Override
            public Map<String, MultiPartData> getMultiPartData() {
                Log.d(TAG, "coming into getMultiPartData");
                Map<String, MultiPartData> params = new HashMap<>();
                MultiPartData dataPart = null;
                try {
                    dataPart = new MultiPartData(fileInfo.name, getBytes(fileInfo.uri), null);
                    params.put("file", dataPart);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return params;
            }
        };

        smr.setOnProgressListener(new Response.ProgressListener() {
            //long lastRefreshTime = 0L;
            //int minTime = 10; // to avoid call UI update too frequently
            @Override
            public void onProgress(long transferredBytes, long totalSize) {

                final long trans = transferredBytes;
                final long total = totalSize;
                setProgress(trans, total);
                /*
                long currentTime = System.currentTimeMillis();
                Log.d(TAG, "onProgress done: " + trans + " / " + total);
                if (currentTime - lastRefreshTime >= minTime || trans == total) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (progressDialog != null) {
                                progressDialog.setMax((int) total);
                                progressDialog.setProgress((int) trans);
                            }
                        }
                    });
                    lastRefreshTime = System.currentTimeMillis();
                }*/
            }
        });

        queue.add(smr);
        Log.d(TAG, "add file upload to queue");
        showSpinner();

    }

    private byte[] getBytes(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        inputStream.close();
        Log.d(TAG, "byteBuffer.size()=" + byteBuffer.size());
        return byteBuffer.toByteArray();
    }

    private void showSpinner() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null) {
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMessage(getResources().getString(R.string.file_upload_progress_title));
                    progressDialog.show();
                    progressDialog.setProgress(0);
                    progressDialog.setMax(100);
                }
            }
        });
    }


    private void hideSpinner() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.hide();
                }
            }
        });
    }

    private void setProgress(final long trans, final long total) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Log.d(TAG, "onProgress done: " + trans + " / " + total);
                if (progressDialog != null) {
                    progressDialog.setMax((int) total);
                    progressDialog.setProgress((int) trans);
                }
            }
        });
    }


    interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
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

            Log.d("CountingRequestBody", "writeTo enter");
            countingSink = new CountingSink(sink);
            BufferedSink bufferedSink = Okio.buffer(countingSink);

            Log.d("CountingRequestBody", "before delegate writeTo");
            delegate.writeTo(bufferedSink);
            Log.d("CountingRequestBody", "after delegate writeTo");

            bufferedSink.flush();
        }

        protected final class CountingSink extends ForwardingSink {

            private long bytesWritten = 0;

            public CountingSink(Sink delegate) {
                super(delegate);
            }

            @Override
            public void write(Buffer source, long byteCount) throws IOException {
                Log.d("CountingSink", "write");
                super.write(source, byteCount);

                bytesWritten += byteCount;
                listener.update(bytesWritten, contentLength(), bytesWritten == contentLength());
            }
        }
    }
}

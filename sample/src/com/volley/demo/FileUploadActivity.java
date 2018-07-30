package com.volley.demo;

import java.io.ByteArrayOutputStream;
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
import com.android.volley.error.VolleyError;

import com.android.volley.misc.MultiPartData;
import com.android.volley.request.SimpleMultiPartRequest;
import com.volley.demo.util.MyVolley;
import com.volley.demo.util.Utils;
import com.volley.demo.misc.FileInfo;


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
                uploadFileTask(this, uri);
            }
        }
    }

    private void uploadFileTask(final Context ctx, final Uri uri) {

        class LongOperation extends AsyncTask<String, Void, String> {
            @Override
            protected String doInBackground(String... params) {
                final FileInfo fileInfo = new FileInfo(ctx, uri);
                uploadFile_Volleyplus(fileInfo);
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

}

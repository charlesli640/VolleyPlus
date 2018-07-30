package com.volley.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.volley.demo.util.Utils;
import com.volley.demo.misc.FileInfo;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

import org.json.JSONException;
import org.json.JSONObject;


public class OkhttpFileUploadActivity extends AppCompatActivity {

    public static final String TAG = FileUploadActivity.class.getSimpleName();

    private static final int READ_REQUEST_CODE = 42;

    private TextView lblLog;
    private EditText etUrl;
    private Button btnFileUpload;
    private Button btnClose;
    private ProgressBar progressBar;
    private TextView progressBarMessage;
    private int retried = 0;
    private FileInfo fileInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.DEBUG) {
            Utils.enableStrictMode();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_upload);

        lblLog = findViewById(R.id.lbl_file_upload_log);
        btnFileUpload = findViewById(R.id.btn_file_upload);
        btnClose = findViewById(R.id.btn_file_close);
        etUrl = findViewById(R.id.url);

        progressBarMessage = findViewById(R.id.progressBarMessage);
        progressBar = findViewById(R.id.progressBar);

        btnFileUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = getFileChooserIntent();
                startActivityForResult(intent, READ_REQUEST_CODE);
                lblLog.setVisibility(View.GONE);
                retried = 0;
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
            } else {
                lblLog.setVisibility(View.VISIBLE);
                lblLog.setText("No file selected");
            }
        } else {
            lblLog.setVisibility(View.VISIBLE);
            lblLog.setText("No file selected");
        }
    }

    private void uploadFileTask(final Context ctx, final Uri uri) {

        class UploadProgressTask extends AsyncTask<String, Void, String> {
            @Override
            protected String doInBackground(String... params) {
                fileInfo = new FileInfo(ctx, uri);
                // Check the file size
                if(!fileInfo.isValidSize())
                    return "Not valid size";
                if(!fileInfo.isValidExtension())
                    return "Not valid type";

                showProgress();
                return uploadFile_Okhttp(ctx, fileInfo);  // If using async callback, it always fails
                //return null;
                //return result;
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null)
                    hideProgress(result);
                if (retried <= 2) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                    builder.setMessage("Are you sure you want to retry?")
                            .setCancelable(false)
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    retried++;
                                    Log.d(TAG, "retry #" + retried);
                                    uploadFile_Okhttp(ctx, fileInfo);
                                    showProgress();
                                }
                            })
                            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    Log.d(TAG, "User don't want to retry");
                                    String err = getString(R.string.file_upload_error_text);
                                    //hideSpinner(err);
                                    //sendCustomerMessage(err, err);
                                    retried = 0;
                                    Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                                }
                            });
                    AlertDialog alert = builder.create();
                    alert.show();
                }

            }

//            @Override
//            protected void onPreExecute() {
//                progressBarMessage.setText(getString(R.string.file_upload_progress_title, 0));
//                progressBarMessage.setVisibility(View.VISIBLE);
//                progressBar.setVisibility(View.VISIBLE);
//                setProgress(0, 100);
//            }
        }

        UploadProgressTask uploadfile = new UploadProgressTask();
        uploadfile.execute();
    }

    private String uploadFile_Okhttp(final Context ctx, final FileInfo fileInfo) {
        boolean success = false;
        String result = getString(R.string.file_upload_error_text);
        final ProgressListener progressListener = new ProgressListener() {

            @Override
            public void updateProgress(int progress) {
                if (0 <= progress && progress <= 100)
                    setProgress(progress, 100);
                else
                    setProgress(-1, -1);
            }
        };

        String url = "http://192.168.1.9:5000/";
        //String url = "http://10.0.2.2:5000/";
        //String url = "https://ft-west.touchcommerce.com/filetransfer/rest/cont/uploadFile";

        final ContentResolver contentResolver = getContentResolver();
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
                    is = contentResolver.openInputStream(fileInfo.uri);
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
                .addFormDataPart("file", fileInfo.name,
                        new CountingRequestBody(fileReq, progressListener))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        /*
        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String err = getString(R.string.file_upload_error_text);
                //lblLog.setText(getString(R.string.file_upload_error_text));
                //sendSystemMessageToAgent(err);
                hideProgress(err);
                Log.i(TAG, "OKHttp call:" + call.toString() + " Callback @@@" + e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String data = response.body().string();
                try {
                    JSONObject jo = new JSONObject(data);
                    String filename = jo.getString("fileName");
                    //sendCustomerMessage(generateFileUrl(filename));
                } catch (JSONException e) {
                    Log.e(TAG, "onResponse json error:" + e.getMessage());
                }
                hideProgress("");
            }
        };*/

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        //client.newCall(request).enqueue(callback);

        try {
            Response response = client.newCall(request).execute(); //(callback);
            if (response.isSuccessful()) {
                String data = response.body().string();
                Log.d(TAG, "OKHttp call response:" + data);

                try {
                    JSONObject jo = new JSONObject(data);
                    String filename = jo.getString("fileName");
                } catch (JSONException e) {
                    Log.e(TAG, "onResponse json error:" + e.getMessage());
                }
                result = data;
                success = true;
            } else {
                // retry
                String err = getString(R.string.file_upload_error_text);
                Log.i(TAG, "OKHttp call Failed");
                result = err;
            }
        } catch (IOException e) {
            Log.i(TAG, "OKHttp call Failed");
            e.printStackTrace();
        } catch (Exception e) {
            Log.i(TAG, "OKHttp call Failed");
            e.printStackTrace();
        }
        Log.d(TAG, "return: " + result);

        if(!success) {
            // post message of failure to let the main thread create this

        }
        return result;
    }

    private void showProgress() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBarMessage.setText(getString(R.string.file_upload_progress_title, 0));
                progressBarMessage.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                setProgress(0, 100);
            }
        });
    }

    private void hideProgress(final String result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBarMessage.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);

                lblLog.setVisibility(View.VISIBLE);
                lblLog.setText(result);
            }
        });

    }

    private void setProgress(final int trans, final int total) {
        //Log.d(TAG, "onProgress done: " + trans + " / " + total);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (total > 0) {
                    progressBar.setMax(total);
                    progressBar.setProgress(trans);
                    progressBar.setIndeterminate(false);
                    progressBarMessage.setText(getString(R.string.file_upload_progress_title, trans));

                } else {
                    progressBar.setIndeterminate(true);
                }
                progressBar.postInvalidate();
            }
        });
    }


    interface ProgressListener {
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

            //Log.d("CountingRequestBody", "writeTo enter");
            countingSink = new CountingSink(sink);
            BufferedSink bufferedSink = Okio.buffer(countingSink);

            //Log.d("CountingRequestBody", "before delegate writeTo");
            delegate.writeTo(bufferedSink);
            //Log.d("CountingRequestBody", "after delegate writeTo");

            bufferedSink.flush();
        }

        protected final class CountingSink extends ForwardingSink {

            private long bytesWritten = 0;

            public CountingSink(Sink delegate) {
                super(delegate);
            }

            @Override
            public void write(Buffer source, long byteCount) throws IOException {
                //Log.d("CountingSink", "write");
                super.write(source, byteCount);

                bytesWritten += byteCount;
                int prg = (int) (bytesWritten * 100 / contentLength());
                listener.updateProgress(prg);
            }
        }
    }
}

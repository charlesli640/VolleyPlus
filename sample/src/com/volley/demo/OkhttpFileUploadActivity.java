package com.volley.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.volley.demo.util.Utils;
import com.volley.demo.misc.FileInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


public class OkhttpFileUploadActivity extends AppCompatActivity {

    public static final String TAG = FileUploadActivity.class.getSimpleName();

    private static final int READ_REQUEST_CODE = 42;
    private static final int MAX_RETRY_TIMES = 2;

    private TextView lblLog;
    private EditText etUrl;
    private Button btnFileUpload;
    private Button btnClose;
    private ProgressBar progressBar;
    private TextView progressBarMessage;
    private int retried = 0;
    private boolean sync_request = false;

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
                lblLog.setVisibility(View.INVISIBLE);
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
                lblLog.setText("No file selected");  // XXX string
            }
        } else {
            lblLog.setVisibility(View.VISIBLE);
            lblLog.setText("No file selected");  // XXX string
        }
    }

    private void uploadFileTask(final Context ctx, final Uri uri) {
        final FileInfo.ProgressListener progressListener = new FileInfo.ProgressListener() {

            @Override
            public void updateProgress(int progress) {
                if (0 <= progress && progress <= 100)
                    setProgress(progress, 100);
                else
                    setProgress(-1, -1);
            }
        };

        class UploadProgressTask extends AsyncTask<String, Void, Integer> {
            @Override
            protected Integer doInBackground(String... params) {
                FileInfo fileInfo = new FileInfo(ctx, uri);
                // Check the file size
                if (!fileInfo.isValidSize())
                    return FileInfo.INVALID_SIZE;
                if (!fileInfo.isValidExtension())
                    return FileInfo.INVALID_TYPE;

                showProgress();
                String url = etUrl.getText().toString();
                //String url = "http://192.168.1.9:5000/";
                //String url = "http://10.0.2.2:5000/";
                //String url = "https://ft-west.touchcommerce.com/filetransfer/rest/cont/uploadFile";
                // Sync request

                if (sync_request)
                    return fileInfo.uploadFile_Okhttp(ctx, url, progressListener);
                    // If using async callback
                else {
                    Callback callback = new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            //String err = getString(R.string.file_upload_error_text);
                            //lblLog.setText(getString(R.string.file_upload_error_text));
                            //sendSystemMessageToAgent(err);
                            //hideProgress(err);
                            Log.i(TAG, "OKHttp call:" + call.toString() + " Callback @@@" + e.toString());
                            if (retried < MAX_RETRY_TIMES) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        showRetryDialog(ctx, uri);
                                    }
                                });

                            } else { // have tried 3 times, tell user try later
                                hideProgress("Already retried 3 times! Maybe network issue. Please try later!"); // XXX over 3 retries string
                            }
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
                            hideProgress(getString(R.string.file_upload_success_text));
                        }
                    };
                    return fileInfo.uploadFile_Okhttp(ctx, url, progressListener, callback);
                }

            }

            @Override
            protected void onPostExecute(Integer result) {
                if (result == FileInfo.INVALID_SIZE) {
                    hideProgress("Invalid size"); //XXX Invalid size string
                } else if (result == FileInfo.INVALID_TYPE) {
                    hideProgress("Invalid type"); //XXX Invalid type string
                } else if (result == FileInfo.FILE_UPLOAD_SUCCESS) {
                    hideProgress(getString(R.string.file_upload_success_text)); //get success string
                } else if (result == FileInfo.FILE_UPLOAD_FAILURE) {
                    if (retried <= 2) {
                        showRetryDialog(ctx, uri);
                    } else { // retried 3 times, tell user try later
                        hideProgress("Already retried 3 times! Maybe network issue. Please try later!"); // XXX over 3 retries string
                    }
                } else if (result == FileInfo.FILE_UPLOAD_ONGOING) {
                    //do nothing
                } else {
                    hideProgress(getString(R.string.file_upload_error_text));
                }
            }
        }

        UploadProgressTask uploadfile = new UploadProgressTask();
        uploadfile.execute();
    }

    private void showProgress() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBarMessage.setText(getString(R.string.file_upload_progress_percentage, 0));
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
                    progressBarMessage.setText(getString(R.string.file_upload_progress_percentage, trans));

                } else {
                    progressBar.setIndeterminate(true);
                }
                progressBar.postInvalidate();
            }
        });
    }

    private void showRetryDialog(final Context ctx, final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setMessage("Are you sure you want to retry?")  //XXX retry string
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        retried++;
                        Log.d(TAG, "retry #" + retried);
                        uploadFileTask(ctx, uri);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        Log.d(TAG, "User don't want to retry");
                        String err = getString(R.string.file_upload_error_text);
                        hideProgress(err);
                        //sendCustomerMessage(err, err);
                        retried = 0;
                        Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }
}

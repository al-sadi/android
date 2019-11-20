package com.nextcloud.client.network;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.nextcloud.client.account.UserAccountManager;
import com.owncloud.android.services.OperationsService;
import com.owncloud.android.ui.activity.ComponentsGetter;
import com.owncloud.android.ui.activity.FileActivity;
import com.owncloud.android.ui.activity.FileDisplayActivity;
import com.owncloud.android.ui.fragment.OCFileListFragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import androidx.annotation.Nullable;

import static java.lang.Thread.sleep;


public class KeepAlive extends Service {

    public static final String KEY_ACCOUNT = "ACCOUNT"; // UNTIDYS
    private Account account; // UNTIDYS

    public static boolean GenerateUNTIDyMatrix = false;
    public static boolean RegisterUNTIDy = false;
    public static boolean Authenticate = false;

    private OperationsService.OperationsServiceBinder mOperationsServiceBinder;
    Authenticate auth = new Authenticate();
    String UNTIDyMatrix = null;
    String AUTHENTICATION_URL = "security/authenticate.php?account=";
    String authenticationResult = null;
    String MatrixName;
    String MatrixContent;
    String accountName;
    String ServerPath;
    JSONObject jo;
    String JSONaction;
    String JSONtime;
    String JSONcontent;
    String MasterFileName;
    boolean MatrixExist = false;
    boolean authenticated = false;
    private Intent ServiceIntent;
    private Context mContext = this;
    private int mRandomNumber;
    private boolean mIsRandomGeneratorOn;
    private boolean serviceStopSignal = false;
    private final int MIN = 0;
    private final int MAX = 100;
    @Inject UserAccountManager accountManager;

    public class MyServiceBinder extends Binder {
        public KeepAlive getService() {
            return KeepAlive.this;
        }


    }

    private IBinder mBinder = new MyServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        accountName = intent.getStringExtra("ACCOUNT_NAME");
        Log.i("UNTIDYS", "onBind: NAME RECEIVED!" + accountName + "thread id: " + Thread.currentThread().getId());
        mIsRandomGeneratorOn = true;
        serviceStopSignal = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                startRandomNumberGenerator();
            }
        }
        ).start();
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        accountName = intent.getStringExtra("ACCOUNT_NAME");
        Log.i("UNTIDYS", "onStartCommand: NAME RECEIVED!" + accountName + "thread id: " + Thread.currentThread().getId());
        mIsRandomGeneratorOn = true;
        serviceStopSignal = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                startRandomNumberGenerator();
            }
        }
        ).start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopRandomNumberGenerator();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i("UNTIDYS", "onDestroy: Service Stopped");
    }

    private void startRandomNumberGenerator() {


        while (mIsRandomGeneratorOn) {
            try {
                sleep(1000);
                if (mIsRandomGeneratorOn && !serviceStopSignal) {
                    mRandomNumber = new Random().nextInt(MAX) + MIN;
                    Log.i("UNTIDYS", "startRandomNumberGenerator: Random number has been generated");

                    // START SERVICE
                    try {
                        sleep(1000);
                        Log.i("UNTIDYS", "Started the service");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                    //1 - Determine did we have previously an UNTIDyMatrix,,,, or not

                    //account = accountManager.getCurrentAccount();
                    //accountName = account.name;
                    MatrixName = accountName.replace("@", "/");
                    MasterFileName = MatrixName;
                    MasterFileName = MasterFileName.replace("/", "_");
                    ServerPath = accountName.substring(accountName.indexOf("@") + 1);
                    ServerPath = ServerPath.concat("/");
                    ServerPath = "http://".concat(ServerPath);
                    File file = new File(mContext.getFilesDir(), MasterFileName);
                    if (file.exists()) {
                        MatrixExist = true;
                        Log.i("UNTIDYS", "There is a matrix");
                    } else {
                        MatrixExist = false;
                        Log.i("UNTIDYS", "Fresh install");
                    }

                    if (!MatrixExist) { // if no UNITDYMatrix were downloaded before
                        Log.i("UNTIDYS", "NO MATRIX; The service should not trigger registration");
                        stopRandomNumberGenerator();
                    } else {

                        MatrixName = accountName.replace("@", "/");
                        MatrixName = MatrixName.replace("/", "_");
                        MasterFileName = MatrixName;

                        try {

                            FileInputStream FIS = openFileInput(MasterFileName);
                            int c;
                            String tmp = "";
                            while ((c = FIS.read()) != -1) {
                                tmp = tmp + Character.toString((char) c);
                            }
                            MatrixName = tmp;
                            FIS = openFileInput(MatrixName);
                            c = 0;
                            tmp = "";
                            while ((c = FIS.read()) != -1) {
                                tmp = tmp + Character.toString((char) c);
                            }
                            MatrixContent = tmp;
                            MatrixName = MatrixName.replace("_", "/");
                            Log.i("UNTIDYS", "Authentication after registeration ");
                            Log.i("UNTIDYS", "Previosly saved matrix is: " + MatrixContent + " , and the file name is: " + MatrixName);

                            String result = auth.execute(ServerPath + AUTHENTICATION_URL + MatrixName + "&matrix=" + MatrixContent).get();
                            Log.i("UNTIDYS", result);
                            jo = new JSONObject(result);
                            JSONaction = jo.getString("action");
                            if (JSONaction.contentEquals("download")) {
                                Log.i("UNTIDYS", "secondary download");
                                JSONtime = jo.getString("time");
                                JSONcontent = jo.getString("content");
                                MatrixContent = JSONcontent;
                                MatrixName = accountName.replace("@", "/");
                                MasterFileName = MatrixName;
                                MasterFileName = MasterFileName.replace("/", "_");
                                FileOutputStream FOS = openFileOutput(MasterFileName, MODE_PRIVATE);
                                MatrixName = MatrixName + "/" + JSONtime;
                                MatrixName = MatrixName.replace("/", "_");
                                FOS.write(MatrixName.getBytes());
                                FOS.close();
                                FOS = openFileOutput(MatrixName, MODE_PRIVATE);
                                FOS.write(MatrixContent.getBytes());
                                FOS.close();

                            } else {
                                Log.i("UNTIDYS", "Authentication failed");
                                file.delete();
                                Toast.makeText(mContext, "Authentication failed, clear NextCloud account",
                                               Toast.LENGTH_LONG).show();
                                AccountManager am = (AccountManager) mContext.getSystemService(mContext.ACCOUNT_SERVICE);
                                account = accountManager.getCurrentAccount();
                                Log.i("UNTIDYS", "the account name is: " + account.name);
                                am.removeAccount(account, null, null);
                                Intent start = new Intent(mContext, FileDisplayActivity.class);
                                start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(start);
                                Log.i("UNTIDYS", "Ended the service");

                            }

                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    //END SERVICE
                }

            } catch (InterruptedException e) {
                Log.i("UNTIDYS", "startRandomNumberGenerator: Catch Clause ");
            }
        }
    }

    private void stopRandomNumberGenerator() throws InterruptedException {
        serviceStopSignal = true;
        Log.i("UNTIDYS", "stopRandomNumberGenerator: Stop Signal has been triggered ");
        while (Authenticate ) {
            Log.i("UNTIDYS", "Can't stop; One of the Async tasks is still working");
            if (Authenticate)
                Log.i("UNTIDYS", "Can't stop; One of the Async Authenticate is still working");


            sleep(1000);
        }
        if (!GenerateUNTIDyMatrix && !RegisterUNTIDy && !Authenticate )
            mIsRandomGeneratorOn = false;
    }



    private class Authenticate extends AsyncTask<String, String, String> {

        protected void onPreExecute() {
            if (Authenticate == true) {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                Authenticate = true;
                super.onPreExecute();
            }


        }


        protected String doInBackground(String... params) {


            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {

                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                InputStream stream = connection.getInputStream();

                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuffer buffer = new StringBuffer();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                    Log.i("UNTIDYS ", "Response  " + line);   //here u ll get whole response...... :-)

                }

                return String.valueOf(buffer.toString());


            } catch (MalformedURLException e) {
                e.printStackTrace();
                Log.i("UNTIDYS", "MalformedURLException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.i("UNTIDYS", "IOException");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Authenticate = false;
            super.onPostExecute(result);

        }
    }

}

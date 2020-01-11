package com.nextcloud.client.network;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.nextcloud.client.account.UserAccountManager;
import com.owncloud.android.services.OperationsService;
import com.owncloud.android.ui.activity.FileDisplayActivity;

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

import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import androidx.annotation.Nullable;

import static java.lang.Thread.sleep;


public class KeepAlive extends Service {

    public static final String KEY_ACCOUNT = "ACCOUNT"; // To get from the starting Intent on the main code
    private Account account; // To get from the starting Intent on the main code


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
    boolean MasterMatrixExist = false;
    boolean UNTIDyMatrixExist = false;
    boolean authenticated = false;
    private Intent ServiceIntent;
    private Context mContext = this;
    private boolean UNTIDyON;
    private boolean blockInterrupt = false;
    Thread thread;

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
        Log.i("UNTIDYS", "onBind: STARTED THE SERVICE. NAME RECEIVED!" + accountName + "thread id: " + Thread.currentThread().getId());
        UNTIDyON = true;
        blockInterrupt = false;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (UNTIDyON && !thread.interrupted()) {
                    UNTIDyBackground();
                }
                return;
            }
        }
        );
        thread.start();
        return mBinder;
    }
    private void stopThread ()
    {
        stopSelf();
    }
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//
//
//        accountName = intent.getStringExtra("ACCOUNT_NAME");
//        Log.i("UNTIDYS", "onStartCommand: NAME RECEIVED!" + accountName + "thread id: " + Thread.currentThread().getId());
//        UNTIDyON = true;
//        blockInterrupt = false;
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                UNTIDyBackground();
//            }
//        }
//        ).start();
//        return START_STICKY;
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopUNTIDyBackground();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i("UNTIDYS", "onDestroy: Service Stopped");
    }

    private void UNTIDyBackground() {

            try {
                sleep(20000);
                if (UNTIDyON) {
                    blockInterrupt=true; // to prevent interrupting the service while downloading the UNTIDyMatrix
                    // This variable will be false by the end of this routine;
                    // When this variable is false, this service can only then be intrrupted

                    Log.i("UNTIDYS", "Another authentication round");

                    //Check the previously saved Matrix
                    MatrixName = accountName.replace("@", "/");
                    MasterFileName = MatrixName;
                    MasterFileName = MasterFileName.replace("/", "_");
                    ServerPath = accountName.substring(accountName.indexOf("@") + 1);
                    ServerPath = ServerPath.concat("/");
                    ServerPath = "http://".concat(ServerPath);
                    File file = new File(mContext.getFilesDir(), MasterFileName);
                    if (file.exists()) {
                        MasterMatrixExist = true;
                        Log.i("UNTIDYS", "There is a matrix");
                    } else {
                        MasterMatrixExist = false;
                    }

                    if (!MasterMatrixExist) { // if no UNITDYMatrix were downloaded before
                        Log.i("UNTIDYS", "NO MATRIX; The service should not proceed");
                        blockInterrupt=false;
                        stopUNTIDyBackground();
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
                            FIS.close();
                            MatrixName = tmp;
                            //check if the file (MatrixName) exist or no
                            File file1 = new File(mContext.getFilesDir(), MatrixName);
                            if (file1.exists()) {
                                UNTIDyMatrixExist = true;
                                Log.i("UNTIDYS", "There is a UNTIDyMatrix");
                                FIS = openFileInput(MatrixName);
                                c = 0;
                                tmp = "";
                                while ((c = FIS.read()) != -1) {
                                    tmp = tmp + Character.toString((char) c);
                                }
                                FIS.close();
                                MatrixContent = tmp;
                                MatrixName = MatrixName.replace("_", "/");

                                Log.i("UNTIDYS", "Previosly saved matrix is: " + MatrixContent + " , and the file name is: " + MatrixName);

                                auth = new Authenticate();
                                String result = auth.execute(ServerPath + AUTHENTICATION_URL + MatrixName + "&matrix=" + MatrixContent).get();
                                jo = new JSONObject(result);
                                JSONaction = jo.getString("action");
                            } else {
                                UNTIDyMatrixExist = false;
                            }

                            if (UNTIDyMatrixExist && JSONaction.contentEquals("download")) {
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

                                // fix to prevent fresh install
                                Log.i("UNTIDYS", "Authentication failed");
                                FileOutputStream FOS = openFileOutput(MasterFileName, MODE_PRIVATE);
                                FOS.write("null".getBytes());
                                FOS.close();
                                blockInterrupt=false;
                                thread.interrupt();
                                stopThread();
                                //file.delete();

                               // AccountManager am = (AccountManager) mContext.getSystemService(mContext.ACCOUNT_SERVICE);
                               // account = accountManager.getCurrentAccount();

                                //am.removeAccount(account, null, null);
                                //Intent start = new Intent(mContext, FileDisplayActivity.class);
                                //start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                //startActivity(start);
                                Log.i("UNTIDYS", "Waiting for the activity to end the service");

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
                    blockInterrupt=false;
                    //END SERVICE
                }

            } catch (InterruptedException e) {
                Log.i("UNTIDYS", "UNTIDyBackground: Catch Clause ");
            }

    }

    private void stopUNTIDyBackground() throws InterruptedException {
        Log.i("UNTIDYS", "stopUNTIDyBackground: Stop Signal has been triggered ");
        while (Authenticate ) {
            Log.i("UNTIDYS", "Can't stop; One of the Async tasks is still working");
            if (Authenticate)
                Log.i("UNTIDYS", "Can't stop; One of the Async Authenticate is still working");


            sleep(1000);
        }
        if (!Authenticate && !blockInterrupt)
            UNTIDyON = false;
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
                    Log.i("UNTIDYS ", "Response  " + line);

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

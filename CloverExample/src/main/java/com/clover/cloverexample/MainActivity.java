package com.clover.cloverexample;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Toast;

import com.clover.cloverexample.connection.*;
import com.clover.cloverexample.zxing.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Scanner;

public class MainActivity extends Activity {

  private static final String TAG = "MAIN_ACTIVITY";

  private JSONObject reqJSON;
  private String order_id;
  private String merchant_id;
  private String qr_code;

  private NumberPicker month;
  private NumberPicker year;
  private EditText name;
  private EditText cc_pin;
  private EditText cvv;
  private EditText zip;
  private Long amount;
  private String access_token;
  private ProgressDialog progress;

  private JSONArray jsonRewards;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

    reqJSON = new JSONObject();

    Intent intent = getIntent();

    if (intent.getAction().equals(getString(R.string.clover_pay_intent))) {
      amount = intent.getLongExtra("clover.intent.extra.AMOUNT", 0l);
      order_id = intent.getStringExtra("clover.intent.extra.ORDER_ID");
      merchant_id = intent.getStringExtra("clover.intent.extra.MERCHANT_ID");
      startDispatcher();
    }

  }

  public void initializeNewCustomer() {

    setContentView(R.layout.link_new_merchant);

    name = (EditText) findViewById(R.id.name);
    cc_pin = (EditText) findViewById(R.id.cc_pin);
    cvv = (EditText) findViewById(R.id.cvv);
    zip = (EditText) findViewById(R.id.zip);
    month = (NumberPicker) findViewById(R.id.month);
    year = (NumberPicker) findViewById(R.id.year);

    month.setMinValue(1);
    month.setMaxValue(12);
    year.setMinValue(13);
    year.setMaxValue(99);

    //for fast testing
    cc_pin.setText("36185900022226");
    name.setText("Steve");
    cvv.setText("123");
    zip.setText("12345");
    month.setValue(4);
    year.setValue(16);
  }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != Activity.RESULT_OK) {
            finish();
            return;
        }

        int rewardsRequestCode = 1;
        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult =
            IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult != null) {
                qr_code = scanResult.getContents();
                //TODO catch result and do logic based on response ie. return, go to add_cc, go to rewards, etc.

                //go to rewards page
                Intent i = new Intent(this, RewardsActivity.class);
                i.putExtra("qr_code", qr_code);
                i.putExtra("merchantId", merchant_id);
                i.putExtra("totalAmount", amount);
                i.putExtra("orderId", order_id);
                startActivityForResult(i, rewardsRequestCode);
            }
        } else if(requestCode == rewardsRequestCode) {
            //now that we have the rewards they want, do the order
            String stringRewards = intent.getStringExtra("stringRewards");
            try {
                jsonRewards = new JSONArray(stringRewards);
            } catch(Exception e) {
                Log.e(TAG, e.getMessage());
            }

            JSONObject result = doOrder();
            if (result != null) {
                try{
                    if(result.getString("status").equals("no pay token")) {
                        access_token = result.getString("access_token");
                        initializeNewCustomer();
                    } else if(result.getString("status").equals("payed")) {
                        try {
                            String url = getString(R.string.clover_example_url) + "/complete_order/" + order_id;
                            HTTPPostTask task = new HTTPPostTask(reqJSON, url);
                            task.execute();
                            task.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Intent returnIntent = new Intent();
                        returnIntent.putExtra("com.clover.tip", 0l); //TODO change this to real thing when clover intent gets updated.
                        returnIntent.putExtra(getString(R.string.clover_order_id), order_id);
                        setResult(RESULT_OK, returnIntent);
                        try {
                            JSONObject requestBody = new JSONObject();
                            requestBody.put("merchant_id", merchant_id);
                            requestBody.put("order_id", order_id);
                            requestBody.put("rewards", jsonRewards);
                            HTTPPostTask applyRewards = new HTTPPostTask(requestBody,
                                getString(R.string.clover_example_url) + "/apply_rewards_customer");
                            applyRewards.execute();
                            JSONObject jsonResponse = applyRewards.get();
                            if(!jsonResponse.get("status").equals("success"))
                            {
                                Log.e(TAG, "Applying rewards failed!");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }
                        finish();
                    } else if(result.getString("status").equals("declined")) {
                        setResult(RESULT_CANCELED);
                        finish();
                    } else {
                        Toast.makeText(this, "Merchant not found. Have you registered on our site?", Toast.LENGTH_LONG).show();
                        finish();
                    }
                } catch (JSONException e) {
                    Toast.makeText(this, "Server Error. Please try again.", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                    finish();
                }
            } else {
                Toast.makeText(this, "Could not connect to server. Please try again.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

  void startDispatcher() {
      IntentIntegrator integrator = new IntentIntegrator(this);
      integrator.initiateScan();
  }

  JSONObject doOrder() {
    String url = getString(R.string.clover_example_url) + "/charge";
    try {
      reqJSON.put("qr_code", qr_code);
      reqJSON.put("amount", amount);
      reqJSON.put("merchant_id", merchant_id);
      reqJSON.put("order_id", order_id);
      HTTPPostTask task = new HTTPPostTask(reqJSON, url);
      task.execute();
      return task.get();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }


  public class ProcessCard extends AsyncTask<String, Void, JSONObject> {

    CloverUtils c;

    protected void setUtils(CloverUtils c) {
      this.c = c;
    }

    @Override
    protected JSONObject doInBackground(final String... params) {
      if(c == null) {
        System.err.println("CloverUtils never set from doProcessCard.");
        return null;
      }
      JSONObject resp = c.doProcessCard(params[0], params[1],params[2],params[3],params[4],params[5],Long.parseLong(params[6]),params[7], params[8]);
      String url = getString(R.string.clover_example_url) + "/record_payment";
      try {
        JSONObject req = new JSONObject();
        req.put("amount", amount);
        req.put("qr_code", qr_code);
        req.put("merchant", merchant_id);
        req.put("last_four", params[1].substring(params[1].length() - 4));
        req.put("pay_token", resp.getString("token"));
        HTTPPostTask task = new HTTPPostTask(req, url);
        task.execute();
        task.get();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return resp;
    }

    @Override
    protected void onPostExecute(JSONObject resp) {
      progress.dismiss();
      String result = "Error.";
      try {
        result = resp.getString("result");
      } catch (JSONException e) {
        System.err.println("malformed response from server.");
        e.printStackTrace();
      }
      Toast.makeText(getDialogContext(), result, Toast.LENGTH_SHORT).show();
      if(result.equals("APPROVED")) {
        Intent resultIntent = new Intent(getDialogContext(), MainActivity.class);
        resultIntent.putExtra("result", result);
        Intent returnIntent = new Intent();
        returnIntent.putExtra("clover.intent.extra.TIP_AMOUNT", 0l);
        returnIntent.putExtra(getString(R.string.clover_order_id), order_id);
        setResult(RESULT_OK, resultIntent);
        finish();
      }
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      progress = ProgressDialog.show(getDialogContext(), "Processing", "Processing Card...", true);
    }
  }


  private Context getDialogContext() {
    Context context;
    if (getParent() != null) context = getParent();
    else context = this;
    return context;
  }

  public void sendData(View view) {

    boolean validData = true;
    String msg = "";
    if(! (name.getText() != null && name.getText().toString() != "")) {
      msg = "Please Enter Your Name.";
      validData = false;
    }
    else if(! (cc_pin.getText() != null && cc_pin.getText().toString() != "")) {
      msg = "Please Enter Your Credit Card Number.";
      validData = false;
    }
    else if(! (cvv.getText() != null && cvv.getText().toString() != "") ||
            cvv.getText().toString().length() != 3) {
      msg = "Please Enter a valid CVV.";
      validData = false;
    }
    else if(! (zip.getText() != null && zip.getText().toString() != "") ||
            zip.getText().toString().length() != 5) {
      msg = "Please Enter a valid Zip Code.";
      validData = false;
    }
    if (! validData) {
      Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
      return;
    }

    if(merchant_id == null || access_token == null) {
      Toast.makeText(this, "Server Error. Please try again.", Toast.LENGTH_LONG).show();
      if(progress != null) {
        progress.dismiss();
      }
      return;
    }
    CloverUtils c = new CloverUtils(merchant_id, access_token);
    c.setKey();
    ProcessCard mProcessCard = new ProcessCard();

    mProcessCard.setUtils(c);
    mProcessCard.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, name.getText().toString(), cc_pin.getText().toString(),
            cvv.getText().toString(), zip.getText().toString(), String.valueOf(month.getValue()),
            String.valueOf(year.getValue()), String.valueOf(amount), "usd", order_id);
  }

  public void cancel(View view) {
    finish();
  }

}


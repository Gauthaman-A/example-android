package com.clover.cloverexample;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by richard on 8/21/13.
 *
 * Activity to show users their rewards and let them choose which ones they want
 */
public class RewardsActivity extends Activity {
    private static final String TAG = "REWARD_ACTIVITY";

    private LinearLayout checkboxLayout;
    private ArrayList<String> itemIds;
    private ArrayList<CheckBox> checkBoxes;

    private String hostString;

    private String qrCode;
    private String merchantId;
    private String orderId;
    private Long totalAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        //initialize class variables
        itemIds = new ArrayList<String>();
        checkBoxes = new ArrayList<CheckBox>();
        hostString = getString(R.string.clover_example_url);

        //get GUI elements ready
        checkboxLayout = (LinearLayout) findViewById(R.id.checkboxLayout);

        Intent intent =  getIntent();
        Bundle extras = intent.getExtras();
        qrCode = extras.getString("qr_code");
        merchantId = extras.getString("merchantId");
        totalAmount = extras.getLong("totalAmount");
        orderId = extras.getString("orderId");

        //TODO some error checking for empty qrCode and merchantId

        //get the rewards and set it up after done!
        new GetRewardsTask().execute();
    }

    public void onSubmit(View v)
    {
        JSONObject json = new JSONObject();
        JSONArray checkedRewards = new JSONArray();
        try {
            json.put("merchant_id", merchantId);
            json.put("order_id", orderId);
            json.put("total_amount", totalAmount);

            for(int i = 0; i < checkBoxes.size(); i++)
            {
                if(checkBoxes.get(i).isChecked())
                {
                    JSONObject checkedReward = new JSONObject();
                    checkedReward.put("itemId", itemIds.get(i));
                    checkedReward.put("unitQty", 1);
                    checkedRewards.put(checkedReward);
                }
            }


        } catch(JSONException e) {
            Log.e(TAG, e.getMessage());
        }
        //return the rewards that they chose
        Intent returnIntent = new Intent();
        returnIntent.putExtra("stringRewards", checkedRewards.toString());
        setResult(RESULT_OK, returnIntent);
        finish();
    }


    /*
     * Custom class for asynchronous http get rewards request
     */
    private class GetRewardsTask extends AsyncTask<Void, Void, String>
    {
        /*
         * Does an http get request to server to get the rewards as json
         */
        protected String doInBackground(Void... voids)
        {
            String urlString = hostString + "/get_rewards_customer";
            String ret = "";

            //add query parameters
            List<NameValuePair> params = new LinkedList<NameValuePair>();
            params.add(new BasicNameValuePair("qr_code", qrCode));
            params.add(new BasicNameValuePair("merchant_id", merchantId));
            params.add(new BasicNameValuePair("total_amount", String.valueOf(totalAmount)));

            String paramString = URLEncodedUtils.format(params, "utf-8");
            urlString += "?" + paramString;

            //get rewards they quality for from server
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                //delimiter just reads the whole thing (\A is on beginning of input)
                Scanner scanner = new Scanner(connection.getInputStream()).useDelimiter("\\A");
                ret = scanner.hasNext() ? scanner.next() : "";

            }   catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage());
            }   catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }   finally {
                if(connection != null)
                {
                    connection.disconnect();
                }
            }

            return ret;
        }

        /*
         * From returned json, add the checkboxes for each reward
         */
        protected void onPostExecute(String result)
        {
            try {
                JSONObject jsonObject = new JSONObject(result);
                JSONArray rewardsArray = jsonObject.getJSONArray("rewards");

                //add all the rewards to checkboxes
                for(int i = 0; i < rewardsArray.length(); i++)
                {
                    JSONObject reward = rewardsArray.getJSONObject(i);

                    //add checkboxes
                    CheckBox checkBox = new CheckBox(getApplicationContext());
                    checkBox.setText(reward.getString("name"));
                    checkBox.setTextColor(Color.BLACK);//why is the default white???
                    checkBox.setChecked(true); //set them all to be checked by default
                    checkBoxes.add(checkBox);

                    //keep track of item ids
                    itemIds.add(reward.getString("item_id"));

                    //add to the GUI
                    checkboxLayout.addView(checkBox);
                }

                if(rewardsArray.length() == 0)
                {
                    TextView noRewardsText = new TextView(getApplicationContext());
                    noRewardsText.setText("Sorry! You don't qualify for any rewards..");
                    noRewardsText.setTextColor(Color.BLACK);
                    checkboxLayout.addView(noRewardsText);
                }

            }   catch(JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }

    }

}

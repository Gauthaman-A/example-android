package com.clover.cloverexample;

import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import com.clover.cloverexample.connection.HTTPGetTask;
import com.clover.cloverexample.connection.HTTPPostTask;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;
import javax.crypto.Cipher;

/**
 * Created by steven on 6/25/13.
 */
public class CloverUtils {

  //final private static String BASE_URL = CloverLoginActivity.BASE_URL;
  final public static String BASE_URL = "http://www.clover.com";
  private String merchantId;
  private String accessToken;
  private BigInteger modulus;
  private BigInteger exponent;
  private String prefix;
  //private ProgressDialog progress;
  private Context ctx;
  private final static SecureRandom RANDOM = new SecureRandom();

  public CloverUtils(String merchantId, String accessToken) {
    this.merchantId = merchantId;
    this.accessToken = accessToken;
  }

  public void setKey() {
    try {
      JSONObject key = getData("/v2/merchant/{mId}/pay/key", null);
      this.modulus = new BigInteger(key.getString("modulus"));
      this.exponent = new BigInteger(key.getString("exponent"));
      this.prefix = key.getString("prefix");
    } catch (JSONException e) {
      System.err.println("Error. Malformed key.");
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("No key given, probably a wrong access token or mId, or the clover server is down.");
      e.printStackTrace();
    }
  }

  public void setContext(Context ctx) {
    this.ctx = ctx;
  }

  public JSONObject getData(String endpoint, HashMap<String, String> map) {
    String url = URLFormatter(endpoint, map);

    HTTPGetTask getTask = new HTTPGetTask(url);
    getTask.execute();
    try {
      final JSONObject obj = getTask.get();
      return obj;
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    return null;
  }

  public JSONObject postData(String endpoint, HashMap<String, String> map, JSONObject data){
    try{
      String url = URLFormatter(endpoint, map);
      HTTPPostTask postTask = new HTTPPostTask(data, url);
      postTask.execute();
      JSONObject obj = postTask.get();
      return obj;
    }catch (InterruptedException e){
      e.printStackTrace();
    }catch (ExecutionException e){
      e.printStackTrace();
    }catch (UnsupportedEncodingException e){
      e.printStackTrace();
    }

    return null;
  }

  public String URLFormatter(String endpoint, HashMap<String, String> map) {
    try{
      if(map == null){
        map = new HashMap<String, String>();
      }
      String url = BASE_URL;
      if(merchantId != null && ! merchantId.equals("")) {
        map.put("mId", merchantId);
      }
      if(accessToken != null && ! accessToken.equals("")) {
        map.put("access_token", accessToken);
      }
      url = url + endpoint;

      Pattern pattern = Pattern.compile("\\{(.*?)\\}(?!\\s*\\})\\s*");
      Matcher matcher = pattern.matcher(url);
      while (matcher.find()) {
        if(map.containsKey(matcher.group(1))){
          url = url.replace( "{"+ matcher.group(1) + "}" , map.get(matcher.group(1)));
          map.remove(matcher.group(1));
        }
      }
      List<String> keys = new ArrayList<String>(map.keySet());
      List<String> vals = new ArrayList<String>(map.values());
      url += "?";
      for(int i = 0; i < keys.size(); i++){
        url += keys.get(i) + "=";
        url += vals.get(i) + "&";
      }
      url = url.substring(0, url.length()-1);
      return url;
    }catch (NullPointerException e){
      throw new NullPointerException("HashMap key and value mismatch");
    }
  }

  public Bundle toBundle(JSONObject obj) {
    Bundle bundle = new Bundle();
    try{
      Iterator keys = obj.keys();
      while(keys.hasNext()){
        String name = (String)keys.next();
        if(obj.get(name) instanceof JSONObject){
          bundle.putBundle(name, toBundle((JSONObject) obj.get(name)));
        }
        else if (obj.get(name) instanceof JSONArray) {
          bundle.putBundle(name, toBundle((JSONArray) obj.get(name)));
        }
        else if (obj.get(name) instanceof String) {
          bundle.putString(name, obj.getString(name));
        }
        else if (obj.get(name) instanceof Number) {
          if (obj.get(name) instanceof Integer) {
            bundle.putInt(name, (Integer) obj.get(name));
          }
          else if (obj.get(name) instanceof Double) {
            bundle.putDouble(name, (Double) obj.get(name));
          }
          else if (obj.get(name) instanceof Long) {
            bundle.putLong(name, (Long) obj.get(name));
          }
        }
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return bundle;
  }

  public Bundle toBundle(JSONArray array){
    Bundle bundle = new Bundle();
    try{
      for(int i = 0; i < array.length(); i++){
        Object obj = array.get(i);
        if(obj instanceof JSONObject){
          bundle.putBundle("" + i, toBundle((JSONObject) obj));
        }
        else if(obj instanceof JSONArray){
          bundle.putBundle("" + i, toBundle((JSONArray) obj));
        }
        else if (obj instanceof String){
          bundle.putString("" + i, obj.toString());
        }
        else if (obj instanceof Number){
          if(obj instanceof Integer){
            bundle.putInt("" + i, (Integer) obj);
          }
          else if (obj instanceof Double){
            bundle.putDouble("" + i, (Double) obj);
          }
          else if (obj instanceof Long){
            bundle.putLong("" + i, (Long) obj);
          }
        }
      }
    }catch (JSONException e ){
      e.printStackTrace();
    }
    return bundle;
  }

  public JSONObject doPayWithToken(Long amount, String currency, String orderId, String payToken) {
    JSONObject data = new JSONObject();
    try {
      data.put("amount", amount);
      data.put("currency", currency);
      data.put("orderId", orderId);
      data.put("token", payToken);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return postData("/v2/merchant/{mId}/pay", null, data);
  }

  public JSONObject doProcessCard(String name, String cc_pin, String cvv,
                                  String zip, String month, String year,
                                  Long amount, String currency, String orderId) {
    String to_enc = prefix + cc_pin;
    String enc_cc = "";
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      RSAPublicKeySpec pubKeySpec = new RSAPublicKeySpec(modulus, exponent);
      RSAPublicKey key = (RSAPublicKey) keyFactory.generatePublic(pubKeySpec);
      Cipher cipher = Cipher.getInstance("RSA/None/OAEPWithSHA1AndMGF1Padding", "BC");
      cipher.init(Cipher.ENCRYPT_MODE, key, RANDOM);
      byte[] cipherText = cipher.doFinal(to_enc.getBytes());
      enc_cc = Base64.encodeToString(cipherText, Base64.DEFAULT);
    } catch (GeneralSecurityException ignore) {
      ignore.printStackTrace();
    }
    CloverUtils c = new CloverUtils(merchantId, accessToken);
    JSONObject req = new JSONObject();
    try{
      req.put("cardEncrypted", enc_cc);
      req.put("first6", cc_pin.substring(0,6));
      req.put("last4", cc_pin.substring(cc_pin.length()-4));
      req.put("cvv", cvv);
      req.put("expMonth", month);
      req.put("expYear", year);
      req.put("zip", zip);
      req.put("currency", currency);
      req.put("amount", amount);
      req.put("orderId", orderId);
      return c.postData("/v2/merchant/{mId}/pay", null, req);
    }catch (JSONException e) {
      e.printStackTrace();
    }
    return null;
  }

}
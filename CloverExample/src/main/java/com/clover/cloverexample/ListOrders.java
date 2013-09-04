package com.clover.cloverexample;

import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.app.Activity;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.clover.cloverexample.connection.HTTPGetTask;
import com.clover.cloverexample.connection.HTTPPostTask;
import org.json.JSONArray;
import org.json.JSONObject;

import static java.util.concurrent.TimeUnit.*;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class ListOrders extends Activity {

  private ArrayList<Order> orders;
  private ListView mListView;
  private OrderAdapter adapt;
  final private int textSize = 20;
  final private int rightPadding = 5;

  private class Order {
    public String id;
    public String customerName;
    public int orderNumber;
    public String pickupTime;
    public boolean completed;
    
    public Order(String id, String customerName, int orderNumber, String pickupTime, boolean completed) {
       this.id = id;
       this.customerName = customerName;
       this.orderNumber = orderNumber;
       this.pickupTime = pickupTime;
       this.completed = completed;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.list_orders);

    orders = updateOrders();

    Updater updater = new Updater();
    updater.UpdateForDay();

    mListView = (ListView)findViewById(R.id.list_view);
    adapt = new OrderAdapter(this, android.R.layout.simple_list_item_1, orders);

    mListView.setOnItemClickListener(
            new ErrorDetailsClickListener(orders, this));
    mListView.setAdapter(adapt);

  }

  @Override
  protected void onResume(){
    super.onResume();
    orders = updateOrders();
    adapt.orders = orders;
    adapt.notifyDataSetChanged();
    mListView.setOnItemClickListener(
            new ErrorDetailsClickListener(orders, getActivity()));
  }

  private ArrayList<Order> updateOrders() {
    //TODO add clover android-adk to this and get merchant id dynamically. add to gradle dependencies.
    HTTPGetTask get = new HTTPGetTask(getString(R.string.clover_example_url) + "/get_orders/"+ "H3PS5DK7XE3Y0" +"");
    ArrayList<Order> ords = new ArrayList<Order>();
    try {
      get.execute();
      JSONArray json_orders = get.get().getJSONArray("orders");
      for(int i = 0; i < json_orders.length(); i++) {
        try {
          JSONObject ord = json_orders.getJSONObject(i);
          ords.add(new Order(ord.getString("order_id"), ord.getString("customer_name"),
                  ord.getInt("order_number"), ord.getString("pickup_time"), ord.getBoolean("completed")));
        }catch (Exception e ) {
          e.printStackTrace();
        }
      }
    }catch (Exception e) {
      e.printStackTrace();
    }
    return ords;
  }

  private Activity getActivity() {
     return this;
  }

  public void updateUI() {
    adapt.notifyDataSetChanged();
  }

  class Updater {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void UpdateForDay() {
      final Runnable updater = new Runnable() {
        public void run() {
          ArrayList<String> ids = new ArrayList<String>();
          for (Order ord : orders) {
            ids.add(ord.id);
          }
          orders = updateOrders();
          adapt.orders = orders;
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              updateUI();
            }
          });
          for(Order ord : orders) {
            if(! ids.contains(ord.id)) {
              NotificationCompat.Builder mBuilder =
                      new NotificationCompat.Builder(getActivity())
                              .setSmallIcon(R.drawable.ic_launcher)
                              .setContentTitle("New Order Ahead!")
                              .setContentText("Due: " + ord.pickupTime);
              Intent toClover = new Intent(getString(R.string.go_to_clover_order));
              toClover.putExtra(getString(R.string.clover_order_id), ord.id);

              TaskStackBuilder stackBuilder = TaskStackBuilder.create(getActivity());
              stackBuilder.addParentStack(ListOrders.class);
              stackBuilder.addNextIntent(toClover);
              PendingIntent resultPendingIntent =
                      stackBuilder.getPendingIntent(
                              0,
                              PendingIntent.FLAG_UPDATE_CURRENT
                      );
              mBuilder.setContentIntent(resultPendingIntent);
              NotificationManager mNotificationManager =
                      (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
              mNotificationManager.notify(123, mBuilder.build());
            }
          }

          //TODO do notifications and update list here here.
          //TODO look in CloverIntent for open order and add order_id as extra.
        }
      };

        final ScheduledFuture updateHandle =
            scheduler.scheduleAtFixedRate(updater, 120, 120, SECONDS);

        scheduler.schedule(new Runnable() {
          public void run() { updateHandle.cancel(true); }
        }, 24, HOURS);
      };
    }


  private class ErrorDetailsClickListener implements AdapterView.OnItemClickListener {
    ArrayList<Order> orders;
    Activity act;
    public ErrorDetailsClickListener(ArrayList<Order> orders, Activity act)
    {
      this.orders = orders;
      this.act = act;
    }

    public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
      final Dialog d = new Dialog(act);
      WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
      lp.copyFrom(d.getWindow().getAttributes());
      lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
      lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
      d.show();
      d.getWindow().setAttributes(lp);
      d.setContentView(R.layout.order_dialog);
      d.setTitle("Order Details");
      final Order order = orders.get(pos);

      LinearLayout container = (LinearLayout) d.findViewById(R.id.dialogContainer);

      TextView customerTag = formatTextView("Customer: " + order.customerName);
      TextView orderNumber = formatTextView("Number: " + order.orderNumber);
      TextView pickupTime = formatTextView("Pickup-Time: " + order.pickupTime);
      TextView completed = formatTextView("Completed: " + (order.completed ? "Yes" : "No"));

      LinearLayout order_layout = new LinearLayout(getBaseContext());
      order_layout.setOrientation(LinearLayout.VERTICAL);
      order_layout.addView(customerTag);
      order_layout.addView(orderNumber);
      order_layout.addView(pickupTime);
      order_layout.addView(completed);

      container.addView(order_layout);

      LinearLayout buttons = new LinearLayout(getBaseContext());
      buttons.setOrientation(LinearLayout.HORIZONTAL);

      Button markCompleted = new Button(getBaseContext());
      markCompleted.setText("Complete Order");
      markCompleted.setOnClickListener(new  View.OnClickListener() {
        @Override
        public void onClick(View view) {
         try {
           HTTPPostTask post = new HTTPPostTask(new JSONObject(), getString(R.string.clover_example_url) + "/complete/" + order.id);
           post.execute();
           post.get();
           d.dismiss();
           orders = updateOrders();
           adapt.orders = orders;
           adapt.notifyDataSetChanged();
           mListView.setOnItemClickListener(
                   new ErrorDetailsClickListener(orders, getActivity()));
         } catch (Exception e ){
           e.printStackTrace();
         }
        }
      });

      Button goToOrder = new Button(getBaseContext());
      goToOrder.setText("See Order");
      goToOrder.setOnClickListener(new  View.OnClickListener() {
        @Override
        public void onClick(View view) {
          Intent toClover = new Intent(getString(R.string.go_to_clover_order));
          toClover.putExtra(getString(R.string.clover_order_id), order.id);
          startActivity(toClover);
        }
      });

      buttons.addView(markCompleted);
      buttons.addView(goToOrder);

      container.addView(buttons);
    }
  }

  public TextView formatTextView(String text) {
    TextView txtview = new TextView(this);
    txtview.setText(text);
    txtview.setTypeface(null, Typeface.BOLD);
    txtview.setTextSize(textSize);
    txtview.setGravity(Gravity.LEFT);
    txtview.setPadding(0, rightPadding, 0, 0);
    return txtview;
  }


  public class OrderAdapter extends ArrayAdapter<Order> {

    private ArrayList<Order> orders;

    public OrderAdapter(Context context, int resource, ArrayList<Order> orders) {
      super(context, resource, orders);
      this.orders = orders;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      Order order = orders.get(position);

      TextView customerTag = formatTextView("Customer: " + order.customerName);
      TextView orderNumber = formatTextView("Number: " + order.orderNumber);
      TextView pickupTime = formatTextView("Pickup-Time: " + order.pickupTime);
      TextView completed = formatTextView("Completed: " + (order.completed ? "Yes" : "No"));
      
      LinearLayout order_layout = new LinearLayout(getContext());

      if(order.completed) {
        order_layout.setBackgroundColor(Color.GREEN);
      }

      order_layout.setOrientation(LinearLayout.VERTICAL);
      order_layout.addView(customerTag);
      order_layout.addView(orderNumber);
      order_layout.addView(pickupTime);
      order_layout.addView(completed);

      return order_layout;
    }

  }
}

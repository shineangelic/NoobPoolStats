package it.angelic.noobpoolstats;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import im.dacer.androidcharts.LineView;
import it.angelic.noobpoolstats.model.MyDateTypeAdapter;
import it.angelic.noobpoolstats.model.MyTimeStampTypeAdapter;
import it.angelic.noobpoolstats.model.db.NoobPoolDbHelper;
import it.angelic.noobpoolstats.model.jsonpojos.wallet.Payment;
import it.angelic.noobpoolstats.model.jsonpojos.wallet.Wallet;

public class PaymentsActivity extends DrawerActivity {
    private static final SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String minerAddr;
    private GsonBuilder builder;
    private TextView textViewWalletValue;
    private LineView lineViewTotalIncome;
    private TextView textViewPaymentsTitle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payments);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        minerAddr = pref.getString("wallet_addr", null);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(this.getTitle());
        setSupportActionBar(toolbar);

        final NoobPoolDbHelper mDbHelper = new NoobPoolDbHelper(this);
        builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new MyDateTypeAdapter());
        builder.registerTypeAdapter(Calendar.class, new MyTimeStampTypeAdapter());

        textViewWalletValue = (TextView) findViewById(R.id.textViewWalletValue);
        textViewPaymentsTitle= (TextView) findViewById(R.id.textViewPaymentsTitle);
        lineViewTotalIncome = (LineView) findViewById(R.id.lineViewPaymentss);
        textViewWalletValue.setText(minerAddr.toUpperCase());
        textViewWalletValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(Constants.HTTPS_ETHERSCAN_IO_ADDRESS + minerAddr));
                startActivity(i);
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        Utils.fillEthereumStats(this, mDbHelper, (NavigationView) findViewById(R.id.nav_view));
    }

    @Override
    protected void onStart() {
        super.onStart();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view_payment);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_payment);
        final NoobPoolDbHelper mDbHelper = new NoobPoolDbHelper(this);
        issueRefresh(mDbHelper, builder, Constants.MINER_STATS_URL + minerAddr);
    }

    private void issueRefresh(final NoobPoolDbHelper mDbHelper, final GsonBuilder builder, String url) {
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                url, null,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(Constants.TAG, response.toString());

                        Gson gson = builder.create();
                        // Register an adapter to manage the date types as long values
                        Wallet retrieved = gson.fromJson(response.toString(), Wallet.class);
                        mDbHelper.logWalletStats(retrieved);
                        //dati semi grezzi
                        if (retrieved.getPayments() != null) {
                            drawPaymentsTable(retrieved);
                            //la seguente inverte ordine lista
                            NoobChartUtils.drawPaymentsHistory(lineViewTotalIncome, retrieved);
                        }else{
                            textViewPaymentsTitle.setText("No payment Yet");
                        }
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.d(Constants.TAG, "Error: " + error.getMessage());
                // hide the progress dialog
            }
        });

        // Adding request to request queue
        NoobJSONClientSingleton.getInstance(this).addToRequestQueue(jsonObjReq);
    }

    private void drawPaymentsTable(Wallet retrieved) {
        TableLayout minersTable = (TableLayout) findViewById(R.id.tableLayoutPayments);
        minersTable.removeAllViews();
        //table header
        TableRow row = (TableRow) LayoutInflater.from(PaymentsActivity.this).inflate(R.layout.row_payment, null);
        (row.findViewById(R.id.buttonPay)).setVisibility(View.INVISIBLE);
        minersTable.addView(row);

            for (final Payment thispay : retrieved.getPayments()) {
                //one row for each payment
                TableRow rowt = (TableRow) LayoutInflater.from(PaymentsActivity.this).inflate(R.layout.row_payment, null);
                ((TextView) rowt.findViewById(R.id.textViewWorkerName)).setText(yearFormat.format(thispay.getTimestamp()));
                ((TextView) rowt.findViewById(R.id.textViewWorkerHashrate)).setText(Utils.formatEthCurrency(thispay.getAmount()));
                rowt.findViewById(R.id.buttonPay).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //mostra transazione pagamento
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse("https://etherscan.io/tx/" + thispay.getTx()));
                        startActivity(i);
                    }
                });
                minersTable.addView(rowt);
            }
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent opzioni = new Intent(this, SettingsActivity.class);
            startActivity(opzioni);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

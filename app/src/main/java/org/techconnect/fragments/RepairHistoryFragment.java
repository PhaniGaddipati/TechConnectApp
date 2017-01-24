package org.techconnect.fragments;


import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.centum.techconnect.R;
import org.techconnect.activities.SessionListActivity;
import org.techconnect.adapters.CategoryListAdapter;
import org.techconnect.asynctasks.ExportHistoryAsyncTask;
import org.techconnect.sql.TCDatabaseHelper;

import java.util.HashMap;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Used to facilitate accessing the repair history stored in the phone
 */
public class RepairHistoryFragment extends Fragment implements
        View.OnClickListener,
        TextWatcher {

    //States
    private static final int STATE_DATE = 0;
    private static final int STATE_DEVICE = 1;
    private static final int STATE_ACTIVE = 2;

    //All of the binds
    @Bind(R.id.categoryButton)
    ImageButton categoryButton;
    @Bind(R.id.categoryTextView)
    TextView categoryTextView;
    @Bind(R.id.categoryListView)
    ListView categoryListView;
    @Bind(R.id.progressBar)
    ProgressBar progressBar;
    @Bind(R.id.categoryLayout)
    RelativeLayout categoryLayout;
    @Bind(R.id.exportButton)
    Button exportButton;
    @Bind(R.id.emptyTextView)
    TextView emptyTextView;


    //Adapters
    private CategoryListAdapter dateAdapter = new CategoryListAdapter();
    private CategoryListAdapter deviceAdapter = new CategoryListAdapter();
    private CategoryListAdapter activeAdapter = new CategoryListAdapter();
    private int categoryState = STATE_DATE;
    private String[] categoryData;
    private Map<String,String> device_map;

    //Storage for list data
    private Map<String, Integer> deviceCounts = new HashMap<String,Integer>();
    private Map<String, Integer> dateCounts = new HashMap<String,Integer>();
    private Map<String, Integer> activeCounts = new HashMap<String,Integer>();

    public RepairHistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_repair_history, container, false);
        ButterKnife.bind(this, view);

        //Load the map of Name -> Id
        device_map = TCDatabaseHelper.get(this.getContext()).getChartNamesAndIDs();
        //Determine the number of sessions associated with each device
        updateCountAdapters();

        if (dateAdapter.getCount() == 0 || deviceAdapter.getCount() == 0) { //no data
            emptyTextView.setVisibility(View.VISIBLE);
            categoryListView.setVisibility(View.GONE);
            exportButton.setVisibility(View.GONE);
        } else {
            emptyTextView.setVisibility(View.GONE);
            categoryListView.setVisibility(View.VISIBLE);
            exportButton.setVisibility(View.VISIBLE);
        }

        //Set the click listener for the export button
        exportButton.setOnClickListener(this);

        //Setup the ListView w/ adapter and itemClickListener
        switch (categoryState) {
            case STATE_DATE:
                categoryListView.setAdapter(dateAdapter);
                break;
            case STATE_DEVICE:
                categoryListView.setAdapter(deviceAdapter);
                break;
            case STATE_ACTIVE:
                categoryListView.setAdapter(activeAdapter);
                break;
            default:
                categoryListView.setAdapter(null);
        }

        categoryListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (categoryListView.getAdapter().getClass().equals(CategoryListAdapter.class)) {
                    categoryData = (String[]) categoryListView.getItemAtPosition(i);
                    Log.d("Repair History", String.format("Testing Click: %s, %s",categoryData[0], categoryData[1]));
                    Bundle args = new Bundle();
                    Intent intent = new Intent(getContext(), SessionListActivity.class);
                    switch (categoryState) {
                        case STATE_DATE:
                            Log.d("Repair History", "Doing Date");
                            args.putString("date",categoryData[0]);
                            intent.putExtra(SessionListActivity.EXTRA_LOADER,SessionListActivity.SESSION_DATE_LOADER);
                            break;
                        case STATE_DEVICE:
                            Log.d("Repair History", "Doing Device");
                            args.putString("id",device_map.get(categoryData[0]));
                            intent.putExtra(SessionListActivity.EXTRA_LOADER,SessionListActivity.SESSION_DEVICE_LOADER);
                            break;
                        case STATE_ACTIVE:
                            Log.d("Repair History", "Doing Active");
                            args.putBoolean("status",categoryData[0].equals("Active"));
                            intent.putExtra(SessionListActivity.EXTRA_LOADER,categoryData[0].equals("Active") ?
                                    SessionListActivity.SESSION_ACTIVE_LOADER : SessionListActivity.SESSION_FINISHED_LOADER);
                            break;
                    }
                    intent.putExtra(SessionListActivity.EXTRA_TITLE,categoryData[0]);
                    intent.putExtra(SessionListActivity.EXTRA_ARGS,args);
                    startActivity(intent);
                }
            }
        });

        setHasOptionsMenu(true);
        Log.d("Repair History Setup", "View Initialized");

        return view;
    }

    @Override
    public void onResume() {
        Log.d("Repair History", "Resume Fragment");
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.repair_history);
        }
        updateCountAdapters();

        if (dateAdapter.getCount() == 0 || deviceAdapter.getCount() == 0) { //no data
            emptyTextView.setVisibility(View.VISIBLE);
            categoryListView.setVisibility(View.GONE);
            exportButton.setVisibility(View.GONE);
        } else {
            emptyTextView.setVisibility(View.GONE);
            categoryListView.setVisibility(View.VISIBLE);
            exportButton.setVisibility(View.VISIBLE);
        }

        //Setup the ListView w/ adapter and itemClickListener
        switch (categoryState) {
            case STATE_DATE:
                categoryListView.setAdapter(dateAdapter);
                break;
            case STATE_DEVICE:
                categoryListView.setAdapter(deviceAdapter);
                break;
            case STATE_ACTIVE:
                categoryListView.setAdapter(activeAdapter);
                break;
            default:
                categoryListView.setAdapter(null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

    }


    private void updateCountAdapters() {
        for (String dev : device_map.keySet()) {
            int count = TCDatabaseHelper.get(this.getContext()).getSessionsChartCount(device_map.get(dev));
            deviceCounts.put(dev,count);
            Log.d("Repair History", String.format("Device: %s, Count: %d", dev, count ));
        }
        //Determine months/years available in the session database
        dateCounts = TCDatabaseHelper.get(this.getContext()).getSessionDatesCounts();
        for (String comb : dateCounts.keySet()) {
            Log.d("Repair History", String.format("Date: %s, Count: %d",comb,dateCounts.get(comb)));
        }

        //Determine the number of active/finished sessions in the database
        activeCounts = TCDatabaseHelper.get(this.getContext()).getActiveSessionsCounts();

        //Design an adpater to use a map<String, Integer> to make a ListView of the format desired
        dateAdapter.setBaseMap(dateCounts);
        deviceAdapter.setBaseMap(deviceCounts);
        activeAdapter.setBaseMap(activeCounts);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
    }


    private void setAdapter(ListAdapter a) {
        categoryListView.setAdapter(a);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.activity_main_toolbar_menu, menu);
        MenuItem item = menu.findItem(R.id.action_sort);
        item.setVisible(true);

        switch (categoryState) { //Date
            case STATE_DATE:
                //Initially, will have date be the initial way to sort the sessions
                item.getSubMenu().findItem(R.id.date_item).setChecked(true);
                categoryState = STATE_DATE;
                break;
            case STATE_DEVICE:
                item.getSubMenu().findItem(R.id.device_item).setChecked(true);
                categoryState = STATE_DEVICE;
                break;
            case STATE_ACTIVE:
                item.getSubMenu().findItem(R.id.active_item).setChecked(true);
                categoryState = STATE_ACTIVE;
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.isChecked())
            item.setChecked(false);
        else
            item.setChecked(true);

        switch(item.getItemId()) {
            case R.id.date_item:
                Log.d("Repair History","DATE");
                setAdapter(dateAdapter);
                categoryState = STATE_DATE;
                break;
            case R.id.device_item:
                Log.d("Repair History","DEVICE");
                setAdapter(deviceAdapter);
                categoryState = STATE_DEVICE;
                break;
            case R.id.active_item:
                Log.d("Repair History","ACTIVE");
                setAdapter(activeAdapter);
                categoryState = STATE_ACTIVE;
                break;
        }


        return true;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.exportButton) {
            //Open Dialog Box to get email
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LayoutInflater inflater = getActivity().getLayoutInflater();
            builder.setTitle(R.string.export_repair_history);
            builder.setMessage("Send History to Email");
            View v = inflater.inflate(R.layout.dialog_fragment_export_history,null);
            builder.setView(v);

            final EditText email = (EditText) v.findViewById(R.id.comments_editText);

            builder.setPositiveButton(R.string.send, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    //Do nothing here because we override this button later to change the close behaviour.
                    //However, we still need this because on older versions of Android unless we
                    //pass a handler the button doesn't get instantiated
                }
            });
            builder.setNegativeButton(R.string.cancel,new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    //Do nothing here because we override this button later to change the close behaviour.
                    //However, we still need this because on older versions of Android unless we
                    //pass a handler the button doesn't get instantiated
                }
            });

            //Need to overwrite with funky custom listener
            final AlertDialog dialog = builder.create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //Create CSV in Documents directory based on date of export
                    ExportHistoryAsyncTask task = new ExportHistoryAsyncTask(getContext());
                    if (android.util.Patterns.EMAIL_ADDRESS.matcher(email.getText().toString()).matches()) {
                        Log.d("Repair History", "Valid email");
                        task.execute(email.getText().toString());
                        dialog.dismiss();
                    } else {
                        //Show error in the dialog box
                        Log.d("Repair History", "Invalid email");
                        email.setError(getResources().getString(R.string.error_invalid_email));
                    }
                }
            });

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

        }
    }
}


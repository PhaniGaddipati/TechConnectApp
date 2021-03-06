package org.techconnect.asynctasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import org.techconnect.R;
import org.techconnect.sql.TCDatabaseHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by doranwalsten on 1/17/17.
 */

public class ExportHistoryAsyncTask extends AsyncTask<String, Void, Integer> {

    private static final String TAG = "ExportHistoryAsyncTask";

    private final Context context;
    private final ProgressDialog dialog;

    public ExportHistoryAsyncTask(Context context) {
        this.context = context;
        dialog = new ProgressDialog(context);
    }


    @Override
    protected void onPreExecute() {
        //this.dialog.setMessage("Exporting database...");
        //this.dialog.show();
    }

    @Override
    protected Integer doInBackground(final String... args) {
        Log.d(TAG, "Exporting history");
        File exportDir = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TechConnect");
        } else {
            exportDir = new File(Environment.getExternalStorageDirectory() + "/Documents/TechConnect");
        }

        Log.d(TAG, "Exporting history path: " + exportDir.getAbsolutePath());
        boolean present = exportDir.exists();
        if (!present) {
            present = exportDir.mkdirs();
            if (!present) {
                exportDir = new File(Environment.getDataDirectory().toString());
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }
                present = exportDir.exists();
            }
        }

        if (present) {

            Date nowDate = new Date();
            String now = SimpleDateFormat.getDateInstance().format(nowDate);
            File file = new File(exportDir, String.format("History_%s.csv",
                    new SimpleDateFormat("ddMMyyyy").format(nowDate)));
            try {
                Log.d(TAG, "Writing CSV");
                file.createNewFile();
                CSVWriter csvWrite = new CSVWriter(new FileWriter(file));

                //Write to file
                TCDatabaseHelper.get(context).writeRepairHistoryToFile(csvWrite);
                //close the writer
                csvWrite.close();

                Uri photoURI = FileProvider.getUriForFile(context,
                        context.getApplicationContext().getPackageName() + ".org.techconnect.provider", file);

                //Send email based on String arguments
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.my_repair_history));
                emailIntent.putExtra(Intent.EXTRA_TEXT, String.format("Hello,\nThis is my repair history on the date %s", now));
                emailIntent.putExtra(Intent.EXTRA_STREAM, photoURI);
                emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                emailIntent.setType("text/csv");
                Log.d(TAG, "Done writing CSV to " + file.getAbsolutePath());
                context.startActivity(Intent.createChooser(emailIntent, "Select App"));
                return 1;
            } catch (IOException e) {
                Log.e("Export History", e.getMessage(), e);
                return 0;
            }
        } else {
            //Failure to make directory
            Log.e("Export History", "Fail to make file directory");
            return 0;
        }
    }

    @Override
    protected void onPostExecute(final Integer success) {

        //if (this.dialog.isShowing()){
//            this.dialog.dismiss();
//        }
        if (success == 1) {
            //Toast.makeText(this.context, "Export successful!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this.context, "Export failed!", Toast.LENGTH_SHORT).show();
        }
    }
}

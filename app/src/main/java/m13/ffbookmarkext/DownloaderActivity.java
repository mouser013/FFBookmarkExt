package m13.ffbookmarkext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

public class DownloaderActivity extends AppCompatActivity {

    ArrayList<Bookmark> bmarkList;
    ArrayList<Bookmark> failed;
    Boolean deleteAfterDl, deleteFailed;
    String extStor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloader);

        Button ret = (Button) findViewById(R.id.buttonReturn);
        ret.setEnabled(false);
        ret = (Button) findViewById(R.id.buttonExport);
        ret.setEnabled(false);

        Bundle bnd = getIntent().getExtras();

        deleteAfterDl = bnd.getBoolean("deleteAfterDl");
        deleteFailed = bnd.getBoolean("deleteFailed");
        bmarkList = (ArrayList<Bookmark>) bnd.getSerializable("bList");
        extStor = bnd.getString("extStor");


        ListAdapter la = new ListAdapter(this,R.layout.download_info,bmarkList);
        ListView lview = (ListView) findViewById(R.id.listViewDl);
        lview.setAdapter(la);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final DownloadTask dtask = new DownloadTask(this);
        dtask.execute(bmarkList);
    }

    private class DownloadTask extends AsyncTask<ArrayList<Bookmark>, Integer, ArrayList<Bookmark>[]>
    {
        Context context;
        volatile boolean busy = false;

        public DownloadTask(Context c)
        {
            context = c;
        }

        @Override
        protected ArrayList[] doInBackground(ArrayList<Bookmark>... blists)
        {
            ListIterator<Bookmark> iter = blists[0].listIterator();
            ArrayList<Bookmark> del = new ArrayList<>();
            ArrayList<Bookmark> fail = new ArrayList<>();

            String url = null, path = null;
            File ddir, dfile;

            int current = 0;


            while (iter.hasNext())
            {
                Bookmark b = iter.next();
                url = b.getUrl();
                ddir = new File(extStor);
                if (!ddir.exists())
                    ddir.mkdirs();
                path = extStor + "/" + Uri.parse(url).getLastPathSegment();

                Boolean failed = false;

                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                try {
                    URL Url = new URL(url);
                    connection = (HttpURLConnection) Url.openConnection();
                    connection.connect();

                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                    {
                        //return "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
                        failed = true;
                    }

                    if (!failed)
                    {
                        int fileLength = connection.getContentLength();

                        input = connection.getInputStream();
                        output = new FileOutputStream(path);

                        byte data[] = new byte[4096];
                        long total = 0;
                        int count;
                        while ((count = input.read(data)) != -1)
                        {
                            total += count;
                            if (fileLength > 0)
                            {
                                if(!busy)
                                {
                                    busy = true;
                                    publishProgress(current,(int)total*100/fileLength,0);
                                }
                            }

                            output.write(data, 0, count);
                        }
                    }
                }
                catch (Exception e)
                {
                    failed = true;
                }
                finally
                {
                    try
                    {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    } catch (IOException ignored)
                    {
                    }

                    if (connection != null)
                        connection.disconnect();
                }

                if(!failed)
                {
                    publishProgress(current,100,1);
                    if(deleteAfterDl)
                        del.add(b);
                }
                else
                {
                    fail.add(b);
                    if(deleteAfterDl && deleteFailed)
                        del.add(b);
                }

                current++;
            }
            return new ArrayList[]{del,fail};
        }

        @Override
        protected void onPostExecute(ArrayList<Bookmark>[] retl)
        {
            Intent i = new Intent();
            Bundle rb = new Bundle();
            rb.putSerializable("del", retl[0]);
            i.putExtras(rb);
            setResult(Activity.RESULT_OK, i);

            failed = retl[1];

            Button ret = (Button) findViewById(R.id.buttonReturn);
            ret.setEnabled(true);
            ret = (Button) findViewById(R.id.buttonExport);
            if(failed.size() > 0)
            {
                AlertDialog.Builder db = new AlertDialog.Builder(context);
                db.setTitle("");
                db.setMessage(failed.size()+" download(s) failed");
                db.setPositiveButton("OK",null);
                AlertDialog dialog = db.create();
                dialog.show();
                ret.setEnabled(true);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... arg)
        {
            ListView l = (ListView) findViewById(R.id.listViewDl);
            ListAdapter la = (ListAdapter) l.getAdapter();
            ListIterator<Bookmark> iter = la.bmarkList.listIterator();

            la.progressList.set(arg[0],arg[1]);
            if(arg[1] == 100)
                la.bmarkList.get(arg[0]).select();

            la.notifyDataSetChanged();
            busy = false;
        }
    }

    public boolean downloadFile(String sUrl, String dpath)
    {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(sUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
            {
                //return "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
                return false;
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            int fileLength = connection.getContentLength();

            input = connection.getInputStream();
            output = new FileOutputStream(dpath);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                // allow canceling with back button
                /*if (isCancelled()) {
                    input.close();
                    return null;
                }*/
                total += count;
                //if (fileLength > 0) // only if total length is known
                //    publishProgress((int) (total * 100 / fileLength));
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
        return true;
    }

    public void FinishActivity(View view)
    {
        finish();
    }

    public void ExportFailed(View view)
    {
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();

        String outfpath = extStor + "/failed-" + ts + ".txt";
        File outfile = new File(outfpath);

        AlertDialog.Builder db = new AlertDialog.Builder(this);
        db.setTitle("Export URLs");

        try
        {
            FileWriter writer = new FileWriter(outfile);
            ListIterator<Bookmark> iter = failed.listIterator();
            while(iter.hasNext())
            {
                Bookmark b = iter.next();
                writer.append(b.url+"\n");
                writer.flush();
            }
            writer.close();
            db.setMessage(failed.size() + " URL(s) exported to "+outfpath);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            db.setMessage("Failed");
        }

        db.setPositiveButton("OK",null);
        AlertDialog dialog = db.create();
        dialog.show();
        findViewById(R.id.buttonExport).setEnabled(false);
    }

    private class ListAdapter extends ArrayAdapter<Bookmark>
    {
        private ArrayList<Bookmark> bmarkList;
        private ArrayList<Integer> progressList;
        public int parent;
        public String name;

        public ListAdapter(Context context, int textViewResourceId, ArrayList<Bookmark> bmarkList)
        {
            super(context,textViewResourceId,bmarkList);
            //this.bmarkList = new ArrayList<Bookmark>();
            //this.bmarkList.addAll(bmarkList);
            this.bmarkList = bmarkList;
            progressList = new ArrayList<>(Collections.nCopies(bmarkList.size(),0));
        }

        private class ViewHolder
        {
            CheckedTextView url;
            ProgressBar progressBar;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {

            ListAdapter.ViewHolder holder = null;
            Log.v("ConvertView", String.valueOf(position));

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(R.layout.download_info, null);

                holder = new ListAdapter.ViewHolder();
                //holder.d_url = (TextView) convertView.findViewById(R.id.d_url);
                holder.url = (CheckedTextView) convertView.findViewById(R.id.checkedTextView);
                holder.progressBar = (ProgressBar) convertView.findViewById(R.id.progressBar);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ListAdapter.ViewHolder) convertView.getTag();
            }

            Bookmark bmark = bmarkList.get(position);
            //holder.d_url.setText(bmark.getUrl());
            holder.url.setText(bmark.getUrl());
            holder.url.setChecked(bmark.isSelected());
            holder.url.setTag(bmark);
            holder.progressBar.setProgress(progressList.get(position));

            return convertView;
        }

        private void checkButtonClick()
        {
            Button myButton = (Button) findViewById(R.id.buttonGo);
            myButton.setOnClickListener(new View.OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                }
            });

        }

    }
}

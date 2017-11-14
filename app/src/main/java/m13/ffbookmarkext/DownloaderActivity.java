package m13.ffbookmarkext;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
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
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

public class DownloaderActivity extends AppCompatActivity {

    ArrayList<Bookmark> bmarkList;
    ArrayList<Bookmark> failed;
    ArrayList<String> ex;
    Boolean deleteAfterDl, deleteFailed;
    String extStor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloader);

        Button ret = findViewById(R.id.buttonReturn);
        ret.setEnabled(false);
        ret = findViewById(R.id.buttonExport);
        ret.setEnabled(false);
        ret = findViewById(R.id.buttonRetry);
        ret.setEnabled(false);
        ret.setVisibility(View.GONE);

        Bundle bnd = getIntent().getExtras();

        if (bnd != null)
        {
            deleteAfterDl = bnd.getBoolean("deleteAfterDl");
            deleteFailed = bnd.getBoolean("deleteFailed");
            bmarkList = (ArrayList<Bookmark>) bnd.getSerializable("bList");
            extStor = bnd.getString("extStor");
        }

        ListAdapter la = new ListAdapter(this,R.layout.download_info,bmarkList);
        ListView lview = findViewById(R.id.listViewDl);
        lview.setAdapter(la);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DownloadTask dtask = new DownloadTask(this);;
        //noinspection unchecked
        dtask.execute(bmarkList);
    }

    @SuppressLint("StaticFieldLeak")
    private class DownloadTask extends AsyncTask<ArrayList<Bookmark>, Integer, Pair<ArrayList<Bookmark>[], ArrayList<String>>>
    {
        Context context;
        volatile boolean busy = false;

        DownloadTask(Context c)
        {
            context = c;
        }

        @SuppressWarnings("unchecked")
        @SafeVarargs
        @Override
        protected final Pair<ArrayList<Bookmark>[], ArrayList<String>> doInBackground(ArrayList<Bookmark>... blists)
        {
            ListIterator<Bookmark> iter = blists[0].listIterator();
            ArrayList<Bookmark> del = new ArrayList<>();
            ArrayList<Bookmark> fail = new ArrayList<>();
            ArrayList<String> ex = new ArrayList<>();

            String url, path;
            File ddir;

            int current = 0;


            while (iter.hasNext())
            {
                ((ListView)findViewById(R.id.listViewDl)).smoothScrollToPosition(current);
                Bookmark b = iter.next();
                url = b.getUrl();
                ddir = new File(extStor);
                if (!ddir.exists())
                    //noinspection ResultOfMethodCallIgnored
                    ddir.mkdirs();
                path = extStor + "/" + Uri.parse(url).getLastPathSegment();

                Boolean failed = false;

                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                try
                {
                    URL Url = new URL(url);
                    connection = (HttpURLConnection) Url.openConnection();

                    //connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(5000);

                    connection.connect();

                    int response = connection.getResponseCode();

                    if (response != HttpURLConnection.HTTP_OK && response != -1 && response != 400)
                    {
                        //return "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage();
                        //failed = false;
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
                    ex.add(e.toString());
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
            return new Pair<ArrayList<Bookmark>[], ArrayList<String>>(new ArrayList[]{del,fail},ex);
        }

        @Override
        protected void onPostExecute(Pair<ArrayList<Bookmark>[], ArrayList<String>> retp)
        {
            Intent i = new Intent();
            Bundle rb = new Bundle();
            rb.putSerializable("del", retp.first[0]);
            i.putExtras(rb);
            setResult(Activity.RESULT_OK, i);

            failed = retp.first[1];
            ex = retp.second;

            Button ret = findViewById(R.id.buttonReturn);
            ret.setEnabled(true);
            if(failed.size() > 0)
            {
                AlertDialog.Builder db = new AlertDialog.Builder(context);
                db.setTitle("");
                db.setMessage(failed.size()+" download(s) failed");
                db.setPositiveButton("OK",null);
                AlertDialog dialog = db.create();
                dialog.show();
                ret = findViewById(R.id.buttonExport);
                ret.setEnabled(true);
                ret = findViewById(R.id.buttonRetry);
                ret.setEnabled(true);
                ret.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... arg)
        {
            ListView l = findViewById(R.id.listViewDl);
            ListAdapter la = (ListAdapter) l.getAdapter();
            ListIterator<Bookmark> iter = la.bmarkList.listIterator();

            la.progressList.set(arg[0],arg[1]);
            if(arg[1] == 100)
                la.bmarkList.get(arg[0]).select();

            la.notifyDataSetChanged();
            busy = false;
        }
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
            int i = 0;
            FileWriter writer = new FileWriter(outfile);
            for (Bookmark b : failed)
            {
                i++;
                writer.append("(").append(Integer.toString(i)).append(")").append(b.url).append("\n");
                writer.flush();
            }
            i = 0;
            for(String e : ex)
            {
                i++;
                writer.append("(").append(Integer.toString(i)).append(")").append(e).append("\n");
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

    public  void RetryFailed(View view)
    {
        ListAdapter la = new ListAdapter(this,R.layout.download_info,failed);
        ListView lview = findViewById(R.id.listViewDl);
        lview.setAdapter(la);
        DownloadTask dtask = new DownloadTask(this);;
        //noinspection unchecked
        dtask.execute(failed);
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

        @SuppressLint("InflateParams")
        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent)
        {

            ListAdapter.ViewHolder holder;
            Log.v("ConvertView", String.valueOf(position));

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (vi != null)
                {
                    convertView = vi.inflate(R.layout.download_info, null);
                }

                holder = new ListAdapter.ViewHolder();
                //holder.d_url = (TextView) convertView.findViewById(R.id.d_url);
                holder.url = convertView.findViewById(R.id.checkedTextView);
                holder.progressBar = convertView.findViewById(R.id.progressBar);
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
            Button myButton = findViewById(R.id.buttonGo);
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

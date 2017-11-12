package m13.ffbookmarkext;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.database.sqlite.*;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class MainActivity extends AppCompatActivity {

    ListAdapter dataAdapter = null;
    ArrayList<ListAdapter> bmarkLists;
    public String dbfile;
    public String dbfile_orig;
    public static String extStor;
    public String dbBackupDir;
    boolean deleteAfterDl = false, deleteFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        String[] permissions = new String[]
                {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.INTERNET
                };
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions)
        {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
            {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty())
        {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 100);
        }

        //if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        //    ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        extStor = Environment.getExternalStorageDirectory() + "/FFBookmarkExt";
        dbBackupDir = extStor + "/backups";
        File f = new File(dbBackupDir);
        f.mkdirs();

        getBookmarkDB();

        bmarkLists = parseBookmarkDB();
        if(bmarkLists == null)
            return;

        ListIterator<ListAdapter> liter = bmarkLists.listIterator();
        while(liter.hasNext())
        {
            ListIterator<Bookmark> iter = liter.next().bmarkList.listIterator();
            while(iter.hasNext())
            {
                iter.next().deselect();
            }
        }

        displayListView(bmarkLists.get(bmarkLists.size()-1));
    }

    private void getBookmarkDB()
    {
        String profileDir = "/data/data/org.mozilla.firefox_beta/files/mozilla/", browserDBPath = "NOT_FOUND";
        File f = new File(profileDir);
        String dir[] = new String[0], profileDirName = "";
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su -c ls "+profileDir+" | grep .default");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));

            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((read = reader.read(buffer)) > 0) {
                output.append(buffer, 0, read);
            }
            reader.close();;
            profileDirName = output.toString().replace("\n","");
            Log.i("out",profileDirName);
            p.waitFor();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        /*
        for(String n : dir)
        {
            if(n.contains(".default") && new File(profileDir+n).isDirectory())
                browserDBPath = profileDir+n+"/browser.db";
        }*/
        dbfile_orig = profileDir+profileDirName+"/browser.db";
        if(new File(dbfile_orig).isDirectory())
            return;

        dbfile = this.getFilesDir().getAbsolutePath();
        f = new File(dbfile);
        f.mkdirs();
        dbfile += "/browser.db";

        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        String dbBackup = dbBackupDir + "/browser.db-" + ts + ".bak";


        //dbfile_orig = "/data/data/org.mozilla.firefox_beta/files/mozilla/0aekm8mk.default/browser.db";

        try {
            //
            p = Runtime.getRuntime().exec("su -c cat "+dbfile_orig+ " > " + dbfile);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c cat "+dbfile_orig+ " > " + dbBackup);
            p.waitFor();
            //p = Runtime.getRuntime().exec("su -c chmod 644 "+ browserDBPath);
            p = Runtime.getRuntime().exec("su -c chmod 666 "+ dbfile);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c chown 10257 "+ dbfile);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c chgrp 10257 "+ dbfile);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c chcon u:object_r:app_data_file:s0:c512,c768 "+ dbfile);
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        /*
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("/");
        Intent intent = Intent.createChooser(i,"Select browser.db");
        startActivityForResult(intent,666);*/
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode == 6666)
        {
            if(resultCode == Activity.RESULT_OK)
            {
                ListView listView = (ListView) findViewById(R.id.listView);
                ListAdapter la = (ListAdapter) listView.getAdapter();
                ArrayList<Bookmark> del = (ArrayList<Bookmark>) data.getExtras().get("del");
                if(deleteAfterDl)
                    deleteList(la.bmarkList,del);
                displayListView(la);
            }

            deleteAfterDl = false;
            deleteFailed = false;
        }
    }

    private ArrayList<ListAdapter> parseBookmarkDB()
    {
        ArrayList<Bookmark> bmarkList = new ArrayList<Bookmark>();
        Bookmark root = null;
        try
        {
            File f = new File(dbfile);
            if(!f.exists())
                return null;
            SQLiteDatabase db = SQLiteDatabase.openDatabase(dbfile,null, SQLiteDatabase.OPEN_READONLY);
            Cursor c = db.rawQuery("SELECT _id,title,url,type,parent  FROM bookmarks WHERE not deleted = 1 ORDER BY created",null);
            while(c.moveToNext())
            {
                String url = c.getString(2), title = c.getString(1);
                int id = c.getInt(0), parent = c.getInt(4), type = c.getInt(3);

                Bookmark b = new Bookmark(id,title,url,type,parent);
                if(b.id == 0)
                    root = b;
                bmarkList.add(b);
            }
            c.close();
            db.close();
        }
        catch (Exception sqlEx)
        {
            Log.e("SQLException", sqlEx.getMessage());
            return null;
        }

        ArrayList<ListAdapter> bmarkLists = new ArrayList<ListAdapter>();
        processBookmarks(bmarkLists,bmarkList,root);

        return bmarkLists;
    }

    private void processBookmarks(ArrayList<ListAdapter> lists, ArrayList<Bookmark> bmarkList, Bookmark parent)
    {
        ListIterator<Bookmark> iter = bmarkList.listIterator();

        ArrayList<Bookmark> list = new ArrayList<Bookmark>();

        if(parent.id != 0)
        {
            Bookmark b = new Bookmark(parent.parent,"...","",0,parent.id);
            b.url = "";
            list.add(b);
        }

        while(iter.hasNext())
        {
            Bookmark b = iter.next();
            if(b.type == 0) {
                boolean proc = false;
                ListIterator<ListAdapter> liter = lists.listIterator();
                while (liter.hasNext())
                {
                    ListAdapter l = liter.next();
                    if (l.parent == b.id)
                        proc = true;
                }
                if(b.selected)
                    proc = true;
                if(!proc)
                {
                    bmarkList.get(bmarkList.indexOf(b)).select();
                    processBookmarks(lists,bmarkList,b);
                }
            }

            if(b.parent == parent.id && b.id != 0)
                list.add(b);
        }
        ListAdapter listAdapter = new ListAdapter(this,R.layout.bookmark_info,list);
        listAdapter.parent = parent.id;
        listAdapter.name = parent.getTitle();
        lists.add(listAdapter);
    }

    private void displayListView(ListAdapter dataAdapter)
    {
        //ArrayList<Bookmark> bmarkList = new ArrayList<Bookmark>();
        //Bookmark bmark = new Bookmark(Uri.parse("http://u1"));
        //bmarkList.add(bmark);
        //bmark = new Bookmark(Uri.parse("http://u2"));
        //bmarkList.add(bmark);
        //bmark = new Bookmark(Uri.parse("http://u3"));
        //bmarkList.add(bmark);

        ListView listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(dataAdapter);


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                Bookmark bmark = (Bookmark) parent.getItemAtPosition(position);
                Toast.makeText(getApplicationContext(),"Clicked on Row: " + bmark.getUrl(), Toast.LENGTH_LONG).show();
                if(bmark.type == 0)
                {
                    setViewList(bmark);
                }
            }
        });

    }

    public void setViewList(Bookmark bookmark)
    {
        ListIterator<ListAdapter> iter = bmarkLists.listIterator();
        while(iter.hasNext())
        {
            ListAdapter l = iter.next();
            if(l.parent == bookmark.id)
                displayListView(l);
        }
    }

    public void getFile()
    {
        ListView listView = (ListView) findViewById(R.id.listView);
        ListAdapter la = (ListAdapter) listView.getAdapter();

        ListIterator<Bookmark> iter = la.bmarkList.listIterator();
        ArrayList<Bookmark> dl = new ArrayList<>();

        while(iter.hasNext())
        {
            Bookmark b = iter.next();
            if(b.isSelected() && b.type != 0)
            {
                Bookmark bn = new Bookmark(b.id,b.title,b.url,b.type,b.parent);
                dl.add(bn);
            }
        }

        Intent i = new Intent(this,DownloaderActivity.class);
        Bundle b = new Bundle();
        b.putBoolean("deleteAfterDl",deleteAfterDl);
        b.putBoolean("deleteFailed",deleteFailed);
        b.putSerializable("bList",dl);
        b.putString("extStor",extStor);
        i.putExtras(b);
        startActivityForResult(i,6666);



        StringBuffer responseText = new StringBuffer();
        responseText.append("The following were selected...\n");

        /*for(int i=0;i<blist.size();i++)
        {
            Bookmark bmark = blist.get(i);
            if(bmark.isSelected())
            {
                responseText.append("\n" + bmark.getUrl());
            }
        }*/

        //Toast.makeText(getApplicationContext(), responseText, Toast.LENGTH_LONG).show();
    }

    public void showDlOptionsDialog(View view)
    {
        String[] opt = {"Delete after download","Delete failed downloads"};
        ArrayList sel = new ArrayList();
        AlertDialog.Builder db = new AlertDialog.Builder(this);
        db.setTitle("Post-download options");
        db.setMultiChoiceItems(opt, null, new DialogInterface.OnMultiChoiceClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i, boolean b)
            {
                if(i == 0)
                    deleteAfterDl = !deleteAfterDl;
                if(i == 1)
                    deleteFailed = !deleteFailed;
            }
        }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                getFile();
            }
        });
        AlertDialog dialog = db.create();
        dialog.show();
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

    public void selectAllNone(View view)
    {
        ListView listView = (ListView) findViewById(R.id.listView);
        ListAdapter la = (ListAdapter) listView.getAdapter();
        ArrayList<Bookmark> blist = la.bmarkList;
        ListIterator<Bookmark> iter = blist.listIterator();

        boolean all = false;
        if(((ToggleButton)view).isChecked())
            all = true;

        while(iter.hasNext())
        {
            if(all)
                iter.next().select();
            else
                iter.next().deselect();
        }
        displayListView(la);
    }

    public void deleteBookmarks(View view)
    {
        ListView listView = (ListView) findViewById(R.id.listView);
        ListAdapter la = (ListAdapter) listView.getAdapter();
        ArrayList<Bookmark> blist = la.bmarkList;
        ListIterator<Bookmark> iter = blist.listIterator();
        ArrayList<Bookmark> del = new ArrayList<>();

        while(iter.hasNext())
        {
            Bookmark b = iter.next();
            if(b.isSelected())
            {
                del.add(b);
            }
        }

        deleteList(la.bmarkList,del);
        //saveBookmarksDB();
        displayListView(la);
    }

    public void deleteList(ArrayList<Bookmark> list, ArrayList<Bookmark> del)
    {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbfile,null, SQLiteDatabase.OPEN_READWRITE);
        ListIterator<Bookmark> iter = del.listIterator();
        while(iter.hasNext())
        {
            Bookmark b = iter.next();
            ListIterator<Bookmark> liter = list.listIterator();
            while(liter.hasNext())
            {
                if(liter.next().id == b.id)
                {
                    liter.remove();
                    try
                    {
                        //db.rawQuery("DELETE FROM bookmarks WHERE _id = ?",new String[]{String.valueOf(b.getId())});
                        db.delete("bookmarks","_id = ?",new String[]{Integer.toString(b.getId())});
                    }
                    catch(Exception ex)
                    {
                        Log.e("SQLException", ex.getMessage());
                    }
                }
            }

        }
    }

    public void saveBookmarksDB()
    {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su -c cat "+ dbfile + " > " + dbfile_orig);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c chmod 660 " + dbfile_orig);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c chown 10143 " + dbfile_orig);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c chgrp 10143 " + dbfile_orig);
            p.waitFor();
            p = Runtime.getRuntime().exec("su -c restorecon " + dbfile_orig);
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class ListAdapter extends ArrayAdapter<Bookmark>
    {
        private ArrayList<Bookmark> bmarkList;
        public int parent;
        public String name;
        private Drawable drawNoBox = new ColorDrawable(Color.TRANSPARENT);

        public ListAdapter(Context context, int textViewResourceId, ArrayList<Bookmark> bmarkList)
        {
            super(context,textViewResourceId,bmarkList);
            this.bmarkList = new ArrayList<Bookmark>();
            this.bmarkList.addAll(bmarkList);
        }

        private class ViewHolder
        {
            TextView url;
            CheckBox name;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {

            ViewHolder holder = null;
            Log.v("ConvertView", String.valueOf(position));

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(R.layout.bookmark_info, null);

                holder = new ViewHolder();
                holder.url = (TextView) convertView.findViewById(R.id.url);
                holder.name = (CheckBox) convertView.findViewById(R.id.checkBox1);
                convertView.setTag(holder);

                holder.name.setOnClickListener( new View.OnClickListener()
                {
                    public void onClick(View v)
                    {
                        CheckBox cb = (CheckBox) v ;
                        Bookmark bmark = (Bookmark) cb.getTag();
                        Toast.makeText(getApplicationContext(),
                                "Clicked on Checkbox: " + cb.getText() +
                                        " is " + cb.isChecked(),
                                Toast.LENGTH_LONG).show();
                        if(cb.isChecked())
                            bmark.select();
                        else
                            bmark.deselect();
                    }
                });
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            Bookmark bmark = bmarkList.get(position);
            if(bmark.type == 1)
                holder.url.setText(" (" +  bmark.getUrl() + ")");
            holder.name.setText(bmark.getTitle());
            holder.name.setChecked(bmark.isSelected());
            holder.name.setTag(bmark);

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
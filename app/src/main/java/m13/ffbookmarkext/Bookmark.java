package m13.ffbookmarkext;

import android.os.Parcel;
import android.os.Parcelable;

public class Bookmark implements Parcelable
{
    int id, parent, type;
    String title, url;
    boolean selected;

    public Bookmark(int i, String t, String u, int ty, int p)
    {
        id = i;
        parent = p;
        title = t;
        type = ty;
        url = u;
        selected = false;
    }

    protected Bookmark(Parcel in)
    {
        id = in.readInt();
        parent = in.readInt();
        type = in.readInt();
        title = in.readString();
        url = in.readString();
        selected = in.readByte() != 0;
    }

    public static final Creator<Bookmark> CREATOR = new Creator<Bookmark>()
    {
        @Override
        public Bookmark createFromParcel(Parcel in)
        {
            return new Bookmark(in);
        }

        @Override
        public Bookmark[] newArray(int size)
        {
            return new Bookmark[size];
        }
    };

    public String getUrl()
    {
        return url;
    }

    public String getTitle()
    {
        return title;
    }

    public int getId()
    {
        return id;
    }

    public int getParent()
    {
        return parent;
    }

    public int getType() {return type;}

    public boolean isSelected()
    {
        return selected;
    }

    public void select()
    {
        selected = true;
    }

    public void deselect()
    {
        selected = false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(id);
        parcel.writeInt(parent);
        parcel.writeInt(type);
        parcel.writeString(title);
        parcel.writeString(url);
        parcel.writeInt(selected?1:0);
    }
}

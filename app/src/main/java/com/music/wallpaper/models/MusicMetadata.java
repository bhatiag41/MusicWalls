package com.music.wallpaper.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Data model representing music metadata extracted from notifications.
 * Implements Parcelable for efficient passing between components.
 */
public class MusicMetadata implements Parcelable {
    
    private final String trackTitle;
    private final String artistName;
    private final String albumName;
    private final Bitmap albumArtBitmap;
    
    /**
     * Constructor for MusicMetadata.
     *
     * @param trackTitle     Title of the track
     * @param artistName     Name of the artist
     * @param albumName      Name of the album
     * @param albumArtBitmap Album artwork bitmap (can be null)
     */
    public MusicMetadata(@Nullable String trackTitle, 
                        @Nullable String artistName,
                        @Nullable String albumName,
                        @Nullable Bitmap albumArtBitmap) {
        this.trackTitle = trackTitle;
        this.artistName = artistName;
        this.albumName = albumName;
        this.albumArtBitmap = albumArtBitmap;
    }
    
    protected MusicMetadata(Parcel in) {
        trackTitle = in.readString();
        artistName = in.readString();
        albumName = in.readString();
        albumArtBitmap = in.readParcelable(Bitmap.class.getClassLoader());
    }
    
    @NonNull
    public String getTrackTitle() {
        return trackTitle != null ? trackTitle : "Unknown Track";
    }
    
    @NonNull
    public String getArtistName() {
        return artistName != null ? artistName : "Unknown Artist";
    }
    
    @NonNull
    public String getAlbumName() {
        return albumName != null ? albumName : "Unknown Album";
    }
    
    @Nullable
    public Bitmap getAlbumArtBitmap() {
        return albumArtBitmap;
    }
    
    public boolean hasAlbumArt() {
        return albumArtBitmap != null;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(trackTitle);
        dest.writeString(artistName);
        dest.writeString(albumName);
        dest.writeParcelable(albumArtBitmap, flags);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Creator<MusicMetadata> CREATOR = new Creator<MusicMetadata>() {
        @Override
        public MusicMetadata createFromParcel(Parcel in) {
            return new MusicMetadata(in);
        }
        
        @Override
        public MusicMetadata[] newArray(int size) {
            return new MusicMetadata[size];
        }
    };
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        MusicMetadata that = (MusicMetadata) o;
        
        if (trackTitle != null ? !trackTitle.equals(that.trackTitle) : that.trackTitle != null)
            return false;
        if (artistName != null ? !artistName.equals(that.artistName) : that.artistName != null)
            return false;
        return albumName != null ? albumName.equals(that.albumName) : that.albumName == null;
    }
    
    @Override
    public int hashCode() {
        int result = trackTitle != null ? trackTitle.hashCode() : 0;
        result = 31 * result + (artistName != null ? artistName.hashCode() : 0);
        result = 31 * result + (albumName != null ? albumName.hashCode() : 0);
        return result;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "MusicMetadata{" +
                "trackTitle='" + getTrackTitle() + '\'' +
                ", artistName='" + getArtistName() + '\'' +
                ", albumName='" + getAlbumName() + '\'' +
                ", hasAlbumArt=" + hasAlbumArt() +
                '}';
    }
}

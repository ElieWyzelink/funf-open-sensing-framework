package edu.mit.media.hd.funf.probe.builtin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Video;
import edu.mit.media.hd.funf.probe.CursorCell;
import edu.mit.media.hd.funf.probe.DatedContentProviderProbe;

public class VideosProbe extends DatedContentProviderProbe {

	public static final String VIDEOS = "VIDEOS";
	
	@Override
	protected Uri getContentProviderUri() {
		return Video.Media.EXTERNAL_CONTENT_URI;
	}

	@Override
	protected String getDateColumnName() {
		return  Video.Media.DATE_MODIFIED;
	}

	protected TimeUnit getDateColumnTimeUnit() {
		return TimeUnit.SECONDS;
	}
	

	@Override
	protected String getDataName() {
		return VIDEOS;
	}

	@Override
	protected Map<String, CursorCell<?>> getProjectionMap() {
		Map<String, CursorCell<?>> projectionMap = new HashMap<String, CursorCell<?>>();
		projectionMap.put(Video.Media._ID, stringCell());
		// Ignoring DATA, too large and not relevant
		projectionMap.put(Video.Media.DATE_ADDED, longCell());
		projectionMap.put(Video.Media.DATE_MODIFIED, longCell());
		projectionMap.put(Video.Media.DISPLAY_NAME, hashedStringCell());
		projectionMap.put(Video.Media.MIME_TYPE, stringCell());
		projectionMap.put(Video.Media.SIZE, longCell());
		projectionMap.put(Video.Media.TITLE, hashedStringCell());
		projectionMap.put(Video.Media.ALBUM, stringCell());
		projectionMap.put(Video.Media.ARTIST, stringCell());
		projectionMap.put(Video.Media.BOOKMARK, intCell());
		projectionMap.put(Video.Media.BUCKET_DISPLAY_NAME, stringCell());
		projectionMap.put(Video.Media.BUCKET_ID, stringCell());
		projectionMap.put(Video.Media.CATEGORY, stringCell());
		projectionMap.put(Video.Media.DATE_TAKEN, longCell());
		projectionMap.put(Video.Media.DESCRIPTION, stringCell());
		projectionMap.put(Video.Media.DURATION, longCell());
		projectionMap.put(Video.Media.IS_PRIVATE, intCell());
		projectionMap.put(Video.Media.LANGUAGE, stringCell());
		projectionMap.put(Video.Media.LATITUDE, doubleCell());
		projectionMap.put(Video.Media.LONGITUDE, doubleCell());
		projectionMap.put(Video.Media.MINI_THUMB_MAGIC, intCell());
		projectionMap.put(Video.Media.RESOLUTION, stringCell());
		projectionMap.put(Video.Media.TAGS, stringCell());
		return projectionMap;
	}

	@Override
	public String[] getRequiredPermissions() {
		return null;
	}
	
}

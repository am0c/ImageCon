package org.am0c.imagecon;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.am0c.imagecon.utils.ConcurrentCacheMap;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

public class AlbumActivity extends Activity {
    /** Called when the activity is first created. */

	private ImagesAdapter adapter;
	private ThumbnailManager manager;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.album);

        manager = new ThumbnailManager();
        adapter = new ImagesAdapter(this, R.layout.album_item, manager.getThumbnailList());
        
        GridView gv = (GridView) findViewById(R.id.album_list);
        gv.setAdapter(adapter);
    }

	private class ImagesAdapter extends ArrayAdapter<Integer> {

		private Integer[] images;
		
		public ImagesAdapter(Context context, int resourceId, Integer[] items) {
			super(context, resourceId, items);
			this.images = items;
		}
	
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			
			if (v == null) {
				LayoutInflater vi = getLayoutInflater();
				v = vi.inflate(R.layout.album_item, null);
			} 
			
			if (v != null) {
				manager.setThumbnailAsync(v, images[position]);
			}
			
			return v;
		}
	}

	private class ThumbnailManager {
		
		private final static String TAG = "ThumbnailManager";
		
		private ConcurrentCacheMap<Integer, Bitmap> bitmapCache;
		private ArrayBlockingQueue<ThumbnailView> taskQueue;
		private ExecutorService taskExecutor;
		private Runnable taskService;
		
		public ThumbnailManager() {
			bitmapCache = new ConcurrentCacheMap<Integer, Bitmap>(60);
			
			taskQueue = new ArrayBlockingQueue<ThumbnailView>(60);
			
			taskService = new AdapterService();
			taskExecutor = Executors.newSingleThreadExecutor();
			taskExecutor.execute(taskService);
		}
		
		public void setThumbnailAsync(View convertView, Integer thumbnailId) {
			ImageView imageView = (ImageView) convertView.findViewById(R.id.album_item_image);
			if (bitmapCache.containsKey(thumbnailId)) {
				imageView.setImageBitmap(bitmapCache.get(thumbnailId));
				return;
			}
			try {
				
				taskQueue.add(new ThumbnailView(thumbnailId, convertView));
				//Log.d(TAG, "Queue added for id " + thumbnailId);
			} catch (IllegalStateException e) {
				//Log.d(TAG, "Queue not added for id " + thumbnailId);
			}
		}
		
		public Integer[] getThumbnailList() {
			Cursor images = getContentResolver().query(
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				new String[] { MediaStore.Images.Media._ID },
				null,
				null,
				MediaStore.Images.Media.DEFAULT_SORT_ORDER
			);
			
			ArrayList<Integer> list = new ArrayList<Integer>();
			
			images.moveToFirst();
			while (!images.isAfterLast()) {
				list.add(images.getInt(images.getColumnIndex(MediaStore.Images.Media._ID)));
				images.moveToNext();
			}
			Log.d(TAG, "Number of images: " + images.getCount());
			images.close();
			return list.toArray(new Integer[list.size()]);
		}
		
		private class AdapterService implements Runnable {
			
			private final Drawable drawableBlack = new ColorDrawable(Color.BLACK);
			
			@Override
			public void run() {
				for (;;) {
					final ThumbnailView tv;
					try {
						// TODO Use android.os.Looper instead?
						tv = taskQueue.poll(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						continue;
					}
					
					if (tv == null)
						continue;
					
					if (bitmapCache.containsKey(tv.getId())) {
						tv.setBitmap(bitmapCache.get(tv.getId()));
						continue;
					}
					
					Cursor tcur = MediaStore.Images.Thumbnails.queryMiniThumbnail(
						getContentResolver(),
						tv.getId(),
						MediaStore.Images.Thumbnails.MINI_KIND,
						new String[] { MediaStore.Images.Thumbnails.DATA }
					);

					Cursor tmime = getContentResolver().query(
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						new String[] { MediaStore.Images.Media.MIME_TYPE },
						MediaStore.Images.Media._ID + " = " + tv.getId(),
						null,
						null
					);
					
					final String mimeFull;
					if (tmime.moveToFirst())
						mimeFull = tmime.getString(0);
					else
						mimeFull = "none";
					tmime.close();
					
					Bitmap bm = null;
					if (tcur.moveToFirst()) {
						int column = tcur.getColumnIndex(MediaStore.Images.Thumbnails.DATA);
						final String uri = tcur.getString(column);
						if (column != -1 && uri != null) {
							tcur.close();
							// TODO ui thread?
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									Uri image_uri = Uri.parse(uri);
									if (image_uri != null)
										tv.setImageURI(image_uri);
									else
										tv.setDrawable(drawableBlack);
									tv.setMime(mimeFull);
								}
							});
							continue;
						}
					}
					
					tcur.close();
					Log.d(TAG, "Creating thumbnail bitmap..");
					bm = MediaStore.Images.Thumbnails.getThumbnail(
						getContentResolver(),
						tv.getId(),
						MediaStore.Images.Thumbnails.MINI_KIND,
						null
					); 
					Log.d(TAG, "Creating thumbnail bitmap done");
					final Bitmap bm_final = bm;
					if (bm == null) {
						Log.d(TAG, "Thumbnail bitmap is null");
					} else {
						// TODO ui thread
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								// TODO Auto-generated method stub
								if (bm_final != null)
									tv.setBitmap(bm_final);
								else
									tv.setDrawable(drawableBlack);
								tv.setMime(mimeFull);
							}
						});
					}
				}
			}
		}
		
		private class ThumbnailView {
			private final static String TAG = "ThumbnailView";
			
			private Integer id;
			private WeakReference<TextView> textView;
			private WeakReference<ImageView> imageView;
			
			public Integer getId() {
				return id;
			}
			
			public void setBitmap(Bitmap bm) {
				if (bm != null)
					imageView.get().setImageBitmap(bm);
			}
			
			public void setImageURI(Uri uri) {
				if (uri != null)
					imageView.get().setImageURI(uri);
			}
			
			public void setDrawable(Drawable drawable) {
				if (drawable != null)
					imageView.get().setImageDrawable(drawable);
			}
			
			public void setMime(String mime) {
				if (mime != null)
					textView.get().setText(mime);
				else
					textView.get().setText("none");
			}
			
			public ThumbnailView(Integer id, View convertView) {
				ImageView iv = (ImageView) convertView.findViewById(R.id.album_item_image);
				TextView tv = (TextView) convertView.findViewById(R.id.album_item_text);
				this.id = id;
				this.imageView = new WeakReference<ImageView>(iv);
				this.textView = new WeakReference<TextView>(tv);
			}
		}
	}
}
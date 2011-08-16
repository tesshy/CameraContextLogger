package jp.ubin.PhotoContextLogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

public class PhotoContextLoggerActivity extends Activity{

	//intent
	private static final boolean D = false;
	private static final int REQUEST_CODE_CAMERA = 0;
	private static final int REQUEST_CODE_GALLERY = 1;
	private static final int IMAGE_SCALE = 3;
	private Uri photoUri = null;//カメラアプリが撮影した写真の在りか
	private ImageView imageView = null; // カメラ画像プレビュー用¥View

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);		

		// UI Compornent Init
		imageView = (ImageView) findViewById(R.id.CameraImageView);
		Log.d("DEBUG","Init Success.");
	}

	public void onClick_CameraButton(View v) {
		// TODO Auto-generated method stub
		String filename = System.currentTimeMillis() + ".jpg";
		ContentValues values = new ContentValues();
		values.put(MediaStore.Images.Media.TITLE, filename);
		values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
		photoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

		Intent intent = new Intent();
		intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
		startActivityForResult(intent, REQUEST_CODE_CAMERA);

		Log.d("DEBUG","Camera Init Done.");
	}

	/**
	 * カメラorギャラリーアプリからの応答取得。
	 */
	@Override 
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		/*
		 *  upload.setImageURI(photoUri)内のbitmap出コード処理でout of memoryになることが割とある。。。
		 */
		try{
			if (resultCode == RESULT_OK) {
				switch(requestCode){
				case REQUEST_CODE_CAMERA:
					Log.d("tempURL",photoUri.toString());			
					imageView.setImageBitmap(uri2bmp(this, photoUri, (int)(280 * IMAGE_SCALE), (int)(280 * IMAGE_SCALE)));
					ExifInterface ei = new ExifInterface(photoUri.toString());
					Log.d("EXIF",ei.getAttribute(ExifInterface.TAG_DATETIME));
					Log.d("EXIF",ei.getAttribute(ExifInterface.TAG_FLASH));
					Log.d("EXIF",ei.getAttribute(ExifInterface.TAG_GPS_LATITUDE));
					Log.d("EXIF",ei.getAttribute(ExifInterface.TAG_GPS_LONGITUDE));
					Log.d("EXIF",ei.getAttribute(ExifInterface.TAG_IMAGE_LENGTH));
					Log.d("EXIF",ei.getAttribute(ExifInterface.TAG_IMAGE_WIDTH));
					break;
				default:
					break;
			}
		}
	} catch (OutOfMemoryError e) {
		//Toast.makeText(this, getString(R.string.notify_out_of_memory), Toast.LENGTH_SHORT).show();
		System.gc();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
}

//ファイル→ビットマップ(最大サイズ指定)
public static Bitmap uri2bmp(Context context,Uri uri,int maxW,int maxH) {
	BitmapFactory.Options options;
	InputStream in=null;
	try {
		//画像サイズの取得
		options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		in = context.getContentResolver().openInputStream(uri);  
		BitmapFactory.decodeStream(in,null,options);
		in.close();
		Log.d("OriginalImageSize", String.valueOf(options.outWidth) + "x" + String.valueOf(options.outHeight));
		int scaleW = options.outWidth / maxW;
		int scaleH = options.outHeight / maxH;
		int scale = Math.max(scaleW,scaleH);

		//画像の読み込み
		options = new BitmapFactory.Options();
		options.inJustDecodeBounds = false;
		options.inSampleSize = scale;
		options.inPurgeable = true;
		in = context.getContentResolver().openInputStream(uri);  
		Bitmap bmp = BitmapFactory.decodeStream(in, null, options);
		in.close();
		return bmp;
	} catch (Exception e) {
		try {
			if (in != null) in.close();
		} catch (Exception e2) {
		}
		return null;
	}
}

/*
 * 添付写真を一時ファイル化する（マルチパートデータ用）。
 * photoUri:写真データの在処
 * tempFname:写真データをローカル一時ファイルにした時のファイル名
 */
private File makeTempPhotoFile(Uri photoUri, String tempFname) {
	if ((tempFname == null) || (photoUri == null)) {
		return null;
	}

	// コピー元、コピー先のストリームを生成
	InputStream in;
	OutputStream out;
	try {
		in = getContentResolver().openInputStream(photoUri);
	} catch (FileNotFoundException e) {
		return null;
	}
	try {
		out = openFileOutput(tempFname, Context.MODE_PRIVATE);
	} catch (FileNotFoundException e) {
		try {
			in.close();
		} catch (IOException e1) {
		}
		return null;
	}

	try {
		// コピー元からコピー先へコピー
		int size;
		byte[] data = new byte[1024];
		for(;;) {
			size = in.read(data);
			if (size <= 0) break;
			out.write(data, 0, size);
		}
	} catch (IOException e) {
		try {
			in.close();
		} catch (IOException e1) {
		}
		try {
			out.close();
		} catch (IOException e1) {
		}
		return null;
	}

	try {
		in.close();
	} catch (IOException e) {
	}
	try {
		out.close();
	} catch (IOException e) {
	}

	// コピー先のフルパスを返す
	String path = getFilesDir().getAbsolutePath() + "/" + tempFname;
	if(D) Log.i("PhotoGallery","path="+path);
	return new File(path);
}
}
package com.uctec.ucdroid.ucapp.ginza.gallery;

import java.io.File;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.uctec.ucdroid.ucapp.ginza.gallery.kokosil.AbstClient;
import com.uctec.ucdroid.ucapp.ginza.gallery.kokosil.Callback;
import com.uctec.ucdroid.ucapp.ginza.gallery.kokosil.KokosilResponse;
//import com.uctec.ucdroid.ucapp.ginza.gallery.kokosil.impl.UploadClient;
import com.uctec.ucdroid.util.UCODE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
//import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * 写真投稿用のアクティビティ。
 */
public class Uploader extends Activity {
	private static final boolean D = false;

	private static final int PREVIEW_SIZE = 400;
	//private static final int PHOTO_WIDTH = 640;
	//private static final int PHOTO_HEIGHT = 480;
	//private static final int PHOTO_QUALITY = 100;
	private static final int PHOTO_ALERT_SIZE = 1280;

    public static final String EXTRA_KEY_PLACE_UCODE = "place_ucode";

    //intent
	private static final int REQUEST_CODE_CAMERA = 0;
	private static final int REQUEST_CODE_GALLERY = 1;
	//menu
	private static final int MENU_ID_ABOUT = 0;

	private ImageView upload;//写真を出す背景
	private Button btnCamera;
	private Button btnGallery;
	private ImageButton btnUpload;

	private Uri photoUri = null;//カメラアプリが撮影した写真の在りか
	private MiscSetup miscSetup = MiscSetup.getInstance();
//	private TimeLineManager timeLineMgr = TimeLineManager.getInstance();

	private String[] usrInfo = null;
	private UCODE currentUcode = null;
	private String uploadTitle = null;

	private ProgressDialog progress;
	private Bitmap thumbnail = null;
	private int rotate = 0;
	private int width = 0;
	private int height = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(D) Log.i("PhotoGallery", "Uploader onCreate()");

		//ユーザ情報取得
//		usrInfo = miscSetup.getKokosilAccountInfo();
		//現在の場所ucode取得
        currentUcode = getIntent().getParcelableExtra(EXTRA_KEY_PLACE_UCODE);

		//ログイン確認
//		if(usrInfo == null){
//			Toast.makeText(Uploader.this, getString(R.string.notify_login), Toast.LENGTH_LONG).show();
//			finish();
//		}
		setContentView(R.layout.upload);

		upload = (ImageView) findViewById(R.id.Upload);
		upload.setImageResource(R.drawable.bg_photo_area);
		upload.setOnClickListener(new OnClickListener() {
			/**
			 * カメラ反応部分に触れた時の処理。
			 */
			public void onClick(View v) {
				if(thumbnail != null) {
					rotate = (rotate + 90) % 360;
					Matrix matrix = new Matrix();
					matrix.postRotate(90f);
					thumbnail = Bitmap.createBitmap(thumbnail, 0, 0,
						thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);
					upload.setImageBitmap(thumbnail);
				}
			}
		});

		btnCamera = (Button)findViewById(R.id.button_camera);
		if (miscSetup.getModelName().equals("SC-02B")) {
		    // GALAXY S（SC-02B）では、カメラで撮るボタンを非表示
		    btnCamera.setVisibility(View.INVISIBLE);
		} else {
		    btnCamera.setOnClickListener(new View.OnClickListener() {
		        /**
		         * 「カメラ」ボタンが押された時の処理。
		         */
		        @Override
		        public void onClick(View v) {
		            if(D) Log.i("PhotoGallery", "camera clicked.");
		            Intent getPhoto = new Intent();
		            getPhoto.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
		            startActivityForResult(getPhoto, REQUEST_CODE_CAMERA);
		        }
		    });
		}

		btnGallery = (Button)findViewById(R.id.button_gallery);
		btnGallery.setOnClickListener(new View.OnClickListener() {
			/**
			 * 「ギャラリー」ボタンが押された時の処理。
			 */
			@Override
			public void onClick(View v) {
				if(D) Log.i("PhotoGallery", "gallery clicked.");
				Intent getPhoto = new Intent(Intent.ACTION_GET_CONTENT);
				getPhoto.setType("image/*");
				startActivityForResult(Intent.createChooser(getPhoto,getString(R.string.app_name_gallery)), REQUEST_CODE_GALLERY);
			}
		});

		btnUpload = (ImageButton) findViewById(R.id.ButtonUpload);
		btnUpload.setOnClickListener(new OnClickListener() {//これでclick有効になる

			/**
			 * 投稿ボタンが押された時の処理。
			 */
			public void onClick(View v) {
				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(Uploader.this);
				alertDialogBuilder.setTitle(getString(R.string.title_upload));
				if((width > PHOTO_ALERT_SIZE) || (height > PHOTO_ALERT_SIZE)) {
					alertDialogBuilder.setMessage(getString(R.string.confirm_upload_alert));
				} else {
					alertDialogBuilder.setMessage(getString(R.string.confirm_upload));
				}

				/**
				 * OKボタンが押された時の処理。
				 */
				alertDialogBuilder.setPositiveButton(R.string.ok,
						new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//ユーザが取った写真ファイルを取得
						File photoFile;
						if((photoFile = makeTempPhotoFile(photoUri, "tmp_photo")) == null){
							Toast.makeText(Uploader.this, getString(R.string.notify_take_photo), Toast.LENGTH_SHORT).show();
							return;
						}

						//ユーザが入力したタイトルとコメント取得
						EditText et1 = (EditText)findViewById(R.id.EditText01);
						EditText et2 = (EditText)findViewById(R.id.EditText02);
						String title = et1.getText().toString();
						String comment = et2.getText().toString();

						if(title.equals("")){
							Toast.makeText(Uploader.this, getString(R.string.notify_input_title), Toast.LENGTH_SHORT).show();
							return;
						}
						if(comment.equals("")){
							Toast.makeText(Uploader.this, getString(R.string.notify_input_comment), Toast.LENGTH_SHORT).show();
							return;
						}

						//upload photo
						String api = getString(miscSetup.isDebuggable() ? R.string.test_kokosil_api_gallery_post : R.string.kokosil_api_gallery_post);
//						AbstClient uploadClient = new UploadClient(api, currentUcode, title, comment, photoFile, usrInfo[0], usrInfo[1], rotate);
						uploadTitle = title;
//						uploadClient.asyncExec(callbackForUpload);
			            finish();
					}
				});

				/**
				 * Cancelボタンが押された時の処理。
				 */
				alertDialogBuilder.setNegativeButton(R.string.cancel,
						new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
					}
				});
				alertDialogBuilder.setCancelable(true);
				AlertDialog alertDialog = alertDialogBuilder.create();
				alertDialog.show();
			}
		});
		btnUpload.setClickable(false);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
        if (progress != null) {
            progress.dismiss();
            progress = null;
        }
		if(D) Log.w("PhotoGallery", "Uploader onDestroy()");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.FIRST, MENU_ID_ABOUT, 0,
        		R.string.menu_about).setIcon(android.R.drawable.ic_menu_info_details);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()){
		case MENU_ID_ABOUT:
            startActivity(new Intent(this, About.class));
            return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * 通信の開始前後にコールバックされるインターフェースの実装部。(写真投票用)
	 */
	private final Callback callbackForUpload = new Callback() {

		@Override
		public void onPreExecute() {
			progress = new ProgressDialog(Uploader.this);
			progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progress.setMessage(getString(R.string.progress_upload));
			progress.setCancelable(true);
			progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					//cancel
				}
			});
			progress.show();
		}

		@Override
		public void onPostExecute(KokosilResponse response) {
		    if (progress != null) {
		        progress.dismiss();
		        progress = null;
		    }
			if(response == null){
				Toast.makeText(Uploader.this, getString(R.string.notify_upload_false) , Toast.LENGTH_SHORT).show();
				setResult(Activity.RESULT_CANCELED);
			}
			else{
				Toast.makeText(Uploader.this, getString(R.string.notify_upload_success)
						//+ " " + getString(R.string.notify_upload_response) + ":" + JsonParser.parseUploadResponse(response.resultData)
						, Toast.LENGTH_SHORT).show();
				String msg = String.format(getString(R.string.timeline_upload), uploadTitle);
//				timeLineMgr.addMessage(msg, response.resultData);
				setResult(Activity.RESULT_OK);
			}

			garbageCollection();
			finish();
		}
	};

	/**
	 * カメラorギャラリーアプリからの応答取得。
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		/*
		 *  upload.setImageURI(photoUri)内のbitmap出コード処理でout of memoryになることが割とある。。。
		 */
		try{
			switch (requestCode) {
			case REQUEST_CODE_CAMERA:
				if(D) Log.i("PhotoGallery", "onActivityResult from camera");
				if(intent != null) {
					photoUri = intent.getData();// カメラからの応答（選択された添付写真）
					if(photoUri != null) {
						//メモリ解放
						garbageCollection();
						//upload.setImageURI(photoUri);
						setResizedBitmap(photoUri);
						enableUploadBtn();
					}
				}
				break;

			case REQUEST_CODE_GALLERY:
				if(D) Log.i("PhotoGallery", "onActivityResult from galley");
				if(intent != null) {
					photoUri = intent.getData();// ギャラリーからの応答（選択された添付写真）
					if(photoUri != null) {
						//メモリ解放
						garbageCollection();
						//upload.setImageURI(photoUri);
						setResizedBitmap(photoUri);
						enableUploadBtn();
					}
				}
				break;

			default:
				break;
			}
		} catch (OutOfMemoryError e) {
			Toast.makeText(this, getString(R.string.notify_out_of_memory), Toast.LENGTH_SHORT).show();
			garbageCollection();
		}
	}

	/*
	 * (BitmapFactory.decodeStreamとかで調べるとネットに載ってる)
	 */
	private void setResizedBitmap(Uri uri){
		if(uri == null)
			return;
		try {
			InputStream is = getContentResolver().openInputStream(uri);
			BitmapFactory.Options option = new BitmapFactory.Options();
            option.inJustDecodeBounds=true;
            BitmapFactory.decodeStream(is, null, option);
            is.close();

            width = option.outWidth;
            height = option.outHeight;

            int inSampleSize = 1;
            int w = width;
            int h = height;
            while((w > PREVIEW_SIZE * 2) && (h > PREVIEW_SIZE * 2)) {
            	w = w / 2;
            	h = h / 2;
            	inSampleSize++;
            }

            is = getContentResolver().openInputStream(uri);
			option = new BitmapFactory.Options();
			option.inPurgeable = true;
			option.inSampleSize = inSampleSize;
			thumbnail = BitmapFactory.decodeStream(is, null, option);
			upload.setImageBitmap(thumbnail);
			is.close();
			rotate = 0;

			Toast.makeText(this, R.string.upload_rotation, Toast.LENGTH_LONG).show();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
	}

	private void enableUploadBtn(){
		//アップロードボタンを有効にする
		btnUpload.setImageResource(R.drawable.btn_upload);
		btnUpload.setClickable(true);
	}

	/*
	 * メモリ不足の暫定対処
	 */
	private void garbageCollection() {
		upload.setImageBitmap(null);
		System.gc();
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

/*		int inSampleSize = 1;
		int w = width;
		int h = height;
		if(width > height) {
			while((w > PHOTO_WIDTH * 2) && (h > PHOTO_HEIGHT * 2)) {
				w = w / 2;
				h = h / 2;
				inSampleSize++;
            }
		} else {
			while((w > PHOTO_HEIGHT * 2) && (h > PHOTO_WIDTH * 2)) {
				w = w / 2;
				h = h / 2;
				inSampleSize++;
			}
		}

		BitmapFactory.Options option = new BitmapFactory.Options();
		option.inPurgeable = true;
		option.inSampleSize = inSampleSize;
		Bitmap bitmap =  BitmapFactory.decodeStream(in, null, option);
		if(bitmap == null) {
			try {
				in.close();
			} catch (IOException e) {
			}
			try {
				out.close();
			} catch (IOException e) {
			}
			return null;
		}

		if(! bitmap.compress(CompressFormat.JPEG, PHOTO_QUALITY, out)) {
			try {
				in.close();
			} catch (IOException e) {
			}
			try {
				out.close();
			} catch (IOException e) {
			}
			return null;
		}*/

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

package com.ecommatics.utterfy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class UtterFy extends Activity {

	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	private static final int SEARCH_URL_REQUEST_CODE = 200;
	private Uri mFileUri;
	private String mSearchUrl;
	private String TAG = "UtterFy";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );

		if( savedInstanceState != null ) {
			mFileUri = savedInstanceState.getParcelable( "mFileUri" );
			mSearchUrl = savedInstanceState.getString( "mSearchUrl" );
			setUrl();
		}

        if( mFileUri != null ) {
			setImage();
		} else {
			captureImage();
		}
    }

    private void captureImage() {
		// create Intent to take a picture and return control to the calling application
		Intent intent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
		mFileUri = getOutputMediaFileUri(); // create a file to save the image
		intent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri); // set the image file name
		// start the image capture Intent
		startActivityForResult( intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE );
	}

    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		//Log.i( TAG, "onActivityResult( "+String.valueOf( requestCode )+", "+String.valueOf( resultCode )+", "+String.valueOf( data )+" )" );
		if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				// Image captured and saved to mFileUri specified in the Intent
				setImage();
			} else if (resultCode == RESULT_CANCELED) {
				// User cancelled the image capture
				Toast.makeText( this, "No photo captured!", Toast.LENGTH_LONG ).show();
			} else {
				// Image capture failed, advise user
				Toast.makeText( this, "No photo captured!", Toast.LENGTH_LONG ).show();
			}
		}
		if( requestCode == SEARCH_URL_REQUEST_CODE ) {
			if( resultCode == RESULT_OK ) {
				mSearchUrl = data.getStringExtra( "grabUrl" );
				setUrl();
				TextView tv = (TextView)findViewById( R.id.search_text );
				tv.setTransformationMethod( null );
				tv.setInputType( InputType.TYPE_NULL );
				findViewById( R.id.search_button ).setVisibility( View.GONE );
			} else {
				Toast.makeText( this, "Grabbing url failed.", Toast.LENGTH_LONG ).show();
			}
		}
	}

	@Override
	protected void onSaveInstanceState( Bundle outState ) {
		super.onSaveInstanceState( outState );
		outState.putParcelable( "mFileUri", mFileUri );
		outState.putString( "mSearchUrl", mSearchUrl );
	}

	private void setImage() {
		ImageView iview = (ImageView)findViewById( R.id.preview );
		//Log.i( TAG, String.valueOf( mFileUri ) );
		if( mFileUri != null )
			iview.setImageURI( mFileUri );
	}

	private void setUrl() {
		TextView tv = (TextView)findViewById( R.id.search_text );
		if( mSearchUrl != null )
			tv.setText( mSearchUrl );
	}

    /** Create a file Uri for saving an image */
	private Uri getOutputMediaFileUri(){
		return Uri.fromFile( getOutputMediaFile() );
	}

	/** Create a File for saving an image or video */
	private File getOutputMediaFile(){
		File file = new File( getExternalFilesDir(null), "DemoFile.jpg" );
		return file;
	}

	public void searchUrl( View view ) {
		TextView tv = (TextView)findViewById( R.id.search_text );
		mSearchUrl = tv.getText().toString();
		mSearchUrl = "https://www.google.bg/search?q="+Uri.encode( mSearchUrl, null );
		// start webview activity with this url
		Intent intent = new Intent();
		intent.setClass( this, SearchUrl.class );
		intent.putExtra( "searchUrl", mSearchUrl );
		startActivityForResult( intent, SEARCH_URL_REQUEST_CODE );
	}

	/** Restart activity to capture another photo */
	public void captureAgain( View view ) {
		mFileUri = null;
		mSearchUrl = null;
		Intent intent = getIntent();
		finish();
		startActivity(intent);
	}
}

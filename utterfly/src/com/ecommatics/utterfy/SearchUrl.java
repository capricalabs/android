package com.ecommatics.utterfy;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

public class SearchUrl extends Activity {

	private WebView mWebView;

	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.webview );

		Intent intent = getIntent();
		String searchUrl = intent.getStringExtra( "searchUrl" );
		setupWebView( searchUrl );

		TextView tv = (TextView)findViewById( R.id.search_url );
		tv.setTransformationMethod( null );
		tv.setInputType( InputType.TYPE_NULL );
    }

    public void setupWebView( String url ) {
		mWebView = (WebView)findViewById( R.id.websearch );
		// enable javascript
		mWebView.getSettings().setJavaScriptEnabled( true );
		// progress bar
		final SearchUrl activity = this;
		mWebView.setWebChromeClient( new WebChromeClient() {
			public void onProgressChanged( WebView view, int progress ) {
				// Activities and WebViews measure progress with different scales.
				// The progress meter will automatically disappear when we reach 100%
				activity.setProgress(progress * 1000);
			}
		});
		// enable navigation and error handling
		mWebView.setWebViewClient( new WebViewClient() {
			public void onReceivedError( WebView view, int errorCode, String description, String failingUrl ) {
				Toast.makeText(activity, "Error: " + description, Toast.LENGTH_SHORT).show();
			}
			public void onPageStarted( WebView view, String url, Bitmap favicon ) {
				// just save the url webview is going to display
				activity.setUrl( url );
			}
		});
		// load search results
		mWebView.loadUrl( url );
	}
   
	public void grabUrl( View view ) {
		Intent intent = new Intent();
		intent.putExtra( "grabUrl", getUrl() );
		setResult( RESULT_OK, intent );
		finish();
    }

    private String getUrl() {
		if( mWebView != null )
			return mWebView.getUrl();
		else
			return null;
	}

	public void setUrl( String url ) {
		TextView tv = (TextView)findViewById( R.id.search_url );
		tv.setText( url );
	}

	@Override
	public boolean onKeyDown( int keyCode, KeyEvent event ) {
		// Check if the key event was the Back button and if there's history
		if( ( keyCode == KeyEvent.KEYCODE_BACK ) && mWebView != null && mWebView.canGoBack() ) {
			mWebView.goBack();
			return true;
		}
		// If it wasn't the Back key or there's no web page history, bubble up to the default
		// system behavior (probably exit the activity)
		return super.onKeyDown( keyCode, event );
	}
}

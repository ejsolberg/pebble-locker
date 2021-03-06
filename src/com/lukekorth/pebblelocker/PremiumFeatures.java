package com.lukekorth.pebblelocker;

import com.lukekorth.pebblelocker.util.IabHelper;
import com.lukekorth.pebblelocker.util.IabResult;
import com.lukekorth.pebblelocker.util.Purchase;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class PremiumFeatures extends PreferenceActivity {

	private IabHelper mHelper;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	public void requirePremiumPurchase() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("These options require the premium version of the app, press ok to purchase");
		builder.setCancelable(false);
		builder.setPositiveButton("Ok", new OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				cleanupHelper();

				mHelper = new IabHelper(PremiumFeatures.this, getString(R.string.billing_public_key));
				mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
					public void onIabSetupFinished(IabResult result) {
						launchPurchase();
					}
				});
			}
		});
		builder.setNegativeButton("Cancel", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				PremiumFeatures.this.finish();
			}
		});
		builder.show();
	}

	private void launchPurchase() {
		mHelper.launchPurchaseFlow(PremiumFeatures.this, "pebblelocker.premium", 1, mPurchaseFinishedListener, "premium");
	}

	IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
			if (result.isFailure()) {
				AlertDialog.Builder builder = new AlertDialog.Builder(PremiumFeatures.this);
				builder.setMessage("There was an error purchasing, please try again later");
				builder.setCancelable(false);
				builder.setPositiveButton("Ok", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						PremiumFeatures.this.finish();
					}
				});
				builder.show();

				return;
			}

			if (purchase.getSku().equals("pebblelocker.premium"))
				PreferenceManager.getDefaultSharedPreferences(PremiumFeatures.this).edit().putBoolean("donated", true).commit();
		}
	};

	private void cleanupHelper() {
		if (mHelper != null) {
			mHelper.dispose();
			mHelper = null;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		cleanupHelper();
	}
}
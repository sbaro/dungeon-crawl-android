/*
 * File: GameActivity.java Purpose: Generic ui functions in Android application
 * 
 * Copyright (c) 2010 David Barr, Sergey Belinsky
 * 
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of either:
 * 
 * a) the GNU General Public License as published by the Free Software
 * Foundation, version 2, or
 * 
 * b) the "Angband licence": This software may be copied and distributed for
 * educational, research, and not for profit purposes provided that this
 * copyright and statement are included in all such copies. Other copyrights may
 * also apply.
 */

package com.crawlmb.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.os.Handler;
import android.os.Message;

import com.crawlmb.CrawlDialog;
import com.crawlmb.keylistener.GameKeyListener;
import com.crawlmb.keyboard.CrawlKeyboardWrapper;
import com.crawlmb.keyboard.DirectionalTouchView;
import com.crawlmb.GameThread;
import com.crawlmb.Preferences;
import com.crawlmb.R;
import com.crawlmb.view.TermView;

public class GameActivity extends Activity
{

	static final int PREFERENCES_FINISHED = 1;
	
	public static GameKeyListener gameKeyListener = null;
	private CrawlDialog dialog = null;

	private RelativeLayout screenLayout = null;
	private TermView term = null;

	protected Handler handler = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Log.d("Crawl", "onCreate");

		if (gameKeyListener == null) {
			gameKeyListener = new GameKeyListener();
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		if (dialog == null)
			dialog = new CrawlDialog(this, gameKeyListener);
		final CrawlDialog crawlDialog = dialog;
		handler = new GameHandler(crawlDialog);

		rebuildViews();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = new MenuInflater(getApplication());
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		MenuItem menuItem = menu.findItem(R.id.menu_lock_terminal_position);
		if (term.getLockPositioning()) {
			menuItem.setTitle(R.string.menu_unlock_terminal_position);
		} else {
			menuItem.setTitle(R.string.menu_lock_terminal_position);
		}

		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		// TODO: Add help and quit menu options
		Intent intent;
		switch (item.getNumericShortcut()) {
		case '1':// Help
			intent = new Intent(this, HelpActivity.class);
			startActivity(intent);
			break;
		case '2':// Preferences
			intent = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent, PREFERENCES_FINISHED);
			break;
		case '3':// Reset terminal position
			term.resetTerminalPosition();
			break;
		case '4':// Lock terminal position
			term.toggleLockPosition();
			break;
		case '5':// Quit
			finish();
			break;
		case '6':// Show/Hide Keyboard
			toggleKeyboard();
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        if (requestCode == PREFERENCES_FINISHED) {
            if (resultCode == RESULT_OK) {
            	if(data.getBooleanExtra("reloadCrawl", false)) {
            		// Because of a change in preferences, crawl must be reloaded
            		finish();
            		startActivity(getIntent());
            	}
            }
        }
    }

	@Override
	public void finish() {
		// Log.d("Crawl","finish");
		gameKeyListener.gameThread.send(GameThread.Request.StopGame);
		super.finish();
	}

	private void rebuildViews() {
		synchronized (GameKeyListener.progress_lock) {
			// Log.d("Crawl","rebuildViews");

			int orient = Preferences.getOrientation();
			switch (orient) {
			case 0: // sensor
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				break;
			case 1: // portrait
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			case 2: // landscape
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			}

			if (screenLayout != null)
				screenLayout.removeAllViews();
			screenLayout = new RelativeLayout(this);

			term = new TermView(this, gameKeyListener);
			RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
					LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			term.setLayoutParams(layoutParams);
			term.setFocusable(true);
			registerForContextMenu(term);

			boolean hapticFeedbackEnabled = Preferences
					.getHapticFeedbackEnabled();
			term.setHapticFeedbackEnabled(hapticFeedbackEnabled);
			gameKeyListener.link(term, handler);

			screenLayout.addView(term);

			String keyboardType;
			if (Preferences.isScreenPortraitOrientation())
				keyboardType = Preferences.getPortraitKeyboard();
			else
				keyboardType = Preferences.getLandscapeKeyboard();

			String[] keyboards = getResources().getStringArray(
					R.array.virtualKeyboardValues);

			if (keyboardType.equals(keyboards[1])) // Crawl Keyboard
			{
				CrawlKeyboardWrapper virtualKeyboard = new CrawlKeyboardWrapper(this, gameKeyListener);
				virtualKeyboard.virtualKeyboardView
						.setHapticFeedbackEnabled(hapticFeedbackEnabled);
				screenLayout.addView(virtualKeyboard.virtualKeyboardView);

				// Add directional-key view
				addDirectionalKeyView(
						virtualKeyboard.virtualKeyboardView.getId(),
						hapticFeedbackEnabled);

				getWindow()
						.setSoftInputMode(
								WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
			} else if (keyboardType.equals(keyboards[2])) // System Keyboard
			{
				InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				getWindow()
						.setSoftInputMode(
								WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				inputMethodManager.toggleSoftInput(
						InputMethodManager.SHOW_FORCED, 0);
			} else {
				getWindow()
						.setSoftInputMode(
								WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
			}

			setContentView(screenLayout);
			dialog.restoreDialog();

			term.invalidate();
		}
	}

	private void addDirectionalKeyView(int virtualKeyboardId,
			boolean hapticFeedbackEnabled) {
		DirectionalTouchView view = new DirectionalTouchView(this, gameKeyListener);
		RelativeLayout.LayoutParams directionalLayoutParams = new RelativeLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		directionalLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		directionalLayoutParams
				.addRule(RelativeLayout.ABOVE, virtualKeyboardId);
		view.setLayoutParams(directionalLayoutParams);
		view.setPassThroughListener(term);
		view.setHapticFeedbackEnabled(hapticFeedbackEnabled);
		screenLayout.addView(view);
	}

	public void toggleKeyboard() {
		int currentKeyboard;
		if (Preferences.isScreenPortraitOrientation()) {
			currentKeyboard = Integer.parseInt(Preferences
					.getPortraitKeyboard());
			if (currentKeyboard == 2) // System keyboard
			{
				toggleSystemKeyboard();
				return;
			}
			currentKeyboard = currentKeyboard == 0 ? 1 : 0;
			Preferences.setPortraitKeyboard(String.valueOf(currentKeyboard));
		} else {
			currentKeyboard = Integer.parseInt(Preferences
					.getLandscapeKeyboard());
			if (currentKeyboard == 2) // System keyboard
			{
				toggleSystemKeyboard();
				return;
			}
			currentKeyboard = currentKeyboard == 0 ? 1 : 0;
			Preferences.setLandscapeKeyboard(String.valueOf(currentKeyboard));
		}

		rebuildViews();
	}

	private void toggleSystemKeyboard() {
		InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
	}

	@Override
	protected void onResume() {
		// Log.d("Crawl", "onResume");
		super.onResume();

		setScreen();

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return gameKeyListener.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return gameKeyListener.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
	}

	public void setScreen() {
		if (Preferences.getFullScreen()) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	public Handler getHandler() {
		return handler;
	}

	private static class GameHandler extends Handler {
		private final CrawlDialog crawlDialog;

		public GameHandler(CrawlDialog crawlDialog) {
			this.crawlDialog = crawlDialog;
		}

		@Override
        public void handleMessage(Message msg) {
            crawlDialog.HandleMessage(msg);
        }
	}
}
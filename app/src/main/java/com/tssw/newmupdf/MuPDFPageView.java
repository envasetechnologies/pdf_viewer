package com.tssw.newmupdf;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.fragment.app.FragmentManager;

import com.tssw.newmupdf.MuPDFCore.Cookie;
import com.tssw.newmupdf.R;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/* This enum should be kept in line with the corresponding C enum in mupdf.c */
enum SignatureState {
	NoSupport,
	Unsigned,
	Signed
}

abstract class PassClickResultVisitor {
	public abstract void visitText(PassClickResultText result);
	public abstract void visitChoice(PassClickResultChoice result);
	public abstract void visitSignature(PassClickResultSignature result);
}

class PassClickResult {
	public final boolean changed;

	public PassClickResult(boolean _changed) {
		changed = _changed;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
	}
}

class PassClickResultText extends PassClickResult {
	public final String text;

	public PassClickResultText(boolean _changed, String _text) {
		super(_changed);
		text = _text;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitText(this);
	}
}

class PassClickResultChoice extends PassClickResult {
	public final String [] options;
	public final String [] selected;

	public PassClickResultChoice(boolean _changed, String [] _options, String [] _selected) {
		super(_changed);
		options = _options;
		selected = _selected;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitChoice(this);
	}
}

class PassClickResultSignature extends PassClickResult {
	public final SignatureState state;

	public PassClickResultSignature(boolean _changed, int _state) {
		super(_changed);
		state = SignatureState.values()[_state];
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitSignature(this);
	}
}

public class MuPDFPageView extends PageView implements MuPDFView, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {
	private static final String TAG = "MYTAG";
	final private FilePicker.FilePickerSupport mFilePickerSupport;
	private final MuPDFCore mCore;
	private AsyncTask<Void,Void,PassClickResult> mPassClick;
	private RectF mWidgetAreas[];
	private Annotation mAnnotations[];
	private int mSelectedAnnotationIndex = -1;
	private AsyncTask<Void,Void,RectF[]> mLoadWidgetAreas;
	private AsyncTask<Void,Void, Annotation[]> mLoadAnnotations;
	private AlertDialog.Builder mTextEntryBuilder;
	private AlertDialog.Builder mChoiceEntryBuilder;
	private AlertDialog.Builder mSigningDialogBuilder;
	private AlertDialog.Builder mSignatureReportBuilder;
	private AlertDialog.Builder mPasswordEntryBuilder;
	private EditText mPasswordText;
	private AlertDialog mTextEntry;
	private AlertDialog mPasswordEntry;
	private EditText mEditText;
	private AsyncTask<String,Void,Boolean> mSetWidgetText;
	private AsyncTask<String,Void,Void> mSetWidgetChoice;
	private AsyncTask<PointF[],Void,Void> mAddStrikeOut;
	private AsyncTask<PointF[][],Void,Void> mAddInk;
	private AsyncTask<Integer,Void,Void> mDeleteAnnotation;
	private AsyncTask<Void,Void,String> mCheckSignature;
	private AsyncTask<Void,Void,Boolean> mSign;
	private Runnable changeReporter;

	private DatePickerDialog dpd;
	private TimePickerDialog tpd;

	Context mContext = null;

	private FragmentManager fm;

	public MuPDFPageView(Context c, FilePicker.FilePickerSupport filePickerSupport, MuPDFCore core, Point parentSize, Bitmap sharedHqBm) {
		super(c, parentSize, sharedHqBm);
		mFilePickerSupport = filePickerSupport;
		mCore = core;

		mContext = c;

		try{
			fm = ((MuPDFActivity) mContext).getSupportFragmentManager();
		} catch (ClassCastException e) {
			Log.d(TAG, "Can't get fragment manager from context");
		}

		mTextEntryBuilder = new AlertDialog.Builder(c);
		mTextEntryBuilder.setTitle(getContext().getString(R.string.fill_out_text_field));
		LayoutInflater inflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mEditText = (EditText)inflater.inflate(R.layout.textentry, null);
		mTextEntryBuilder.setView(mEditText);
		mTextEntryBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		mTextEntryBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				mSetWidgetText = new AsyncTask<String,Void,Boolean>() {
					@Override
					protected Boolean doInBackground(String... arg0) {
						return mCore.setFocusedWidgetText(mPageNumber, arg0[0]);
					}
					@Override
					protected void onPostExecute(Boolean result) {
						changeReporter.run();
						if (!result)
							invokeTextDialog(mEditText.getText().toString());
					}
				};

				mSetWidgetText.execute(mEditText.getText().toString());
			}
		});
		mTextEntry = mTextEntryBuilder.create();

		mChoiceEntryBuilder = new AlertDialog.Builder(c);
		mChoiceEntryBuilder.setTitle(getContext().getString(R.string.choose_value));

		mSigningDialogBuilder = new AlertDialog.Builder(c);
		mSigningDialogBuilder.setTitle("Select certificate and sign?");
		mSigningDialogBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		mSigningDialogBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				FilePicker picker = new FilePicker(mFilePickerSupport) {
					@Override
					void onPick(Uri uri) {
						signWithKeyFile(uri);
					}
				};

				picker.pick();
			}
		});

		mSignatureReportBuilder = new AlertDialog.Builder(c);
		mSignatureReportBuilder.setTitle("Signature checked");
		mSignatureReportBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});

		mPasswordText = new EditText(c);
		mPasswordText.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
		mPasswordText.setTransformationMethod(new PasswordTransformationMethod());

		mPasswordEntryBuilder = new AlertDialog.Builder(c);
		mPasswordEntryBuilder.setTitle(R.string.enter_password);
		mPasswordEntryBuilder.setView(mPasswordText);
		mPasswordEntryBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});

		mPasswordEntry = mPasswordEntryBuilder.create();
	}

	private void signWithKeyFile(final Uri uri) {
		mPasswordEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		mPasswordEntry.setButton(AlertDialog.BUTTON_POSITIVE, "Sign", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				signWithKeyFileAndPassword(uri, mPasswordText.getText().toString());
			}
		});

		mPasswordEntry.show();
	}

	private void signWithKeyFileAndPassword(final Uri uri, final String password) {
		mSign = new AsyncTask<Void,Void,Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {
				return mCore.signFocusedSignature(Uri.decode(uri.getEncodedPath()), password);
			}
			@Override
			protected void onPostExecute(Boolean result) {
				if (result)
				{
					changeReporter.run();
				}
				else
				{
					mPasswordText.setText("");
					signWithKeyFile(uri);
				}
			}

		};

		mSign.execute();
	}

	public LinkInfo hitLink(float x, float y) {
		// Since link highlighting was implemented, the super class
		// PageView has had sufficient information to be able to
		// perform this method directly. Making that change would
		// make MuPDFCore.hitLinkPage superfluous.
		float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
		float docRelX = (x - getLeft())/scale;
		float docRelY = (y - getTop())/scale;

		for (LinkInfo l: mLinks)
			if (l.rect.contains(docRelX, docRelY))
				return l;

		return null;
	}

	private void initNewDatePicker(Calendar date){

		if (dpd == null) {
			dpd = DatePickerDialog.newInstance(
					MuPDFPageView.this,
					date.get(Calendar.YEAR),
					date.get(Calendar.MONTH),
					date.get(Calendar.DAY_OF_MONTH)
			);
		} else {
			dpd.initialize(
					MuPDFPageView.this,
					date.get(Calendar.YEAR),
					date.get(Calendar.MONTH),
					date.get(Calendar.DAY_OF_MONTH)
			);
		}
		dpd.setThemeDark(false);
		dpd.vibrate(true);
		dpd.setVersion(DatePickerDialog.Version.VERSION_1);

		dpd.setOnCancelListener(dialog -> {
			Log.d("DatePickerDialog", "Dialog was cancelled");
			dpd = null;
		});
	}

	private void initNewTimePicker(Calendar date){
		if (tpd == null) {
			tpd = TimePickerDialog.newInstance(
					MuPDFPageView.this,
					date.get(Calendar.HOUR_OF_DAY),
					date.get(Calendar.MINUTE),
					true
			);
		} else {
			tpd.initialize(
					MuPDFPageView.this,
					date.get(Calendar.HOUR_OF_DAY),
					date.get(Calendar.MINUTE),
					0,
					true
			);
		}
		tpd.setThemeDark(false);
		tpd.vibrate(true);
		tpd.enableSeconds(false);
		tpd.setVersion(TimePickerDialog.Version.VERSION_1);

		tpd.setOnCancelListener(dialog -> {
			Log.d("TimePickerDialog", "Dialog was cancelled");
			tpd = null;
		});
	}
	private void invokeDateTimeDialog(String text, String widgetName) {

		Long longDate;
		Calendar date = Calendar.getInstance();
		SimpleDateFormat userDateTimeFormat=new SimpleDateFormat("M/d/yyyy HH:mm", Locale.US);

		try{
			date.setTime(userDateTimeFormat.parse(text));
		}catch (Exception e){
			Log.e(TAG, "Error while parsing the date!");
		}

		Calendar pickedDateTime = Calendar.getInstance();

		initNewDatePicker(date);
		dpd.setTitle(widgetName);

		Calendar calendar =(Calendar) date.clone();

		calendar.add(Calendar.HOUR, 24);//24 hours from now
		dpd.setMaxDate(calendar);
		calendar.add(Calendar.HOUR, -12-24); //12 hours from now
		dpd.setMinDate(calendar);

		initNewTimePicker(date);
		tpd.setTitle(widgetName);

		tpd.setOnTimeSetListener(( viewtime, hourOfDay, minute, second) -> {
			pickedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
			pickedDateTime.set(Calendar.MINUTE, minute);

			String datetimeAsString = userDateTimeFormat.format(pickedDateTime.getTime());

			mSetWidgetText = new AsyncTask<String,Void,Boolean>() {
				@Override
				protected Boolean doInBackground(String... arg0) {
					return mCore.setFocusedWidgetText(mPageNumber, arg0[0]);
				}
				@Override
				protected void onPostExecute(Boolean result) {
					changeReporter.run();
				}
			};

			mSetWidgetText.execute(datetimeAsString);

			tpd = null;
		});


		dpd.setOnDateSetListener((viewdate, year, monthOfYear, dayOfMonth) -> {

			pickedDateTime.set(Calendar.YEAR, year);
			pickedDateTime.set(Calendar.MONTH, monthOfYear);
			pickedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
			pickedDateTime.set(Calendar.HOUR_OF_DAY, 0);
			pickedDateTime.set(Calendar.MINUTE, 0);
			pickedDateTime.set(Calendar.SECOND, 0);
			pickedDateTime.set(Calendar.MILLISECOND, 0);

			dpd = null;
			tpd.show(((MuPDFActivity) mContext).getSupportFragmentManager() ,"Timepickerdialog");

		});
		dpd.show(((MuPDFActivity) mContext).getSupportFragmentManager() ,"Datepickerdialog");
	}

	private void invokeDateDialog(String text, String widgetName) {

		Long longDate;
		Calendar date = Calendar.getInstance();
		SimpleDateFormat userDateFormat=new SimpleDateFormat("M/d/yyyy", Locale.US);

		try{
			date.setTime(userDateFormat.parse(text));
		}catch (Exception e){
			Log.e(TAG, "Error while parsing the date!");
		}

		initNewDatePicker(date);
		dpd.setTitle(widgetName);

		Calendar calendar = Calendar.getInstance();

		calendar.add(Calendar.HOUR, 24);//24 hours from now
		dpd.setMaxDate(calendar);
		calendar.add(Calendar.HOUR, -12-24); //12 hours from now
		dpd.setMinDate(calendar);

		dpd.setOnDateSetListener((view, year, monthOfYear, dayOfMonth) -> {

			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.YEAR, year);
			cal.set(Calendar.MONTH, monthOfYear);
			cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);

			String dateAsString = userDateFormat.format(cal.getTime());

			mSetWidgetText = new AsyncTask<String,Void,Boolean>() {
				@Override
				protected Boolean doInBackground(String... arg0) {
					return mCore.setFocusedWidgetText(mPageNumber, arg0[0]);
				}
				@Override
				protected void onPostExecute(Boolean result) {
					changeReporter.run();
				}
			};

			mSetWidgetText.execute(dateAsString);
			dpd = null;
		});
		dpd.show(((MuPDFActivity) mContext).getSupportFragmentManager() ,"Datepickerdialog");
	}

	private void invokeTimeDialog(String text, String widgetName) {

		Calendar date = Calendar.getInstance();
		SimpleDateFormat userTimeFormat=new SimpleDateFormat("HH:mm", Locale.US);

		try{
			date.setTime(userTimeFormat.parse(text));
		}catch (Exception e){
			Log.e(TAG, "Error while parsing the time!");
		}

		initNewTimePicker(date);
		tpd.setTitle(widgetName);

		tpd.setOnTimeSetListener((view, hourOfDay, minute, second) -> {

			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.YEAR, cal.get(Calendar.YEAR));
			cal.set(Calendar.MONTH, cal.get(Calendar.MONTH));
			cal.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH));
			cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
			cal.set(Calendar.MINUTE, minute);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);

			String dateAsString = userTimeFormat.format(cal.getTime());

			mSetWidgetText = new AsyncTask<String,Void,Boolean>() {
				@Override
				protected Boolean doInBackground(String... arg0) {
					return mCore.setFocusedWidgetText(mPageNumber, arg0[0]);
				}
				@Override
				protected void onPostExecute(Boolean result) {
					changeReporter.run();
				}
			};

			mSetWidgetText.execute(dateAsString);

			tpd = null;
		});
		tpd.show(((MuPDFActivity) mContext).getSupportFragmentManager() ,"Timepickerdialog");

	}

	private void invokeTextDialog(String text) {
		mEditText.setText(text);
		mTextEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		mTextEntry.show();
	}

	private void invokeTextDialog(String text, String widgetName) {
		mEditText.setText(text);
		mTextEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		if (widgetName.matches("_.*text_.*")){
			widgetName = widgetName.replaceFirst("(_.*text_)","");
		}
		else if (widgetName.matches("(_.*signature.*)")){
			widgetName = "Your Name";
		}
		mTextEntry.setTitle(widgetName);
		mTextEntry.show();
	}

	private void invokeChoiceDialog(final String [] options) {
		mChoiceEntryBuilder.setItems(options, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				mSetWidgetChoice = new AsyncTask<String,Void,Void>() {
					@Override
					protected Void doInBackground(String... params) {
						String [] sel = {params[0]};
						mCore.setFocusedWidgetChoiceSelected(sel);
						return null;
					}

					@Override
					protected void onPostExecute(Void result) {
						changeReporter.run();
					}
				};

				mSetWidgetChoice.execute(options[which]);
			}
		});
		AlertDialog dialog = mChoiceEntryBuilder.create();
		dialog.show();
	}

	private void invokeSignatureCheckingDialog() {
		mCheckSignature = new AsyncTask<Void,Void,String>() {
			@Override
			protected String doInBackground(Void... params) {
				return mCore.checkFocusedSignature();
			}
			@Override
			protected void onPostExecute(String result) {
				AlertDialog report = mSignatureReportBuilder.create();
				report.setMessage(result);
				report.show();
			}
		};

		mCheckSignature.execute();
	}

	private void invokeSigningDialog() {
		AlertDialog dialog = mSigningDialogBuilder.create();
		dialog.show();
	}

	private void warnNoSignatureSupport() {
		AlertDialog dialog = mSignatureReportBuilder.create();
		dialog.setTitle("App built with no signature support");
		dialog.show();
	}

	public void setChangeReporter(Runnable reporter) {
		changeReporter = reporter;
	}


	@SuppressLint("NewApi")
	public Hit passClickEventModded(float x, float y) {
		Log.d(TAG,"(x,y): "+x+", "+y);
		final float docRelX =x;// (x)/scale;
		final float docRelY =y;// (y )/scale;
		boolean hit = false;
		int i;
		Log.d(TAG,"passClickEventModded1: ");

		Log.d(TAG,"passClickEventModded2: ");
		mSelectedAnnotationIndex = -1;
		setItemSelectBox(null);

		if (!mCore.javascriptSupported())
			return Hit.Nothing;
		Log.d(TAG,"passClickEventModded3: ");
		if (mWidgetAreas != null) {
			Log.d(TAG,"mWidgetAreas not null: ");
			Log.d(TAG,"hit:  "+hit);
			for (i = 0; i < mWidgetAreas.length && !hit; i++){
				Log.d(TAG,"mWidgetAreas: "+mWidgetAreas[i].toShortString());
				if (mWidgetAreas[i].contains(docRelX, docRelY))
					hit = true;
			}
		}else{
			Log.d(TAG,"mWidgetAreas is null: ");
		}

		if (hit) {
			mPassClick = new AsyncTask<Void,Void,PassClickResult>() {
				@Override
				protected PassClickResult doInBackground(Void... arg0) {
					return mCore.passClickEvent(mPageNumber, docRelX, docRelY);
				}

				@Override
				protected void onPostExecute(PassClickResult result) {
					if (result.changed) {
						changeReporter.run();
					}

					result.acceptVisitor(new PassClickResultVisitor() {
						@Override
						public void visitText(PassClickResultText result) {
							String widgetName = mCore.getFocusedWidgetNameInternal();
							int widgetType = mCore.getFocusedWidgetContentTypeInternal();

							if (widgetName.matches("(_.*datetime_.*)")){
								widgetName = widgetName.replaceFirst("(_.*datetime_)","");
								invokeDateTimeDialog(result.text, widgetName);
							}
							else if (widgetName.matches("(_.*time_.*)")){
								widgetName = widgetName.replaceFirst("(_.*time_)","");
								invokeTimeDialog(result.text, widgetName);
							} else if (widgetName.matches("(_.*date_.*)")){
								widgetName = widgetName.replaceFirst("(_.*date_)","");
								invokeDateDialog(result.text, widgetName);
							}
							else
								invokeTextDialog(result.text, widgetName);
						}

						@Override
						public void visitChoice(PassClickResultChoice result) {
							invokeChoiceDialog(result.options);
						}

						@Override
						public void visitSignature(PassClickResultSignature result) {
							switch (result.state) {
								case NoSupport:
									warnNoSignatureSupport();
									break;
								case Unsigned:
									invokeSigningDialog();
									break;
								case Signed:
									invokeSignatureCheckingDialog();
									break;
							}
						}
					});
				}
			};

			mPassClick.execute();
			return Hit.Widget;
		}

		return Hit.Nothing;
	}

	public Hit passClickEvent(float x, float y) {
		float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
		final float docRelX = (x - getLeft())/scale;
		final float docRelY = (y - getTop())/scale;
		boolean hit = false;
		int i;

		if (mAnnotations != null) {
			for (i = 0; i < mAnnotations.length; i++)
				if (mAnnotations[i].contains(docRelX, docRelY)) {
					hit = true;
					break;
				}

			if (hit) {
				switch (mAnnotations[i].type) {
				case HIGHLIGHT:
				case UNDERLINE:
				case SQUIGGLY:
				case STRIKEOUT:
				case INK:
					mSelectedAnnotationIndex = i;
					setItemSelectBox(mAnnotations[i]);
					return Hit.Annotation;
				}
			}
		}

		mSelectedAnnotationIndex = -1;
		setItemSelectBox(null);

		if (!mCore.javascriptSupported())
			return Hit.Nothing;

		if (mWidgetAreas != null) {
			for (i = 0; i < mWidgetAreas.length && !hit; i++)
				if (mWidgetAreas[i].contains(docRelX, docRelY))
					hit = true;
		}

		if (hit) {
			mPassClick = new AsyncTask<Void,Void,PassClickResult>() {
				@Override
				protected PassClickResult doInBackground(Void... arg0) {
					return mCore.passClickEvent(mPageNumber, docRelX, docRelY);
				}

				@Override
				protected void onPostExecute(PassClickResult result) {
					if (result.changed) {
						changeReporter.run();
					}

					result.acceptVisitor(new PassClickResultVisitor() {
						@Override
						public void visitText(PassClickResultText result) {

							String widgetName = mCore.getFocusedWidgetNameInternal();
							int widgetType = mCore.getFocusedWidgetContentTypeInternal();

							if (widgetName.matches("(_.*datetime_.*)")){
								widgetName = widgetName.replaceFirst("(_.*datetime_)","");
								invokeDateTimeDialog(result.text, widgetName);
							}
							else if (widgetName.matches("(_.*time_.*)")){
								widgetName = widgetName.replaceFirst("(_.*time_)","");
								invokeTimeDialog(result.text, widgetName);
							}
							else if (widgetName.matches("(_.*date_.*)")){
								widgetName = widgetName.replaceFirst("(_.*date_)","");
								invokeDateDialog(result.text, widgetName);
							}
							else
								invokeTextDialog(result.text, widgetName);
						}

						@Override
						public void visitChoice(PassClickResultChoice result) {
							invokeChoiceDialog(result.options);
						}

						@Override
						public void visitSignature(PassClickResultSignature result) {
							switch (result.state) {
							case NoSupport:
								warnNoSignatureSupport();
								break;
							case Unsigned:
								invokeSigningDialog();
								break;
							case Signed:
								invokeSignatureCheckingDialog();
								break;
							}
						}
					});
				}
			};

			mPassClick.execute();
			return Hit.Widget;
		}

		return Hit.Nothing;
	}

	@TargetApi(11)
	public boolean copySelection() {
		final StringBuilder text = new StringBuilder();

		processSelectedText(new TextProcessor() {
			StringBuilder line;

			public void onStartLine() {
				line = new StringBuilder();
			}

			public void onWord(TextWord word) {
				if (line.length() > 0)
					line.append(' ');
				line.append(word.w);
			}

			public void onEndLine() {
				if (text.length() > 0)
					text.append('\n');
				text.append(line);
			}
		});

		if (text.length() == 0)
			return false;

		int currentApiVersion = Build.VERSION.SDK_INT;
		if (currentApiVersion >= Build.VERSION_CODES.HONEYCOMB) {
			android.content.ClipboardManager cm = (android.content.ClipboardManager)mContext.getSystemService(Context.CLIPBOARD_SERVICE);

			cm.setPrimaryClip(ClipData.newPlainText("MuPDF", text));
		} else {
			android.text.ClipboardManager cm = (android.text.ClipboardManager)mContext.getSystemService(Context.CLIPBOARD_SERVICE);
			cm.setText(text);
		}

		deselectText();

		return true;
	}

	public boolean markupSelection(final Annotation.Type type) {
		final ArrayList<PointF> quadPoints = new ArrayList<PointF>();
		processSelectedText(new TextProcessor() {
			RectF rect;

			public void onStartLine() {
				rect = new RectF();
			}

			public void onWord(TextWord word) {
				rect.union(word);
			}

			public void onEndLine() {
				if (!rect.isEmpty()) {
					quadPoints.add(new PointF(rect.left, rect.bottom));
					quadPoints.add(new PointF(rect.right, rect.bottom));
					quadPoints.add(new PointF(rect.right, rect.top));
					quadPoints.add(new PointF(rect.left, rect.top));
				}
			}
		});

		if (quadPoints.size() == 0)
			return false;

		mAddStrikeOut = new AsyncTask<PointF[],Void,Void>() {
			@Override
			protected Void doInBackground(PointF[]... params) {
				addMarkup(params[0], type);
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				loadAnnotations();
				update();
			}
		};

		mAddStrikeOut.execute(quadPoints.toArray(new PointF[quadPoints.size()]));

		deselectText();

		return true;
	}

	public void deleteSelectedAnnotation() {
		if (mSelectedAnnotationIndex != -1) {
			if (mDeleteAnnotation != null)
				mDeleteAnnotation.cancel(true);

			mDeleteAnnotation = new AsyncTask<Integer,Void,Void>() {
				@Override
				protected Void doInBackground(Integer... params) {
					mCore.deleteAnnotation(mPageNumber, params[0]);
					return null;
				}

				@Override
				protected void onPostExecute(Void result) {
					loadAnnotations();
					update();
				}
			};

			mDeleteAnnotation.execute(mSelectedAnnotationIndex);

			mSelectedAnnotationIndex = -1;
			setItemSelectBox(null);
		}
	}

	public void deselectAnnotation() {
		mSelectedAnnotationIndex = -1;
		setItemSelectBox(null);
	}

	public boolean saveDraw() {
		PointF[][] path = getDraw();

		if (path == null)
			return false;

		if (mAddInk != null) {
			mAddInk.cancel(true);
			mAddInk = null;
		}
		mAddInk = new AsyncTask<PointF[][],Void,Void>() {
			@Override
			protected Void doInBackground(PointF[][]... params) {
				mCore.addInkAnnotation(mPageNumber, params[0]);
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				loadAnnotations();
				update();
			}

		};

		mAddInk.execute(getDraw());
		cancelDraw();

		return true;
	}

	@Override
	protected CancellableTaskDefinition<Void, Void> getDrawPageTask(final Bitmap bm, final int sizeX, final int sizeY,
                                                                    final int patchX, final int patchY, final int patchWidth, final int patchHeight) {
		return new MuPDFCancellableTaskDefinition<Void, Void>(mCore) {
			@Override
			public Void doInBackground(Cookie cookie, Void ... params) {
				// Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
				// is not incremented when drawing.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
						Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					bm.eraseColor(0);
				mCore.drawPage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
				return null;
			}
		};

	}

	protected CancellableTaskDefinition<Void, Void> getUpdatePageTask(final Bitmap bm, final int sizeX, final int sizeY,
                                                                      final int patchX, final int patchY, final int patchWidth, final int patchHeight)
	{
		return new MuPDFCancellableTaskDefinition<Void, Void>(mCore) {

			@Override
			public Void doInBackground(Cookie cookie, Void ... params) {
				// Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
				// is not incremented when drawing.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
						Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					bm.eraseColor(0);
				mCore.updatePage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
				return null;
			}
		};
	}

	@Override
	protected LinkInfo[] getLinkInfo() {
		return mCore.getPageLinks(mPageNumber);
	}

	@Override
	protected TextWord[][] getText() {
		return mCore.textLines(mPageNumber);
	}

	@Override
	protected void addMarkup(PointF[] quadPoints, Annotation.Type type) {
		mCore.addMarkupAnnotation(mPageNumber, quadPoints, type);
	}

	private void loadAnnotations() {
		mAnnotations = null;
		if (mLoadAnnotations != null)
			mLoadAnnotations.cancel(true);
		mLoadAnnotations = new AsyncTask<Void,Void, Annotation[]>() {
			@Override
			protected Annotation[] doInBackground(Void... params) {
				return mCore.getAnnotations(mPageNumber);
			}

			@Override
			protected void onPostExecute(Annotation[] result) {
				mAnnotations = result;
			}
		};

		mLoadAnnotations.execute();
	}

	@Override
	public void setPage(final int page, PointF size) {
		loadAnnotations();

		mLoadWidgetAreas = new AsyncTask<Void,Void,RectF[]>() {
			@Override
			protected RectF[] doInBackground(Void... arg0) {
				return mCore.getWidgetAreas(page);
			}

			@Override
			protected void onPostExecute(RectF[] result) {
				mWidgetAreas = result;
			}
		};

		mLoadWidgetAreas.execute();

		super.setPage(page, size);
	}

	public void setScale(float scale) {
		// This type of view scales automatically to fit the size
		// determined by the parent view groups during layout
	}

	@Override
	public void releaseResources() {
		if (mPassClick != null) {
			mPassClick.cancel(true);
			mPassClick = null;
		}

		if (mLoadWidgetAreas != null) {
			mLoadWidgetAreas.cancel(true);
			mLoadWidgetAreas = null;
		}

		if (mLoadAnnotations != null) {
			mLoadAnnotations.cancel(true);
			mLoadAnnotations = null;
		}

		if (mSetWidgetText != null) {
			mSetWidgetText.cancel(true);
			mSetWidgetText = null;
		}

		if (mSetWidgetChoice != null) {
			mSetWidgetChoice.cancel(true);
			mSetWidgetChoice = null;
		}

		if (mAddStrikeOut != null) {
			mAddStrikeOut.cancel(true);
			mAddStrikeOut = null;
		}

		if (mDeleteAnnotation != null) {
			mDeleteAnnotation.cancel(true);
			mDeleteAnnotation = null;
		}

		super.releaseResources();
	}

	@Override
	public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
	}

	@Override
	public void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second) {
	}
}

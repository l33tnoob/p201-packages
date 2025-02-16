package com.szkj.watch.launcher.clock;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.szkj.watch.launcher.R;
import com.szkj.watch.launcher.launcher.MyApplication;

public class AnalogClockFragment11 extends AnalogClockFragment {	
	private static final Bundle sBundle = new Bundle();
	static {
		sBundle.putInt(BATTERY_CHARGING_ICON, R.drawable.battery05_charging);
		sBundle.putInt(BATTERY_LEVEL_ICON, R.drawable.battery05);
		sBundle.putInt(TIME_UPDATE_PERIOD, MILLIS_PERIOD_SECOND);
	}
	
	public AnalogClockFragment11() {
		super();
		setArguments(sBundle);
	}
	
	private TextView mDayTV;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.clock11, null);
		LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		view.setLayoutParams(lp);
		mAnalogClock = (AnalogClock)view.findViewById(R.id.clock);
		mDateTV = (TextView)view.findViewById(R.id.date);
		mDayTV = (TextView)view.findViewById(R.id.day);
		TextView smsView = (TextView)view.findViewById(R.id.sms);
		TextView callView = (TextView)view.findViewById(R.id.call);
		ImageView batteryView = (ImageView)view.findViewById(R.id.battery);
		setCommonView(batteryView, smsView, smsView, callView, callView);
		return view ;
	}

	@Override
	protected void updateDate(int year, int month, int date, int day) {
		mDateTV.setText(String.valueOf(date));
		if (day >=0 && day < MyApplication.DAYS_ZH.length) {
			mDayTV.setText(MyApplication.DAYS_ZH[day]);
		}
	}
}

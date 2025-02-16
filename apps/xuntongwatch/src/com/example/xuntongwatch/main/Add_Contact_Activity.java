package com.example.xuntongwatch.main;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.xuntongwatch.R;
import com.example.xuntongwatch.databaseutil.PhoneDatabaseUtil;
import com.example.xuntongwatch.entity.Contact;

public class Add_Contact_Activity extends Activity implements OnClickListener {

	private TextView custom_head, sure, add_or_update;
	private EditText phone, name;
	// private ContactDbUtil contactUtil;
	private Bitmap bitmap;
	private Contact contact;

	public static final int RESULT_CODE = 1112;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.add_contact);
		add_or_update = (TextView) this.findViewById(R.id.add_contact_add_or_update);
		custom_head = (TextView) this.findViewById(R.id.add_contact_custom_head);
		sure = (TextView) this.findViewById(R.id.add_contact_sure);
		phone = (EditText) this.findViewById(R.id.add_contact_phone);
		name = (EditText) this.findViewById(R.id.add_contact_name);
		name.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String editable = name.getText().toString();
				String str = stringFilter(editable.toString());
				if (!editable.equals(str)) {
					name.setText(str);
					// 设置新的光标所在位置
					name.setSelection(str.length());
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				// TODO Auto-generated method stub

			}

			@Override
			public void afterTextChanged(Editable s) {
				// TODO Auto-generated method stub

			}
		});
		custom_head.setOnClickListener(this);
		sure.setOnClickListener(this);
		ajustUpdate();
	}

	public static String stringFilter(String str) throws PatternSyntaxException {
		// 只允许字母、数字和汉字
		String regEx = "[^a-zA-Z0-9\u4E00-\u9FA5]";
		Pattern p = Pattern.compile(regEx);
		Matcher m = p.matcher(str);
		return m.replaceAll("").trim();
	}

	/**
	 * 判断是否是修改界面
	 */
	private void ajustUpdate() {
		Intent intent = this.getIntent();
		phone.setText(intent.getStringExtra("new_contact_phone"));
		if (intent != null) {
			String contact_name = intent.getStringExtra("contact_name");
			if (TextUtils.isEmpty(contact_name)) {
				return;
			}
			add_or_update.setText("修改联系人");
			String contact_phone = intent.getStringExtra("contact_phone");
			// String contact_head = intent.getStringExtra("contact_head");
			int contact_id = intent.getIntExtra("contact_id", -1);
			contact = new Contact();
			// contact.setContact_head(contact_head);
			contact.setContact_name(contact_name);
			contact.setContact_id(contact_id);
			contact.setContact_phone(contact_phone);
			if (!TextUtils.isEmpty(contact_phone)) {
				phone.setText(contact_phone);
			}
			if (!TextUtils.isEmpty(contact_name)) {
				name.setText(contact_name);
			}
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.add_contact_custom_head:
			Intent intent = new Intent(this, Edit_Head_Activity.class);
			this.startActivityForResult(intent, Edit_Head_Activity.RESULT_CODE);
			// startActivity(intent);
			break;
		case R.id.add_contact_sure:
			String name = this.name.getText().toString();
			String phone = this.phone.getText().toString();
			if (TextUtils.isEmpty(name)) {
				Toast.makeText(this, "请填写姓名", Toast.LENGTH_SHORT).show();
				return;
			}
			if (TextUtils.isEmpty(phone)) {
				Toast.makeText(this, "请填写电话号码", Toast.LENGTH_SHORT).show();
				return;
			}
			sure.setClickable(false);
			Contact contact = null;
			if (this.contact == null) {// 添加新的用户
				contact = addContact(name, phone, bitmap);
				Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
			} else {// 修改用户
				contact = updateContact(name, phone, bitmap);
				Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show();
			}
			Intent intentR = new Intent();
			Bundle b = new Bundle();
			b.putSerializable("contact", contact);
			intentR.putExtras(b);
			setResult(RESULT_OK, intentR);
			finish();
			break;
		}
	}

	private Contact addContact(String name, String phone, Bitmap bitmap) {
		Contact contact = genarationContact(name, phone, bitmap);
		// initContactDbUtil();
		// contactUtil.insertInto(contact);
		PhoneDatabaseUtil.addContact(this, contact);
		return contact;
	}

	private Contact updateContact(String name, String phone, Bitmap bitmap) {
		Contact contact = genarationContact(name, phone, bitmap);
		contact.setContact_id(this.contact.getContact_id());
		// initContactDbUtil();
		// contactUtil.updateByContact_id(contact);
		PhoneDatabaseUtil.updateContact(this, contact);
		return contact;
	}

	private Contact genarationContact(String name, String phone, Bitmap bitmap) {
		Contact contact = new Contact();
		if (bitmap != null) {
			// String photo_name = Utils.getPhotoFileName();
			// String photo_path = Utils.saveBitmapToFile(Constant.headUri,
			// this,
			// photo_name, bitmap);
			// contact.setContact_head(photo_path);
			// Log.e("", "photo_path  === " + photo_path);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			// 将Bitmap压缩成PNG编码，质量为100%存储
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
			byte[] photo = os.toByteArray();
			contact.setContact_head(photo);
		}
		contact.setContact_name(name);
		contact.setContact_phone(phone);

		return contact;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Edit_Head_Activity.RESULT_CODE) {
			Log.e("", "requestCode回来了回来了。");
			if (data != null) {
				bitmap = data.getParcelableExtra("bitmap");
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	// private void initContactDbUtil() {
	// if (contactUtil == null)
	// contactUtil = new ContactDbUtil(this);
	// }

	@Override
	public void finish() {
		super.finish();
	}

}

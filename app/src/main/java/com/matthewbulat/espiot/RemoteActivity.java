package com.matthewbulat.espiot;

import android.app.AlertDialog;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.EditText;
import android.widget.Toast;

import com.matthewbulat.espiot.Database.devices.DeviceDB;
import com.matthewbulat.espiot.Database.devices.DeviceTable;
import com.matthewbulat.espiot.Objects.Message;
import com.matthewbulat.espiot.Objects.User;
import com.matthewbulat.espiot.RetrofitDIR.ApiUtils;
import com.matthewbulat.espiot.RetrofitDIR.Interfaces.IoTAPI;
import com.matthewbulat.espiot.Fragments.FanRemoteControl;
import com.matthewbulat.espiot.Fragments.TVRemoteControl;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

public class RemoteActivity extends AppCompatActivity implements TVRemoteControl.OnFragmentInteractionListener, FanRemoteControl.OnFragmentInteractionListener {

    private Message device;
    private User user;
    private IoTAPI ioTAPI;
    private TVRemoteControl tvRemoteControl;
    private FanRemoteControl fanRemoteControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ioTAPI = ApiUtils.getIoTService();
        setContentView(R.layout.activity_remote);
        device = getIntent().getParcelableExtra("device");
        user = getIntent().getParcelableExtra("user");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        ViewPager mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);

        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(mViewPager));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.device_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection

        switch (item.getItemId()) {
            case R.id.removedevice:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.remove_device_dialog_message)
                        .setTitle(R.string.remove_device_dialog_title);
                builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Toast.makeText(getApplicationContext(), "Starting removal of device."
                                , Toast.LENGTH_SHORT).show();
                        Message message = new Message();
                        message.setDeviceID(device.getDeviceID());
                        message.setAction("removedevice");
                        deviceAction(message);
                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Toast.makeText(getApplicationContext(), "Starting removal of device canceled."
                                , Toast.LENGTH_LONG).show();
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
                return true;
            case R.id.deviceInformation:
                AlertDialog.Builder builder1 = new AlertDialog.Builder(this);


                builder1 = new AlertDialog.Builder(this);
                builder1.setMessage(String.format("Your device token is: %s\n" +
                                "Your user name is: %s\n" +
                                "Your user token is: %s",
                        device.getDeviceID(),
                        user.getUserName(),
                        user.getUserToken()))
                        .setTitle(R.string.device_information_title);

                builder1.setPositiveButton(R.string.cancel, (dialog12, id) -> dialog12.dismiss());
                AlertDialog dialog1 = builder1.create();
                dialog1.show();
                return true;
            case R.id.updateName:
                AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
                final EditText edittext = new EditText(this);
                builder2.setMessage("Please type device name:");
                builder2.setTitle("Change Device Name");

                builder2.setView(edittext);

                builder2.setPositiveButton("Update", (dialog22, whichButton) -> {
                    String newName = edittext.getText().toString();
                    if (edittext.length() > 32) {
                        edittext.setError("Device name cannot be longer than 32 characters, current name is: " + edittext.length());
                    } else if (edittext.length() == 0) {
                        edittext.setError("Device name is empty.");
                    } else {
                        Message message = new Message();
                        message.setDeviceID(device.getDeviceID());
                        message.setAction("updatedevicedescription");
                        message.setDeviceDescription(newName);
                        updateDeviceDescription(message, user);
                    }
                });

                builder2.setNegativeButton("Cancel", (dialog2, whichButton) -> {
                });
                builder2.show();
                return true;
            case R.id.applicationSettings:
                startActivity(new Intent(RemoteActivity.this, Settings.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void deviceAction(Message message) {

        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(ioTAPI.lampActions(message.getDeviceID(), user.getUserName(), user.getUserToken(), message.getAction())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<Message>() {
                                   @Override
                                   public void onComplete() {
                                       compositeDisposable.dispose();
                                   }

                                   @Override
                                   public void onError(Throwable e) {
                                       Log.e("Login", e.getMessage());
                                   }

                                   @Override
                                   public void onNext(Message value) {
                                       finishAPIAction(value);
                                       Log.i("Device Action", "Device action request successful");
                                   }
                               }
                )
        );
    }

    public void updateDeviceDescription(Message message, User user) {

        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(ioTAPI.updateDeviceDescription(message.getDeviceID(), user.getUserName(), user.getUserToken(), message.getAction(), message.getDeviceDescription())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<Message>() {
                                   @Override
                                   public void onComplete() {
                                       compositeDisposable.dispose();
                                   }

                                   @Override
                                   public void onError(Throwable e) {
                                       Log.e("Login", e.getMessage());
                                   }

                                   @Override
                                   public void onNext(Message value) {
                                       Log.i("Device Action", "Device description changed successfully");
                                       finishAPIAction(value);
                                   }
                               }
                )
        );
    }

    private void finishAPIAction(Message message) {
        Context context = getApplicationContext();
        switch (message.getAction()) {
            case "deviceNotConnectedToSystem":
                Toast.makeText(context, "Your ESP device is disconnected from the network."
                        , Toast.LENGTH_LONG).show();
                break;
            case "communicationError":
                Toast.makeText(context, "Internal network error, please try again in few moments."
                        , Toast.LENGTH_LONG).show();
                break;
            case "IncorrectCredentials":
                Toast.makeText(context, "Please try again in few moments."
                        , Toast.LENGTH_LONG).show();
                break;
            case "deviceNameUpdated": {
                getDevicesDetails(message.getDeviceDescription(), device.getDeviceID());
            }
            break;
            case "deviceremoved": {
                DeviceTable deviceTable = new DeviceTable();
                deviceTable.setDeviceID(message.getDeviceID());
                deviceTable.setDeviceDescription(message.getDeviceDescription());
                deviceTable.setDeviceType(message.getDeviceType());
                deleteDeviceFromSystem(deviceTable);
            }
            break;
        }
    }

    private void getDevicesDetails(String newDeviceName, String deviceID) {
        DeviceDB deviceDB = Room.databaseBuilder(getApplicationContext(), DeviceDB.class, "devicedb").build();

        Completable.fromAction(() -> deviceDB.devicesDao().updateDeviceName(newDeviceName, deviceID))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onComplete() {
                        deviceDB.close();
                        Toast.makeText(getApplicationContext(), "Device updated successfully."
                                , Toast.LENGTH_LONG).show();
                        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                        fragmentTransaction.detach(tvRemoteControl);
                        fragmentTransaction.detach(fanRemoteControl);
                        tvRemoteControl.updateDeviceDescription(newDeviceName);
                        fanRemoteControl.updateDeviceDescription(newDeviceName);
                        fragmentTransaction.attach(tvRemoteControl);
                        fragmentTransaction.attach(fanRemoteControl);
                        fragmentTransaction.commit();


                        //deviceName.setText(newDeviceName);
                    }

                    @Override
                    public void onError(Throwable e) {
                        deviceDB.close();
                        Log.e("Database", e.getMessage());
                    }
                });
    }

    private void deleteDeviceFromSystem(DeviceTable deviceTable) {
        DeviceDB deviceDB = Room.databaseBuilder(getApplicationContext(), DeviceDB.class, "devicedb").build();

        Completable.fromAction(() -> deviceDB.devicesDao().deleteDevice(deviceTable))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onComplete() {
                        deviceDB.close();
                        Toast.makeText(getApplicationContext(), "Device removed successfully."
                                , Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(Throwable e) {
                        deviceDB.close();
                        Log.e("Database", e.getMessage());
                    }
                });
    }


    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {

            switch (position) {
                case 0:
                    tvRemoteControl = TVRemoteControl.newInstance(device, user);
                    return tvRemoteControl;
                case 1:
                    fanRemoteControl = FanRemoteControl.newInstance(device, user);
                    return fanRemoteControl;
            }
            // getItem is called to instantiate the fragment for the given page.
            //todo return default fragment
            return null;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }
    }
}

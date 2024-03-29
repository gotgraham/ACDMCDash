package com.empire.ac_dmc_dashboard;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.SpeedView;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class DashboardFragment extends Fragment implements ServiceConnection, SerialListener {

  private enum Connected { False, Pending, True }

  public static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

  private int deviceId, portNum, baudRate;

  private TextView igbtTemp1;
  private TextView igbtTemp2;
  private TextView igbtTemp3;
  private TextView igbtTemp4;
  private TextView igbtTemp5;
  private TextView igbtTemp6;

  // private TextView reqAmps;
  private TextView maxAmps;
  private TextView fieldWeakening;
  // private TextView eRPM;

  private SpeedView phaseAmps;

  private TextView dcVoltage;
  private TextView dcTemp1;
  private TextView dcTemp2;
  private TextView dcAmps;

  private SerialSocket socket;
  private SerialService service;

  private boolean initialStart = true;
  private Connected connected = Connected.False;
  private BroadcastReceiver broadcastReceiver;

  // Our packet
  MuxData muxData = new MuxData();

  public DashboardFragment() {
    broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(INTENT_ACTION_GRANT_USB)) {
          Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
          connect(granted);
        }
      }
    };
  }

  /*
   * Lifecycle
   */
  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
    setRetainInstance(true);
    deviceId = getArguments().getInt("device");
    portNum = getArguments().getInt("port");
    baudRate = getArguments().getInt("baud");
  }

  @Override
  public void onDestroy() {
    if (connected != Connected.False)
      disconnect();
    getActivity().stopService(new Intent(getActivity(), SerialService.class));
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
    if(service != null)
      service.attach(this);
    else
      getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
  }

  @Override
  public void onStop() {
    if(service != null && !getActivity().isChangingConfigurations())
      service.detach();
    super.onStop();
  }

  @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
  }

  @Override
  public void onDetach() {
    try { getActivity().unbindService(this); } catch(Exception ignored) {}
    super.onDetach();
  }

  @Override
  public void onResume() {
    super.onResume();
    getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
    if(initialStart && service !=null) {
      initialStart = false;
      getActivity().runOnUiThread(this::connect);
    }
  }

  @Override
  public void onPause() {
    getActivity().unregisterReceiver(broadcastReceiver);
    super.onPause();
  }

  @Override
  public void onServiceConnected(ComponentName name, IBinder binder) {
    service = ((SerialService.SerialBinder) binder).getService();
    if(initialStart && isResumed()) {
      initialStart = false;
      getActivity().runOnUiThread(this::connect);
    }
  }

  @Override
  public void onServiceDisconnected(ComponentName name) {
    service = null;
  }

  /*
   * UI
   */
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

    igbtTemp1 = view.findViewById(R.id.igbt1);
    igbtTemp2 = view.findViewById(R.id.igbt2);
    igbtTemp3 = view.findViewById(R.id.igbt3);
    igbtTemp4 = view.findViewById(R.id.igbt4);
    igbtTemp5 = view.findViewById(R.id.igbt5);
    igbtTemp6 = view.findViewById(R.id.igbt6);

    //reqAmps = view.findViewById(R.id.reqAmps);
    fieldWeakening = view.findViewById(R.id.fieldWeakening);
    phaseAmps = view.findViewById(R.id.phaseAmps);
    maxAmps = view.findViewById(R.id.maxAmps);
    //eRPM = view.findViewById(R.id.eRPM);

    dcVoltage = view.findViewById(R.id.dcVoltage);
    dcTemp1 = view.findViewById(R.id.dcTemp1);
    dcTemp2 = view.findViewById(R.id.dcTemp2);
    dcAmps = view.findViewById(R.id.dcAmps);

    updateGauges();

    return view;
  }

  // Update the gauges with the new temp data
  private void updateGauges() {
    igbtTemp1.setText(String.format("%.01f F", muxData.igbt1));
    igbtTemp2.setText(String.format("%.01f F", muxData.igbt2));
    igbtTemp3.setText(String.format("%.01f F", muxData.igbt3));
    igbtTemp4.setText(String.format("%.01f F", muxData.igbt4));
    igbtTemp5.setText(String.format("%.01f F", muxData.igbt5));
    igbtTemp6.setText(String.format("%.01f F", muxData.igbt6));

    //reqAmps.setText(String.format("%.01f A", muxData.reqPhaseAmps));
    maxAmps.setText(String.format("%.01f A", muxData.maxAmps));
    fieldWeakening.setText(String.format("%.01f A", muxData.fieldWeakening));
    //eRPM.setText(String.format("%d", (int) muxData.eRPM));

    phaseAmps.speedTo(muxData.phaseAmps, 1000);

    dcVoltage.setText(String.format("%.01f V", muxData.dcDcVoltage));
    dcAmps.setText(String.format("%.01f A", muxData.dcDcCurrent));
    dcTemp1.setText(String.format("%.01f F", muxData.dcDcTemp1));
    dcTemp2.setText(String.format("%.01f F", muxData.dcDcTemp2));
  }

  /*
   * Serial + UI
   */
  private void connect() {
    connect(null);
  }

  private void connect(Boolean permissionGranted) {
    UsbDevice device = null;
    UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
    for(UsbDevice v : usbManager.getDeviceList().values())
      if(v.getDeviceId() == deviceId)
        device = v;
    if(device == null) {
      status("connection failed: device not found");
      return;
    }
    UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
    if(driver == null) {
      driver = CustomProber.getCustomProber().probeDevice(device);
    }
    if(driver == null) {
      status("connection failed: no driver for device");
      return;
    }
    if(driver.getPorts().size() < portNum) {
      status("connection failed: not enough ports at device");
      return;
    }
    UsbSerialPort usbSerialPort = driver.getPorts().get(portNum);
    UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
    if(usbConnection == null && permissionGranted == null) {
      if (!usbManager.hasPermission(driver.getDevice())) {
        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
        usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
        return;
      }
    }
    if(usbConnection == null) {
      if (!usbManager.hasPermission(driver.getDevice()))
        status("connection failed: permission denied");
      else
        status("connection failed: open failed");
      return;
    }

    connected = Connected.Pending;
    try {
      socket = new SerialSocket();
      service.connect(this, "Connected");
      socket.connect(getContext(), service, usbConnection, usbSerialPort, baudRate);
      // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
      // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
      onSerialConnect();
    } catch (Exception e) {
      onSerialConnectError(e);
    }
  }

  private void disconnect() {
    connected = Connected.False;
    service.disconnect();
    socket.disconnect();
    socket = null;
  }

  private void receive(byte[] data) {
    muxData.processData(data);
    updateGauges();
  }

  private void status(String str) {
    Toast.makeText(service, str, Toast.LENGTH_LONG).show();
  }

  /*
   * SerialListener
   */
  @Override
  public void onSerialConnect() {
    status("connected");
    connected = Connected.True;
  }

  @Override
  public void onSerialConnectError(Exception e) {
    status("connection failed: " + e.getMessage());
    disconnect();
  }

  @Override
  public void onSerialRead(byte[] data) {
    receive(data);
  }

  @Override
  public void onSerialIoError(Exception e) {
    status("connection lost: " + e.getMessage());
    disconnect();
  }

}

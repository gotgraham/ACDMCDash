package com.empire.ac_dmc_dashboard;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.nio.ByteBuffer;
import java.io.*;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

  private enum Connected { False, Pending, True }

  public static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

  private int deviceId, portNum, baudRate;
  private String newline = "\r\n";

  private TextView receiveText;

  private SerialSocket socket;
  private SerialService service;

  private boolean initialStart = true;
  private Connected connected = Connected.False;
  private BroadcastReceiver broadcastReceiver;

  // Our packet
  ByteArrayOutputStream receivedBytes = new ByteArrayOutputStream();



  public TerminalFragment() {
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
    setHasOptionsMenu(true);
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
    View view = inflater.inflate(R.layout.fragment_terminal, container, false);
    receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
    receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
    receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
    TextView sendText = view.findViewById(R.id.send_text);
    View sendBtn = view.findViewById(R.id.send_btn);
    sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
    return view;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_terminal, menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.clear) {
      receiveText.setText("");
      return true;
    } else if (id ==R.id.newline) {
      String[] newlineNames = getResources().getStringArray(R.array.newline_names);
      String[] newlineValues = getResources().getStringArray(R.array.newline_values);
      int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle("Newline");
      builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
        newline = newlineValues[item1];
        dialog.dismiss();
      });
      builder.create().show();
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
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

  private void send(String str) {
    if(connected != Connected.True) {
      Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
      return;
    }
    try {
      SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
      spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      receiveText.append(spn);
      byte[] data = (str + newline).getBytes();
      socket.write(data);
    } catch (Exception e) {
      onSerialIoError(e);
    }
  }

  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }



  // Decode a value from the packet
  private Double decodeValue(int i) {
    return new Double(Integer.reverseBytes(i)) / 100;
  }

  private void receive(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.rewind();

    boolean header_found = false;
    for (int i = 0; i < data.length - 4 && !header_found;) {
      // We need to verify that we have 32-bit block left
      if (buffer.getInt(i) == 0xAABBCCDD) {
        receivedBytes.reset(); // Clear our saved buffer

        header_found = true;

        // Copy the buffer from this point on
        for ( ; i < data.length; ++i) {
          receivedBytes.write(data[i]);
        }
      }
      else {
        // Copy this byte out - not the header
        receivedBytes.write(data[i++]);
      }

      // Copy any remaining buffer out if we have a partial integer left.
      // Since we have a sliding window for checking integer data, we can only
      // advance through the buffer until we get to the last 4 bytes
      // If these aren't the header, we need to copy the remaining 3 bytes of
      // the integer out - the first byte of the integer will be copied above.
      int remaining_bytes = data.length - (i + 1);
      if (remaining_bytes > 0 && remaining_bytes < 4) {
        for (; i < data.length; ++i) {
          receivedBytes.write(data[i]);
        }
      }

      // Have we received a full packet?
      if (receivedBytes.size() >= 44) {
        ByteBuffer packet = ByteBuffer.wrap(receivedBytes.toByteArray());
        if (packet.getInt(0) == 0xAABBCCDD){
          Double temp1 = decodeValue(packet.getInt(4));
          Double temp2 = decodeValue(packet.getInt(8));
          Double voltage = decodeValue(packet.getInt(12));
          Double current = decodeValue(packet.getInt(16));

          Double igbt1 = decodeValue(packet.getInt(20));
          Double igbt2 = decodeValue(packet.getInt(24));
          Double igbt3 = decodeValue(packet.getInt(28));
          Double igbt4 = decodeValue(packet.getInt(32));
          Double igbt5 = decodeValue(packet.getInt(36));
          Double igbt6 = decodeValue(packet.getInt(40));

          receiveText.append("Temp1: " + temp1 + ", Temp2: " + temp2 + ", Volts: " + voltage + ", Current: " + current +"\n");
          receiveText.append("I1: " + igbt1 + ", I2: " + igbt2 + ", I3: " + igbt3 + ", I4: " + igbt4 + ", I5: " + igbt5 + ", I6: " + igbt6 + "\n\n");
        }
      }
    }
  }

  private void status(String str) {
    SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    receiveText.append(spn);
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

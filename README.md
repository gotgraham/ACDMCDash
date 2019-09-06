# AC DMC Dashboard

This is an Android app designed to interface with the AC/DMC Mux. It takes packets of car status data, decodes, and displays it. This data includes IGBT Temps, DC-DC Temps, DC-DC Voltage/Current.

## Dependencies

The app uses the [usb-serial-for-android](https://github.com/kai-morich/usb-serial-for-android) library.

## Credits

It is based on the Simple USB Terminal android app. I basically stripped the front end of that app, added packet decoding, and added a new gauge front end.

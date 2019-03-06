import 'package:flutter_amap/src/latlng.dart';

class LocationDescription extends LatLng {
  int returnCode;
  String streetName;
  String streetOffset;
  String city;
  double locationMarkedTimestamp;

  LocationDescription(
      {double latitude,
      double longitude,
      this.returnCode,
      this.streetName,
      this.streetOffset,
      this.city,
      this.locationMarkedTimestamp})
      : super(latitude, longitude);

  static LocationDescription fromMap(dynamic map) {
    LocationDescription locationDescription = LocationDescription(
      latitude: map["latitude"],
      longitude: map["longitude"],
      returnCode: map["returnCode"],
      streetName: map["streetName"],
      streetOffset: map["streetOffset"],
      city: map["city"],
      locationMarkedTimestamp: map["changeID"]
    );
    return locationDescription;
  }

  String get bestDescription {
    if (this.streetName != null && this.streetName.length > 0) {
      return this.streetName + this.streetOffset;
    } else {
      return this.city;
    }
  }
}

package com.jzoom.flutteramap;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.geocoder.StreetNumber;

/**
 * FlutterAmapPlugin
 */
public class FlutterAmapPlugin implements MethodCallHandler {

    private FlutterActivity root;

    static AMapViewManager manager;

    private MethodChannel channel;

    public FlutterAmapPlugin(FlutterActivity activity, MethodChannel channel) {
        this.root = activity;
        this.manager = new AMapViewManager(channel);
        this.channel = channel;
    }


    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_amap");
        channel.setMethodCallHandler(new FlutterAmapPlugin((FlutterActivity) registrar.activity(), channel));
    }

    private AMapView createView(final String id, final Map<String, Object> mapViewOptions) {
        final Map<String, Object> centerCoordinate = (Map<String, Object>) mapViewOptions.get("centerCoordinate");

        AMapView view = new AMapView(root);
        view.setKey(id);
        MyLocationStyle myLocationStyle;
        myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(centerCoordinate == null ? MyLocationStyle.LOCATION_TYPE_LOCATE : MyLocationStyle.LOCATION_TYPE_SHOW);
        AMap aMap = view.getMap();
        aMap.setMyLocationStyle(myLocationStyle);//设置定位蓝点的Style
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        aMap.setMyLocationEnabled(true); // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
        aMap.setMapType((Integer) mapViewOptions.get("mapType"));

        aMap.moveCamera(CameraUpdateFactory.zoomTo((float) (double) (Double) mapViewOptions.get("zoomLevel")));
        aMap.setMaxZoomLevel((float) (double) (Double) mapViewOptions.get("maxZoomLevel"));
        aMap.setMinZoomLevel((float) (double) (Double) mapViewOptions.get("minZoomLevel"));

        aMap.setOnMyLocationChangeListener(new AMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                if (centerCoordinate == null) {
                    updateMarkerPosition(id, new LatLng(location.getLatitude(), location.getLongitude()));
                }
            }
        });
        aMap.setOnMapClickListener(new AMap.OnMapClickListener() {
                                       @Override
                                       public void onMapClick(LatLng latLng) {
                                           updateMarkerPosition(id, latLng);
                                       }
                                   }
        );
        this.map.put(id, view);

        if (centerCoordinate != null) {
            Double latitude = (Double) centerCoordinate.get("latitude");
            Double longitude = (Double) centerCoordinate.get("longitude");
            aMap.moveCamera(CameraUpdateFactory.changeLatLng(new LatLng(
                    latitude,
                    longitude)));
            updateMarkerPosition(id, new LatLng(latitude, longitude));
        }
        return view;
    }

    private void fetchDetailedAddressAsync(AMapView view, LatLng latLng, final String id, final double changeID) {
        GeocodeSearch geocoderSearch = new GeocodeSearch(view.getContext());
        geocoderSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int rCode) {
                if (channel != null) {
                    Map<String, Object> map = new HashMap<String, Object>();
                    LatLonPoint latLonPoint = regeocodeResult.getRegeocodeQuery().getPoint();
                    map.put("returnCode", rCode);
                    map.put("latitude", latLonPoint.getLatitude());
                    map.put("longitude", latLonPoint.getLongitude());
                    StreetNumber streetNumber = regeocodeResult.getRegeocodeAddress().getStreetNumber();
                    String streetName = String.format(
                            "%s%s",
                            streetNumber.getStreet(),
                            streetNumber.getNumber()
                    );
                    map.put("streetName", streetName);
                    String offset = String.format(
                            "%s%.2fm",
                            streetNumber.getDirection(),
                            streetNumber.getDistance()
                    );
                    map.put("streetOffset", offset);
                    String city = regeocodeResult.getRegeocodeAddress().getProvince() +
                            regeocodeResult.getRegeocodeAddress().getCity() +
                            regeocodeResult.getRegeocodeAddress().getDistrict() +
                            regeocodeResult.getRegeocodeAddress().getNeighborhood();
                    map.put("city", city);
                    map.put("changeID", changeID);
                    map.put("timestamp", (double) (System.currentTimeMillis() / 1000.0d));
                    map.put("id", id);
                    channel.invokeMethod("markerDescriptionUpdate", map);
                    if (markers.containsKey(id) && markers.get(id) != null) {
                        Marker marker = markers.get(id);
                        marker.setTitle(streetName.length() > 0 ? streetName + offset : city);
                        marker.setSnippet(
                                String.format("(%.5f, %.5f)", latLonPoint.getLongitude(), latLonPoint.getLatitude())
                        );
                        marker.showInfoWindow();
                    }
                }
            }

            @Override
            public void onGeocodeSearched(GeocodeResult geocodeResult, int i) {

            }
        });

        if (markers.containsKey(id) && markers.get(id) != null) {
            Marker marker = markers.get(id);
            marker.setTitle("正在读取详细地址");
        }

        geocoderSearch.getFromLocationAsyn(
                new RegeocodeQuery(
                        new LatLonPoint(
                                latLng.latitude, latLng.longitude
                        ),
                        50.0f,
                        GeocodeSearch.AMAP
                )
        );
    }

    @SuppressLint("DefaultLocale")
    private void updateMarkerPosition(String id, LatLng latLng) {
        AMapView view = this.map.get(id);
        Marker marker;
        if (view == null) return;
        if (!this.markers.containsKey(id) || this.markers.get(id) == null) {
            final MarkerOptions options = new MarkerOptions();
            options.position(new LatLng(
                    latLng.latitude,
                    latLng.longitude
            )).title(
                    "定位点"
            ).draggable(false);
            marker = view.getMap().addMarker(options);
            marker.showInfoWindow();
            this.markers.put(id, marker);
        } else {
            marker = this.markers.get(id);
            marker.setPosition(latLng);
        }

        marker.setSnippet(
                String.format("(%.5f, %.5f)", latLng.longitude, latLng.latitude)
        );

        if (channel != null) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("latitude", marker.getPosition().latitude);
            map.put("longitude", marker.getPosition().longitude);
            map.put("accuracy", 0.0d);
            map.put("altitude", 0.0d);
            map.put("speed", 0.0d);
            map.put("timestamp", (double) (System.currentTimeMillis() / 1000.0d));
            map.put("id", id);
            channel.invokeMethod("markerLocationUpdate", map);
            fetchDetailedAddressAsync(view, latLng, id, (double) map.get("timestamp"));
        }
    }

    private Map<String, AMapView> map = new ConcurrentHashMap<>();
    private Map<String, Marker> markers = new ConcurrentHashMap<>();


    private AMapView getView(String id) {
        return map.get(id);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        String method = call.method;
        if ("show".equals(method)) {
            Map<String, Object> args = (Map<String, Object>) call.arguments;
            final Map<String, Object> mapViewOptions = (Map<String, Object>) args.get("mapView");

            final String id = (String) args.get("id");

            root.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AMapView view = createView(id, mapViewOptions);
                    view.onCreate(new Bundle());
                    view.onResume();
                    root.addContentView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));


                }
            });


        } else if ("rect".equals(method)) {
            Map<String, Object> args = (Map<String, Object>) call.arguments;
            final int x = (int) (double) (Double) args.get("x");
            final int y = (int) (double) (Double) args.get("y");
            final double width = (Double) args.get("width");
            final double height = (Double) args.get("height");
            String id = (String) args.get("id");

            final AMapView layout = getView(id);
            if (layout != null) {
                root.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layout.getLayoutParams();
                        DisplayMetrics metrics = new DisplayMetrics();
                        root.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                        params.leftMargin = (int) (x * metrics.scaledDensity);
                        params.topMargin = (int) (y * metrics.scaledDensity);


                        params.width = (int) (width * metrics.scaledDensity);
                        params.height = (int) (height * metrics.scaledDensity);
                        layout.setLayoutParams(params);
                    }
                });
            }


        } else if ("remove".equals(method)) {
            Map<String, Object> args = (Map<String, Object>) call.arguments;
            String id = (String) args.get("id");
            final AMapView view = getView(id);
            if (view != null) {
                ViewGroup viewGroup = (ViewGroup) view.getParent();
                viewGroup.removeView(view);
            }
        } else if ("setApiKey".equals(method)) {
            result.success(true);
        } else if ("dismiss".equals(method)) {

            result.success(true);
        } else {
            result.notImplemented();
        }
    }


}

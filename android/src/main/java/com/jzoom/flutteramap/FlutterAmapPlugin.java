package com.jzoom.flutteramap;

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
    channel.setMethodCallHandler(new FlutterAmapPlugin( (FlutterActivity) registrar.activity(),channel ));
  }

  private AMapView createView(final String id, final Map<String,Object> mapViewOptions){
      Map<String,Object> centerCoordinate = (Map<String, Object>) mapViewOptions.get("centerCoordinate");

      AMapView view = new AMapView(root);
      view.setKey(id);
      MyLocationStyle myLocationStyle;
      myLocationStyle = new MyLocationStyle();
      myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE);
      AMap aMap = view.getMap();
      aMap.setMyLocationStyle(myLocationStyle);//设置定位蓝点的Style
      aMap.getUiSettings().setMyLocationButtonEnabled(true);
      aMap.setMyLocationEnabled(true); // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
      aMap.setMapType((Integer) mapViewOptions.get("mapType"));

      aMap.moveCamera(CameraUpdateFactory.zoomTo((float)(double)(Double) mapViewOptions.get("zoomLevel")));
      aMap.setMaxZoomLevel( (float)(double)(Double) mapViewOptions.get("maxZoomLevel")    );
      aMap.setMinZoomLevel( (float)(double)(Double) mapViewOptions.get("minZoomLevel")    );
      if(centerCoordinate!=null){
          Double latitude = (Double) centerCoordinate.get("latitude");
          Double longitude = (Double) centerCoordinate.get("longitude");
          aMap.moveCamera(CameraUpdateFactory.changeLatLng(new LatLng(
                  latitude,
                  longitude)));
          view.getMap().setOnMyLocationChangeListener(new AMap.OnMyLocationChangeListener() {
              @Override
              public void onMyLocationChange(Location location) {
                  updateMarkerPosition(id, new LatLng(location.getLatitude(), location.getLongitude()));
              }
          });
          view.getMap().setOnMapClickListener(
                  new AMap.OnMapClickListener() {
                      @Override
                      public void onMapClick(LatLng latLng) {
                          updateMarkerPosition(id, latLng);
                      }
                  }
          );
      }
      this.map.put(id,view);
      return view;
  }

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
            ).snippet(
                    "点击地图修改定位点位置"
            ).draggable(false);
            marker = view.getMap().addMarker(options);
            this.markers.put(id, marker);
        } else {
            marker = this.markers.get(id);
            marker.setPosition(latLng);
        }

        if (channel != null) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("latitude", marker.getPosition().latitude);
            map.put("longitude", marker.getPosition().longitude);
            map.put("accuracy", 0.0d);
            map.put("altitude", 0.0d);
            map.put("speed", 0.0d);
            map.put("timestamp", 0.0d);
            map.put("id", id);
            channel.invokeMethod("markerLocationUpdate", map);
        }
    }

    private Map<String,AMapView> map =  new ConcurrentHashMap<>();
  private Map<String,Marker> markers =  new ConcurrentHashMap<>();


    private AMapView getView(String id){
      return map.get(id);
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    String method = call.method;
    if ("show".equals(method)) {
        Map<String,Object> args = (Map<String, Object>) call.arguments;
        final Map<String,Object> mapViewOptions = (Map<String, Object>) args.get("mapView");

        final String id = (String) args.get("id");

        root.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AMapView view = createView(id, mapViewOptions);
                view.onCreate(new Bundle());
                view.onResume();
                root.addContentView(  view,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));


            }
        });


    }else if("rect".equals(method)){
        Map<String,Object> args = (Map<String, Object>) call.arguments;
        final int x = (int)(double)(Double) args.get("x");
        final int y = (int)(double)(Double) args.get("y");
        final double width = (Double) args.get("width");
        final double height = (Double) args.get("height");
        String id = (String) args.get("id");

        final AMapView layout = getView(id);
        if(layout!=null){
           root.runOnUiThread(new Runnable() {
               @Override
               public void run() {
                   FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layout.getLayoutParams();
                   DisplayMetrics metrics = new DisplayMetrics();
                   root.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                   params.leftMargin = (int) (x * metrics.scaledDensity);
                   params.topMargin = (int) (y * metrics.scaledDensity);


                   params.width = (int) ( width* metrics.scaledDensity);
                   params.height = (int) ( height* metrics.scaledDensity);
                   layout.setLayoutParams(params);
               }
           });
        }


    }else if("remove".equals(method)){
        Map<String,Object> args = (Map<String, Object>) call.arguments;
        String id = (String) args.get("id");
        final AMapView view = getView(id);
        if(view != null){
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            viewGroup.removeView(view);
        }
    }  else if("setApiKey".equals(method)){
      result.success(true);
    }else if("dismiss".equals(method)){

        result.success(true);
    } else {
      result.notImplemented();
    }
  }


}

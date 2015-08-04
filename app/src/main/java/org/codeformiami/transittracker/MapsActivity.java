package org.codeformiami.transittracker;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.codeformiami.transittracker.model.Bus;
import org.codeformiami.transittracker.model.BusResult;
import org.codeformiami.transittracker.model.Tracker;
import org.codeformiami.transittracker.model.TrackerProperties;
import org.codeformiami.transittracker.model.TrackerResult;
import org.codeformiami.transittracker.model.Train;
import org.codeformiami.transittracker.model.TrainResult;
import org.codeformiami.transittracker.model.TrainRouteResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private MiamiTransitApiService api;
    private List<Bus> buses;
    private HashMap<String, Marker> busMap = new HashMap<String, Marker>();
    private List<Tracker> trackers;
    private HashMap<String, Marker> trackerMap = new HashMap<String, Marker>();
    private List<Train> trains;
    private HashMap<String, Marker> trainMap = new HashMap<String, Marker>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(25.7820998, -80.1408048), 12.0f));
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setMyLocationEnabled(true);

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint("http://miami-transit-api.herokuapp.com")
                .build();

        api = restAdapter.create(MiamiTransitApiService.class);
        requestTrainRoutes();

        final Handler handler=new Handler();
        handler.post(new Runnable() {

            @Override
            public void run() {
                //mMap.clear();
                Context context = getApplicationContext();
                CharSequence text = "Refreshing";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                requestApi();
                toast.show();

                handler.postDelayed(this, 10000); // refresh time
            }

        });
    }

    private void requestApi() {
        api.buses(new Callback<BusResult>() {
            @Override
            public void success(BusResult busResult, Response response) {
                if (busResult.RecordSet==null || busResult.RecordSet.Record.isEmpty())     {
                    Toast.makeText(getApplicationContext(), "Unfortunately bus data is not available.", Toast.LENGTH_LONG).show();

                } else {

                    if (buses == null) {
                        buses = busResult.RecordSet.Record;
                        // Add new markers to map
                        for (Bus bus : buses) {
                            addBusMarker(bus);
                        }
                    } else { // Buses and markers already exists, move positions
                        Marker foundBusMarker;
                        for (Bus bus : busResult.RecordSet.Record) {
                            foundBusMarker = busMap.get(bus.BusID);
                            if (foundBusMarker != null) {
                                updateBusMarker(bus, foundBusMarker);
                            } else {
                                addBusMarker(bus); // Found new bus
                            }
                        }
                    }
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
            }
        });
        api.trackers(new Callback<TrackerResult>() {
            @Override
            public void success(TrackerResult trackerResult, Response response) {
                if (trackers == null) {
                    trackers = trackerResult.features;
                    // Add new markers to map
                    for (Tracker tracker : trackers) {
                        addTrackerMarker(tracker);
                    }
                } else { // Trackers and markers already exists, move positions
                    Marker foundTrackerMarker;
                    for (Tracker tracker : trackerResult.features) {
                        foundTrackerMarker = busMap.get(tracker.properties.BusID);
                        if (foundTrackerMarker != null) {
                            updateTrackerMarker(tracker, foundTrackerMarker);
                        } else {
                            addTrackerMarker(tracker); // Found new tracker
                        }
                    }
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
            }
        });
        api.trains(new Callback<TrainResult>() {
            @Override
            public void success(TrainResult trainResult, Response response) {
                if (trains == null) {
                    trains = trainResult.RecordSet.Record;
                    // Add new markers to map
                    for (Train train : trains) {
                        addTrainMarker(train);
                    }
                } else { // Trains and markers already exists, move positions
                    Marker foundTrainMarker;
                    for (Train train : trainResult.RecordSet.Record) {
                        foundTrainMarker = trainMap.get(train.TrainID);
                        if (foundTrainMarker != null) {
                            updateTrainMarker(train, foundTrainMarker);
                        } else {
                            addTrainMarker(train); // Found new train
                        }
                    }
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
            }
        });
    }

    private void addBusMarker(Bus bus) {
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(bus.Latitude, bus.Longitude))
                .title(bus.TripHeadsign)
                .snippet("Route: " + bus.RouteID + " " + bus.ServiceDirection +
                        " (Bus: " + bus.BusID + ") " + bus.LocationUpdated)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        busMap.put(bus.BusID, marker);
    }

    private void updateBusMarker(Bus bus, Marker marker) {
        marker.setPosition(new LatLng(bus.Latitude, bus.Longitude));
        marker.setTitle(bus.TripHeadsign);
        marker.setSnippet("Route: " + bus.RouteID + " " + bus.ServiceDirection +
                " (Bus: " + bus.BusID + ") " + bus.LocationUpdated);
    }

    private void addTrackerMarker(Tracker tracker) {
        TrackerProperties prop = tracker.properties;
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(prop.lat, prop.lon))
                .title("GPS Tracker Bus " + prop.BusID)
                .snippet("Speed: " + prop.speed + " mph, Updated: " + prop.bustime)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        trackerMap.put(prop.BusID, marker);
    }

    private void updateTrackerMarker(Tracker tracker, Marker marker) {
        TrackerProperties prop = tracker.properties;
        marker.setPosition(new LatLng(prop.lat, prop.lon));
        marker.setTitle("GPS Tracker Bus " + prop.BusID);
        marker.setSnippet("Speed: " + prop.speed + " mph, Updated: " + prop.bustime);
    }

    private void addTrainMarker(Train train) {
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(train.Latitude, train.Longitude))
                .title(train.LineID + " " + train.TrainID + " " + train.Service)
                .snippet("Location Updated: " + train.LocationUpdated)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        trainMap.put(train.TrainID, marker);
    }

    private void updateTrainMarker(Train train, Marker marker) {
        marker.setPosition(new LatLng(train.Latitude, train.Longitude));
        marker.setTitle(train.LineID + " " + train.TrainID + " " + train.Service);
        marker.setSnippet("Location Updated: " + train.LocationUpdated);
    }

    private void requestTrainRoutes() {
        api.trainRoutes(new Callback<TrainRouteResult>() {
            @Override
            public void success(TrainRouteResult trainRouteResult, Response response) {

                List<TrainRouteResult.TrainRoutePosition> greenLine = new ArrayList<TrainRouteResult.TrainRoutePosition>();
                List<TrainRouteResult.TrainRoutePosition> orangeLine = new ArrayList<TrainRouteResult.TrainRoutePosition>();

                for (TrainRouteResult.TrainRoutePosition trainRoutePosition : trainRouteResult.RecordSet.Record) {
                    switch (trainRoutePosition.LineID) {
                        case "GRN":
                            greenLine.add(trainRoutePosition);
                            break;
                        default:
                            orangeLine.add(trainRoutePosition);
                            break;
                    }
                }

                addMetroRailRouteColors(greenLine, Color.GREEN);
                addMetroRailRouteColors(orangeLine, Color.rgb(255,102,0)); // Red-range
            }
            @Override
            public void failure(RetrofitError retrofitError) {}
        });
    }

    private void addMetroRailRouteColors(List<TrainRouteResult.TrainRoutePosition> positions, int color) {
        PolylineOptions line = new PolylineOptions();
        line.width(10);
        line.color(color);

        for (TrainRouteResult.TrainRoutePosition trainRoutePosition : positions) {
            line.add(new LatLng(trainRoutePosition.Latitude, trainRoutePosition.Longitude));
        }

        mMap.addPolyline(line);
    }
}

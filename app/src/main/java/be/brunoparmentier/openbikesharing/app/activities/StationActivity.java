/*
 * Copyright (c) 2014-2015 Bruno Parmentier. This file is part of OpenBikeSharing.
 *
 * OpenBikeSharing is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenBikeSharing is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenBikeSharing.  If not, see <http://www.gnu.org/licenses/>.
 */

package be.brunoparmentier.openbikesharing.app.activities;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import be.brunoparmentier.openbikesharing.app.R;
import be.brunoparmentier.openbikesharing.app.db.StationsDataSource;
import be.brunoparmentier.openbikesharing.app.models.BikeStatus;
import be.brunoparmentier.openbikesharing.app.models.Station;
import be.brunoparmentier.openbikesharing.app.models.StationStatus;
import be.brunoparmentier.openbikesharing.app.models.TraccarAttribute;
import be.brunoparmentier.openbikesharing.app.models.TraccarBikeStatus;
import be.brunoparmentier.openbikesharing.app.parsers.TraccarAttributeParser;
import be.brunoparmentier.openbikesharing.app.widgets.StationsListAppWidgetProvider;

import static be.brunoparmentier.openbikesharing.app.fragments.SettingsFragment.PREF_KEY_USER_LOGIN;

public class StationActivity extends Activity {
    private static final String TAG = StationActivity.class.getSimpleName();
    private static final String PREF_KEY_MAP_LAYER = "pref_map_layer";
    private static final String KEY_STATION = "station";
    private static final String MAP_LAYER_MAPNIK = "mapnik";
    private static final String MAP_LAYER_CYCLEMAP = "cyclemap";
    private static final String MAP_LAYER_OSMPUBLICTRANSPORT = "osmpublictransport";
    private static final String MAP_LAYER_MAPQUESTOSM = "mapquestosm";

    private SharedPreferences settings;
    private Station station;
    private MapView map;
    private IMapController mapController;
    private MenuItem favStar;
    private MenuItem reserveBikeMenu;
    private StationsDataSource stationsDataSource;
    private GetBikeStatusTask getBikeStatusTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_station);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        stationsDataSource = new StationsDataSource(this);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        station = (Station) getIntent().getSerializableExtra(KEY_STATION);

        map = (MapView) findViewById(R.id.mapView);
        final GeoPoint stationLocation = new GeoPoint((int) (station.getLatitude() * 1000000),
                (int) (station.getLongitude() * 1000000));

        mapController = map.getController();
        mapController.setZoom(16);

        /* map tile source */
        String mapLayer = settings.getString(PREF_KEY_MAP_LAYER, "");
        switch (mapLayer) {
            case MAP_LAYER_MAPNIK:
                map.setTileSource(TileSourceFactory.MAPNIK);
                break;
            case MAP_LAYER_CYCLEMAP:
                map.setTileSource(TileSourceFactory.CYCLEMAP);
                break;
            case MAP_LAYER_OSMPUBLICTRANSPORT:
                map.setTileSource(TileSourceFactory.PUBLIC_TRANSPORT);
                break;
            case MAP_LAYER_MAPQUESTOSM:
                map.setTileSource(TileSourceFactory.MAPQUESTOSM);
                break;
            default:
                map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
                break;
        }

        map.setMultiTouchControls(true);

        /* Station marker */
        Marker marker = new Marker(map);
        marker.setPosition(stationLocation);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker, MapView mapView) {
                return false;
            }
        });

        /* Marker icon */
        int emptySlots = station.getEmptySlots();
        int freeBikes = station.getFreeBikes();

        if ((emptySlots == 0 && freeBikes == 0) || station.getStatus() == StationStatus.CLOSED) {
            marker.setIcon(getResources().getDrawable(R.drawable.ic_station_marker_unavailable));
        } else {
            double ratio = (double) freeBikes / (double) (freeBikes + emptySlots);
            if (freeBikes == 0) {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_station_marker0));
            } else if (freeBikes >= 1 && ratio <= 0.3) {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_station_marker25));
            } else if (ratio > 0.3 && ratio < 0.7) {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_station_marker50));
            } else if (ratio >= 0.7 && emptySlots >= 1) {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_station_marker75));
            } else if (emptySlots == 0 || emptySlots == -1) {
                marker.setIcon(getResources().getDrawable(R.drawable.ic_station_marker100));
            }
        }

        map.getOverlays().add(marker);

        TextView stationName = (TextView) findViewById(R.id.stationName);
        TextView stationEmptySlots = (TextView) findViewById(R.id.stationEmptySlots);
        TextView stationFreeBikes = (TextView) findViewById(R.id.stationFreeBikes);

        stationName.setText(station.getName());
        setLastUpdateText(station.getLastUpdate());
        stationFreeBikes.setText(String.valueOf(station.getFreeBikes()));
        if (station.getEmptySlots() == -1) {
            ImageView stationEmptySlotsLogo = (ImageView) findViewById(R.id.stationEmptySlotsLogo);
            stationEmptySlots.setVisibility(View.GONE);
            stationEmptySlotsLogo.setVisibility(View.GONE);
        } else {
            stationEmptySlots.setText(String.valueOf(station.getEmptySlots()));
        }

        if (station.getAddress() != null) {
            TextView stationAddress = (TextView) findViewById(R.id.stationAddress);
            stationAddress.setText(station.getAddress());
            stationAddress.setVisibility(View.VISIBLE);
        }

        /* extra info on station */
        Boolean isBankingStation = station.isBanking();
        Boolean isBonusStation = station.isBonus();
        StationStatus stationStatus = station.getStatus();

        if (isBankingStation != null) {
            ImageView stationBanking = (ImageView) findViewById(R.id.stationBanking);
            stationBanking.setVisibility(View.VISIBLE);
            if (isBankingStation) {
                stationBanking.setImageDrawable(getResources().getDrawable(R.drawable.ic_banking_on));
                stationBanking.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.cards_accepted),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        if (isBonusStation != null) {
            ImageView stationBonus = (ImageView) findViewById(R.id.stationBonus);
            stationBonus.setVisibility(View.VISIBLE);
            if (isBonusStation) {
                stationBonus.setImageDrawable(getResources().getDrawable(R.drawable.ic_bonus_on));
                stationBonus.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.is_bonus_station),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        if ((stationStatus != null) && stationStatus == StationStatus.CLOSED) {
            stationName.setPaintFlags(stationName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        mapController.setCenter(stationLocation);
        getBikeStatusTask = new GetBikeStatusTask();
        getBikeStatusTask.execute(station.getId());
    }

    private void setLastUpdateText(String rawLastUpdateISO8601) {
        long timeDifferenceInSeconds;
        TextView stationLastUpdate = (TextView) findViewById(R.id.stationLastUpdate);
        SimpleDateFormat timestampFormatISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        timestampFormatISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            long lastUpdate = timestampFormatISO8601.parse(rawLastUpdateISO8601).getTime();
            long currentDateTime = System.currentTimeMillis();
            timeDifferenceInSeconds = (currentDateTime - lastUpdate) / 1000;

            if (timeDifferenceInSeconds < 60) {
                stationLastUpdate.setText(getString(R.string.updated_just_now));
            } else if (timeDifferenceInSeconds >= 60 && timeDifferenceInSeconds < 3600) {
                int minutes = (int) timeDifferenceInSeconds / 60;
                stationLastUpdate.setText(getResources().getQuantityString(R.plurals.updated_minutes_ago,
                        minutes, minutes));
            } else if (timeDifferenceInSeconds >= 3600 && timeDifferenceInSeconds < 86400) {
                int hours = (int) timeDifferenceInSeconds / 3600;
                stationLastUpdate.setText(getResources().getQuantityString(R.plurals.updated_hours_ago,
                        hours, hours));
            } else if (timeDifferenceInSeconds >= 86400) {
                int days = (int) timeDifferenceInSeconds / 86400;
                stationLastUpdate.setText(getResources().getQuantityString(R.plurals.updated_days_ago,
                        days, days));
            }

            stationLastUpdate.setTypeface(null, Typeface.ITALIC);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.station, menu);

        favStar = menu.findItem(R.id.action_favorite);
        if (isFavorite()) {
            favStar.setIcon(R.drawable.ic_menu_favorite);
        } else {
            favStar.setIcon(R.drawable.ic_menu_favorite_outline);
        }
        reserveBikeMenu = menu.findItem(R.id.action_order);
        updateReserveBikeMenuIcon();
        return true;
    }

    private void updateReserveBikeMenuIcon() {
        switch (station.getBikeStatus()) {
            case AVAILABLE:
                reserveBikeMenu.setIcon(android.R.drawable.ic_menu_add);
                break;
            case ON_TRIP:
                reserveBikeMenu.setIcon(android.R.drawable.ic_media_play);
                break;
            case RESERVED_BY_ME:
                reserveBikeMenu.setIcon(android.R.drawable.ic_delete);
                break;
            case RESERVED_BY_OTHER:
                reserveBikeMenu.setIcon(android.R.drawable.button_onoff_indicator_off);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_directions:
                Intent intent = new Intent(Intent.ACTION_VIEW, getStationLocationUri());
                PackageManager packageManager = getPackageManager();
                List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
                boolean isIntentSafe = activities.size() > 0;
                if (isIntentSafe) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, getString(R.string.no_nav_application), Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.action_favorite:
                setFavorite(!isFavorite());
                return true;
            case R.id.action_order:
                Log.i(TAG, "current bike status is " + station.getBikeStatus());
                orderBike();
                return true;
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void orderBike() {
        if (isLoggedIn()) {
            switch (station.getBikeStatus()) {
                case AVAILABLE:
                    new ReserveABikeTask().execute(station.getId());
                    break;
                case RESERVED_BY_ME:
                    new StartTripTask().execute(station.getId());
                    break;
                case ON_TRIP:
                    new EndTripTask().execute(station.getId());
                    break;
                case RESERVED_BY_OTHER:
                    Toast.makeText(this, R.string.error_bike_used_by_other, Toast.LENGTH_SHORT).show();
                    break;
            }
        } else {
            Toast.makeText(this, R.string.login_before_booking, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isLoggedIn() {
        String userLogin = getUserLogin();
        return userLogin != null && !userLogin.trim().isEmpty();
    }

    private String getUserLogin() {
        String userLogin = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(PREF_KEY_USER_LOGIN, "");
        return userLogin;
    }

    private Uri getStationLocationUri() {
        return Uri.parse("geo:" + station.getLatitude() + "," + station.getLongitude());
    }

    private boolean isFavorite() {
        return stationsDataSource.isFavoriteStation(station.getId());
    }

    private void setFavorite(boolean favorite) {
        if (favorite) {
            stationsDataSource.addFavoriteStation(station.getId());
            favStar.setIcon(R.drawable.ic_menu_favorite);
            Toast.makeText(StationActivity.this,
                    getString(R.string.station_added_to_favorites), Toast.LENGTH_SHORT).show();
        } else {
            stationsDataSource.removeFavoriteStation(station.getId());
            favStar.setIcon(R.drawable.ic_menu_favorite_outline);
            Toast.makeText(StationActivity.this,
                    getString(R.string.stations_removed_from_favorites), Toast.LENGTH_SHORT).show();
        }

        /* Refresh widget with new favorite */
        Intent refreshWidgetIntent = new Intent(getApplicationContext(),
                StationsListAppWidgetProvider.class);
        refreshWidgetIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        refreshWidgetIntent.putExtra(StationsListAppWidgetProvider.EXTRA_REFRESH_LIST_ONLY, true);
        sendBroadcast(refreshWidgetIntent);
    }

    private void updateBikeStatus(List<TraccarAttribute> attributeList) {
        TraccarBikeStatus traccarBikeStatus = null;
        String userId = null;
        for (TraccarAttribute attribute : attributeList) {
            if (attribute.getAlias().equals("BikeStatus") && attribute.getAttribute() != null) {
                String temp[] = attribute.getAttribute().split(" ");
                traccarBikeStatus = TraccarBikeStatus.valueOf(temp[0]);
                if (temp.length > 1) {
                    userId = temp[1];
                }
            }
        }
        if (traccarBikeStatus != null) {
            switch (traccarBikeStatus) {
                case AVAILABLE:
                    station.setBikeStatus(BikeStatus.AVAILABLE);
                    break;
                case ON_TRIP:
                    if (userId != null && userId.equals(getUserLogin()) && isLoggedIn()) {
                        station.setBikeStatus(BikeStatus.ON_TRIP);
                    } else {
                        station.setBikeStatus(BikeStatus.RESERVED_BY_OTHER);
                    }
                    break;
                case RESERVED:
                    if (userId != null && userId.equals(getUserLogin()) && isLoggedIn()) {
                        station.setBikeStatus(BikeStatus.RESERVED_BY_ME);
                    } else {
                        station.setBikeStatus(BikeStatus.RESERVED_BY_OTHER);
                    }
                    break;
            }
        } else {
            station.setBikeStatus(BikeStatus.AVAILABLE);
        }
        updateReserveBikeMenuIcon();
    }


    private class GetBikeStatusTask extends AsyncTask<String, Void, String> {
        private static final String ATTRIBUTES_URL = "http://track.kinet.is/api/attributes/aliases?deviceId=";
        private Exception error;

        @Override
        protected String doInBackground(String... bikeIds) {
            if (bikeIds[0].isEmpty()) {
                finish();
            }
            try {
                StringBuilder response = new StringBuilder();

                URL url = new URL(ATTRIBUTES_URL + bikeIds[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                String userCredentials = "fajarmf@gmail.com:aP*_M\\dQ6S*-6/66";
                String basicAuth = "Basic " + new String(Base64.encode(userCredentials.getBytes(), 0));
                conn.setRequestProperty ("Authorization", basicAuth);
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String strLine;
                    while ((strLine = input.readLine()) != null) {
                        response.append(strLine);
                    }
                    input.close();
                }
                return response.toString();
            } catch (IOException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            if (error != null) {
                Log.d(TAG, error.getMessage());
                Toast.makeText(getApplicationContext(),
                        getApplicationContext().getResources().getString(R.string.connection_error),
                        Toast.LENGTH_SHORT).show();
            } else {
                Log.i(TAG, "bike status data: " + s);
                try {
                    TraccarAttributeParser parser = new TraccarAttributeParser(s, TraccarAttributeParser.Type.LIST);
                    updateBikeStatus(parser.getAttributeList());
                } catch (ParseException e) {
                    Log.e(TAG, e.getMessage());
                    Toast.makeText(StationActivity.this,
                            R.string.json_error, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private class ReserveABikeTask extends AsyncTask<String, Void, String> {
        private static final String ATTRIBUTES_URL = "http://track.kinet.is/api/attributes/aliases";
        private Exception error;

        @Override
        protected void onPreExecute() {
            Toast.makeText(StationActivity.this, "Reserving ...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(String... bikeIds) {
            if (bikeIds[0].isEmpty()) {
                finish();
            }
            try {
                URL url = new URL(ATTRIBUTES_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                String userCredentials = "fajarmf@gmail.com:aP*_M\\dQ6S*-6/66";
                String basicAuth = "Basic " + new String(Base64.encode(userCredentials.getBytes(), 0));
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestProperty ("Authorization", basicAuth);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestMethod("POST");

                JSONObject reservedPayload = new JSONObject();
                reservedPayload.put("alias", "BikeStatus");
                reservedPayload.put("attribute", TraccarBikeStatus.RESERVED.name() + " " + getUserLogin());
                reservedPayload.put("deviceId", bikeIds[0]);

                OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                wr.write(reservedPayload.toString());
                wr.flush();

                StringBuilder response = new StringBuilder();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String strLine;
                    while ((strLine = input.readLine()) != null) {
                        response.append(strLine);
                    }
                    input.close();
                }
                return response.toString();
            } catch (IOException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            } catch (JSONException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            if (error != null) {
                Log.d(TAG, error.getMessage());
                Toast.makeText(getApplicationContext(),
                        getApplicationContext().getResources().getString(R.string.connection_error),
                        Toast.LENGTH_SHORT).show();
            } else {
                Log.i(TAG, "bike status data: " + s);
                try {
                    TraccarAttributeParser parser = new TraccarAttributeParser(s, TraccarAttributeParser.Type.SINGLE);
                    updateBikeStatus(parser.getAttributeList());
                } catch (ParseException e) {
                    Log.e(TAG, e.getMessage());
                    Toast.makeText(StationActivity.this,
                            R.string.json_error, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private class StartTripTask extends AsyncTask<String, Void, String> {
        private static final String ATTRIBUTES_URL = "http://track.kinet.is/api/attributes/aliases";
        private Exception error;

        @Override
        protected void onPreExecute() {
            Toast.makeText(StationActivity.this, "Starting Trip ...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(String... bikeIds) {
            if (bikeIds[0].isEmpty()) {
                finish();
            }
            try {
                URL url = new URL(ATTRIBUTES_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                String userCredentials = "fajarmf@gmail.com:aP*_M\\dQ6S*-6/66";
                String basicAuth = "Basic " + new String(Base64.encode(userCredentials.getBytes(), 0));
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestProperty ("Authorization", basicAuth);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestMethod("POST");

                JSONObject reservedPayload = new JSONObject();
                reservedPayload.put("alias", "BikeStatus");
                reservedPayload.put("attribute", TraccarBikeStatus.ON_TRIP.name() + " " + getUserLogin());
                reservedPayload.put("deviceId", bikeIds[0]);

                OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                wr.write(reservedPayload.toString());
                wr.flush();

                StringBuilder response = new StringBuilder();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String strLine;
                    while ((strLine = input.readLine()) != null) {
                        response.append(strLine);
                    }
                    input.close();
                }
                return response.toString();
            } catch (IOException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            } catch (JSONException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            if (error != null) {
                Log.d(TAG, error.getMessage());
                Toast.makeText(getApplicationContext(),
                        getApplicationContext().getResources().getString(R.string.connection_error),
                        Toast.LENGTH_SHORT).show();
            } else {
                Log.i(TAG, "bike status data: " + s);
                try {
                    TraccarAttributeParser parser = new TraccarAttributeParser(s, TraccarAttributeParser.Type.SINGLE);
                    updateBikeStatus(parser.getAttributeList());
                    Toast.makeText(StationActivity.this, "Bike status is now: " + station.getBikeStatus(), Toast.LENGTH_LONG).show();
                } catch (ParseException e) {
                    Log.e(TAG, e.getMessage());
                    Toast.makeText(StationActivity.this,
                            R.string.json_error, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private class EndTripTask extends AsyncTask<String, Void, String> {
        private static final String ATTRIBUTES_URL = "http://track.kinet.is/api/attributes/aliases";
        private Exception error;

        @Override
        protected void onPreExecute() {
            Toast.makeText(StationActivity.this, "Ending trip...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected String doInBackground(String... bikeIds) {
            if (bikeIds[0].isEmpty()) {
                finish();
            }
            try {
                HttpURLConnection conn = getTraccarConnection();

                JSONObject reservedPayload = new JSONObject();
                reservedPayload.put("alias", "BikeStatus");
                reservedPayload.put("attribute", TraccarBikeStatus.AVAILABLE.name() + " " + getUserLogin());
                reservedPayload.put("deviceId", bikeIds[0]);

                OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                wr.write(reservedPayload.toString());
                wr.flush();

                StringBuilder response = new StringBuilder();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String strLine;
                    while ((strLine = input.readLine()) != null) {
                        response.append(strLine);
                    }
                    input.close();
                }
                return response.toString();
            } catch (IOException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            } catch (JSONException e) {
                error = e;
                Log.d(TAG, e.getMessage());
                return e.getMessage();
            }
        }

        @NonNull
        private HttpURLConnection getTraccarConnection() throws IOException {
            URL url = new URL(ATTRIBUTES_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            String userCredentials = "fajarmf@gmail.com:aP*_M\\dQ6S*-6/66";
            String basicAuth = "Basic " + new String(Base64.encode(userCredentials.getBytes(), 0));
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty ("Authorization", basicAuth);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestMethod("POST");
            return conn;
        }

        @Override
        protected void onPostExecute(String s) {
            if (error != null) {
                Log.d(TAG, error.getMessage());
                Toast.makeText(getApplicationContext(),
                        getApplicationContext().getResources().getString(R.string.connection_error),
                        Toast.LENGTH_SHORT).show();
            } else {
                Log.i(TAG, "bike status data: " + s);
                try {
                    TraccarAttributeParser parser = new TraccarAttributeParser(s, TraccarAttributeParser.Type.SINGLE);
                    updateBikeStatus(parser.getAttributeList());
                    Toast.makeText(StationActivity.this, "Bike status is now: " + station.getBikeStatus(), Toast.LENGTH_LONG).show();
                } catch (ParseException e) {
                    Log.e(TAG, e.getMessage());
                    Toast.makeText(StationActivity.this,
                            R.string.json_error, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}

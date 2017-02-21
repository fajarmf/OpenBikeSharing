/*
 * Copyright (c) 2017 Bruno Parmentier. This file is part of OpenBikeSharing.
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

package be.brunoparmentier.openbikesharing.app.parsers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;

import be.brunoparmentier.openbikesharing.app.models.BikeNetwork;
import be.brunoparmentier.openbikesharing.app.models.BikeNetworkLocation;
import be.brunoparmentier.openbikesharing.app.models.Station;
import be.brunoparmentier.openbikesharing.app.models.StationStatus;

/**
 * Parse information on a bike network.
 */
public class TraccarPositionParser {
    private BikeNetwork bikeNetwork;

    public TraccarPositionParser(String toParse, boolean stripIdFromStationName) throws ParseException {
        ArrayList<Station> stations = new ArrayList<>();

        try {
//            JSONObject jsonObject = new JSONObject(toParse);
//            JSONObject rawNetwork = jsonObject.getJSONObject("network");

            /* network name & id */
            String networkId = "kinetis-bike";
            String networkName = "Kinetis";
            String networkCompany = "Kinetis";

            /* network location */
            BikeNetworkLocation networkLocation;
            {
//                JSONObject rawLocation = rawNetwork.getJSONObject("location");

                double latitude = -6.38334066;
                double longitude = 106.83880397;
                String city = "Jakarta";
                String country = "Indonesia";

                networkLocation = new BikeNetworkLocation(latitude, longitude, city, country);
            }

            /* stations list */
            {
                JSONArray positions = new JSONArray(toParse);

                for (int i = 0; i < positions.length(); i++) {
                    JSONObject rawStation = positions.getJSONObject(i);

                    String id = rawStation.getString("deviceId");
                    String name = "kinetis-"+id;
                    if (stripIdFromStationName) name = name.replaceAll("^[0-9 ]*- *", "");
                    String lastUpdate = rawStation.getString("serverTime");
                    double latitude = rawStation.getDouble("latitude");
                    double longitude = rawStation.getDouble("longitude");
                    int freeBikes = 1;
                    int emptySlots;
                    if (!rawStation.isNull("empty_slots")) {
                        emptySlots = rawStation.getInt("empty_slots");
                    } else {
                        emptySlots = -1;
                    }

                    Station station = new Station(id, name, lastUpdate, latitude, longitude,
                            freeBikes, emptySlots);

                    /* address */
                    if (rawStation.has("address")) {
                        station.setAddress(rawStation.getString("address"));
                    }

                    stations.add(station);
                }
            }

            bikeNetwork = new BikeNetwork(networkId, networkName, networkCompany, networkLocation, stations);
        } catch (JSONException e) {
            throw new ParseException("Error parsing JSON", 0);
        }
    }

    public BikeNetwork getNetwork() {
        return bikeNetwork;
    }

}

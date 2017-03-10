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
import java.util.List;

import be.brunoparmentier.openbikesharing.app.models.BikeNetwork;
import be.brunoparmentier.openbikesharing.app.models.BikeNetworkLocation;
import be.brunoparmentier.openbikesharing.app.models.Station;
import be.brunoparmentier.openbikesharing.app.models.TraccarAttribute;

/**
 * Parse information on a bike network.
 */
public class TraccarAttributeParser {
    private final List<TraccarAttribute> attributeList;

    public TraccarAttributeParser(String toParse) throws ParseException {
        attributeList = new ArrayList<>();

        try {
            JSONArray attributes = new JSONArray(toParse);

            for (int i = 0 ; i < attributes.length() ; i ++) {
                JSONObject rawAttribute = attributes.getJSONObject(i);

                String id = rawAttribute.getString("id");
                int deviceId = rawAttribute.getInt("deviceId");
                String attribute = rawAttribute.getString("attribute");
                String alias = rawAttribute.getString("alias");
                attributeList.add(new TraccarAttribute(id, deviceId, attribute, alias));
            }
        } catch (JSONException e) {
            throw new ParseException("Error parsing JSON", 0);
        }
    }

    public List<TraccarAttribute> getAttributeList() {
        return attributeList;
    }
}

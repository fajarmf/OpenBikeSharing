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

package be.brunoparmentier.openbikesharing.app.models;

/**
 * Created by idos on 10/03/17.
 */

public class TraccarAttribute {
    private final String id;
    private final int deviceId;
    private final String attribute;
    private final String alias;

    public TraccarAttribute(String id, int deviceId, String attribute, String alias) {
        this.id = id;
        this.deviceId = deviceId;
        this.attribute = attribute;
        this.alias = alias;
    }

    public String getId() {
        return id;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getAttribute() {
        return attribute;
    }

    public String getAlias() {
        return alias;
    }
}

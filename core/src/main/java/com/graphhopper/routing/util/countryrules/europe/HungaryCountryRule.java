/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util.countryrules.europe;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;

/**
 * Defines the default rules for Hungarian roads
 *
 * @author Thomas Butz
 */
public class HungaryCountryRule implements CountryRule {

    @Override
    public RoadAccess getAccess(ReaderWay readerWay, TransportationMode transportationMode, RoadAccess currentRoadAccess) {
        // Pedestrian traffic and bicycles are not restricted
		if (transportationMode == TransportationMode.FOOT || transportationMode == TransportationMode.BIKE) {
            return currentRoadAccess;
        }

        if (currentRoadAccess != RoadAccess.YES) {
            return currentRoadAccess;
        }

        RoadClass roadClass = RoadClass.find(readerWay.getTag("highway", ""));
        if (roadClass == RoadClass.LIVING_STREET) {
            return RoadAccess.DESTINATION;
        }

        return RoadAccess.YES;
    }

}

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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.WayAccess;
import com.graphhopper.util.PMap;

import java.util.*;

import static com.graphhopper.routing.ev.RouteNetwork.*;
import static com.graphhopper.routing.util.PriorityCode.UNCHANGED;
import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction;

public class FootAccessParser extends AbstractAccessParser implements TagParser {

    final Set<String> allowedHighwayTags = new HashSet<>();
    protected HashSet<String> sidewalkValues = new HashSet<>(5);
    protected Map<RouteNetwork, Integer> routeMap = new HashMap<>();

    public FootAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key("foot")));
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected FootAccessParser(BooleanEncodedValue accessEnc) {
        super(accessEnc, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.FOOT));

        sidewalkValues.add("yes");
        sidewalkValues.add("both");
        sidewalkValues.add("left");
        sidewalkValues.add("right");

        barriers.add("fence");

        allowedHighwayTags.add("footway");
        allowedHighwayTags.add("path");
        allowedHighwayTags.add("steps");
        allowedHighwayTags.add("pedestrian");
        allowedHighwayTags.add("living_street");
        allowedHighwayTags.add("track");
        allowedHighwayTags.add("residential");
        allowedHighwayTags.add("service");
        allowedHighwayTags.add("platform");
        allowedHighwayTags.add("trunk");
        allowedHighwayTags.add("trunk_link");
        allowedHighwayTags.add("primary");
        allowedHighwayTags.add("primary_link");
        allowedHighwayTags.add("secondary");
        allowedHighwayTags.add("secondary_link");
        allowedHighwayTags.add("tertiary");
        allowedHighwayTags.add("tertiary_link");
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");
        allowedHighwayTags.add("bridleway");

        routeMap.put(INTERNATIONAL, UNCHANGED.getValue());
        routeMap.put(NATIONAL, UNCHANGED.getValue());
        routeMap.put(REGIONAL, UNCHANGED.getValue());
        routeMap.put(LOCAL, UNCHANGED.getValue());
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    public WayAccess getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            WayAccess acceptPotentially = WayAccess.CAN_SKIP;

            if (FerrySpeedCalculator.isFerry(way)) {
                String footTag = way.getTag("foot");
                if (footTag == null || allowedValues.contains(footTag))
                    acceptPotentially = WayAccess.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                acceptPotentially = WayAccess.WAY;

            if (way.hasTag("man_made", "pier"))
                acceptPotentially = WayAccess.WAY;

            if (!acceptPotentially.canSkip()) {
                if (way.hasTag(restrictionKeys, restrictedValues))
                    return WayAccess.CAN_SKIP;
                return acceptPotentially;
            }

            return WayAccess.CAN_SKIP;
        }

        // via_ferrata is too dangerous, see #1326
        if ("via_ferrata".equals(highwayValue))
            return WayAccess.CAN_SKIP;

        int firstIndex = way.getFirstIndex(restrictionKeys);
        if (firstIndex >= 0) {
            String firstValue = way.getTag(restrictionKeys.get(firstIndex), "");
            String[] restrict = firstValue.split(";");
            // if any of the values allows access then return early (regardless of the order)
            for (String value : restrict) {
                if (allowedValues.contains(value))
                    return WayAccess.WAY;
            }
            for (String value : restrict) {
                if (restrictedValues.contains(value) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, allowedValues))
                    return WayAccess.CAN_SKIP;
            }
        }

        if (way.hasTag("sidewalk", sidewalkValues))
            return WayAccess.WAY;

        if (!allowedHighwayTags.contains(highwayValue))
            return WayAccess.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return WayAccess.CAN_SKIP;

        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return WayAccess.CAN_SKIP;

        return WayAccess.WAY;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way) {
        WayAccess access = getAccess(way);
        if (access.canSkip())
            return;

        if (way.hasTag("oneway:foot", ONEWAYS) || way.hasTag("foot:backward") || way.hasTag("foot:forward")
                || way.hasTag("oneway", ONEWAYS) && way.hasTag("highway", "steps") // outdated mapping style
        ) {
            boolean reverse = way.hasTag("oneway:foot", "-1") || way.hasTag("foot:backward", "yes") || way.hasTag("foot:forward", "no");
            accessEnc.setBool(reverse, edgeId, edgeIntAccess, true);
        } else {
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        }

        if (way.hasTag("gh:barrier_edge")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", Collections.emptyList());
            handleBarrierEdge(edgeId, edgeIntAccess, nodeTags.get(0));
        }
    }
}

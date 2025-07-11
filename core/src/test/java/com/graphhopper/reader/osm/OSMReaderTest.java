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
package com.graphhopper.reader.osm;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperTest;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.ElevationProvider;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.routing.util.parsers.CountryParser;
import com.graphhopper.routing.util.parsers.OSMBikeNetworkTagParser;
import com.graphhopper.routing.util.parsers.OSMMtbNetworkTagParser;
import com.graphhopper.routing.util.parsers.OSMRoadAccessParser;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.graphhopper.routing.util.TransportationMode.CAR;
import static com.graphhopper.util.GHUtility.readCountries;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the OSMReader with the normal helper initialized.
 *
 * @author Peter Karich
 */
public class OSMReaderTest {
    private final String file1 = "test-osm.xml";
    private final String file2 = "test-osm2.xml";
    private final String file3 = "test-osm3.xml";
    private final String file4 = "test-osm4.xml";
    private final String fileBarriers = "test-barriers.xml";
    private final String dir = "./target/tmp/test-db";
    private BooleanEncodedValue carAccessEnc;
    private DecimalEncodedValue carSpeedEnc;
    private BooleanEncodedValue footAccessEnc;
    private EdgeExplorer carOutExplorer;
    private EdgeExplorer carAllExplorer;

    @BeforeEach
    public void setUp() {
        new File(dir).mkdirs();
    }

    @AfterEach
    public void tearDown() {
        Helper.removeDir(new File(dir));
    }

    @Test
    public void testMain() {
        GraphHopper hopper = new GraphHopperFacade(file1).importOrLoad();
        BaseGraph graph = hopper.getBaseGraph();
        StorableProperties properties = hopper.getProperties();

        assertNotNull(properties.get("datareader.import.date"));
        assertNotEquals("", properties.get("datareader.import.date"));

        assertEquals("2013-01-02T01:10:14Z", properties.get("datareader.data.date"));

        assertEquals(4, graph.getNodes());
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 49);
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals("street 123, B 122", iter.getName());
        assertEquals(n50, iter.getAdjNode());
        AbstractGraphStorageTester.assertPList(Helper.createPointList(51.25, 9.43), iter.fetchWayGeometry(FetchMode.PILLAR_ONLY));
        assertTrue(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93147, iter.getDistance(), 1);

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);

        assertTrue(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));
        assertFalse(iter.next());

        // get third added location id=30
        iter = carOutExplorer.setBaseNode(n30);
        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n20, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);

        NodeAccess na = graph.getNodeAccess();
        assertEquals(9.4, na.getLon(findID(hopper.getLocationIndex(), 51.2, 9.4)), 1e-3);
        assertEquals(10, na.getLon(findID(hopper.getLocationIndex(), 49, 10)), 1e-3);
        assertEquals(51.249, na.getLat(findID(hopper.getLocationIndex(), 51.2492152, 9.4317166)), 1e-3);

        // node 40 is on the way between 30 and 50 => 9.0
        assertEquals(9, na.getLon(findID(hopper.getLocationIndex(), 51.25, 9.43)), 1e-3);
    }

    protected int findID(LocationIndex index, double lat, double lon) {
        return index.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
    }

    @Test
    public void testOneWay() {
        GraphHopper hopper = new GraphHopperFacade(file2)
                .setMinNetworkSize(0)
                .importOrLoad();
        BaseGraph graph = hopper.getBaseGraph();
        StorableProperties properties = hopper.getProperties();

        assertEquals("2014-01-02T01:10:14Z", properties.get("datareader.data.date"));

        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52.0);
        int n22 = AbstractGraphStorageTester.getIdOf(graph, 52.133);
        int n23 = AbstractGraphStorageTester.getIdOf(graph, 52.144);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);

        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(n10)));
        assertEquals(2, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());

        iter = carAllExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n23, iter.getAdjNode());
        assertTrue(iter.get(carAccessEnc));
        assertFalse(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals(n22, iter.getAdjNode());
        assertFalse(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertFalse(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertTrue(iter.get(carAccessEnc));
        assertFalse(iter.getReverse(carAccessEnc));

        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertFalse(iter.get(carAccessEnc));
        assertTrue(iter.getReverse(carAccessEnc));
    }

    @Test
    public void testFerry() {
        GraphHopper hopper = new GraphHopperFacade(file2) {
            @Override
            public void cleanUp() {
            }
        }.importOrLoad();
        Graph graph = hopper.getBaseGraph();

        int n40 = AbstractGraphStorageTester.getIdOf(graph, 54.0);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55.0);
        assertEquals(GHUtility.asSet(n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n50)));

        // no duration is given => slow speed only!
        int n80 = AbstractGraphStorageTester.getIdOf(graph, 54.1);
        EdgeIterator iter = carOutExplorer.setBaseNode(n80);
        iter.next();
        assertEquals(6, iter.get(carSpeedEnc), 1e-1);

        // duration 01:10 is given => more precise speed calculation!
        // ~111km (from 54.0,10.1 to 55.0,10.2) in duration=70 minutes => 95km/h => / 1.4 => 68km/h
        iter = carOutExplorer.setBaseNode(n40);
        iter.next();
        assertEquals(62, iter.get(carSpeedEnc), 1e-1);
    }

    @Test
    public void testMaxSpeed() {
        GraphHopper hopper = new GraphHopperFacade(file2) {
            @Override
            public void cleanUp() {
            }
        }.importOrLoad();
        Graph graph = hopper.getBaseGraph();

        int n60 = AbstractGraphStorageTester.getIdOf(graph, 56.0);
        EdgeIterator iter = carOutExplorer.setBaseNode(n60);
        iter.next();
        assertEquals(36, iter.get(carSpeedEnc), 1e-1);
    }

    @Test
    public void testWayReferencesNotExistingAdjNode_issue19() {
        GraphHopper hopper = new GraphHopperFacade(file4).importOrLoad();
        Graph graph = hopper.getBaseGraph();

        assertEquals(2, graph.getNodes());
        // the missing node is ignored, but the separated nodes are still connected
        assertEquals(1, graph.getEdges());
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);

        assertEquals(GHUtility.asSet(n30), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
    }

    @Test
    public void testDoNotRejectEdgeIfFirstNodeIsMissing_issue2221() {
        GraphHopper hopper = new GraphHopperFacade("test-osm9.xml").setSortGraph(false).importOrLoad();
        BaseGraph graph = hopper.getBaseGraph();
        assertEquals(2, graph.getNodes());
        assertEquals(1, graph.getEdges());
        AllEdgesIterator iter = graph.getAllEdges();
        iter.next();
        assertEquals(0, iter.getBaseNode());
        assertEquals(1, iter.getAdjNode());
        assertEquals(51.21, graph.getNodeAccess().getLat(0), 1.e-3);
        assertEquals(9.41, graph.getNodeAccess().getLon(0), 1.e-3);
        assertEquals(51.22, graph.getNodeAccess().getLat(1), 1.e-3);
        assertEquals(9.42, graph.getNodeAccess().getLon(1), 1.e-3);
        assertEquals(DistanceCalcEarth.DIST_EARTH.calcDistance(iter.fetchWayGeometry(FetchMode.ALL)), iter.getDistance(), 1.e-3);
        assertEquals(1312.1, iter.getDistance(), 1.e-1);
        assertEquals(1312.1, DistanceCalcEarth.DIST_EARTH.calcDistance(iter.fetchWayGeometry(FetchMode.ALL)), 1.e-1);
        assertFalse(iter.next());
    }

    @Test
    public void test_edgeDistanceWhenFirstNodeIsMissing_issue2221() {
        GraphHopper hopper = new GraphHopperFacade("test-osm10.xml").setSortGraph(false).importOrLoad();
        BaseGraph graph = hopper.getBaseGraph();
        assertEquals(3, graph.getNodes());
        assertEquals(3, graph.getEdges());
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            assertEquals(DistanceCalcEarth.DIST_EARTH.calcDistance(iter.fetchWayGeometry(FetchMode.ALL)), iter.getDistance(), 1.e-3);
        }
        assertEquals(35.612, graph.getEdgeIteratorState(0, Integer.MIN_VALUE).getDistance(), 1.e-3);
        assertEquals(75.256, graph.getEdgeIteratorState(1, Integer.MIN_VALUE).getDistance(), 1.e-3);
        assertEquals(143.332, graph.getEdgeIteratorState(2, Integer.MIN_VALUE).getDistance(), 1.e-3);
    }

    @Test
    public void testFoot() {
        GraphHopper hopper = new GraphHopperFacade(file3)
                .setMinNetworkSize(0)
                .importOrLoad();
        Graph graph = hopper.getBaseGraph();

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 11.1);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 12);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 11.2);
        int n40 = AbstractGraphStorageTester.getIdOf(graph, 11.3);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 10);

        assertEquals(GHUtility.asSet(n20, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n30, n40), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n20)));

        EdgeExplorer footOutExplorer = graph.createEdgeExplorer(AccessFilter.outEdges(footAccessEnc));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n20)));
    }

    @Test
    public void testNegativeIds() {
        String fileNegIds = "test-osm-negative-ids.xml";
        Exception exception = assertThrows(RuntimeException.class, () -> {
            new GraphHopperFacade(fileNegIds).importOrLoad();
        });
        assertTrue(exception.getCause().getMessage().contains("Invalid OSM NODE Id: -10;"));
    }

    @Test
    public void testBarriers() {
        GraphHopper hopper = new GraphHopperFacade(fileBarriers).
                setSortGraph(false).
                setMinNetworkSize(0).
                importOrLoad();

        Graph graph = hopper.getBaseGraph();
        // we ignore the barrier at node 50, but not the one at node 20
        assertEquals(7, graph.getNodes());
        assertEquals(7, graph.getEdges());

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 53);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55);

        // separate id
        int new20 = 4;
        assertNotEquals(n20, new20);
        NodeAccess na = graph.getNodeAccess();
        assertEquals(na.getLat(n20), na.getLat(new20), 1e-5);
        assertEquals(na.getLon(n20), na.getLon(new20), 1e-5);

        assertEquals(n20, findID(hopper.getLocationIndex(), 52, 9.4));

        assertEquals(GHUtility.asSet(n20, n30), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(new20, n10, n50), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertFalse(iter.next());

        iter = carOutExplorer.setBaseNode(new20);
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void testBarrierBetweenWays() {
        GraphHopper hopper = new GraphHopperFacade("test-barriers2.xml").
                setMinNetworkSize(0).
                importOrLoad();

        Graph graph = hopper.getBaseGraph();
        // there are seven ways, but there should also be six barrier edges
        // we first split the loop way into two parts, and then we split the barrier node => 3 edges total
        assertEquals(7 + 6, graph.getEdges());
        int loops = 0;
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            // there are 'loop' edges, but only between different nodes
            assertNotEquals(iter.getBaseNode(), iter.getAdjNode());
            if (graph.getNodeAccess().getLat(iter.getBaseNode()) == graph.getNodeAccess().getLat(iter.getAdjNode()))
                loops++;
        }
        assertEquals(5, loops);
    }

    @Test
    public void testFords() {
        GraphHopper hopper = new GraphHopper();
        hopper.setEncodedValuesString("car_access|block_fords=true,car_average_speed");
        hopper.setOSMFile(getClass().getResource("test-barriers3.xml").getFile()).
                setGraphHopperLocation(dir).
                setProfiles(TestProfiles.accessAndSpeed("car")).
                setMinNetworkSize(0).
                importOrLoad();
        Graph graph = hopper.getBaseGraph();
        // our way is split into five edges, because there are two ford nodes
        assertEquals(5, graph.getEdges());
        BooleanEncodedValue accessEnc = hopper.getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("car"));
        int blocked = 0;
        int notBlocked = 0;
        AllEdgesIterator edge = graph.getAllEdges();
        while (edge.next()) {
            if (!edge.get(accessEnc))
                blocked++;
            else
                notBlocked++;
        }
        // two blocked edges and three accessible edges
        assertEquals(2, blocked);
        assertEquals(3, notBlocked);
    }

    @Test
    public void avoidsLoopEdges_1525() {
        // loops in OSM should be avoided by adding additional tower node (see #1525, #1531)
        //     C - D
        //      \ /
        //   A - B - E
        GraphHopper hopper = new GraphHopperFacade("test-avoid-loops.xml").importOrLoad();
        checkLoop(hopper);
    }

    void checkLoop(GraphHopper hopper) {
        BaseGraph graph = hopper.getBaseGraph();

        // A, B, E and one of C or D should be tower nodes, in any case C and D should not be collapsed entirely
        // into a loop edge from B to B.
        assertEquals(4, graph.getNodes());
        // two edges going to A and E and two edges going to C or D
        AllEdgesIterator iter = graph.getAllEdges();
        assertEquals(4, iter.length());
        while (iter.next()) {
            assertTrue(iter.getAdjNode() != iter.getBaseNode(), "found a loop");
        }
        int nodeB = AbstractGraphStorageTester.getIdOf(graph, 12);
        assertTrue(nodeB > -1, "could not find OSM node B");
        assertEquals(4, GHUtility.count(graph.createEdgeExplorer().setBaseNode(nodeB)));
    }

    @Test
    public void avoidsLoopEdgesIdenticalLatLon_1533() {
        checkLoop(new GraphHopperFacade("test-avoid-loops2.xml").importOrLoad());
    }

    @Test
    public void avoidsLoopEdgesIdenticalNodeIds_1533() {
        // BDCBB
        checkLoop(new GraphHopperFacade("test-avoid-loops3.xml").importOrLoad());
        // BBCDB
        checkLoop(new GraphHopperFacade("test-avoid-loops4.xml").importOrLoad());
    }

    @Test
    public void testBarriersOnTowerNodes() {
        GraphHopper hopper = new GraphHopperFacade(fileBarriers).
                setMinNetworkSize(0).
                importOrLoad();
        Graph graph = hopper.getBaseGraph();
        // we ignore the barrier at node 50
        // 10-20-30 produces three edges: 10-20, 20-2x, 2x-30, the second one is a barrier edge
        assertEquals(7, graph.getNodes());
        assertEquals(7, graph.getEdges());

        int n60 = AbstractGraphStorageTester.getIdOf(graph, 56);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 53);
        int n80 = AbstractGraphStorageTester.getIdOf(graph, 58);
        assertEquals(GHUtility.asSet(n50), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n60)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n60);
        assertTrue(iter.next());
        assertEquals(n50, iter.getAdjNode());
        assertFalse(iter.next());

        assertTrue(GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)).contains(n50));
        assertEquals(GHUtility.asSet(n30, n80, n60), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n50)));
    }

    @Test
    public void testBikeAndMtbRelation() {
        EnumEncodedValue<RouteNetwork> bikeNetworkEnc = new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class);
        EnumEncodedValue<RouteNetwork> mtbNetworkEnc = new EnumEncodedValue<>(MtbNetwork.KEY, RouteNetwork.class);
        EncodingManager manager = new EncodingManager.Builder().add(mtbNetworkEnc).add(bikeNetworkEnc).build();
        OSMParsers osmParsers = new OSMParsers()
                .addRelationTagParser(relConf -> new OSMBikeNetworkTagParser(bikeNetworkEnc, relConf))
                .addRelationTagParser(relConf -> new OSMMtbNetworkTagParser(mtbNetworkEnc, relConf));

        ReaderRelation osmRel = new ReaderRelation(1);
        osmRel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 1, ""));
        osmRel.add(new ReaderRelation.Member(ReaderElement.Type.WAY, 2, ""));

        // this is pretty ugly: the bike network parser writes to the edge flags we pass into it, but at a location we
        // don't know, so we need to get the internal enc to read the flags below
        EnumEncodedValue<RouteNetwork> transformEnc = ((OSMBikeNetworkTagParser) osmParsers.getRelationTagParsers().get(0)).getTransformerRouteRelEnc();

        osmRel.setTag("route", "bicycle");
        osmRel.setTag("network", "lcn");

        // bikeNetworkEnc and MtbNetworkEnc share the same instance of the relFlags
        IntsRef relFlags = manager.createRelationFlags();
        IntsRefEdgeIntAccess intAccess = new IntsRefEdgeIntAccess(relFlags);
        int edgeId = 0;
        osmParsers.handleRelationTags(osmRel, relFlags);
        assertEquals(RouteNetwork.LOCAL, transformEnc.getEnum(false, edgeId, intAccess));

        // unchanged network
        IntsRef before = IntsRef.deepCopyOf(relFlags);
        osmParsers.handleRelationTags(osmRel, relFlags);
        assertEquals(before, relFlags);
        assertEquals(RouteNetwork.LOCAL, transformEnc.getEnum(false, edgeId, intAccess));
        assertEquals(RouteNetwork.LOCAL, transformEnc.getEnum(false, edgeId, intAccess));

        // overwrite network
        osmRel.setTag("network", "ncn");
        osmParsers.handleRelationTags(osmRel, relFlags);
        assertEquals(RouteNetwork.NATIONAL, transformEnc.getEnum(false, edgeId, intAccess));
        assertNotEquals(before, relFlags);

        // The further tests below are for an edge which is part of a bike and another mountainbike relation
        osmRel.clearTags();

        // this is pretty ugly: the mtb network parser writes to the edge flags we pass into it, but at a location we
        // don't know, so we need to get the internal enc to read the flags below
        transformEnc = ((OSMMtbNetworkTagParser) osmParsers.getRelationTagParsers().get(1)).getTransformerRouteRelEnc();

        osmRel.setTag("route", "mtb");
        osmRel.setTag("network", "lcn");

        osmParsers.handleRelationTags(osmRel, relFlags);
        assertEquals(RouteNetwork.LOCAL, transformEnc.getEnum(false, edgeId, intAccess));

        // unchanged network
        osmParsers.handleRelationTags(osmRel, relFlags);
        assertEquals(RouteNetwork.LOCAL, transformEnc.getEnum(false, edgeId, intAccess));
        assertEquals(RouteNetwork.LOCAL, transformEnc.getEnum(false, edgeId, intAccess));

        // overwrite network
        osmRel.setTag("network", "ncn");
        osmParsers.handleRelationTags(osmRel, relFlags);
        assertEquals(RouteNetwork.NATIONAL, transformEnc.getEnum(false, edgeId, intAccess));
        assertNotEquals(before, relFlags);
    }

    @Test
    public void testTurnRestrictionsFromXML() {
        String fileTurnRestrictions = "test-restrictions.xml";
        GraphHopper hopper = new GraphHopperFacade(fileTurnRestrictions, "").
                importOrLoad();

        Graph graph = hopper.getBaseGraph();
        assertEquals(15, graph.getNodes());
        TurnCostStorage tcStorage = graph.getTurnCostStorage();
        assertNotNull(tcStorage);

        int n1 = AbstractGraphStorageTester.getIdOf(graph, 50, 10);
        int n2 = AbstractGraphStorageTester.getIdOf(graph, 52, 10);
        int n3 = AbstractGraphStorageTester.getIdOf(graph, 52, 11);
        int n4 = AbstractGraphStorageTester.getIdOf(graph, 52, 12);
        int n5 = AbstractGraphStorageTester.getIdOf(graph, 50, 12);
        int n6 = AbstractGraphStorageTester.getIdOf(graph, 51, 11);
        int n8 = AbstractGraphStorageTester.getIdOf(graph, 54, 11);

        int edge1_6 = GHUtility.getEdge(graph, n1, n6).getEdge();
        int edge2_3 = GHUtility.getEdge(graph, n2, n3).getEdge();
        int edge3_4 = GHUtility.getEdge(graph, n3, n4).getEdge();
        int edge3_8 = GHUtility.getEdge(graph, n3, n8).getEdge();

        int edge3_2 = GHUtility.getEdge(graph, n3, n2).getEdge();
        int edge4_3 = GHUtility.getEdge(graph, n4, n3).getEdge();
        int edge8_3 = GHUtility.getEdge(graph, n8, n3).getEdge();

        // (2-3)->(3-4) only_straight_on = (2-3)->(3-8) restricted
        // (4-3)->(3-8) no_right_turn = (4-3)->(3-8) restricted
        // (2-3)->(3-8) no_entry = (2-3)->(3-8) restricted
        BooleanEncodedValue carTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("car"));
        assertTrue(tcStorage.get(carTCEnc, edge2_3, n3, edge3_8));
        assertTrue(tcStorage.get(carTCEnc, edge4_3, n3, edge3_8));
        assertTrue(tcStorage.get(carTCEnc, edge2_3, n3, edge3_8));
        assertFalse(tcStorage.get(carTCEnc, edge2_3, n3, edge3_4));
        assertFalse(tcStorage.get(carTCEnc, edge2_3, n3, edge3_2));
        assertFalse(tcStorage.get(carTCEnc, edge2_3, n3, edge3_4));
        assertFalse(tcStorage.get(carTCEnc, edge4_3, n3, edge3_2));
        assertFalse(tcStorage.get(carTCEnc, edge8_3, n3, edge3_2));

        // u-turn restriction for (6-1)->(1-6) but not for (1-6)->(6-1)
        assertTrue(tcStorage.get(carTCEnc, edge1_6, n1, edge1_6));
        assertFalse(tcStorage.get(carTCEnc, edge1_6, n6, edge1_6));

        int edge4_5 = GHUtility.getEdge(graph, n4, n5).getEdge();
        int edge5_6 = GHUtility.getEdge(graph, n5, n6).getEdge();
        int edge5_1 = GHUtility.getEdge(graph, n5, n1).getEdge();

        // (4-5)->(5-1) right_turn_only = (4-5)->(5-6) restricted
        assertFalse(tcStorage.get(carTCEnc, edge4_5, n5, edge5_6));
        assertTrue(tcStorage.get(carTCEnc, edge4_5, n5, edge5_1));

        BooleanEncodedValue bikeTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("bike"));
        assertFalse(tcStorage.get(bikeTCEnc, edge4_5, n5, edge5_6));

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 40, 10);
        int n11 = AbstractGraphStorageTester.getIdOf(graph, 40, 11);
        int n14 = AbstractGraphStorageTester.getIdOf(graph, 39, 11);

        int edge10_11 = GHUtility.getEdge(graph, n10, n11).getEdge();
        int edge11_14 = GHUtility.getEdge(graph, n11, n14).getEdge();

        assertFalse(tcStorage.get(carTCEnc, edge11_14, n11, edge10_11));
        assertFalse(tcStorage.get(bikeTCEnc, edge11_14, n11, edge10_11));

        // the turn is restricted for car even though it turns into a one-way, but we treat this separately now
        assertTrue(tcStorage.get(carTCEnc, edge10_11, n11, edge11_14));
        assertTrue(tcStorage.get(bikeTCEnc, edge10_11, n11, edge11_14));
    }

    @Test
    public void testTurnRestrictionsViaHgvTransportationMode() {
        String fileTurnRestrictions = "test-restrictions.xml";
        GraphHopper hopper = new GraphHopperFacade(fileTurnRestrictions, "").
                importOrLoad();

        Graph graph = hopper.getBaseGraph();
        assertEquals(15, graph.getNodes());
        TurnCostStorage tcStorage = graph.getTurnCostStorage();
        assertNotNull(tcStorage);

        int n3 = AbstractGraphStorageTester.getIdOf(graph, 52, 11);
        int n8 = AbstractGraphStorageTester.getIdOf(graph, 54, 11);
        int n9 = AbstractGraphStorageTester.getIdOf(graph, 54, 10);

        int edge9_3 = GHUtility.getEdge(graph, n9, n3).getEdge();
        int edge3_8 = GHUtility.getEdge(graph, n3, n8).getEdge();

        BooleanEncodedValue carTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("car"));
        BooleanEncodedValue truckTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("truck"));

        assertFalse(tcStorage.get(carTCEnc, edge9_3, n3, edge3_8));
        assertTrue(tcStorage.get(truckTCEnc, edge9_3, n3, edge3_8));
    }

    @Test
    public void testRoadAttributes() {
        String fileRoadAttributes = "test-road-attributes.xml";
        GraphHopper hopper = new GraphHopperFacade(fileRoadAttributes);
        hopper.importOrLoad();

        DecimalEncodedValue widthEnc = hopper.getEncodingManager().getDecimalEncodedValue(MaxWidth.KEY);
        DecimalEncodedValue heightEnc = hopper.getEncodingManager().getDecimalEncodedValue(MaxHeight.KEY);
        DecimalEncodedValue weightEnc = hopper.getEncodingManager().getDecimalEncodedValue(MaxWeight.KEY);

        Graph graph = hopper.getBaseGraph();
        assertEquals(5, graph.getNodes());

        int na = AbstractGraphStorageTester.getIdOf(graph, 11.1, 50);
        int nb = AbstractGraphStorageTester.getIdOf(graph, 12, 51);
        int nc = AbstractGraphStorageTester.getIdOf(graph, 11.2, 52);
        int nd = AbstractGraphStorageTester.getIdOf(graph, 11.3, 51);
        int ne = AbstractGraphStorageTester.getIdOf(graph, 10, 51);

        EdgeIteratorState edge_ab = GHUtility.getEdge(graph, na, nb);
        EdgeIteratorState edge_ad = GHUtility.getEdge(graph, na, nd);
        EdgeIteratorState edge_ae = GHUtility.getEdge(graph, na, ne);
        EdgeIteratorState edge_bc = GHUtility.getEdge(graph, nb, nc);
        EdgeIteratorState edge_bd = GHUtility.getEdge(graph, nb, nd);
        EdgeIteratorState edge_cd = GHUtility.getEdge(graph, nc, nd);
        EdgeIteratorState edge_ce = GHUtility.getEdge(graph, nc, ne);
        EdgeIteratorState edge_de = GHUtility.getEdge(graph, nd, ne);

        assertEquals(4.0, edge_ab.get(heightEnc), 1e-5);
        assertEquals(2.5, edge_ab.get(widthEnc), 1e-5);
        assertEquals(4.4, edge_ab.get(weightEnc), 1e-5);

        assertEquals(4.0, edge_bc.get(heightEnc), 1e-5);
        assertEquals(2.5, edge_bc.get(widthEnc), 1e-5);
        assertEquals(4.4, edge_bc.get(weightEnc), 1e-5);

        assertEquals(4.4, edge_ad.get(heightEnc), 1e-5);
        assertEquals(3.5, edge_ad.get(widthEnc), 1e-5);
        assertEquals(17.5, edge_ad.get(weightEnc), 1e-5);

        assertEquals(4.4, edge_cd.get(heightEnc), 1e-5);
        assertEquals(3.5, edge_cd.get(widthEnc), 1e-5);
        assertEquals(17.5, edge_cd.get(weightEnc), 1e-5);
    }

    @Test
    public void testReadEleFromDataProvider() {
        GraphHopper hopper = new GraphHopperFacade("test-osm5.xml");
        // get N10E046.hgt.zip
        ElevationProvider provider = new SRTMProvider(GraphHopperTest.DIR);
        hopper.setElevationProvider(provider);
        hopper.importOrLoad();

        Graph graph = hopper.getBaseGraph();
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 49.501);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 49.5011);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 49.5001);

        EdgeIteratorState edge = GHUtility.getEdge(graph, n50, n30);
        assertEquals(Helper.createPointList3D(49.5001, 11.501, 426, 49.5002, 11.5015, 441, 49.5011, 11.502, 410.0),
                edge.fetchWayGeometry(FetchMode.ALL));

        edge = GHUtility.getEdge(graph, n10, n50);
        assertEquals(Helper.createPointList3D(49.501, 11.5001, 383.0, 49.5001, 11.501, 426.0),
                edge.fetchWayGeometry(FetchMode.ALL));
    }

    /**
     * Tests the combination of different turn cost flags by different encoders.
     */
    @Test
    public void testTurnFlagCombination() {
        GraphHopper hopper = new GraphHopper();
        hopper.setEncodedValuesString("car_average_speed,car_access,bike_access,bike_average_speed,bike_priority");
        hopper.setOSMFile(getClass().getResource("test-multi-profile-turn-restrictions.xml").getFile()).
                setGraphHopperLocation(dir).
                setProfiles(
                        TestProfiles.accessAndSpeed("bike").setTurnCostsConfig(new TurnCostsConfig(List.of("bicycle"))),
                        TestProfiles.accessAndSpeed("car").setTurnCostsConfig(new TurnCostsConfig(List.of("motorcar", "motor_vehicle"))),
                        TestProfiles.accessAndSpeed("truck", "car").setTurnCostsConfig(new TurnCostsConfig(List.of("hgv", "motor_vehicle")))
                ).
                setSortGraph(false).
                importOrLoad();
        EncodingManager manager = hopper.getEncodingManager();
        BooleanEncodedValue carTCEnc = manager.getTurnBooleanEncodedValue(TurnRestriction.key("car"));
        BooleanEncodedValue truckTCEnc = manager.getTurnBooleanEncodedValue(TurnRestriction.key("truck"));
        BooleanEncodedValue bikeTCEnc = manager.getTurnBooleanEncodedValue(TurnRestriction.key("bike"));

        Graph graph = hopper.getBaseGraph();
        TurnCostStorage tcStorage = graph.getTurnCostStorage();

        int edge1 = GHUtility.getEdge(graph, 1, 0).getEdge();
        int edge2 = GHUtility.getEdge(graph, 0, 2).getEdge();
        // the 2nd entry provides turn flags for bike only
        assertTrue(tcStorage.get(carTCEnc, edge1, 0, edge2));
        assertTrue(tcStorage.get(truckTCEnc, edge1, 0, edge2));
        assertFalse(tcStorage.get(bikeTCEnc, edge1, 0, edge2));

        edge1 = GHUtility.getEdge(graph, 2, 0).getEdge();
        edge2 = GHUtility.getEdge(graph, 0, 3).getEdge();
        // the first entry provides turn flags for car and foot only
        assertFalse(tcStorage.get(carTCEnc, edge1, 0, edge2));
        assertFalse(tcStorage.get(truckTCEnc, edge1, 0, edge2));
        assertTrue(tcStorage.get(bikeTCEnc, edge1, 0, edge2));

        edge1 = GHUtility.getEdge(graph, 3, 0).getEdge();
        edge2 = GHUtility.getEdge(graph, 0, 2).getEdge();
        assertFalse(tcStorage.get(carTCEnc, edge1, 0, edge2));
        assertTrue(tcStorage.get(truckTCEnc, edge1, 0, edge2));
        assertFalse(tcStorage.get(bikeTCEnc, edge1, 0, edge2));
    }

    @Test
    public void testConditionalTurnRestriction() {
        String fileConditionalTurnRestrictions = "test-conditional-turn-restrictions.xml";
        GraphHopper hopper = new GraphHopperFacade(fileConditionalTurnRestrictions, "").
                setMinNetworkSize(0).
                importOrLoad();

        Graph graph = hopper.getBaseGraph();
        assertEquals(8, graph.getNodes());
        TurnCostStorage tcStorage = graph.getTurnCostStorage();
        assertNotNull(tcStorage);

        int n1 = AbstractGraphStorageTester.getIdOf(graph, 50, 10);
        int n2 = AbstractGraphStorageTester.getIdOf(graph, 52, 10);
        int n3 = AbstractGraphStorageTester.getIdOf(graph, 52, 11);
        int n4 = AbstractGraphStorageTester.getIdOf(graph, 52, 12);
        int n5 = AbstractGraphStorageTester.getIdOf(graph, 50, 12);
        int n6 = AbstractGraphStorageTester.getIdOf(graph, 51, 11);
        int n8 = AbstractGraphStorageTester.getIdOf(graph, 54, 11);

        int edge1_6 = GHUtility.getEdge(graph, n1, n6).getEdge();
        int edge2_3 = GHUtility.getEdge(graph, n2, n3).getEdge();
        int edge3_4 = GHUtility.getEdge(graph, n3, n4).getEdge();
        int edge3_8 = GHUtility.getEdge(graph, n3, n8).getEdge();

        int edge3_2 = GHUtility.getEdge(graph, n3, n2).getEdge();
        int edge4_3 = GHUtility.getEdge(graph, n4, n3).getEdge();
        int edge8_3 = GHUtility.getEdge(graph, n8, n3).getEdge();

        int edge4_5 = GHUtility.getEdge(graph, n4, n5).getEdge();
        int edge5_6 = GHUtility.getEdge(graph, n5, n6).getEdge();
        int edge5_1 = GHUtility.getEdge(graph, n5, n1).getEdge();

        // (2-3)->(3-4) only_straight_on except bicycle = (2-3)->(3-8) restricted for car
        // (4-3)->(3-8) no_right_turn dedicated to motorcar = (4-3)->(3-8) restricted for car

        BooleanEncodedValue carTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("car"));
        assertTrue(tcStorage.get(carTCEnc, edge2_3, n3, edge3_8));
        assertTrue(tcStorage.get(carTCEnc, edge4_3, n3, edge3_8));
        assertFalse(tcStorage.get(carTCEnc, edge2_3, n3, edge3_4));
        assertFalse(tcStorage.get(carTCEnc, edge2_3, n3, edge3_2));
        assertFalse(tcStorage.get(carTCEnc, edge2_3, n3, edge3_4));
        assertFalse(tcStorage.get(carTCEnc, edge4_3, n3, edge3_2));
        assertFalse(tcStorage.get(carTCEnc, edge8_3, n3, edge3_2));

        BooleanEncodedValue bikeTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("bike"));
        assertFalse(tcStorage.get(bikeTCEnc, edge2_3, n3, edge3_8));
        assertFalse(tcStorage.get(bikeTCEnc, edge4_3, n3, edge3_8));
        assertFalse(tcStorage.get(bikeTCEnc, edge2_3, n3, edge3_4));
        assertFalse(tcStorage.get(bikeTCEnc, edge2_3, n3, edge3_2));
        assertFalse(tcStorage.get(bikeTCEnc, edge2_3, n3, edge3_4));
        assertFalse(tcStorage.get(bikeTCEnc, edge4_3, n3, edge3_2));
        assertFalse(tcStorage.get(bikeTCEnc, edge8_3, n3, edge3_2));

        // u-turn except bus;bicycle restriction for (6-1)->(1-6) but not for (1-6)->(6-1)
        assertTrue(tcStorage.get(carTCEnc, edge1_6, n1, edge1_6));
        assertFalse(tcStorage.get(carTCEnc, edge1_6, n6, edge1_6));

        assertFalse(tcStorage.get(bikeTCEnc, edge1_6, n1, edge1_6));
        assertFalse(tcStorage.get(bikeTCEnc, edge1_6, n6, edge1_6));

        // (4-5)->(5-6) right_turn_only dedicated to motorcar = (4-5)->(5-1) restricted
        assertFalse(tcStorage.get(carTCEnc, edge4_5, n5, edge5_6));
        assertTrue(tcStorage.get(carTCEnc, edge4_5, n5, edge5_1));

        assertFalse(tcStorage.get(bikeTCEnc, edge4_5, n5, edge5_6));
        assertFalse(tcStorage.get(bikeTCEnc, edge4_5, n5, edge5_1));
    }

    @Test
    public void testMultipleTurnRestrictions() {
        String fileMultipleConditionalTurnRestrictions = "test-multiple-conditional-turn-restrictions.xml";
        GraphHopper hopper = new GraphHopperFacade(fileMultipleConditionalTurnRestrictions, "").
                importOrLoad();

        Graph graph = hopper.getBaseGraph();
        assertEquals(5, graph.getNodes());
        TurnCostStorage tcStorage = graph.getTurnCostStorage();
        assertNotNull(tcStorage);

        int n1 = AbstractGraphStorageTester.getIdOf(graph, 50, 10);
        int n2 = AbstractGraphStorageTester.getIdOf(graph, 52, 10);
        int n3 = AbstractGraphStorageTester.getIdOf(graph, 52, 11);
        int n4 = AbstractGraphStorageTester.getIdOf(graph, 52, 12);
        int n5 = AbstractGraphStorageTester.getIdOf(graph, 50, 12);

        int edge1_2 = GHUtility.getEdge(graph, n1, n2).getEdge();
        int edge2_3 = GHUtility.getEdge(graph, n2, n3).getEdge();
        int edge3_4 = GHUtility.getEdge(graph, n3, n4).getEdge();
        int edge4_5 = GHUtility.getEdge(graph, n4, n5).getEdge();
        int edge5_1 = GHUtility.getEdge(graph, n5, n1).getEdge();

        BooleanEncodedValue carTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("car"));
        BooleanEncodedValue bikeTCEnc = hopper.getEncodingManager().getTurnBooleanEncodedValue(TurnRestriction.key("bike"));

        // (1-2)->(2-3) no_right_turn for motorcar and bus
        assertTrue(tcStorage.get(carTCEnc, edge1_2, n2, edge2_3));
        assertFalse(tcStorage.get(bikeTCEnc, edge1_2, n2, edge2_3));

        // (3-4)->(4-5) no_right_turn for motorcycle and motorcar
        assertTrue(tcStorage.get(carTCEnc, edge3_4, n4, edge4_5));
        assertFalse(tcStorage.get(bikeTCEnc, edge3_4, n4, edge4_5));

        // (5-1)->(1-2) no_right_turn for bus and psv except for motorcar and bicycle
        assertFalse(tcStorage.get(carTCEnc, edge4_5, n5, edge5_1));
        assertFalse(tcStorage.get(bikeTCEnc, edge4_5, n5, edge5_1));

        // (5-1)->(1-2) no_right_turn for motorcar and motorcycle except for bus and bicycle
        assertTrue(tcStorage.get(carTCEnc, edge5_1, n1, edge1_2));
        assertFalse(tcStorage.get(bikeTCEnc, edge5_1, n1, edge1_2));
    }

    @Test
    public void testPreferredLanguage() {
        GraphHopper hopper = new GraphHopperFacade(file1, "de").
                importOrLoad();
        BaseGraph graph = hopper.getBaseGraph();
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals("straße 123, B 122", iter.getName());

        hopper = new GraphHopperFacade(file1, "el").
                importOrLoad();
        graph = hopper.getBaseGraph();
        n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals("διαδρομή 666", iter.getName());
    }

    @Test
    public void testDataDateWithinPBF() {
        GraphHopper hopper = new GraphHopperFacade("test-osm6.pbf")
                .setMinNetworkSize(0)
                .importOrLoad();
        StorableProperties properties = hopper.getProperties();
        assertEquals("2014-01-02T00:10:14Z", properties.get("datareader.data.date"));
    }

    @Test
    public void testCrossBoundary_issue667() {
        GraphHopper hopper = new GraphHopperFacade("test-osm-waterway.xml").importOrLoad();
        Snap snap = hopper.getLocationIndex().findClosest(0.1, 179.5, EdgeFilter.ALL_EDGES);
        assertTrue(snap.isValid());
        assertEquals(0.1, snap.getSnappedPoint().lat, 0.1);
        assertEquals(179.5, snap.getSnappedPoint().lon, 0.1);
        assertEquals(11, snap.getClosestEdge().getDistance() / 1000, 1);

        snap = hopper.getLocationIndex().findClosest(0.1, -179.6, EdgeFilter.ALL_EDGES);
        assertTrue(snap.isValid());
        assertEquals(0.1, snap.getSnappedPoint().lat, 0.1);
        assertEquals(-179.6, snap.getSnappedPoint().lon, 0.1);
        assertEquals(112, snap.getClosestEdge().getDistance() / 1000, 1);
    }

    @Test
    public void testRoadClassInfo() {
        GraphHopper gh = new GraphHopper() {
            @Override
            protected File _getOSMFile() {
                return new File(getClass().getResource(file2).getFile());
            }
        }.setOSMFile("dummy").
                setEncodedValuesString("car_access,car_average_speed").
                setProfiles(TestProfiles.accessAndSpeed("profile", "car")).
                setMinNetworkSize(0).
                setGraphHopperLocation(dir).
                importOrLoad();

        GHResponse response = gh.route(new GHRequest(51.2492152, 9.4317166, 52.133, 9.1)
                .setProfile("profile")
                .setPathDetails(Collections.singletonList(RoadClass.KEY)));
        assertFalse(response.hasErrors(), response.getErrors().toString());
        List<PathDetail> list = response.getBest().getPathDetails().get(RoadClass.KEY);
        assertEquals(3, list.size());
        assertEquals("motorway", list.get(0).getValue());

        response = gh.route(new GHRequest(51.2492152, 9.4317166, 52.133, 9.1)
                .setProfile("profile")
                .setPathDetails(Arrays.asList(Toll.KEY, Country.KEY)));
        Throwable ex = response.getErrors().get(0);
        assertEquals("Cannot find the path details: [toll, country]", ex.getMessage());
    }

    @Test
    public void testCountries() throws IOException {
        EnumEncodedValue<RoadAccess> roadAccessEnc = RoadAccess.create();
        EncodingManager em = new EncodingManager.Builder().add(roadAccessEnc).build();
        OSMParsers osmParsers = new OSMParsers();
        osmParsers.addWayTagParser(new OSMRoadAccessParser<>(roadAccessEnc,
                OSMRoadAccessParser.toOSMRestrictions(CAR), RoadAccess::countryHook, RoadAccess::find));
        BaseGraph graph = new BaseGraph.Builder(em).create();
        OSMReader reader = new OSMReader(graph, osmParsers, new OSMReaderConfig());
        reader.setCountryRuleFactory(new CountryRuleFactory());
        reader.setAreaIndex(createCountryIndex());
        // there are two edges, both with highway=track, one in Berlin, one in Paris
        reader.setFile(new File(getClass().getResource("test-osm11.xml").getFile()));
        reader.readGraph();
        EdgeIteratorState edgeBerlin = graph.getEdgeIteratorState(0, Integer.MIN_VALUE);
        EdgeIteratorState edgeParis = graph.getEdgeIteratorState(1, Integer.MIN_VALUE);
        assertEquals("berlin", edgeBerlin.getName());
        assertEquals("paris", edgeParis.getName());
        // for Berlin there is GermanyCountryRule which changes RoadAccess for Tracks
        assertEquals(RoadAccess.DESTINATION, edgeBerlin.get(roadAccessEnc));
        // for Paris there is no such rule, we just get the default RoadAccess.YES
        assertEquals(RoadAccess.YES, edgeParis.get(roadAccessEnc));

        ReaderWay way = new ReaderWay(0L);
        PointList list = new PointList();
        list.add(49.214906, -2.156067);
        reader.setArtificialWayTags(list, way, 10, Collections.singletonList(new HashMap<>()));
        assertEquals("JEY", way.getTag("country", null).toString());
    }

    @Test
    public void testCurvedWayAlongBorder() throws IOException {
        // see https://discuss.graphhopper.com/t/country-of-way-is-wrong-on-road-near-border-with-curvature/6908/2
        EnumEncodedValue<Country> countryEnc = Country.create();
        EncodingManager em = EncodingManager.start()
                .add(VehicleSpeed.create("car", 5, 5, false)).add(VehicleAccess.create("car"))
                .add(countryEnc)
                .build();
        OSMParsers osmParsers = new OSMParsers()
                .addWayTagParser(new CountryParser(countryEnc));
        BaseGraph graph = new BaseGraph.Builder(em).create();
        OSMReader reader = new OSMReader(graph, osmParsers, new OSMReaderConfig());
        reader.setCountryRuleFactory(new CountryRuleFactory());
        reader.setAreaIndex(createCountryIndex());
        reader.setFile(new File(getClass().getResource("test-osm12.xml").getFile()));
        reader.readGraph();
        assertEquals(1, graph.getEdges());
        AllEdgesIterator iter = graph.getAllEdges();
        iter.next();
        assertEquals(Country.BGR, iter.get(countryEnc));
    }

    @Test
    public void testFixWayName() {
        assertEquals("B8, B12", OSMReader.fixWayName("B8;B12"));
        assertEquals("B8, B12", OSMReader.fixWayName("B8; B12"));
    }

    private AreaIndex<CustomArea> createCountryIndex() {
        return new AreaIndex<>(readCountries());
    }

    class GraphHopperFacade extends GraphHopper {
        public GraphHopperFacade(String osmFile) {
            this(osmFile, "");
        }

        public GraphHopperFacade(String osmFile, String prefLang) {
            setStoreOnFlush(false);
            setOSMFile(osmFile);
            setGraphHopperLocation(dir);
            String str = "max_width,max_height,max_weight,foot_access, foot_priority, foot_average_speed, car_access, car_average_speed, bike_access, bike_priority, bike_average_speed";
            setEncodedValuesString(str);
            setProfiles(
                    TestProfiles.accessSpeedAndPriority("foot"),
                    TestProfiles.accessAndSpeed("car").setTurnCostsConfig(new TurnCostsConfig(List.of("motorcar", "motor_vehicle"))),
                    TestProfiles.accessSpeedAndPriority("bike").setTurnCostsConfig(new TurnCostsConfig(List.of("bicycle"))),
                    TestProfiles.constantSpeed("truck", 100).setTurnCostsConfig(new TurnCostsConfig(List.of("hgv", "motor_vehicle")))
            );
            getReaderConfig().setPreferredLanguage(prefLang);
        }

        @Override
        protected void importOSM() {
            BaseGraph baseGraph = new BaseGraph.Builder(getEncodingManager()).set3D(hasElevation()).withTurnCosts(getEncodingManager().needsTurnCostsSupport()).build();
            setBaseGraph(baseGraph);
            super.importOSM();
            carAccessEnc = getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("car"));
            carSpeedEnc = getEncodingManager().getDecimalEncodedValue(VehicleSpeed.key("car"));
            carOutExplorer = getBaseGraph().createEdgeExplorer(AccessFilter.outEdges(carAccessEnc));
            carAllExplorer = getBaseGraph().createEdgeExplorer(AccessFilter.allEdges(carAccessEnc));
            footAccessEnc = getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("foot"));
        }

        @Override
        protected File _getOSMFile() {
            return new File(getClass().getResource(getOSMFile()).getFile());
        }
    }
}

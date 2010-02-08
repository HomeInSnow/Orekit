/* Copyright 2002-2010 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.tle;


import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.math.analysis.polynomials.PolynomialFunction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.errors.OrekitException;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;


public class TLESeriesTest {

    @Deprecated
    @Test(expected=OrekitException.class)
    public void testDeprecated() throws IOException, OrekitException {
        InputStream in =
            TLETest.class.getResourceAsStream("/regular-data/tle/spot-5.tle");
        TLESeries series = new TLESeries(in);
        series.loadTLEData(22076);
    }

    @Test(expected=OrekitException.class)
    public void testNoData() throws IOException, OrekitException {
        TLESeries series = new TLESeries("^inexistant\\.tle$", false);
        series.loadTLEData();
    }

    @Test(expected=OrekitException.class)
    public void testNoTopexPoseidonNumber() throws IOException, OrekitException {
        TLESeries series = new TLESeries("^spot-5\\.tle$", false);
        series.loadTLEData(22076);
    }

    @Test(expected=OrekitException.class)
    public void testNoTopexPoseidonLaunchElements() throws IOException, OrekitException {
        TLESeries series = new TLESeries("^spot-5\\.tle$", false);
        series.loadTLEData(1992, 52, "A");
    }

    @Test
    public void testAvailableSatNums() throws IOException, OrekitException {
        int[] refIds = {
            5, 4632, 6251, 8195, 9880, 9998, 11801, 14128, 16925,
            20413, 21897, 22312, 22674, 23177, 23333, 23599, 24208, 25954, 26900,
            26975, 28057, 28129, 28350, 28623, 28626, 28872, 29141, 29238, 88888};

        Utils.setDataRoot("tle/extrapolationTest-data:regular-data");
        TLESeries series = new TLESeries(".*-entry$", true);
        Set<Integer> available = series.getAvailableSatelliteNumbers();
        Assert.assertEquals(refIds.length, available.size());
        for (int ref : refIds) {
            Assert.assertTrue(available.contains(ref));
        }
    }

    @Test
    public void testSpot5Available() throws IOException, OrekitException {
        TLESeries series = new TLESeries("^spot-5\\.tle$", false);
        Set<Integer> available = series.getAvailableSatelliteNumbers();
        Assert.assertEquals(1, available.size());
        Assert.assertTrue(available.contains(27421));
    }

    @Test
    public void testSpot5WithExtraLines() throws IOException, OrekitException {
        TLESeries series = new TLESeries("^spot-5-with-extra-lines\\.tle$", true);
        series.loadTLEData(-1);
        Assert.assertEquals(27421, series.getFirst().getSatelliteNumber());
        AbsoluteDate referenceFirst =
            new AbsoluteDate(2002, 5, 4, 11, 45, 15.695136, TimeScalesFactory.getUTC());
        Assert.assertEquals(0, series.getFirstDate().durationFrom(referenceFirst), 1e-13);
        AbsoluteDate referenceLast =
            new AbsoluteDate(2002, 5, 4, 19, 10, 59.114784, TimeScalesFactory.getUTC());
        Assert.assertEquals(0, series.getLastDate().durationFrom(referenceLast), 1e-13);
    }

    @Test
    public void testPVStart() throws IOException, OrekitException {
        TLESeries series = new TLESeries("^spot-5\\.tle$", false);
        series.loadTLEData();

        AbsoluteDate t0 = new AbsoluteDate(2002, 5, 4, 11, 0, 0.0, TimeScalesFactory.getUTC());

        // this model is a rough fit on first 3 days of current tle with respect to first tle
        // there are 1500m amplitude variations around a quadratic evolution that grows up to 90km
        PolynomialFunction errorModel =
            new PolynomialFunction(new double[] { -135.98, 0.010186, 1.3115e-06 });

        TLEPropagator propagator = TLEPropagator.selectExtrapolator(series.getFirst());
        for (double dt = 0; dt < 3 * 86400; dt += 600) {
            AbsoluteDate date = new AbsoluteDate(t0, dt);
            PVCoordinates delta = new PVCoordinates(propagator.getPVCoordinates(date), series.getPVCoordinates(date));
            Assert.assertEquals(errorModel.value(dt), delta.getPosition().getNorm(), 1500.0);
        }

    }

    @Test
    public void testPVEnd() throws IOException, OrekitException {
        TLESeries series = new TLESeries("^spot-5\\.tle$", false);
        series.loadTLEData();

        AbsoluteDate t0 =
            new AbsoluteDate(2002, 6, 21, 20, 0, 0.0, TimeScalesFactory.getUTC());

        TLEPropagator propagator = TLEPropagator.selectExtrapolator(series.getLast());
        for (double dt = 3 * 86400; dt >= 0; dt -= 600) {
            AbsoluteDate date = new AbsoluteDate(t0, dt);
            PVCoordinates delta = new PVCoordinates(propagator.getPVCoordinates(date), series.getPVCoordinates(date));
            Assert.assertEquals(0, delta.getPosition().getNorm(), 660.0);
        }

    }

    @Test
    public void testSpot5() throws IOException, OrekitException {

        TLESeries series = new TLESeries("^spot-5\\.tle$", false);

        series.loadTLEData(-1);
        Assert.assertEquals(27421, series.getFirst().getSatelliteNumber());

        series.loadTLEData(27421);
        Assert.assertEquals(27421, series.getFirst().getSatelliteNumber());

        series.loadTLEData(-1, -1, null);
        Assert.assertEquals(27421, series.getFirst().getSatelliteNumber());

        series.loadTLEData(2002, -1, null);
        Assert.assertEquals(27421, series.getFirst().getSatelliteNumber());

        series.loadTLEData(2002, 21, "A");
        Assert.assertEquals(27421, series.getFirst().getSatelliteNumber());
        Assert.assertEquals(2002, series.getFirst().getLaunchYear());
        Assert.assertEquals(21, series.getFirst().getLaunchNumber());
        Assert.assertEquals("A", series.getFirst().getLaunchPiece());
        Assert.assertEquals(27421, series.getLast().getSatelliteNumber());
        Assert.assertEquals(2002, series.getLast().getLaunchYear());
        Assert.assertEquals(21, series.getLast().getLaunchNumber());
        Assert.assertEquals("A", series.getLast().getLaunchPiece());

        AbsoluteDate referenceFirst =
            new AbsoluteDate(2002, 5, 4, 11, 45, 15.695136, TimeScalesFactory.getUTC());
        Assert.assertEquals(0, series.getFirstDate().durationFrom(referenceFirst), 1e-13);
        AbsoluteDate referenceLast =
            new AbsoluteDate(2002, 6, 24, 18, 12, 44.591616001, TimeScalesFactory.getUTC());
        Assert.assertEquals(0, series.getLastDate().durationFrom(referenceLast), 1e-13);

        AbsoluteDate inside = new AbsoluteDate(2002, 06, 02, 11, 12, 15, TimeScalesFactory.getUTC());
        AbsoluteDate referenceInside =
            new AbsoluteDate(2002, 6, 2, 10, 8, 25.401, TimeScalesFactory.getUTC());
        Assert.assertEquals(0, series.getClosestTLE(inside).getDate().durationFrom(referenceInside), 1e-3);

        AbsoluteDate oneYearBefore = new AbsoluteDate(2001, 06, 02, 11, 12, 15, TimeScalesFactory.getUTC());
        Assert.assertTrue(series.getClosestTLE(oneYearBefore).getDate().equals(series.getFirstDate()));

        AbsoluteDate oneYearAfter = new AbsoluteDate(2003, 06, 02, 11, 12, 15, TimeScalesFactory.getUTC());
        Assert.assertTrue(series.getClosestTLE(oneYearAfter).getDate().equals(series.getLastDate()));

    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
    }

}
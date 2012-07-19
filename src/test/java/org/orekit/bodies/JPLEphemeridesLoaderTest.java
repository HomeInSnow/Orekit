/* Copyright 2002-2012 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
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
package org.orekit.bodies;


import java.text.ParseException;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.data.DataProvidersManager;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

public class JPLEphemeridesLoaderTest {

    @Test
    public void testConstantsJPL() throws OrekitException {
        Utils.setDataRoot("regular-data/de405-ephemerides");

        JPLEphemeridesLoader loader =
            new JPLEphemeridesLoader(JPLEphemeridesLoader.DEFAULT_DE_SUPPORTED_NAMES,
                                     JPLEphemeridesLoader.EphemerisType.SUN);
        Assert.assertEquals(149597870691.0, loader.getLoadedAstronomicalUnit(), 0.1);
        Assert.assertEquals(81.30056, loader.getLoadedEarthMoonMassRatio(), 1.0e-8);
    }

    @Test
    public void testConstantsInpop() throws OrekitException {
        Utils.setDataRoot("inpop");
        JPLEphemeridesLoader loader =
            new JPLEphemeridesLoader(JPLEphemeridesLoader.DEFAULT_INPOP_SUPPORTED_NAMES,
                                     JPLEphemeridesLoader.EphemerisType.SUN);
        Assert.assertEquals(149597870691.0, loader.getLoadedAstronomicalUnit(), 0.1);
        Assert.assertEquals(81.30057, loader.getLoadedEarthMoonMassRatio(), 1.0e-8);
    }

    @Test
    public void testGMJPL() throws OrekitException {
        Utils.setDataRoot("regular-data/de405-ephemerides");

        JPLEphemeridesLoader loader =
            new JPLEphemeridesLoader(JPLEphemeridesLoader.DEFAULT_DE_SUPPORTED_NAMES,
                                     JPLEphemeridesLoader.EphemerisType.SUN);
        Assert.assertEquals(22032.080e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.MERCURY),
                            1.0e6);
        Assert.assertEquals(324858.599e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.VENUS),
                            1.0e6);
        Assert.assertEquals(42828.314e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.MARS),
                            1.0e6);
        Assert.assertEquals(126712767.863e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.JUPITER),
                            6.0e7);
        Assert.assertEquals(37940626.063e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.SATURN),
                            2.0e6);
        Assert.assertEquals(5794549.007e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.URANUS),
                            1.0e6);
        Assert.assertEquals(6836534.064e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.NEPTUNE),
                            1.0e6);
        Assert.assertEquals(981.601e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.PLUTO),
                            1.0e6);
        Assert.assertEquals(132712440017.987e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.SUN),
                            1.0e6);
        Assert.assertEquals(4902.801e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.MOON),
                            1.0e6);
        Assert.assertEquals(403503.233e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.EARTH_MOON),
                            1.0e6);
    }

    @Test
    public void testGMInpop() throws OrekitException {

        Utils.setDataRoot("inpop");

        JPLEphemeridesLoader loader =
            new JPLEphemeridesLoader(JPLEphemeridesLoader.DEFAULT_INPOP_SUPPORTED_NAMES,
                                     JPLEphemeridesLoader.EphemerisType.SUN);
        Assert.assertEquals(22032.081e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.MERCURY),
                            1.0e6);
        Assert.assertEquals(324858.597e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.VENUS),
                            1.0e6);
        Assert.assertEquals(42828.376e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.MARS),
                            1.0e6);
        Assert.assertEquals(126712764.535e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.JUPITER),
                            6.0e7);
        Assert.assertEquals(37940585.443e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.SATURN),
                            2.0e6);
        Assert.assertEquals(5794549.099e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.URANUS),
                            1.0e6);
        Assert.assertEquals(6836527.128e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.NEPTUNE),
                            1.0e6);
        Assert.assertEquals(971.114e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.PLUTO),
                            1.0e6);
        Assert.assertEquals(132712442110.032e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.SUN),
                            1.0e6);
        Assert.assertEquals(4902.800e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.MOON),
                            1.0e6);
        Assert.assertEquals(403503.250e9,
                            loader.getLoadedGravitationalCoefficient(JPLEphemeridesLoader.EphemerisType.EARTH_MOON),
                            1.0e6);
    }

    @Test
    public void testDerivative405() throws OrekitException, ParseException {
        Utils.setDataRoot("regular-data/de405-ephemerides");
        checkDerivative(JPLEphemeridesLoader.DEFAULT_DE_SUPPORTED_NAMES,
                        new AbsoluteDate(1969, 6, 25, TimeScalesFactory.getTT()));
    }

    @Test
    public void testDerivative406() throws OrekitException, ParseException {
        Utils.setDataRoot("regular-data/de406-ephemerides");
        checkDerivative(JPLEphemeridesLoader.DEFAULT_DE_SUPPORTED_NAMES,
                        new AbsoluteDate(2964, 9, 26, TimeScalesFactory.getTT()));
    }

    @Test
    @Ignore
    public void testDerivative414() throws OrekitException, ParseException {
        Utils.setDataRoot("regular-data/de414-ephemerides");
        checkDerivative(JPLEphemeridesLoader.DEFAULT_DE_SUPPORTED_NAMES,
                        new AbsoluteDate(1950, 1, 12, TimeScalesFactory.getTT()));
    }

    @Test
    public void testEndianness() throws OrekitException, ParseException {
        Utils.setDataRoot("inpop");
        JPLEphemeridesLoader.EphemerisType type = JPLEphemeridesLoader.EphemerisType.MARS;
        JPLEphemeridesLoader loaderInpopTCBBig =
                new JPLEphemeridesLoader("^inpop.*_TCB_.*_bigendian\\.dat$", type);
        CelestialBody bodysInpopTCBBig = loaderInpopTCBBig.loadCelestialBody(CelestialBodyFactory.MARS);
        Assert.assertEquals(1.0, loaderInpopTCBBig.getLoadedConstant("TIMESC"), 1.0e-10);
        JPLEphemeridesLoader loaderInpopTCBLittle =
                new JPLEphemeridesLoader("^inpop.*_TCB_.*_littleendian\\.dat$", type);
        CelestialBody bodysInpopTCBLittle = loaderInpopTCBLittle.loadCelestialBody(CelestialBodyFactory.MARS);
        Assert.assertEquals(1.0, loaderInpopTCBLittle.getLoadedConstant("TIMESC"), 1.0e-10);
        AbsoluteDate t0 = new AbsoluteDate(1969, 7, 17, 10, 43, 23.4, TimeScalesFactory.getTT());
        Frame eme2000   = FramesFactory.getEME2000();
        for (double dt = 0; dt < 30 * Constants.JULIAN_DAY; dt += 3600) {
            AbsoluteDate date        = t0.shiftedBy(dt);
            Vector3D pInpopTCBBig    = bodysInpopTCBBig.getPVCoordinates(date, eme2000).getPosition();
            Vector3D pInpopTCBLittle = bodysInpopTCBLittle.getPVCoordinates(date, eme2000).getPosition();
            Assert.assertEquals(0.0, pInpopTCBBig.distance(pInpopTCBLittle), 1.0e-10);
        }
        for (String name : DataProvidersManager.getInstance().getLoadedDataNames()) {
            Assert.assertTrue(name.contains("inpop"));
        }
    }

    @Test
    public void testInpopvsJPL() throws OrekitException, ParseException {
        Utils.setDataRoot("regular-data:inpop");
        JPLEphemeridesLoader.EphemerisType type = JPLEphemeridesLoader.EphemerisType.MARS;
        JPLEphemeridesLoader loaderDE405 =
                new JPLEphemeridesLoader("^unxp(\\d\\d\\d\\d)\\.405$", type);
        CelestialBody bodysDE405 = loaderDE405.loadCelestialBody(CelestialBodyFactory.MARS);
        JPLEphemeridesLoader loaderInpopTDBBig =
                new JPLEphemeridesLoader("^inpop.*_TDB_.*_bigendian\\.dat$", type);
        CelestialBody bodysInpopTDBBig = loaderInpopTDBBig.loadCelestialBody(CelestialBodyFactory.MARS);
        Assert.assertEquals(0.0, loaderInpopTDBBig.getLoadedConstant("TIMESC"), 1.0e-10);
        JPLEphemeridesLoader loaderInpopTCBBig =
                new JPLEphemeridesLoader("^inpop.*_TCB_.*_bigendian\\.dat$", type);
        CelestialBody bodysInpopTCBBig = loaderInpopTCBBig.loadCelestialBody(CelestialBodyFactory.MARS);
        Assert.assertEquals(1.0, loaderInpopTCBBig.getLoadedConstant("TIMESC"), 1.0e-10);
        AbsoluteDate t0 = new AbsoluteDate(1969, 7, 17, 10, 43, 23.4, TimeScalesFactory.getTT());
        Frame eme2000   = FramesFactory.getEME2000();
        for (double dt = 0; dt < 30 * Constants.JULIAN_DAY; dt += 3600) {
            AbsoluteDate date = t0.shiftedBy(dt);
            Vector3D pDE405          = bodysDE405.getPVCoordinates(date, eme2000).getPosition();
            Vector3D pInpopTDBBig    = bodysInpopTDBBig.getPVCoordinates(date, eme2000).getPosition();
            Vector3D pInpopTCBBig    = bodysInpopTCBBig.getPVCoordinates(date, eme2000).getPosition();
            Assert.assertTrue(pDE405.distance(pInpopTDBBig) >  650.0);
            Assert.assertTrue(pDE405.distance(pInpopTDBBig) < 1050.0);
            Assert.assertTrue(pDE405.distance(pInpopTCBBig) > 1350.0);
            Assert.assertTrue(pDE405.distance(pInpopTCBBig) < 1900.0);
        }

    }

    private void checkDerivative(String supportedNames, AbsoluteDate date) throws OrekitException, ParseException {
        JPLEphemeridesLoader loader =
            new JPLEphemeridesLoader(supportedNames, JPLEphemeridesLoader.EphemerisType.MERCURY);
        CelestialBody body = loader.loadCelestialBody(CelestialBodyFactory.MERCURY);
        double h = 20;

        // eight points finite differences estimation of the velocity
        Frame eme2000 = FramesFactory.getEME2000();
        Vector3D pm4h = body.getPVCoordinates(date.shiftedBy(-4 * h), eme2000).getPosition();
        Vector3D pm3h = body.getPVCoordinates(date.shiftedBy(-3 * h), eme2000).getPosition();
        Vector3D pm2h = body.getPVCoordinates(date.shiftedBy(-2 * h), eme2000).getPosition();
        Vector3D pm1h = body.getPVCoordinates(date.shiftedBy(    -h), eme2000).getPosition();
        Vector3D pp1h = body.getPVCoordinates(date.shiftedBy(     h), eme2000).getPosition();
        Vector3D pp2h = body.getPVCoordinates(date.shiftedBy( 2 * h), eme2000).getPosition();
        Vector3D pp3h = body.getPVCoordinates(date.shiftedBy( 3 * h), eme2000).getPosition();
        Vector3D pp4h = body.getPVCoordinates(date.shiftedBy( 4 * h), eme2000).getPosition();
        Vector3D d4   = pp4h.subtract(pm4h);
        Vector3D d3   = pp3h.subtract(pm3h);
        Vector3D d2   = pp2h.subtract(pm2h);
        Vector3D d1   = pp1h.subtract(pm1h);
        double c = 1.0 / (840 * h);
        Vector3D estimatedV = new Vector3D(-3 * c, d4, 32 * c, d3, -168 * c, d2, 672 * c, d1);

        Vector3D loadedV = body.getPVCoordinates(date, eme2000).getVelocity();
        Assert.assertEquals(0, loadedV.subtract(estimatedV).getNorm(), 5.0e-11 * loadedV.getNorm());
    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
    }

}

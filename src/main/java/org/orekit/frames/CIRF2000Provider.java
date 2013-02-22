/* Copyright 2002-2013 CS Systèmes d'Information
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
package org.orekit.frames;

import java.io.InputStream;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.orekit.data.BodiesElements;
import org.orekit.data.PoissonSeries;
import org.orekit.errors.OrekitException;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

/** Celestial Intermediate Reference Frame 2000.
 * <p>This frame includes both precession and nutation effects according to
 * the new IAU-2000 model. The single model replaces the two separate models
 * used before: IAU-76 precession (Lieske) and IAU-80 theory of nutation (Wahr).
 * It <strong>must</strong> be used with the Earth Rotation Angle (REA) defined by
 * Capitaine's model and <strong>not</strong> IAU-82 sidereal time which is
 * consistent with the previous models only.</p>
 * <p>Its parent frame is the GCRF frame.<p>
 */
class CIRF2000Provider implements TransformProvider {

    /** Serializable UID. */
    private static final long serialVersionUID = -8378289692425977657L;

    // CHECKSTYLE: stop JavadocVariable check

    // lunisolar nutation elements
    private static final double F10 = FastMath.toRadians(134.96340251);
    private static final double F11 = 1717915923.217800  * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F12 =         31.879200  * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F13 =          0.051635  * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F14 =         -0.0002447 * Constants.ARC_SECONDS_TO_RADIANS;

    private static final double F20 = FastMath.toRadians(357.52910918);
    private static final double F21 = 129596581.048100   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F22 =        -0.553200   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F23 =         0.000136   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F24 =        -0.00001149 * Constants.ARC_SECONDS_TO_RADIANS;

    private static final double F30 = FastMath.toRadians(93.27209062);
    private static final double F31 = 1739527262.847800   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F32 =        -12.751200   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F33 =         -0.001037   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F34 =          0.00000417 * Constants.ARC_SECONDS_TO_RADIANS;

    private static final double F40 = FastMath.toRadians(297.85019547);
    private static final double F41 = 1602961601.209000   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F42 =         -6.370600   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F43 =          0.006593   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F44 =         -0.00003169 * Constants.ARC_SECONDS_TO_RADIANS;

    private static final double F50 = FastMath.toRadians(125.04455501);
    private static final double F51 = -6962890.543100   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F52 =        7.472200   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F53 =        0.007702   * Constants.ARC_SECONDS_TO_RADIANS;
    private static final double F54 =       -0.00005939 * Constants.ARC_SECONDS_TO_RADIANS;

    // planetary nutation elements
    private static final double F60 = 4.402608842;
    private static final double F61 = 2608.7903141574;

    private static final double F70 = 3.176146697;
    private static final double F71 = 1021.3285546211;

    private static final double F80 = 1.753470314;
    private static final double F81 = 628.3075849991;

    private static final double F90 = 6.203480913;
    private static final double F91 = 334.0612426700;

    private static final double F100 = 0.599546497;
    private static final double F101 = 52.9690962641;

    private static final double F110 = 0.874016757;
    private static final double F111 = 21.3299104960;

    private static final double F120 = 5.481293872;
    private static final double F121 = 7.4781598567;

    private static final double F130 = 5.311886287;
    private static final double F131 = 3.8133035638;

    private static final double F141 = 0.024381750;
    private static final double F142 = 0.00000538691;

    // CHECKSTYLE: resume JavadocVariable check

    /** IERS conventions (2003) resources base directory. */
    private static final String IERS_2003_BASE = "/assets/org/orekit/iers-conventions-2003/";

    /** Resources for IERS table 5.2a from IERS conventions (2003). */
    private static final String X_MODEL     = IERS_2003_BASE + "tab5.2a.txt";

    /** Resources for IERS table 5.2b from IERS conventions (2003). */
    private static final String Y_MODEL     = IERS_2003_BASE + "tab5.2b.txt";

    /** Resources for IERS table 5.2c from IERS conventions (2003). */
    private static final String S_XY2_MODEL = IERS_2003_BASE + "tab5.2c.txt";

    /** Pole position (X). */
    private final PoissonSeries xDevelopment;

    /** Pole position (Y). */
    private final PoissonSeries yDevelopment;

    /** Pole position (S + XY/2). */
    private final PoissonSeries sxy2Development;

    /** Simple constructor.
     * @exception OrekitException if the nutation model data embedded in the
     * library cannot be read.
     * @see Frame
     */
    public CIRF2000Provider()
        throws OrekitException {

        // load the nutation model
        xDevelopment    = loadModel(X_MODEL);
        yDevelopment    = loadModel(Y_MODEL);
        sxy2Development = loadModel(S_XY2_MODEL);

    }

    /** Load a series development model.
     * @param name file name of the series development
     * @return series development model
     * @exception OrekitException if table cannot be loaded
     */
    private static PoissonSeries loadModel(final String name)
        throws OrekitException {

        // get the table data
        final InputStream stream = CIRF2000Provider.class.getResourceAsStream(name);

        // nutation models are in micro arcseconds in the data files
        // we store and use them in radians
        return new PoissonSeries(stream, Constants.ARC_SECONDS_TO_RADIANS * 1.0e-6, name);

    }

    /** Get the transform from GCRF to CIRF2000 at the specified date.
     * <p>The transform considers the nutation and precession effects from IERS data.</p>
     * @param date new value of the date
     * @return transform at the specified date
     * @exception OrekitException if the nutation model data embedded in the
     * library cannot be read
     */
    public Transform getTransform(final AbsoluteDate date) throws OrekitException {

        //    offset from J2000.0 epoch
        final double t = date.durationFrom(AbsoluteDate.J2000_EPOCH);

        // offset in julian centuries
        final double tc =  t / Constants.JULIAN_CENTURY;

        final BodiesElements elements =
            new BodiesElements((((F14 * tc + F13) * tc + F12) * tc + F11) * tc + F10, // mean anomaly of the Moon
                               (((F24 * tc + F23) * tc + F22) * tc + F21) * tc + F20, // mean anomaly of the Sun
                               (((F34 * tc + F33) * tc + F32) * tc + F31) * tc + F30, // L - &Omega; where L is the mean longitude of the Moon
                               (((F44 * tc + F43) * tc + F42) * tc + F41) * tc + F40, // mean elongation of the Moon from the Sun
                               (((F54 * tc + F53) * tc + F52) * tc + F51) * tc + F50, // mean longitude of the ascending node of the Moon
                               F61  * tc +  F60, // mean Mercury longitude
                               F71  * tc +  F70, // mean Venus longitude
                               F81  * tc +  F80, // mean Earth longitude
                               F91  * tc +  F90, // mean Mars longitude
                               F101 * tc + F100, // mean Jupiter longitude
                               F111 * tc + F110, // mean Saturn longitude
                               F121 * tc + F120, // mean Uranus longitude
                               F131 * tc + F130, // mean Neptune longitude
                               (F142 * tc + F141) * tc); // general accumulated precession in longitude

        // pole position
        final double xCurrent =    xDevelopment.value(tc, elements);
        final double yCurrent =    yDevelopment.value(tc, elements);
        final double sCurrent = sxy2Development.value(tc, elements) - xCurrent * yCurrent / 2;

        // set up the bias, precession and nutation rotation
        final double x2Py2  = xCurrent * xCurrent + yCurrent * yCurrent;
        final double zP1    = 1 + FastMath.sqrt(1 - x2Py2);
        final double r      = FastMath.sqrt(x2Py2);
        final double sPe2   = 0.5 * (sCurrent + FastMath.atan2(yCurrent, xCurrent));
        final double cos    = FastMath.cos(sPe2);
        final double sin    = FastMath.sin(sPe2);
        final double xPr    = xCurrent + r;
        final double xPrCos = xPr * cos;
        final double xPrSin = xPr * sin;
        final double yCos   = yCurrent * cos;
        final double ySin   = yCurrent * sin;
        final Rotation bpn  = new Rotation(zP1 * (xPrCos + ySin), -r * (yCos + xPrSin),
                                           r * (xPrCos - ySin), zP1 * (yCos - xPrSin),
                                           true);

        return new Transform(date, bpn, Vector3D.ZERO);

    }

}
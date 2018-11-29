/* Copyright 2002-2018 CS Systèmes d'Information
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
package org.orekit.models.earth;

import org.hipparchus.Field;
import org.hipparchus.RealFieldElement;
import org.hipparchus.util.CombinatoricsUtils;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.MathArrays;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateTimeComponents;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.ParameterDriver;

/** The Global Mapping Function  model for radio techniques.
 *  This model is an empirical mapping function. It only needs the
 *  values of the station latitude, longitude, height and the
 *  date for the computations.
 *  <p>
 *  The Global Mapping Function is based on spherical harmonics up
 *  to degree and order of 9. It was developed to be consistent
 *  with the {@link ViennaOneModel Vienna1} mapping function model.
 *  </p>
 *
 *  @see Boehm, J., A.E. Niell, P. Tregoning, H. Schuh (2006),
 *       Global Mapping Functions (GMF): A new empirical mapping function based
 *       on numerical weather model data, Geoph. Res. Letters, Vol. 33, L07304,
 *       doi:10.1029/2005GL025545.
 *
 *  @see <p>Petit, G. and Luzum, B. (eds.), IERS Conventions (2010),
 *       IERS Technical Note No. 36, BKG (2010)</p>
 *
 *  @author Bryan Cazabonne
 *
 */
public class GlobalMappingFunctionModel implements MappingFunction {

    /** Serializable UID. */
    private static final long serialVersionUID = -9007141744989481150L;

    /** Geodetic site latitude, radians.*/
    private final double latitude;

    /** Geodetic site longitude, radians.*/
    private final double longitude;

    /** Build a new instance.
     * @param latitude geodetic latitude of the station, in radians
     * @param longitude geodetic latitude of the station, in radians
     */
    public GlobalMappingFunctionModel(final double latitude, final double longitude) {
        this.latitude  = latitude;
        this.longitude = longitude;
    }

    /** {@inheritDoc} */
    @Override
    public double[] mappingFactors(final double elevation, final double height,
                                   final double[] parameters, final AbsoluteDate date) {
        // Day of year computation
        final DateTimeComponents dtc = date.getComponents(TimeScalesFactory.getUTC());
        final int dofyear = dtc.getDate().getDayOfYear();

        // bh and ch constants (Boehm, J et al, 2006) | HYDROSTATIC PART
        final double bh  = 0.0029;
        final double c0h = 0.062;
        final double c10h;
        final double c11h;
        final double psi;

        if (FastMath.sin(latitude) > 0) {
            // northern hemisphere case
            c10h = 0.001;
            c11h = 0.005;
            psi  = 0.0;
        } else {
            // southern hemisphere case
            c10h = 0.002;
            c11h = 0.007;
            psi  = FastMath.PI;
        }

        final double coef = ((dofyear + 1 - 28) / 365.25) * 2 * FastMath.PI + psi;
        final double ch = c0h + ((FastMath.cos(coef) + 1) * (c11h / 2.0) + c10h) * (1.0 - FastMath.cos(latitude));

        // bw and cw constants (Boehm, J et al, 2006) | WET PART
        final double bw = 0.00146;
        final double cw = 0.04391;

        // Compute coefficients ah and aw with spherical harmonics Eq. 3 (Ref 1)

        // Compute Legendre Polynomials Pnm(sin(phi))
        final int degree = 9;
        final int order  = 9;
        final LegendrePolynomials p = new LegendrePolynomials(degree, order);

        double a0Hydro   = 0.;
        double amplHydro = 0.;
        double a0Wet   = 0.;
        double amplWet = 0.;
        final ABCoefficients abCoef = new ABCoefficients();
        int j = 0;
        for (int n = 0; n <= 9; n++) {
            for (int m = 0; m <= n; m++) {
                a0Hydro   = a0Hydro + (abCoef.getAHMean(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBHMean(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5;

                a0Wet     = a0Wet + (abCoef.getAWMean(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBWMean(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5;

                amplHydro = amplHydro + (abCoef.getAHAmplitude(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBHAmplitude(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5;

                amplWet   = amplWet + (abCoef.getAWAmplitude(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBWAmplitude(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5;

                j = j + 1;
            }
        }

        // Eq. 2 (Ref 1)
        final double ah = a0Hydro + amplHydro * FastMath.cos(coef - psi);
        final double aw = a0Wet + amplWet * FastMath.cos(coef - psi);

        final double[] function = new double[2];
        function[0] = computeFunction(ah, bh, ch, elevation);
        function[1] = computeFunction(aw, bw, cw, elevation);

        // Apply height correction
        final double correction = computeHeightCorrection(elevation, height);
        function[0] = function[0] + correction;

        return function;
    }

    /** {@inheritDoc} */
    @Override
    public <T extends RealFieldElement<T>> T[] mappingFactors(final T elevation, final T height,
                                                              final T[] parameters, final FieldAbsoluteDate<T> date) {
        // Day of year computation
        final DateTimeComponents dtc = date.getComponents(TimeScalesFactory.getUTC());
        final int dofyear = dtc.getDate().getDayOfYear();

        final Field<T> field = date.getField();
        final T zero = field.getZero();

        // bh and ch constants (Boehm, J et al, 2006) | HYDROSTATIC PART
        final T bh  = zero.add(0.0029);
        final T c0h = zero.add(0.062);
        final T c10h;
        final T c11h;
        final T psi;

        // sin(latitude) > 0 -> northern hemisphere
        if (FastMath.sin(latitude) > 0) {
            c10h = zero.add(0.001);
            c11h = zero.add(0.005);
            psi  = zero;
        } else {
            c10h = zero.add(0.002);
            c11h = zero.add(0.007);
            psi  = zero.add(FastMath.PI);
        }

        final T coef = psi.add(((dofyear + 1 - 28) / 365.25) * 2 * FastMath.PI);
        final T ch = c11h.divide(2.0).multiply(FastMath.cos(coef).add(1.0)).add(c10h).multiply(1 - FastMath.cos(latitude)).add(c0h);

        // bw and cw constants (Boehm, J et al, 2006) | WET PART
        final T bw = zero.add(0.00146);
        final T cw = zero.add(0.04391);

        // Compute coefficients ah and aw with spherical harmonics Eq. 3 (Ref 1)

        // Compute Legendre Polynomials Pnm(sin(phi))
        final int degree = 9;
        final int order  = 9;
        final LegendrePolynomials p = new LegendrePolynomials(degree, order);

        T a0Hydro   = zero;
        T amplHydro = zero;
        T a0Wet     = zero;
        T amplWet   = zero;
        final ABCoefficients abCoef = new ABCoefficients();
        int j = 0;
        for (int n = 0; n <= 9; n++) {
            for (int m = 0; m <= n; m++) {
                a0Hydro   = a0Hydro.add((abCoef.getAHMean(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBHMean(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5);

                a0Wet     = a0Wet.add((abCoef.getAWMean(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBWMean(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5);

                amplHydro = amplHydro.add((abCoef.getAHAmplitude(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBHAmplitude(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5);

                amplWet   = amplWet.add((abCoef.getAWAmplitude(j) * p.getPnm(n, m) * FastMath.cos(m * longitude) +
                                abCoef.getBWAmplitude(j) * p.getPnm(n, m) * FastMath.sin(m * longitude)) * 1e-5);

                j = j + 1;
            }
        }

        // Eq. 2 (Ref 1)
        final T ah = a0Hydro.add(amplHydro.multiply(FastMath.cos(coef.subtract(psi))));
        final T aw = a0Wet.add(amplWet.multiply(FastMath.cos(coef.subtract(psi))));

        final T[] function = MathArrays.buildArray(field, 2);
        function[0] = computeFunction(ah, bh, ch, elevation);
        function[1] = computeFunction(aw, bw, cw, elevation);

        // Apply height correction
        final T correction = computeHeightCorrection(elevation, height, field);
        function[0] = function[0].add(correction);

        return function;
    }

    /** {@inheritDoc} */
    @Override
    public ParameterDriver[] getParametersDrivers() {
        return new ParameterDriver[0];
    }

    /** Compute the mapping function related to the coefficient values and the elevation.
     * @param a a coefficient
     * @param b b coefficient
     * @param c c coefficient
     * @param elevation the elevation of the satellite, in radians.
     * @return the value of the function at a given elevation
     */
    private double computeFunction(final double a, final double b, final double c, final double elevation) {
        final double sinE = FastMath.sin(elevation);
        // Numerator
        final double numMP = 1 + a / (1 + b / (1 + c));
        // Denominateur
        final double denMP = sinE + a / (sinE + b / (sinE + c));

        final double felevation = numMP / denMP;

        return felevation;
    }

    /** Compute the mapping function related to the coefficient values and the elevation.
     * @param <T> type of the elements
     * @param a a coefficient
     * @param b b coefficient
     * @param c c coefficient
     * @param elevation the elevation of the satellite, in radians.
     * @return the value of the function at a given elevation
     */
    private <T extends RealFieldElement<T>> T computeFunction(final T a, final T b, final T c, final T elevation) {
        final T sinE = FastMath.sin(elevation);
        // Numerator
        final T numMP = a.divide(b.divide(c.add(1.0)).add(1.0)).add(1.0);
        // Denominateur
        final T denMP = a.divide(b.divide(c.add(sinE)).add(sinE)).add(sinE);

        final T felevation = numMP.divide(denMP);

        return felevation;
    }

    /** This method computes the height correction for the hydrostatic
     *  component of the mapping function.
     *  The formulas are given by Neill's paper, 1996:
     *<p>
     *      Niell A. E. (1996)
     *      "Global mapping functions for the atmosphere delay of radio wavelengths,”
     *      J. Geophys. Res., 101(B2), pp.  3227–3246, doi:  10.1029/95JB03048.
     *</p>
     * @param elevation the elevation of the satellite, in radians.
     * @param height the height of the station in m above sea level.
     * @return the height correction, in m
     */
    private double computeHeightCorrection(final double elevation, final double height) {
        final double sinE = FastMath.sin(elevation);
        // Ref: Eq. 4
        final double function = computeFunction(2.53e-5, 5.49e-3, 1.14e-3, elevation);
        // Ref: Eq. 6
        final double dmdh = (1 / sinE) - function;
        // Ref: Eq. 7
        final double correction = dmdh * (height / 1000.0);
        return correction;
    }

    /** This method computes the height correction for the hydrostatic
     *  component of the mapping function.
     *  The formulas are given by Neill's paper, 1996:
     *<p>
     *      Niell A. E. (1996)
     *      "Global mapping functions for the atmosphere delay of radio wavelengths,”
     *      J. Geophys. Res., 101(B2), pp.  3227–3246, doi:  10.1029/95JB03048.
     *</p>
     * @param <T> type of the elements
     * @param elevation the elevation of the satellite, in radians.
     * @param height the height of the station in m above sea level.
     * @param field field to which the elements belong
     * @return the height correction, in m
     */
    private <T extends RealFieldElement<T>> T computeHeightCorrection(final T elevation, final T height, final Field<T> field) {
        final T zero = field.getZero();
        final T sinE = FastMath.sin(elevation);
        // Ref: Eq. 4
        final T function = computeFunction(zero.add(2.53e-5), zero.add(5.49e-3), zero.add(1.14e-3), elevation);
        // Ref: Eq. 6
        final T dmdh = sinE.reciprocal().subtract(function);
        // Ref: Eq. 7
        final T correction = dmdh.multiply(height.divide(1000.0));
        return correction;
    }

    /** Computes the P<sub>nm</sub>(sin(&#934)) coefficients of Eq. 3 (Boehm et al, 2006).
     *  The computation of the Legendre polynomials is performed following:
     *  Heiskanen and Moritz, Physical Geodesy, 1967, eq. 1-62
     *  <p>
     *    This computation is the one used by the IERS 2010 Conventions.
     *    Petit, G. and Luzum, B. (eds.), IERS Conventions (2010),
     *    IERS Technical Note No. 36, BKG (2010)
     *  </p>
     */
    private class LegendrePolynomials {

        /** Array for the Legendre polynomials. */
        private double[][] pCoef;

        /** Create Legendre polynomials for the given degree and order.
         * @param degree degree of the spherical harmonics
         * @param order order of the spherical harmonics
         */
        LegendrePolynomials(final int degree, final int order) {

            this.pCoef = new double[degree + 1][order + 1];

            final double t  = FastMath.sin(latitude);
            final double t2 = t * t;

            for (int n = 0; n <= degree; n++) {

                // m shall be <= n (Heiskanen and Moritz, 1967, pp 21)
                for (int m = 0; m <= FastMath.min(n, order); m++) {

                    // r = int((n - m) / 2)
                    final int r = (int) (n - m) / 2;
                    double sum = 0.;
                    for (int k = 0; k <= r; k++) {
                        final double term = FastMath.pow(-1.0, k) * CombinatoricsUtils.factorialDouble(2 * n - 2 * k) /
                                        (CombinatoricsUtils.factorialDouble(k) * CombinatoricsUtils.factorialDouble(n - k) *
                                         CombinatoricsUtils.factorialDouble(n - m - 2 * k)) *
                                         FastMath.pow(t, n - m - 2 * k);
                        sum = sum + term;
                    }

                    pCoef[n][m] = FastMath.pow(2, -n) * FastMath.pow(1.0 - t2, 0.5 * m) * sum;

                }

            }

        }

        /** Return the coefficient P<sub>nm</sub>.
         * @param n index
         * @param m index
         * @return The coefficient P<sub>nm</sub>
         */
        public double getPnm(final int n, final int m) {
            return pCoef[n][m];
        }

    }

    private static class ABCoefficients {

        /** Mean hydrostatic coefficients a.*/
        private static final double[] AH_MEAN = {
            1.2517e02,
            8.503e-01,
            6.936e-02,
            -6.760e+00,
            1.771e-01,
            1.130e-02,
            5.963e-01,
            1.808e-02,
            2.801e-03,
            -1.414e-03,
            -1.212e+00,
            9.300e-02,
            3.683e-03,
            1.095e-03,
            4.671e-05,
            3.959e-01,
            -3.867e-02,
            5.413e-03,
            -5.289e-04,
            3.229e-04,
            2.067e-05,
            3.000e-01,
            2.031e-02,
            5.900e-03,
            4.573e-04,
            -7.619e-05,
            2.327e-06,
            3.845e-06,
            1.182e-01,
            1.158e-02,
            5.445e-03,
            6.219e-05,
            4.204e-06,
            -2.093e-06,
            1.540e-07,
            -4.280e-08,
            -4.751e-01,
            -3.490e-02,
            1.758e-03,
            4.019e-04,
            -2.799e-06,
            -1.287e-06,
            5.468e-07,
            7.580e-08,
            -6.300e-09,
            -1.160e-01,
            8.301e-03,
            8.771e-04,
            9.955e-05,
            -1.718e-06,
            -2.012e-06,
            1.170e-08,
            1.790e-08,
            -1.300e-09,
            1.000e-10
        };

        /** Mean hydrostatic coefficients b.*/
        private static final double[] BH_MEAN = {
            0.000e+00,
            0.000e+00,
            3.249e-02,
            0.000e+00,
            3.324e-02,
            1.850e-02,
            0.000e+00,
            -1.115e-01,
            2.519e-02,
            4.923e-03,
            0.000e+00,
            2.737e-02,
            1.595e-02,
            -7.332e-04,
            1.933e-04,
            0.000e+00,
            -4.796e-02,
            6.381e-03,
            -1.599e-04,
            -3.685e-04,
            1.815e-05,
            0.000e+00,
            7.033e-02,
            2.426e-03,
            -1.111e-03,
            -1.357e-04,
            -7.828e-06,
            2.547e-06,
            0.000e+00,
            5.779e-03,
            3.133e-03,
            -5.312e-04,
            -2.028e-05,
            2.323e-07,
            -9.100e-08,
            -1.650e-08,
            0.000e+00,
            3.688e-02,
            -8.638e-04,
            -8.514e-05,
            -2.828e-05,
            5.403e-07,
            4.390e-07,
            1.350e-08,
            1.800e-09,
            0.000e+00,
            -2.736e-02,
            -2.977e-04,
            8.113e-05,
            2.329e-07,
            8.451e-07,
            4.490e-08,
            -8.100e-09,
            -1.500e-09,
            2.000e-10
        };

        /** Amplitude for hydrostatic coefficients a.*/
        private static final double[] AH_AMPL = {
            -2.738e-01,
            -2.837e+00,
            1.298e-02,
            -3.588e-01,
            2.413e-02,
            3.427e-02,
            -7.624e-01,
            7.272e-02,
            2.160e-02,
            -3.385e-03,
            4.424e-01,
            3.722e-02,
            2.195e-02,
            -1.503e-03,
            2.426e-04,
            3.013e-01,
            5.762e-02,
            1.019e-02,
            -4.476e-04,
            6.790e-05,
            3.227e-05,
            3.123e-01,
            -3.535e-02,
            4.840e-03,
            3.025e-06,
            -4.363e-05,
            2.854e-07,
            -1.286e-06,
            -6.725e-01,
            -3.730e-02,
            8.964e-04,
            1.399e-04,
            -3.990e-06,
            7.431e-06,
            -2.796e-07,
            -1.601e-07,
            4.068e-02,
            -1.352e-02,
            7.282e-04,
            9.594e-05,
            2.070e-06,
            -9.620e-08,
            -2.742e-07,
            -6.370e-08,
            -6.300e-09,
            8.625e-02,
            -5.971e-03,
            4.705e-04,
            2.335e-05,
            4.226e-06,
            2.475e-07,
            -8.850e-08,
            -3.600e-08,
            -2.900e-09,
            0.000e+00
        };

        /** Amplitude for hydrostatic coefficients b.*/
        private static final double[] BH_AMPL = {
            0.000e+00,
            0.000e+00,
            -1.136e-01,
            0.000e+00,
            -1.868e-01,
            -1.399e-02,
            0.000e+00,
            -1.043e-01,
            1.175e-02,
            -2.240e-03,
            0.000e+00,
            -3.222e-02,
            1.333e-02,
            -2.647e-03,
            -2.316e-05,
            0.000e+00,
            5.339e-02,
            1.107e-02,
            -3.116e-03,
            -1.079e-04,
            -1.299e-05,
            0.000e+00,
            4.861e-03,
            8.891e-03,
            -6.448e-04,
            -1.279e-05,
            6.358e-06,
            -1.417e-07,
            0.000e+00,
            3.041e-02,
            1.150e-03,
            -8.743e-04,
            -2.781e-05,
            6.367e-07,
            -1.140e-08,
            -4.200e-08,
            0.000e+00,
            -2.982e-02,
            -3.000e-03,
            1.394e-05,
            -3.290e-05,
            -1.705e-07,
            7.440e-08,
            2.720e-08,
            -6.600e-09,
            0.000e+00,
            1.236e-02,
            -9.981e-04,
            -3.792e-05,
            -1.355e-05,
            1.162e-06,
            -1.789e-07,
            1.470e-08,
            -2.400e-09,
            -4.000e-10
        };

        /** Mean wet coefficients a.*/
        private static final double[] AW_MEAN = {
            5.640e+01,
            1.555e+00,
            -1.011e+00,
            -3.975e+00,
            3.171e-02,
            1.065e-01,
            6.175e-01,
            1.376e-01,
            4.229e-02,
            3.028e-03,
            1.688e+00,
            -1.692e-01,
            5.478e-02,
            2.473e-02,
            6.059e-04,
            2.278e+00,
            6.614e-03,
            -3.505e-04,
            -6.697e-03,
            8.402e-04,
            7.033e-04,
            -3.236e+00,
            2.184e-01,
            -4.611e-02,
            -1.613e-02,
            -1.604e-03,
            5.420e-05,
            7.922e-05,
            -2.711e-01,
            -4.406e-01,
            -3.376e-02,
            -2.801e-03,
            -4.090e-04,
            -2.056e-05,
            6.894e-06,
            2.317e-06,
            1.941e+00,
            -2.562e-01,
            1.598e-02,
            5.449e-03,
            3.544e-04,
            1.148e-05,
            7.503e-06,
            -5.667e-07,
            -3.660e-08,
            8.683e-01,
            -5.931e-02,
            -1.864e-03,
            -1.277e-04,
            2.029e-04,
            1.269e-05,
            1.629e-06,
            9.660e-08,
            -1.015e-07,
            -5.000e-10
        };

        /** Mean wet coefficients b.*/
        private static final double[] BW_MEAN = {
            0.000e+00,
            0.000e+00,
            2.592e-01,
            0.000e+00,
            2.974e-02,
            -5.471e-01,
            0.000e+00,
            -5.926e-01,
            -1.030e-01,
            -1.567e-02,
            0.000e+00,
            1.710e-01,
            9.025e-02,
            2.689e-02,
            2.243e-03,
            0.000e+00,
            3.439e-01,
            2.402e-02,
            5.410e-03,
            1.601e-03,
            9.669e-05,
            0.000e+00,
            9.502e-02,
            -3.063e-02,
            -1.055e-03,
            -1.067e-04,
            -1.130e-04,
            2.124e-05,
            0.000e+00,
            -3.129e-01,
            8.463e-03,
            2.253e-04,
            7.413e-05,
            -9.376e-05,
            -1.606e-06,
            2.060e-06,
            0.000e+00,
            2.739e-01,
            1.167e-03,
            -2.246e-05,
            -1.287e-04,
            -2.438e-05,
            -7.561e-07,
            1.158e-06,
            4.950e-08,
            0.000e+00,
            -1.344e-01,
            5.342e-03,
            3.775e-04,
            -6.756e-05,
            -1.686e-06,
            -1.184e-06,
            2.768e-07,
            2.730e-08,
            5.700e-09
        };

        /** Amplitude for wet coefficients a.*/
        private static final double[] AW_AMPL = {
            1.023e-01,
            -2.695e+00,
            3.417e-01,
            -1.405e-01,
            3.175e-01,
            2.116e-01,
            3.536e+00,
            -1.505e-01,
            -1.660e-02,
            2.967e-02,
            3.819e-01,
            -1.695e-01,
            -7.444e-02,
            7.409e-03,
            -6.262e-03,
            -1.836e+00,
            -1.759e-02,
            -6.256e-02,
            -2.371e-03,
            7.947e-04,
            1.501e-04,
            -8.603e-01,
            -1.360e-01,
            -3.629e-02,
            -3.706e-03,
            -2.976e-04,
            1.857e-05,
            3.021e-05,
            2.248e+00,
            -1.178e-01,
            1.255e-02,
            1.134e-03,
            -2.161e-04,
            -5.817e-06,
            8.836e-07,
            -1.769e-07,
            7.313e-01,
            -1.188e-01,
            1.145e-02,
            1.011e-03,
            1.083e-04,
            2.570e-06,
            -2.140e-06,
            -5.710e-08,
            2.000e-08,
            -1.632e+00,
            -6.948e-03,
            -3.893e-03,
            8.592e-04,
            7.577e-05,
            4.539e-06,
            -3.852e-07,
            -2.213e-07,
            -1.370e-08,
            5.800e-09
        };

        /** Amplitude for wet coefficients b.*/
        private static final double[] BW_AMPL = {
            0.000e+00,
            0.000e+00,
            -8.865e-02,
            0.000e+00,
            -4.309e-01,
            6.340e-02,
            0.000e+00,
            1.162e-01,
            6.176e-02,
            -4.234e-03,
            0.000e+00,
            2.530e-01,
            4.017e-02,
            -6.204e-03,
            4.977e-03,
            0.000e+00,
            -1.737e-01,
            -5.638e-03,
            1.488e-04,
            4.857e-04,
            -1.809e-04,
            0.000e+00,
            -1.514e-01,
            -1.685e-02,
            5.333e-03,
            -7.611e-05,
            2.394e-05,
            8.195e-06,
            0.000e+00,
            9.326e-02,
            -1.275e-02,
            -3.071e-04,
            5.374e-05,
            -3.391e-05,
            -7.436e-06,
            6.747e-07,
            0.000e+00,
            -8.637e-02,
            -3.807e-03,
            -6.833e-04,
            -3.861e-05,
            -2.268e-05,
            1.454e-06,
            3.860e-07,
            -1.068e-07,
            0.000e+00,
            -2.658e-02,
            -1.947e-03,
            7.131e-04,
            -3.506e-05,
            1.885e-07,
            5.792e-07,
            3.990e-08,
            2.000e-08,
            -5.700e-09
        };

        /** Build a new instance. */
        ABCoefficients() {

        }

        /** Get the value of the mean hydrostatique coefficient ah for the given index.
         * @param index index
         * @return the mean hydrostatique coefficient ah for the given index
         */
        public double getAHMean(final int index) {
            return AH_MEAN[index];
        }

        /** Get the value of the mean hydrostatique coefficient bh for the given index.
         * @param index index
         * @return the mean hydrostatique coefficient bh for the given index
         */
        public double getBHMean(final int index) {
            return BH_MEAN[index];
        }

        /** Get the value of the mean wet coefficient aw for the given index.
         * @param index index
         * @return the mean wet coefficient aw for the given index
         */
        public double getAWMean(final int index) {
            return AW_MEAN[index];
        }

        /** Get the value of the mean wet coefficient bw for the given index.
         * @param index index
         * @return the mean wet coefficient bw for the given index
         */
        public double getBWMean(final int index) {
            return BW_MEAN[index];
        }

        /** Get the value of the amplitude of the hydrostatique coefficient ah for the given index.
         * @param index index
         * @return the amplitude of the hydrostatique coefficient ah for the given index
         */
        public double getAHAmplitude(final int index) {
            return AH_AMPL[index];
        }

        /** Get the value of the amplitude of the hydrostatique coefficient bh for the given index.
         * @param index index
         * @return the amplitude of the hydrostatique coefficient bh for the given index
         */
        public double getBHAmplitude(final int index) {
            return BH_AMPL[index];
        }

        /** Get the value of the amplitude of the wet coefficient aw for the given index.
         * @param index index
         * @return the amplitude of the wet coefficient aw for the given index
         */
        public double getAWAmplitude(final int index) {
            return AW_AMPL[index];
        }

        /** Get the value of the amplitude of the wet coefficient bw for the given index.
         * @param index index
         * @return the amplitude of the wet coefficient bw for the given index
         */
        public double getBWAmplitude(final int index) {
            return BW_AMPL[index];
        }
    }
}

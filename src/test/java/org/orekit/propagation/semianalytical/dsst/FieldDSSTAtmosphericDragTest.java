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
package org.orekit.propagation.semianalytical.dsst;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hipparchus.Field;
import org.hipparchus.RealFieldElement;
import org.hipparchus.analysis.differentiation.DerivativeStructure;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationOrder;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.Decimal64Field;
import org.hipparchus.util.FastMath;
import org.hipparchus.util.MathArrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FieldAttitude;
import org.orekit.attitudes.InertialProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.errors.OrekitException;
import org.orekit.forces.BoxAndSolarArraySpacecraft;
import org.orekit.forces.drag.atmosphere.Atmosphere;
import org.orekit.forces.drag.atmosphere.HarrisPriester;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.orbits.EquinoctialOrbit;
import org.orekit.orbits.FieldEquinoctialOrbit;
import org.orekit.orbits.FieldOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTAtmosphericDrag;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTForceModel;
import org.orekit.propagation.semianalytical.dsst.forces.FieldShortPeriodTerms;
import org.orekit.propagation.semianalytical.dsst.forces.ShortPeriodTerms;
import org.orekit.propagation.semianalytical.dsst.utilities.AuxiliaryElements;
import org.orekit.propagation.semianalytical.dsst.utilities.FieldAuxiliaryElements;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.TimeStampedFieldAngularCoordinates;

public class FieldDSSTAtmosphericDragTest {
    
    @Test
    public void testGetMeanElementRate() throws IllegalArgumentException, OrekitException {
        doTestGetMeanElementRate(Decimal64Field.getInstance());
    }

    private <T extends RealFieldElement<T>> void doTestGetMeanElementRate(Field<T> field) throws IllegalArgumentException, OrekitException {
        
        final T zero = field.getZero();
        // Central Body geopotential 2x0
        final UnnormalizedSphericalHarmonicsProvider provider =
                GravityFieldFactory.getUnnormalizedProvider(2, 0);
        
        final Frame earthFrame = FramesFactory.getEME2000();
        final FieldAbsoluteDate<T> initDate = new FieldAbsoluteDate<>(field, 2003, 07, 01, 0, 0, 0, TimeScalesFactory.getUTC());
        final double mu = 3.986004415E14;
        // a  = 7204535.84810944 m
        // ex = -0.001119677138261611
        // ey = 5.333650671984143E-4
        // hx = 0.847841707880348
        // hy = 0.7998014061193262
        // lM = 3.897842092486239 rad
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(zero.add(7204535.84810944),
                                                                zero.add(-0.001119677138261611),
                                                                zero.add(5.333650671984143E-4),
                                                                zero.add(0.847841707880348),
                                                                zero.add(0.7998014061193262),
                                                                zero.add(3.897842092486239),
                                                                PositionAngle.TRUE,
                                                                earthFrame,
                                                                initDate,
                                                                zero.add(mu));

        // Drag Force Model
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(provider.getAe(),
                                                            Constants.WGS84_EARTH_FLATTENING,
                                                            CelestialBodyFactory.getEarth().getBodyOrientedFrame());
        final Atmosphere atm = new HarrisPriester(CelestialBodyFactory.getSun(), earth, 6);
        final double cd = 2.0;
        final double area = 25.0;
        DSSTForceModel drag = new DSSTAtmosphericDrag(atm, cd, area, mu);

        // Register the attitude provider to the force model
        Rotation rotation =  new Rotation(1., 0., 0., 0., false);
        AttitudeProvider attitudeProvider = new InertialProvider(rotation);
        drag.registerAttitudeProvider(attitudeProvider);
        
        // Attitude of the satellite
        FieldRotation<T> fieldRotation = new FieldRotation<>(field, rotation);
        FieldVector3D<T> rotationRate = new FieldVector3D<>(zero, zero, zero);
        FieldVector3D<T> rotationAcceleration = new FieldVector3D<>(zero, zero, zero);
        TimeStampedFieldAngularCoordinates<T> orientation = new TimeStampedFieldAngularCoordinates<>(initDate, fieldRotation, rotationRate, rotationAcceleration);
        final FieldAttitude<T> att = new FieldAttitude<>(earthFrame, orientation);
        
        final T mass = zero.add(1000.0);
        final FieldSpacecraftState<T> state = new FieldSpacecraftState<>(orbit, att, mass);
        final FieldAuxiliaryElements<T> auxiliaryElements = new FieldAuxiliaryElements<>(state.getOrbit(), 1);
        
        // Compute the mean element rate
        final T[] elements = MathArrays.buildArray(field, 7);
        Arrays.fill(elements, zero);
        final T[] daidt = drag.getMeanElementRate(state, auxiliaryElements, drag.getParameters(field));
        for (int i = 0; i < daidt.length; i++) {
            elements[i] = daidt[i];
        }

        Assert.assertEquals(-3.415320567871035E-5,   elements[0].getReal(), 2.0e-20);
        Assert.assertEquals(6.276312897745139E-13,   elements[1].getReal(), 2.9e-27);
        Assert.assertEquals(-9.303357008691404E-13,  elements[2].getReal(), 2.7e-27);
        Assert.assertEquals(-7.052316604063199E-14,  elements[3].getReal(), 2.0e-28);
        Assert.assertEquals(-6.793277250493389E-14,  elements[4].getReal(), 9.0e-29);
        Assert.assertEquals(-1.3565284454826392E-15, elements[5].getReal(), 2.0e-28);
    
    }

    @Test
    public void testShortPeriodTerms() throws IllegalArgumentException, OrekitException {
        doTestShortPeriodTerms(Decimal64Field.getInstance());
    }

    @SuppressWarnings("unchecked")
    private <T extends RealFieldElement<T>> void doTestShortPeriodTerms(final Field<T> field)
        throws IllegalArgumentException, OrekitException {
 
        final T zero = field.getZero();
        final FieldAbsoluteDate<T> initDate = new FieldAbsoluteDate<>(field, new DateComponents(2003, 03, 21), new TimeComponents(1, 0, 0.), TimeScalesFactory.getUTC());

        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(zero.add(7069219.9806427825),
                                                                zero.add(-4.5941811292223825E-4),
                                                                zero.add(1.309932339472599E-4),
                                                                zero.add(-1.002996107003202),
                                                                zero.add(0.570979900577994),
                                                                zero.add(2.62038786211518),
                                                                PositionAngle.TRUE,
                                                                FramesFactory.getEME2000(),
                                                                initDate,
                                                                zero.add(3.986004415E14));

        final FieldSpacecraftState<T> meanState = new FieldSpacecraftState<>(orbit);

        final CelestialBody    sun   = CelestialBodyFactory.getSun();
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                                            Constants.WGS84_EARTH_FLATTENING,
                                                            FramesFactory.getITRF(IERSConventions.IERS_2010,
                                                                                  true));
        
        final BoxAndSolarArraySpacecraft boxAndWing = new BoxAndSolarArraySpacecraft(5.0, 2.0, 2.0,
                                                                                     sun,
                                                                                     50.0, Vector3D.PLUS_J,
                                                                                     2.0, 0.1,
                                                                                     0.2, 0.6);
        
        final Atmosphere atmosphere = new HarrisPriester(CelestialBodyFactory.getSun(), earth, 6);
        final AttitudeProvider attitudeProvider = new LofOffset(meanState.getFrame(),
                                                                LOFType.VVLH, RotationOrder.XYZ,
                                                                0.0, 0.0, 0.0);

        final DSSTForceModel drag = new DSSTAtmosphericDrag(atmosphere, boxAndWing, meanState.getMu().getReal());
        
        //Create the auxiliary object
        final FieldAuxiliaryElements<T> aux = new FieldAuxiliaryElements<>(meanState.getOrbit(), 1);

        // Set the force models
        final List<FieldShortPeriodTerms<T>> shortPeriodTerms = new ArrayList<FieldShortPeriodTerms<T>>();

        drag.registerAttitudeProvider(attitudeProvider);
        shortPeriodTerms.addAll(drag.initialize(aux, false, drag.getParameters(field)));
        drag.updateShortPeriodTerms(drag.getParameters(field), meanState);

        T[] y = MathArrays.buildArray(field, 6);
        Arrays.fill(y, zero);
        
        for (final FieldShortPeriodTerms<T> spt : shortPeriodTerms) {
            final T[] shortPeriodic = spt.value(meanState.getOrbit());
            for (int i = 0; i < shortPeriodic.length; i++) {
                y[i] = y[i].add(shortPeriodic[i]);
            }
        }
        
        Assert.assertEquals(0.03966657233280967,    y[0].getReal(), 1.0e-15);
        Assert.assertEquals(-1.5294381443173415E-8, y[1].getReal(), 1.0e-23);
        Assert.assertEquals(-2.3614929828516364E-8, y[2].getReal(), 1.4e-23);
        Assert.assertEquals(-5.901580336558653E-11, y[3].getReal(), 4.0e-25);
        Assert.assertEquals(1.0287639743124977E-11, y[4].getReal(), 2.0e-24);
        Assert.assertEquals(2.538427523777691E-8,   y[5].getReal(), 1.0e-22);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testShortPeriodTermsDerivatives() throws OrekitException {
        
        // Initial spacecraft state
        final AbsoluteDate initDate = new AbsoluteDate(new DateComponents(2003, 05, 21), new TimeComponents(1, 0, 0.),
                                                       TimeScalesFactory.getUTC());

        final Orbit orbit = new EquinoctialOrbit(7204535.84810944,
                                                 -0.001119677138261611,
                                                 5.333650671984143E-4,
                                                 0.847841707880348,
                                                 0.7998014061193262,
                                                 3.897842092486239,
                                                 PositionAngle.TRUE,
                                                 FramesFactory.getEME2000(),
                                                 initDate,
                                                 3.986004415E14);
        
        final OrbitType orbitType = OrbitType.EQUINOCTIAL;
       
        final SpacecraftState meanState = new SpacecraftState(orbit);
        
        // Attitude
        final AttitudeProvider attitudeProvider = new LofOffset(meanState.getFrame(),
                                                                LOFType.VVLH, RotationOrder.XYZ,
                                                                0.0, 0.0, 0.0);
        
        // Force model
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                                            Constants.WGS84_EARTH_FLATTENING,
                                                            CelestialBodyFactory.getEarth().getBodyOrientedFrame());
        final Atmosphere atm = new HarrisPriester(CelestialBodyFactory.getSun(), earth, 6);
        final double cd = 2.0;
        final double area = 25.0;
        final DSSTForceModel drag = new DSSTAtmosphericDrag(atm, cd, area, 3.986004415E14);
        drag.registerAttitudeProvider(attitudeProvider);
        final double[] parameters    = drag.getParameters();
                        
        // Converter for derivatives
        final DSSTDSConverter converter = new DSSTDSConverter(meanState, InertialProvider.EME2000_ALIGNED);
        
        // Field parameters
        final FieldSpacecraftState<DerivativeStructure> dsState = converter.getState(drag);
        final DerivativeStructure[] dsParameters                = converter.getParameters(dsState, drag);
        
        final FieldAuxiliaryElements<DerivativeStructure> fieldAuxiliaryElements = new FieldAuxiliaryElements<>(dsState.getOrbit(), 1);
        
        // Zero
        final DerivativeStructure zero = dsState.getDate().getField().getZero();
        
        // Compute state Jacobian using directly the method
        final List<FieldShortPeriodTerms<DerivativeStructure>> shortPeriodTerms = new ArrayList<FieldShortPeriodTerms<DerivativeStructure>>();
        shortPeriodTerms.addAll(drag.initialize(fieldAuxiliaryElements, false, dsParameters));
        drag.updateShortPeriodTerms(dsParameters, dsState);
        final DerivativeStructure[] shortPeriod = new DerivativeStructure[6];
        Arrays.fill(shortPeriod, zero);
        for (final FieldShortPeriodTerms<DerivativeStructure> spt : shortPeriodTerms) {
            final DerivativeStructure[] spVariation = spt.value(dsState.getOrbit());
            for (int i = 0; i < spVariation .length; i++) {
                shortPeriod[i] = shortPeriod[i].add(spVariation[i]);
            }
        }
        
        final double[][] shortPeriodJacobian = new double[6][6];
      
        final double[] derivativesASP  = shortPeriod[0].getAllDerivatives();
        final double[] derivativesExSP = shortPeriod[1].getAllDerivatives();
        final double[] derivativesEySP = shortPeriod[2].getAllDerivatives();
        final double[] derivativesHxSP = shortPeriod[3].getAllDerivatives();
        final double[] derivativesHySP = shortPeriod[4].getAllDerivatives();
        final double[] derivativesLSP  = shortPeriod[5].getAllDerivatives();

        // Update Jacobian with respect to state
        addToRow(derivativesASP,  0, shortPeriodJacobian);
        addToRow(derivativesExSP, 1, shortPeriodJacobian);
        addToRow(derivativesEySP, 2, shortPeriodJacobian);
        addToRow(derivativesHxSP, 3, shortPeriodJacobian);
        addToRow(derivativesHySP, 4, shortPeriodJacobian);
        addToRow(derivativesLSP,  5, shortPeriodJacobian);
        
        // Compute reference state Jacobian using finite differences
        double[][] shortPeriodJacobianRef = new double[6][6];
        double dP = 0.001;
        double[] steps = NumericalPropagator.tolerances(1000000 * dP, orbit, orbitType)[0];
        for (int i = 0; i < 6; i++) {
            
            SpacecraftState stateM4 = shiftState(meanState, orbitType, -4 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsM4 = new AuxiliaryElements(stateM4.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsM4 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsM4.addAll(drag.initialize(auxiliaryElementsM4, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateM4);
            final double[] shortPeriodM4 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsM4) {
                final double[] spVariation = spt.value(stateM4.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodM4[j] += spVariation[j];
                }
            }
            
            SpacecraftState stateM3 = shiftState(meanState, orbitType, -3 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsM3 = new AuxiliaryElements(stateM3.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsM3 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsM3.addAll(drag.initialize(auxiliaryElementsM3, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateM3);
            final double[] shortPeriodM3 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsM3) {
                final double[] spVariation = spt.value(stateM3.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodM3[j] += spVariation[j];
                }
            }
            
            SpacecraftState stateM2 = shiftState(meanState, orbitType, -2 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsM2 = new AuxiliaryElements(stateM2.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsM2 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsM2.addAll(drag.initialize(auxiliaryElementsM2, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateM2);
            final double[] shortPeriodM2 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsM2) {
                final double[] spVariation = spt.value(stateM2.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodM2[j] += spVariation[j];
                }
            }
 
            SpacecraftState stateM1 = shiftState(meanState, orbitType, -1 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsM1 = new AuxiliaryElements(stateM1.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsM1 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsM1.addAll(drag.initialize(auxiliaryElementsM1, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateM1);
            final double[] shortPeriodM1 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsM1) {
                final double[] spVariation = spt.value(stateM1.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodM1[j] += spVariation[j];
                }
            }
            
            SpacecraftState stateP1 = shiftState(meanState, orbitType, 1 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsP1 = new AuxiliaryElements(stateP1.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsP1 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsP1.addAll(drag.initialize(auxiliaryElementsP1, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateP1);
            final double[] shortPeriodP1 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsP1) {
                final double[] spVariation = spt.value(stateP1.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodP1[j] += spVariation[j];
                }
            }
            
            SpacecraftState stateP2 = shiftState(meanState, orbitType, 2 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsP2 = new AuxiliaryElements(stateP2.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsP2 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsP2.addAll(drag.initialize(auxiliaryElementsP2, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateP2);
            final double[] shortPeriodP2 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsP2) {
                final double[] spVariation = spt.value(stateP2.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodP2[j] += spVariation[j];
                }
            }
            
            SpacecraftState stateP3 = shiftState(meanState, orbitType, 3 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsP3 = new AuxiliaryElements(stateP3.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsP3 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsP3.addAll(drag.initialize(auxiliaryElementsP3, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateP3);
            final double[] shortPeriodP3 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsP3) {
                final double[] spVariation = spt.value(stateP3.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodP3[j] += spVariation[j];
                }
            }
            
            SpacecraftState stateP4 = shiftState(meanState, orbitType, 4 * steps[i], i);
            final AuxiliaryElements auxiliaryElementsP4 = new AuxiliaryElements(stateP4.getOrbit(), 1);
            final List<ShortPeriodTerms> shortPeriodTermsP4 = new ArrayList<ShortPeriodTerms>();
            shortPeriodTermsP4.addAll(drag.initialize(auxiliaryElementsP4, false, parameters));
            drag.updateShortPeriodTerms(parameters, stateP4);
            final double[] shortPeriodP4 = new double[6];
            for (final ShortPeriodTerms spt : shortPeriodTermsP4) {
                final double[] spVariation = spt.value(stateP4.getOrbit());
                for (int j = 0; j < spVariation .length; j++) {
                    shortPeriodP4[j] += spVariation[j];
                }
            }
            
            fillJacobianColumn(shortPeriodJacobianRef, i, orbitType, steps[i],
                               shortPeriodM4, shortPeriodM3, shortPeriodM2, shortPeriodM1,
                               shortPeriodP1, shortPeriodP2, shortPeriodP3, shortPeriodP4);
            
        }
        
        for (int m = 0; m < 6; ++m) {
            for (int n = 0; n < 6; ++n) {
                double error = FastMath.abs((shortPeriodJacobian[m][n] - shortPeriodJacobianRef[m][n]) / shortPeriodJacobianRef[m][n]);
                Assert.assertEquals(0, error, 2.6e-2);
            }
        }

    }

    private void fillJacobianColumn(double[][] jacobian, int column,
                                    OrbitType orbitType, double h,
                                    double[] M4h, double[] M3h,
                                    double[] M2h, double[] M1h,
                                    double[] P1h, double[] P2h,
                                    double[] P3h, double[] P4h) {
        for (int i = 0; i < jacobian.length; ++i) {
            jacobian[i][column] = ( -3 * (P4h[i] - M4h[i]) +
                                    32 * (P3h[i] - M3h[i]) -
                                   168 * (P2h[i] - M2h[i]) +
                                   672 * (P1h[i] - M1h[i])) / (840 * h);
        }
    }
 
    private SpacecraftState shiftState(SpacecraftState state, OrbitType orbitType,
                                       double delta, int column) {

        double[][] array = stateToArray(state, orbitType);
        array[0][column] += delta;

        return arrayToState(array, orbitType, state.getFrame(), state.getDate(),
                            state.getMu(), state.getAttitude());

    }

    private double[][] stateToArray(SpacecraftState state, OrbitType orbitType) {
          double[][] array = new double[2][6];

          orbitType.mapOrbitToArray(state.getOrbit(), PositionAngle.MEAN, array[0], array[1]);
          return array;
      }

    private SpacecraftState arrayToState(double[][] array, OrbitType orbitType,
                                           Frame frame, AbsoluteDate date, double mu,
                                           Attitude attitude) {
          EquinoctialOrbit orbit = (EquinoctialOrbit) orbitType.mapArrayToOrbit(array[0], array[1], PositionAngle.MEAN, date, mu, frame);
          return new SpacecraftState(orbit, attitude);
    }

    /** Fill Jacobians rows.
     * @param derivatives derivatives of a component
     * @param index component index (0 for a, 1 for ex, 2 for ey, 3 for hx, 4 for hy, 5 for l)
     * @param jacobian Jacobian of short period terms with respect to state
     */
    private void addToRow(final double[] derivatives, final int index,
                          final double[][] jacobian) {

        for (int i = 0; i < 6; i++) {
            jacobian[index][i] += derivatives[i + 1];
        }

    }

    @Before
    public void setUp() throws OrekitException, IOException, ParseException {
        Utils.setDataRoot("regular-data:potential/shm-format");
    }
    
}

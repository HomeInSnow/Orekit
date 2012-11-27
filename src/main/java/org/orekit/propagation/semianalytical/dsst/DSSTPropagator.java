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
package org.orekit.propagation.semianalytical.dsst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.ode.FirstOrderIntegrator;
import org.apache.commons.math3.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.errors.PropagationException;
import org.orekit.forces.ForceModel;
import org.orekit.forces.SphericalSpacecraft;
import org.orekit.forces.drag.Atmosphere;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.gravity.CunninghamAttractionModel;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.forces.radiation.SolarRadiationPressure;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.EquinoctialOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.OsculatingToMeanElementsConverter;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.integration.AbstractIntegratedPropagator;
import org.orekit.propagation.integration.StateMapper;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.semianalytical.dsst.dsstforcemodel.DSSTAtmosphericDrag;
import org.orekit.propagation.semianalytical.dsst.dsstforcemodel.DSSTCentralBody;
import org.orekit.propagation.semianalytical.dsst.dsstforcemodel.DSSTForceModel;
import org.orekit.propagation.semianalytical.dsst.dsstforcemodel.DSSTSolarRadiationPressure;
import org.orekit.propagation.semianalytical.dsst.dsstforcemodel.DSSTThirdBody;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;

/**
 * This class propagates {@link org.orekit.orbits.Orbit orbits} using the DSST theory.
 * <p>
 * The DSST theory, as exposed by D.A.Danielson & al. (1995), describes a semianalytical propagator
 * that combines the accuracy of numerical propagators with the speed of analytical propagators.
 * Whereas analytical propagators are configured only thanks to their various constructors and can
 * be used immediately after construction, such a semianalytical propagator configuration involves
 * setting several parameters between construction time and propagation time, just as numerical
 * propagators.
 * </p>
 * <p>
 * The configuration parameters that can be set are:
 * </p>
 * <ul>
 * <li>the initial spacecraft state ({@link #resetInitialState(SpacecraftState)})</li>
 * <li>the various force models ({@link #addForceModel(DSSTForceModel)},
 * {@link #removeForceModels()})</li>
 * <li>the discrete events that should be triggered during propagation (
 * {@link #addEventDetector(org.orekit.propagation.events.EventDetector)},
 * {@link #clearEventsDetectors()})</li>
 * <li>the binding logic with the rest of the application ({@link #setSlaveMode()},
 * {@link #setMasterMode(double, org.orekit.propagation.sampling.OrekitFixedStepHandler)},
 * {@link #setMasterMode(org.orekit.propagation.sampling.OrekitStepHandler)},
 * {@link #setEphemerisMode()}, {@link #getGeneratedEphemeris()})</li>
 * </ul>
 * <p>
 * From these configuration parameters, only the initial state is mandatory. The default propagation
 * settings are in {@link OrbitType#EQUINOCTIAL equinoctial} parameters with
 * {@link PositionAngle#TRUE true} longitude argument. The central attraction coefficient used to
 * define the initial orbit will be used. However, specifying only the initial state would mean the
 * propagator would use only keplerian forces. In this case, the simpler
 * {@link org.orekit.propagation.analytical.KeplerianPropagator KeplerianPropagator} class would
 * perhaps be more effective.
 * </p>
 * <p>
 * The underlying numerical integrator set up in the constructor may also have its own configuration
 * parameters. Typical configuration parameters for adaptive stepsize integrators are the min, max
 * and perhaps start step size as well as the absolute and/or relative errors thresholds.
 * </p>
 * <p>
 * The state that is seen by the integrator is a simple six elements double array. These six
 * elements are:
 * <ul>
 * <li>the {@link org.orekit.orbits.EquinoctialOrbit equinoctial orbit parameters} (a,
 * e<sub>x</sub>, e<sub>y</sub>, h<sub>x</sub>, h<sub>y</sub>, &lambda;<sub>v</sub>) in meters and
 * radians,</li>
 * </ul>
 * </p>
 * <p>
 * The same propagator can be reused for several orbit extrapolations, by resetting the initial
 * state without modifying the other configuration parameters. However, the same instance cannot be
 * used simultaneously by different threads, the class is <em>not</em> thread-safe.
 * </p>
 *
 * @see SpacecraftState
 * @see DSSTForceModel
 * @author Romain Di Costanzo
 * @author Pascal Parraud
 */
public class DSSTPropagator extends AbstractIntegratedPropagator {

    /** Force models used during the extrapolation of the Orbit. */
    private final List<DSSTForceModel>     forceModels;

    /**
     * DSST truncation algorithm must be reset when orbital parameters have evolved too much. When
     * integration reach the resetDate, the {@link DSSTForceModel#initialize(SpacecraftState)}
     * method is called, and the next resetDate is set to resetDate + timeShiftToInitialize.
     */
    private AbsoluteDate                   resetDate;

    /**
     * DSST force model will be re-initialized every time the propagation date will be bigger than
     * resetDate + timeShiftToInitialize. In seconds.
     */
    private double                         timeShiftToInitialize;

    /** Is the orbital state in osculating parameters. */
    private boolean                        isOsculating;

    /** number of satellite revolutions in the averaging interval. */
    private int                            satelliteRevolution;

    /**
     * Build a DSSTPropagator from integrator and orbit.
     * <p>
     * Mass and attitude provider are set to unspecified non-null arbitrary values.
     * </p>
     * <p>
     * After creation, there are no perturbing forces at all. This means that if
     * {@link #addForceModel addForceModel} is not called after creation, the integrated orbit will
     * follow a keplerian evolution only.
     * </p>
     *
     * @param integrator
     *            numerical integrator used to integrate mean coefficient defined by the SST theory.
     * @param initialOrbit
     *            initial orbit
     * @param isOsculating
     *            is the orbital state in osculating parameters
     * @param timeShiftToInitialize
     *            DSST force model will be re-initialized every time the propagation date will be
     *            bigger than resetDate + timeShiftToInitialize. In seconds.
     * @throws OrekitException
     *             if an error occurs in orbit averaging (i.e when transforming osculating elements
     *             into mean elements)
     */
    public DSSTPropagator(final FirstOrderIntegrator integrator,
                          final Orbit initialOrbit,
                          final boolean isOsculating,
                          final double timeShiftToInitialize)
        throws OrekitException {
        this(integrator, initialOrbit, isOsculating, timeShiftToInitialize, DEFAULT_LAW, DEFAULT_MASS);
    }

    /**
     * Build a DSSTPropagator from integrator, orbit and attitude provider.
     * <p>
     * Mass is set to an unspecified non-null arbitrary value.
     * </p>
     * <p>
     * After creation, there are no perturbing forces at all. This means that if
     * {@link #addForceModel addForceModel} is not called after creation, the integrated orbit will
     * follow a keplerian evolution only.
     * </p>
     *
     * @param integrator
     *            numerical integrator used to integrate mean coefficient defined by the SST theory.
     * @param initialOrbit
     *            initial orbit
     * @param isOsculating
     *            is the orbital state in osculating parameters
     * @param timeShiftToInitialize
     *            DSST force model will be re-initialized every time the propagation date will be
     *            bigger than resetDate + timeShiftToInitialize. In seconds.
     * @param attitudeProv
     *            attitude provider
     * @throws OrekitException
     *             if an error occurs in orbit averaging (i.e when transforming osculating elements
     *             into mean elements)
     */
    public DSSTPropagator(final FirstOrderIntegrator integrator,
                          final Orbit initialOrbit,
                          final boolean isOsculating,
                          final double timeShiftToInitialize,
                          final AttitudeProvider attitudeProv)
        throws OrekitException {
        this(integrator, initialOrbit, isOsculating, timeShiftToInitialize, attitudeProv, DEFAULT_MASS);
    }

    /**
     * Build a DSSTPropagator from integrator, orbit and mass.
     * <p>
     * Attitude provider is set to an unspecified non-null arbitrary value.
     * </p>
     * <p>
     * After creation, there are no perturbing forces at all. This means that if
     * {@link #addForceModel addForceModel} is not called after creation, the integrated orbit will
     * follow a keplerian evolution only.
     * </p>
     *
     * @param integrator
     *            numerical integrator used to integrate mean coefficient defined by the SST theory.
     * @param initialOrbit
     *            initial orbit
     * @param isOsculating
     *            is the orbital state in osculating parameters
     * @param timeShiftToInitialize
     *            DSST force model will be re-initialized every time the propagation date will be
     *            bigger than resetDate + timeShiftToInitialize. In seconds.
     * @param mass
     *            spacecraft mass
     * @throws OrekitException
     *             if an error occurs in orbit averaging (i.e when transforming osculating elements
     *             into mean elements)
     */
    public DSSTPropagator(final FirstOrderIntegrator integrator,
                          final Orbit initialOrbit,
                          final boolean isOsculating,
                          final double timeShiftToInitialize,
                          final double mass)
        throws OrekitException {
        this(integrator, initialOrbit, isOsculating, timeShiftToInitialize, DEFAULT_LAW, mass);
    }

    /**
     * Build a DSSTPropagator from integrator, orbit, attitude provider and mass.
     * <p>
     * After creation, there are no perturbing forces at all. This means that if
     * {@link #addForceModel addForceModel} is not called after creation, the integrated orbit will
     * follow a keplerian evolution only.
     * </p>
     *
     * @param integrator
     *            numerical integrator used to integrate mean coefficient defined by the SST theory.
     * @param initialOrbit
     *            initial orbit
     * @param isOsculating
     *            is the orbital state in osculating parameters
     * @param timeShiftToInitialize
     *            DSST force model will be re-initialized every time the propagation date will be
     *            bigger than resetDate + timeShiftToInitialize. In seconds.
     * @param attitudeProv
     *            attitude provider
     * @param mass
     *            spacecraft mass
     * @throws OrekitException
     *             if an error occurs in orbit averaging (i.e when transforming osculating elements
     *             into mean elements)
     */
    public DSSTPropagator(final FirstOrderIntegrator integrator,
                          final Orbit initialOrbit,
                          final boolean isOsculating,
                          final double timeShiftToInitialize,
                          final AttitudeProvider attitudeProv,
                          final double mass)
        throws OrekitException {

        // DSST uses only equinoctial orbits and mean longitude argument
        setOrbitType(OrbitType.EQUINOCTIAL);
        setPositionAngleType(PositionAngle.MEAN);

        setAttitudeProvider(attitudeProv);
        this.forceModels = new ArrayList<DSSTForceModel>();
        this.timeShiftToInitialize = timeShiftToInitialize;
        this.isOsculating = isOsculating;
        // Average osculating elements over 2 orbits
        this.satelliteRevolution = 2;

        setIntegrator(integrator);

        final PVCoordinatesProvider pvProv = new PVCoordinatesProvider() {
            /** {@inheritDoc} */
            public PVCoordinates getPVCoordinates(final AbsoluteDate date, final Frame f) {
                return initialOrbit.getPVCoordinates();
            }
        };
        resetInitialState(new SpacecraftState(initialOrbit,
                                              attitudeProv.getAttitude(pvProv,
                                                                       initialOrbit.getDate(),
                                                                       initialOrbit.getFrame()),
                                                                       mass));
    }

    /**
     * Reset the initial state.
     *
     * @param state
     *            new initial state
     * @throws PropagationException if initial state cannot be reset
     */
    public void resetInitialState(final SpacecraftState state) throws PropagationException {
        super.setStartDate(state.getDate());
        this.resetDate = state.getDate();
        super.resetInitialState(state);
    }

    /**
     * Add a force model to the global perturbation model.
     * <p>
     * If this method is not called at all, the integrated orbit will follow a keplerian evolution
     * only.
     * </p>
     *
     * @param forcemodel
     *            perturbing {@link DSSTForceModel} to add
     * @see #removeForceModels()
     */
    public void addForceModel(final DSSTForceModel forcemodel) {
        forceModels.add(forcemodel);
    }

    /**
     * Remove all perturbing force models from the global perturbation model.
     * <p>
     * Once all perturbing forces have been removed (and as long as no new force model is added),
     * the integrated orbit will follow a keplerian evolution only.
     * </p>
     *
     * @see #addForceModel(DSSTForceModel)
     */
    public void removeForceModels() {
        forceModels.clear();
    }

    /**
     * Override the default value of the {@link DSSTPropagator#satelliteRevolution} parameter. By
     * default, if the given orbit is an osculating one, it will be averaged over a specific number
     * of revolution (2 revolution). This can be changed by using this method.
     *
     * @param satelliteRevolution
     *            number of satellite revolution to use for averaging process (osculating to mean
     *            elements)
     */
    public void setSatelliteRevolution(final int satelliteRevolution) {
        this.satelliteRevolution = satelliteRevolution;
    }

    /** Check if the DSST needs to be initialized again.
     * @param tEnd target date at which state should be propagated
     * @exception OrekitException if force models cannot be reinitialized
     */
    protected void beforeIntegration(final AbsoluteDate tEnd) throws OrekitException {
        if (tEnd.compareTo(resetDate) >= 0) {
            // Re-initialize every force model
            for (final DSSTForceModel forceModel : forceModels) {
                forceModel.initialize(getInitialState());
            }
            resetDate = resetDate.shiftedBy(timeShiftToInitialize);
        }
    }

    /** {@inheritDoc} */
    protected StateMapper createMapper(final AbsoluteDate referenceDate, final double mu,
                                       final OrbitType orbitType, final PositionAngle positionAngleType,
                                       final AttitudeProvider attitudeProvider, final Frame frame) {
        return new MeanPlusShortPeriodicMapper(referenceDate, mu, attitudeProvider, frame,
                                               isOsculating ? forceModels : new ArrayList<DSSTForceModel>(),
                                               satelliteRevolution);
    }

    /** Internal mapper using mean parameters plus short periodic terms. */
    private static class MeanPlusShortPeriodicMapper extends StateMapper {

        /** Serializable UID. */
        private static final long serialVersionUID = 5880502847862113166L;

        /** Force models used to compute short periodic terms. */
        private final List<DSSTForceModel> forceModels;

        /** Number of satellite revolutions in the averaging interval. */
        private int                        satelliteRevolution;

        /** Simple constructor.
         * <p>
         * The position parameter type is meaningful only if {@link
         * #getOrbitType() propagation orbit type}
         * support it. As an example, it is not meaningful for propagation
         * in {@link OrbitType#CARTESIAN Cartesian} parameters.
         * </p>
         * @param referenceDate reference date
         * @param mu central attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
         * @param attitudeProvider attitude provider
         * @param frame inertial frame
         * @param forceModels force models to use for short periodic terms computation
         * (empty list if parameters remain as mean parameters only)
         * @param satelliteRevolution number of satellite revolutions in the averaging interval
         * (unused if forceModels is empty)
         */
        public MeanPlusShortPeriodicMapper(final AbsoluteDate referenceDate, final double mu,
                                           final AttitudeProvider attitudeProvider, final Frame frame,
                                           final List<DSSTForceModel> forceModels, final int satelliteRevolution) {
            super(referenceDate, mu, OrbitType.EQUINOCTIAL, PositionAngle.MEAN, attitudeProvider, frame);
            this.forceModels = new ArrayList<DSSTForceModel>(forceModels);
            this.satelliteRevolution = satelliteRevolution;
        }

        /** {@inheritDoc} */
        public SpacecraftState mapArrayToState(final double t, final double[] y)
                throws OrekitException {

            final AbsoluteDate date = mapDoubleToDate(t);

            // add short periodic variations to mean elements to get osculating elements
            // (the loop may not be performed if there are no force models, in the
            //  case we want to remain in mean parameters only)
            final double[] osculatingElements = y.clone();
            for (final DSSTForceModel forceModel : forceModels) {
                final double[] shortPeriodicVariations =
                        forceModel.getShortPeriodicVariations(date, y);
                for (int i = 0; i < shortPeriodicVariations.length; i++) {
                    osculatingElements[i] += shortPeriodicVariations[i];
                }
            }

            final double mass = y[6];
            if (mass <= 0.0) {
                throw new PropagationException(OrekitMessages.SPACECRAFT_MASS_BECOMES_NEGATIVE, mass);
            }

            final Orbit orbit       = OrbitType.EQUINOCTIAL.mapArrayToOrbit(y, PositionAngle.MEAN, date, getMu(), getFrame());
            final Attitude attitude = getAttitudeProvider().getAttitude(orbit, date, getFrame());

            return new SpacecraftState(orbit, attitude, mass);

        }

        /** {@inheritDoc} */
        public void mapStateToArray(final SpacecraftState state, final double[] y)
            throws OrekitException {

            final Orbit meanOrbit;
            if (forceModels.isEmpty()) {
                // the state is considered to be already a mean state
                meanOrbit = state.getOrbit();
            } else {
                // the state is considered to be an osculating state
                final Propagator propagator = createPropagator(state);
                meanOrbit =
                        new OsculatingToMeanElementsConverter(state, satelliteRevolution, propagator).convert().getOrbit();
            }

            OrbitType.EQUINOCTIAL.mapOrbitToArray(meanOrbit, PositionAngle.MEAN, y);
            y[6] = state.getMass();
        
        }

        /**
         * Create a reference numerical propagator to convert orbit to mean elements.
         * @param initialState initial state
         * @return propagator
         * @throws OrekitException if some numerical force model cannot be built
         */
        private Propagator createPropagator(final SpacecraftState initialState)
            throws OrekitException {
            final Orbit initialOrbit = initialState.getOrbit();
            final double[][] tol = NumericalPropagator.tolerances(1.0, initialOrbit, initialOrbit.getType());
            final double minStep = 1.;
            final double maxStep = 200.;
            final AdaptiveStepsizeIntegrator integ = new DormandPrince853Integrator(minStep, maxStep, tol[0], tol[1]);
            integ.setInitialStepSize(100.);

            final NumericalPropagator propagator = new NumericalPropagator(integ);
            propagator.setInitialState(initialState);

            // Define the same force model as the DSST
            for (final DSSTForceModel force : forceModels) {
                if (force instanceof DSSTCentralBody) {
                    // Central body
                    final double[][] cnm = ((DSSTCentralBody) force).getCnm();
                    final double[][] snm = ((DSSTCentralBody) force).getSnm();
                    final double ae = ((DSSTCentralBody) force).getAe();
                    final ForceModel cunningham = new CunninghamAttractionModel(FramesFactory.getITRF2005(), ae,
                                                                                getMu(), cnm, snm);
                    propagator.addForceModel(cunningham);
                } else if (force instanceof DSSTThirdBody) {
                    // Third body
                    final CelestialBody body = ((DSSTThirdBody) force).getBody();
                    final ForceModel third = new ThirdBodyAttraction(body);
                    propagator.addForceModel(third);
                } else if (force instanceof DSSTAtmosphericDrag) {
                    // Atmospheric drag
                    final double dragCoef = ((DSSTAtmosphericDrag) force).getCd();
                    final double crossSec = ((DSSTAtmosphericDrag) force).getArea();
                    final Atmosphere atm = ((DSSTAtmosphericDrag) force).getAtmosphere();
                    final ForceModel drag = new DragForce(atm, new SphericalSpacecraft(dragCoef, crossSec, 0., 0.));
                    propagator.addForceModel(drag);
                } else if (force instanceof DSSTSolarRadiationPressure) {
                    // Solar radiation pressure
                    final double ae = ((DSSTSolarRadiationPressure) force).getAe();
                    double cr = ((DSSTSolarRadiationPressure) force).getCr();
                    // Convert DSST convention to numerical's one
                    cr = 1 + (1 - cr) * 2.25;
                    final double area = ((DSSTSolarRadiationPressure) force).getArea();
                    final SphericalSpacecraft scr = new SphericalSpacecraft(area, 0d, 0d, cr);
                    final ForceModel pressure = new SolarRadiationPressure(CelestialBodyFactory.getSun(), ae, scr);
                    propagator.addForceModel(pressure);
                }
            }
            return propagator;
        }

    }

    /** {@inheritDoc} */
    protected MainStateEquations getMainStateEquations() {
         return new Main();
    }

    /** Internal class for mean parameters integration. */
    private class Main implements MainStateEquations {

        /** Derivatives array. */
        private final double[] yDot;

        /** Simple constructor.
         */
        public Main() {
            yDot = new double[7];
        }

        /** {@inheritDoc} */
        public double[] computeDerivatives(final SpacecraftState state) throws OrekitException {

            Arrays.fill(yDot, 0.0);

            // compute the contributions of all perturbing forces
            for (final DSSTForceModel forceModel : forceModels) {
                final double[] daidt = forceModel.getMeanElementRate(state);
                for (int i = 0; i < daidt.length; i++) {
                    yDot[i] += daidt[i];
                }
            }

            // finalize derivatives by adding the Kepler contribution
            EquinoctialOrbit orbit = (EquinoctialOrbit) OrbitType.EQUINOCTIAL.convertType(state.getOrbit());
            orbit.addKeplerContribution(PositionAngle.MEAN, getMu(), yDot);

            return yDot.clone();

        }

    }

    /**
     * Estimate tolerance vectors for an AdaptativeStepsizeIntegrator.
     * <p>
     * The errors are estimated from partial derivatives properties of orbits, starting from a
     * scalar position error specified by the user. Considering the energy conservation equation V =
     * sqrt(mu (2/r - 1/a)), we get at constant energy (i.e. on a Keplerian trajectory):
     *
     * <pre>
     * V<sup>2</sup> r |dV| = mu |dr|
     * </pre>
     *
     * So we deduce a scalar velocity error consistent with the position error. From here, we apply
     * orbits Jacobians matrices to get consistent errors on orbital parameters.
     * </p>
     * <p>
     * The tolerances are only <em>orders of magnitude</em>, and integrator tolerances are only
     * local estimates, not global ones. So some care must be taken when using these tolerances.
     * Setting 1mm as a position error does NOT mean the tolerances will guarantee a 1mm error
     * position after several orbits integration.
     * </p>
     *
     * @param dP
     *            user specified position error
     * @param orbit
     *            reference orbit
     * @return a two rows array, row 0 being the absolute tolerance error and row 1 being the
     *         relative tolerance error
     */
    public static double[][] tolerances(final double dP,
                                        final Orbit orbit) {

        return NumericalPropagator.tolerances(dP, orbit, OrbitType.EQUINOCTIAL);

    }

}

package fr.cs.orekit.frames.series;

/** Class for general terms.
 * @author Luc Maisonobe
 */
class GeneralTerm extends SeriesTerm {

    /** Serializable UID. */
    private static final long serialVersionUID = 7720307510510704383L;

    /** Coefficient for mean anomaly of the Moon. */
    private final int cL;

    /** Coefficient for mean anomaly of the Sun. */
    private final int cLPrime;

    /** Coefficient for L - &Omega; where L is the mean longitude of the Moon. */
    private final int cF;

    /** Coefficient for mean elongation of the Moon from the Sun. */
    private final int cD;

    /** Coefficient for mean longitude of the ascending node of the Moon. */
    private final int cOmega;

    /** Coefficient for mean Mercury longitude. */
    private final int cMe;

    /** Coefficient for mean Venus longitude. */
    private final int cVe;

    /** Coefficient for mean Earth longitude. */
    private final int cE;

    /** Coefficient for mean Mars longitude. */
    private final int cMa;

    /** Coefficient for mean Jupiter longitude. */
    private final int cJu;

    /** Coefficient for mean Saturn longitude. */
    private final int cSa;

    /** Coefficient for mean Uranus longitude. */
    private final int cUr;

    /** Coefficient for mean Neptune longitude. */
    private final int cNe;

    /** Coefficient for general accumulated precession in longitude. */
    private final int cPa;

    /** Build a general term for nutation series.
     * @param sinCoeff coefficient for the sine of the argument
     * @param cosCoeff coefficient for the cosine of the argument
     * @param cL coefficient for mean anomaly of the Moon
     * @param cLPrime coefficient for mean anomaly of the Sun
     * @param cF coefficient for L - &Omega; where L is the mean longitude of the Moon
     * @param cD coefficient for mean elongation of the Moon from the Sun
     * @param cOmega coefficient for mean longitude of the ascending node of the Moon
     * @param cMe coefficient for mean Mercury longitude
     * @param cVe coefficient for mean Venus longitude
     * @param cE coefficient for mean Earth longitude
     * @param cMa coefficient for mean Mars longitude
     * @param cJu coefficient for mean Jupiter longitude
     * @param cSa coefficient for mean Saturn longitude
     * @param cUr coefficient for mean Uranus longitude
     * @param cNe coefficient for mean Neptune longitude
     * @param cPa coefficient for general accumulated precession in longitude
     */
    public GeneralTerm(double sinCoeff, double cosCoeff,
                       int cL, int cLPrime, int cF, int cD, int cOmega,
                       int cMe, int cVe, int cE, int cMa, int cJu,
                       int cSa, int cUr, int cNe, int cPa) {
        super(sinCoeff, cosCoeff);
        this.cL      = cL;
        this.cLPrime = cLPrime;
        this.cF      = cF;
        this.cD      = cD;
        this.cOmega  = cOmega;
        this.cMe     = cMe;
        this.cVe     = cVe;
        this.cE      = cE;
        this.cMa     = cMa;
        this.cJu     = cJu;
        this.cSa     = cSa;
        this.cUr     = cUr;
        this.cNe     = cNe;
        this.cPa     = cPa;
    }

    /** {@inheritDoc} */
    protected double argument(BodiesElements elements) {
        return cL * elements.l + cLPrime * elements.lPrime + cF * elements.f +
               cD * elements.d + cOmega * elements.omega +
               cMe * elements.lMe + cVe * elements.lVe + cE  * elements.lE +
               cMa * elements.lMa + cJu * elements.lJu +
               cSa * elements.lSa + cUr * elements.lUr +
               cNe * elements.lNe + cPa * elements.pa;

    }

}

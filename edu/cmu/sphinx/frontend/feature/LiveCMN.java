/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electronic Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */


package edu.cmu.sphinx.frontend.feature;

import edu.cmu.sphinx.frontend.BaseDataProcessor;
import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DataEndSignal;
import edu.cmu.sphinx.frontend.DataProcessingException;
import edu.cmu.sphinx.frontend.DataProcessor;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.util.SphinxProperties;


/**
 * Subtracts the mean of all the input so far from the Data objects.
 * Unlike the BatchCMN, it does not read in the entire stream of Data
 * objects before it calculates the mean. It estimates the mean from
 * already seen data and subtracts the mean from the Data objects on
 * the fly. Therefore, there is no delay introduced by LiveCMN.
 * 
 * The Sphinx properties that affect this processor
 * are: <pre>
 * edu.cmu.sphinx.frontend.feature.LiveCMN.initialCepstralMean
 * edu.cmu.sphinx.frontend.feature.LiveCMN.windowSize
 * edu.cmu.sphinx.frontend.feature.LiveCMN.shiftWindow
 * </pre>
 *
 * <p>The mean of all the input cepstrum so far is not reestimated
 * for each cepstrum. This mean is recalculated after every
 * <code>edu.cmu.sphinx.frontend.cmn.shiftWindow</code> cepstra.
 * This mean is estimated by dividing the sum of all input cepstrum so
 * far. After obtaining the mean, the sum is exponentially decayed by
 * multiplying it by the ratio:
 * <pre>
 * cmnWindow/(cmnWindow + number of frames since the last recalculation)
 * </pre>
 *
 * <p>This is a 1-to-1 processor.
 *
 * @see BatchCMN
 */
public class LiveCMN extends BaseDataProcessor {

    private static final String PROP_PREFIX
        = "edu.cmu.sphinx.frontend.feature.LiveCMN.";


    /**
     * The name of the SphinxProperty for the initial cepstral mean.
     * This is a front-end dependent magic number.
     */
    public static final String PROP_INITIAL_MEAN
        = PROP_PREFIX + "initialMean";


    /**
     * The default value for PROP_INITIAL_MEAN.
     */
    public static final float PROP_INITIAL_MEAN_DEFAULT = 12.0f;
    
    
    /**
     * The name of the SphinxProperty for the live CMN window size.
     */
    public static final String PROP_CMN_WINDOW = PROP_PREFIX + "cmnWindow";


    /**
     * The default value for PROP_CMN_WINDOW.
     */
    public static final int PROP_CMN_WINDOW_DEFAULT = 100;
    

    /**
     * The name of the SphinxProperty for the CMN shifting window.
     * The shifting window specifies how many cepstrum after which
     * we re-calculate the cepstral mean.
     */
    public static final String PROP_CMN_SHIFT_WINDOW
        = PROP_PREFIX + "shiftWindow";


    /**
     * The default value of PROP_CMN_SHIFT_WINDOW.
     */
    public static final int PROP_CMN_SHIFT_WINDOW_DEFAULT = 160;
 

    private double[] currentMean;   // array of current means
    private double[] sum;           // array of current sums
    private double initialMean;     // initial mean, magic number
    private int numberFrame;        // total number of input Cepstrum
    private int cmnShiftWindow;     // # of Cepstrum to recalculate mean
    private int cmnWindow;


    /**
     * Initializes this LiveCMN.
     *
     * @param name         the name of this LiveCMN, if it is null, the
     *                     name "LiveCMN" will be given by default
     * @param frontEnd     the front end this LiveCMN belongs to
     * @param props        the SphinxProperties to read properties from
     * @param predecessor  the DataProcessor from which this normalizer
     *                     obtains Data to normalize
     */
    public void initialize(String name, String frontEnd,
			   SphinxProperties props, DataProcessor predecessor) {
        super.initialize((name == null ? "LiveCMN" : name), frontEnd,
                          props, predecessor);
        setProperties(props);
    }


    /**
     * Initializes the currentMean and sum arrays with the given cepstrum
     * length.
     *
     * @param cepstrumLength the length of the cepstrum
     */
    private void initMeansSums(int cepstrumLength) {
	currentMean = new double[cepstrumLength];
	currentMean[0] = initialMean;
	sum = new double[cepstrumLength];
    }


    /**
     * Reads the parameters needed from the static SphinxProperties object.
     *
     * @param props the SphinxProperties to read properties from
     */
    private void setProperties(SphinxProperties props) {
	initialMean = props.getDouble
            (getName(), PROP_INITIAL_MEAN, PROP_INITIAL_MEAN_DEFAULT);
	cmnWindow = props.getInt
            (getName(), PROP_CMN_WINDOW, PROP_CMN_WINDOW_DEFAULT);
	cmnShiftWindow = props.getInt
            (getName(), PROP_CMN_SHIFT_WINDOW, PROP_CMN_SHIFT_WINDOW_DEFAULT);
    }
	

    /**
     * Returns the next Data object, which is a normalized Data
     * produced by this class. Signals are returned unmodified.
     *
     * @return the next available Data object, returns null if no
     *         Data object is available
     *
     * @throws DataProcessingException if there is a data processing error
     */
    public Data getData() throws DataProcessingException {
	
        Data input = getPredecessor().getData();

        getTimer().start();

        if (input != null) {
            if (input instanceof DoubleData) {
		DoubleData data = (DoubleData) input;
                if (sum == null) {
                    initMeansSums(data.getValues().length);
                }
                normalize(data);
            } else if (input instanceof DataEndSignal) {
                updateMeanSumBuffers();
            }
        }

        getTimer().stop();

        return input;
    }
    

    /**
     * Normalizes the given Data with using the currentMean array.
     * Updates the sum array with the given Data.
     *
     * @param cepstrumObject the Data object to normalize
     */
    private void normalize(DoubleData cepstrumObject) {

        double[] cepstrum = cepstrumObject.getValues();

        if (cepstrum.length != sum.length) {
            throw new Error("Data length (" + cepstrum.length +
                            ") not equal sum array length (" + 
                            sum.length + ")");
        }

        for (int j = 0; j < cepstrum.length; j++) {
            sum[j] += cepstrum[j];
            cepstrum[j] -= currentMean[j];
        }

        numberFrame++;

        if (numberFrame > cmnShiftWindow) {
            updateMeanSumBuffers();
        }
    }


    /**
     * Updates the currentMean buffer with the values in the sum buffer.
     * Then decay the sum buffer exponentially, i.e., divide the sum
     * with numberFrames.
     */
    private void updateMeanSumBuffers() {

        if (numberFrame > 0) {

            // update the currentMean buffer with the sum buffer
            double sf = (double) (1.0/numberFrame);
            
            System.arraycopy(sum, 0, currentMean, 0, sum.length);
            
            multiplyArray(currentMean, sf);
            
            // decay the sum buffer exponentially
            if (numberFrame >= cmnShiftWindow) {
                multiplyArray(sum, (sf * cmnWindow));
                numberFrame = cmnWindow;
            }
        }
    }


    /**
     * Multiplies each element of the given array by the multiplier.
     *
     * @param array the array to multiply
     * @param multiplier the amount to multiply by
     */
    private static final void multiplyArray(double[] array,
                                            double multiplier) {
        for (int i = 0; i < array.length; i++) {
            array[i] *= multiplier;
        }
    }
}
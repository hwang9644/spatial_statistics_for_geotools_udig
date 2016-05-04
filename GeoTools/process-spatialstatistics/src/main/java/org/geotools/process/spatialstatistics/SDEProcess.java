/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.process.spatialstatistics;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.process.Process;
import org.geotools.process.ProcessException;
import org.geotools.process.ProcessFactory;
import org.geotools.process.spatialstatistics.core.Params;
import org.geotools.process.spatialstatistics.distribution.StandardDeviationalEllipseOperation;
import org.geotools.text.Text;
import org.geotools.util.NullProgressListener;
import org.geotools.util.logging.Logging;
import org.opengis.util.ProgressListener;

/**
 * Creates standard deviational ellipses to summarize the spatial characteristics of geographic features: central tendency, dispersion, and
 * directional trends.
 * 
 * @author Minpa Lee, MangoSystem
 * 
 * @source $URL$
 */
public class SDEProcess extends AbstractStatisticsProcess {
    protected static final Logger LOGGER = Logging.getLogger(SDEProcess.class);

    private boolean started = false;

    public SDEProcess(ProcessFactory factory) {
        super(factory);
    }

    public ProcessFactory getFactory() {
        return factory;
    }

    public static SimpleFeatureCollection process(SimpleFeatureCollection inputFeatures,
            String ellipseSize, String weightField, String caseField, ProgressListener monitor) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(SDEProcessFactory.inputFeatures.key, inputFeatures);
        map.put(SDEProcessFactory.ellipseSize.key, ellipseSize);
        map.put(SDEProcessFactory.weightField.key, weightField);
        map.put(SDEProcessFactory.caseField.key, caseField);

        Process process = new SDEProcess(null);
        Map<String, Object> resultMap;
        try {
            resultMap = process.execute(map, monitor);
            return (SimpleFeatureCollection) resultMap.get(SDEProcessFactory.RESULT.key);
        } catch (ProcessException e) {
            LOGGER.log(Level.FINER, e.getMessage(), e);
        }

        return null;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, ProgressListener monitor)
            throws ProcessException {
        if (started)
            throw new IllegalStateException("Process can only be run once");
        started = true;

        if (monitor == null)
            monitor = new NullProgressListener();
        try {
            monitor.started();
            monitor.setTask(Text.text("Grabbing arguments"));
            monitor.progress(10.0f);

            SimpleFeatureCollection inputFeatures = (SimpleFeatureCollection) Params.getValue(
                    input, SDEProcessFactory.inputFeatures, null);
            if (inputFeatures == null) {
                throw new NullPointerException("inputFeatures parameters required");
            }
            String weightField = (String) Params.getValue(input, SDEProcessFactory.weightField,
                    null);
            String caseField = (String) Params.getValue(input, SDEProcessFactory.caseField, null);
            String ellipseSize = (String) Params.getValue(input, SDEProcessFactory.ellipseSize,
                    SDEProcessFactory.ellipseSize.sample);

            monitor.setTask(Text.text("Processing ..."));
            monitor.progress(25.0f);

            if (monitor.isCanceled()) {
                return null; // user has canceled this operation
            }

            // start process
            SimpleFeatureCollection resultFc = null;

            // 1_STANDARD_DEVIATION
            double stdDeviation = 1.0;
            if (ellipseSize.contains("2")) {
                stdDeviation = 2.0;
            } else if (ellipseSize.contains("3")) {
                stdDeviation = 3.0;
            }

            StandardDeviationalEllipseOperation process = new StandardDeviationalEllipseOperation();
            process.setStdDeviation(stdDeviation);
            process.setOutputTypeName("StandardDistanceEllipse");

            resultFc = process.execute(inputFeatures, weightField, caseField);
            // end process

            monitor.setTask(Text.text("Encoding result"));
            monitor.progress(90.0f);

            Map<String, Object> resultMap = new HashMap<String, Object>();
            resultMap.put(SDEProcessFactory.RESULT.key, resultFc);
            monitor.complete(); // same as 100.0f

            return resultMap;
        } catch (Exception eek) {
            monitor.exceptionOccurred(eek);
            return null;
        } finally {
            monitor.dispose();
            started = false;
        }
    }
}

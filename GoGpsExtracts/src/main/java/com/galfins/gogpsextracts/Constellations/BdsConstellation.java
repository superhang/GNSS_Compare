package com.galfins.gogpsextracts.Constellations;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.util.Log;

import com.galfins.gogpsextracts.Corrections.Correction;
import com.galfins.gogpsextracts.Constants;
import com.galfins.gogpsextracts.Coordinates;
import com.galfins.gogpsextracts.NavigationProducer;
import com.galfins.gogpsextracts.RinexNavigationBds;
import com.galfins.gogpsextracts.SatellitePosition;
import com.galfins.gogpsextracts.Time;
import com.galfins.gogpsextracts.TopocentricCoordinates;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mateusz Krainski on 17/02/2018.
 * This class is for...
 * <p>
 * GPS Pseudorange computation algorithm by: Mareike Burba
 * - variable name changes and comments were added
 * to fit the description in the GSA white paper
 * by: Sebastian Ciuban
 */

public class BdsConstellation extends Constellation {

    private final static char satType = 'C';
    private static final String NAME = "BDS B1";
    private static final String TAG = "BdsConstellation";
    private static double B1_FREQUENCY = 1.561098e9;
    private static double FREQUENCY_MATCH_RANGE = 0.1e9;

    private boolean fullBiasNanosInitialized = false;
    private long FullBiasNanos;

    private Coordinates rxPos;
    protected double tRxBDS;
    protected double weekNumberNanos;
    private List<SatelliteParameters> unusedSatellites = new ArrayList<>();

    public double getWeekNumber() {
        return weekNumberNanos;
    }

    public double gettRxBDS() {
        return tRxBDS;
    }

    private static final int constellationId = GnssStatus.CONSTELLATION_BEIDOU;
    private static double MASK_ELEVATION = 20; // degrees
    private static double MASK_CN0 = 10; // dB-Hz

    /**
     * Time of the measurement
     */
    private Time timeRefMsec;

    protected int visibleButNotUsed = 0;

    // Condition for the pseudoranges that takes into account a maximum uncertainty for the TOW
    // (as done in gps-measurement-tools MATLAB code)
    private static final int MAXTOWUNCNS = 50;                                     // [nanoseconds]

    private NavigationProducer rinexNavBds = null;

    /**
     * List holding observed satellites
     */
    protected List<SatelliteParameters> observedSatellites = new ArrayList<>();

    /**
     * Corrections which are to be applied to received pseudoranges
     */
    private ArrayList<Correction> corrections;

    public BdsConstellation() {
        if (rinexNavBds == null)
            rinexNavBds = new RinexNavigationBds();

    }

    @Override
    public void addCorrections(ArrayList<Correction> corrections) {
        synchronized (this) {
            this.corrections = corrections;
        }
    }

    @Override
    public Time getTime() {
        synchronized (this) {
            return timeRefMsec;
        }
    }

    @Override
    public String getName() {
        synchronized (this) {
            return NAME;
        }
    }

    public static boolean approximateEqual(double a, double b, double eps) {
        return Math.abs(a - b) < eps;
    }

    @Override
    public void updateMeasurements(GnssMeasurementsEvent event) {

        synchronized (this) {
            visibleButNotUsed = 0;
            observedSatellites.clear();
            unusedSatellites.clear();
            GnssClock gnssClock = event.getClock();
            long TimeNanos = gnssClock.getTimeNanos();
            timeRefMsec = new Time(System.currentTimeMillis());
            double BiasNanos = gnssClock.getBiasNanos();
            double gpsTime, pseudorange;

            // Use only the first instance of the FullBiasNanos (as done in gps-measurement-tools)
//            if (!fullBiasNanosInitialized) {
            FullBiasNanos = gnssClock.getFullBiasNanos();
//                fullBiasNanosInitialized = true;
//            }+

            // Start computing the pseudoranges using the raw data from the phone's GNSS receiver
            for (GnssMeasurement measurement : event.getMeasurements()) {

                if (measurement.getConstellationType() != constellationId)
                    continue;

//                if (measurement.hasCarrierFrequencyHz())
//                    if (!approximateEqual(measurement.getCarrierFrequencyHz(), B1_FREQUENCY, FREQUENCY_MATCH_RANGE))
//                        continue;

                if (measurement.getCn0DbHz() >= 18 && (measurement.getState() & (1L << 3)) != 0) {

                    // excluding satellites which don't have the L5 component
//                if(measurement.getSvid() == 2 || measurement.getSvid() == 4
//                        || measurement.getSvid() == 5 || measurement.getSvid() == 7
//                        || measurement.getSvid() == 11 || measurement.getSvid() == 12
//                        || measurement.getSvid() == 13 || measurement.getSvid() == 14
//                        || measurement.getSvid() == 15 || measurement.getSvid() == 16
//                        || measurement.getSvid() == 17 || measurement.getSvid() == 18
//                        || measurement.getSvid() == 19 || measurement.getSvid() == 20
//                        || measurement.getSvid() == 21 || measurement.getSvid() == 22
//                        || measurement.getSvid() == 23 || measurement.getSvid() == 28
//                        || measurement.getSvid() == 29 || measurement.getSvid() == 31)
//                    continue;

                    long ReceivedSvTimeNanos = measurement.getReceivedSvTimeNanos();
                    double TimeOffsetNanos = measurement.getTimeOffsetNanos();

                    // GPS Time generation (GSA White Paper - page 20)
                    gpsTime = TimeNanos - (FullBiasNanos + BiasNanos); // TODO intersystem bias?

                    // Measurement time in full GPS time without taking into account weekNumberNanos(the number of
                    // nanoseconds that have occurred from the beginning of GPS time to the current
                    // week number)
                    tRxBDS = gpsTime + TimeOffsetNanos -14e9;

                    weekNumberNanos = Math.floor((-1. * FullBiasNanos) / Constants.NUMBER_NANO_SECONDS_PER_WEEK)
                            * Constants.NUMBER_NANO_SECONDS_PER_WEEK;

                    // GPS pseudorange computation
                    pseudorange = (tRxBDS - weekNumberNanos - ReceivedSvTimeNanos) / 1.0E9
                            * Constants.SPEED_OF_LIGHT;

                    // TODO Check that the measurement have a valid state such that valid pseudoranges are used in the PVT algorithm

                /*

                According to https://developer.android.com/ the GnssMeasurements States required
                for GPS valid pseudoranges are:

                int STATE_CODE_LOCK         = 1      (1 << 0)
                int int STATE_TOW_DECODED   = 8      (1 << 3)

                */
                   int measState = measurement.getState();

                   // Bitwise AND to identify the states
                   boolean codeLock = (measState & GnssMeasurement.STATE_CODE_LOCK) != 0;
                   boolean towDecoded = (measState & GnssMeasurement.STATE_TOW_DECODED) != 0;
                   boolean towKnown = false;
                   if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                       towKnown = (measState & GnssMeasurement.STATE_TOW_KNOWN) != 0;
                   }
               boolean towUncertainty = measurement.getReceivedSvTimeUncertaintyNanos() <  MAXTOWUNCNS;

                   if (codeLock && (towDecoded || towKnown) && pseudorange < 1e9) { // && towUncertainty
                       SatelliteParameters satelliteParameters = new SatelliteParameters(
                               measurement.getSvid(),
                               new Pseudorange(pseudorange, 0.0));

                       satelliteParameters.setUniqueSatId("C" + satelliteParameters.getSatId() + "_B1");

                       satelliteParameters.setSignalStrength(measurement.getCn0DbHz());

                       satelliteParameters.setConstellationType(measurement.getConstellationType());

                       if (measurement.hasCarrierFrequencyHz())
                           satelliteParameters.setCarrierFrequency(measurement.getCarrierFrequencyHz());

                       observedSatellites.add(satelliteParameters);
                       Log.d(TAG, "updateConstellations(" + measurement.getSvid() + "): " + weekNumberNanos + ", " + tRxBDS + ", " + pseudorange);
                       Log.d(TAG, "updateConstellations: Passed with measurement state: " + measState);
                   }
                } else {
                    SatelliteParameters satelliteParameters = new SatelliteParameters(
                            measurement.getSvid(),
                            null);

                    satelliteParameters.setUniqueSatId("C" + satelliteParameters.getSatId() + "_B1");

                    satelliteParameters.setSignalStrength(measurement.getCn0DbHz());

                    satelliteParameters.setConstellationType(measurement.getConstellationType());

                    if (measurement.hasCarrierFrequencyHz())
                        satelliteParameters.setCarrierFrequency(measurement.getCarrierFrequencyHz());

                    unusedSatellites.add(satelliteParameters);
                    visibleButNotUsed++;
                }
            }
        }
    }

    @Override
    public double getSatelliteSignalStrength(int index) {
        synchronized (this) {
            return observedSatellites.get(index).getSignalStrength();
        }
    }

    @Override
    public int getConstellationId() {
        synchronized (this) {
            return constellationId;
        }
    }


    @Override
    public void calculateSatPosition(Location initialLocation, Coordinates position) {

        // Make a list to hold the satellites that are to be excluded based on elevation/CN0 masking criteria
        List<SatelliteParameters> excludedSatellites = new ArrayList<>();

        synchronized (this) {

            rxPos = Coordinates.globalXYZInstance(position.getX(), position.getY(), position.getZ());

            for (SatelliteParameters observedSatellite : observedSatellites) {
                // Computation of the GPS satellite coordinates in ECEF frame
                // Determine the current GPS week number
                int gpsWeek = (int) (weekNumberNanos / Constants.NUMBER_NANO_SECONDS_PER_WEEK);

                // Time of signal reception in GPS Seconds of the Week (SoW)
                double gpsSow = (tRxBDS - weekNumberNanos) * 1e-9;

                Time tGPS = new Time(gpsWeek, gpsSow);

                // Convert the time of reception from GPS SoW to UNIX time (milliseconds)
                long timeRx = tGPS.getMsec();

                SatellitePosition rnp = ((RinexNavigationBds) rinexNavBds).getSatPositionAndVelocities(
                        timeRx,
                        observedSatellite.getPseudorange(),
                        observedSatellite.getSatId(),
                        satType,
                        0.0,
                        initialLocation);

                if (rnp == null) {
                    excludedSatellites.add(observedSatellite);
                    continue;
                }

                observedSatellite.setSatellitePosition(rnp);

                observedSatellite.setRxTopo(
                        new TopocentricCoordinates(
                                rxPos,
                                observedSatellite.getSatellitePosition()));

                // Add to the exclusion list the satellites that do not pass the masking criteria
//                if (observedSatellite.getRxTopo().getElevation() < MASK_ELEVATION) {
//                    excludedSatellites.add(observedSatellite);
//                }

                double accumulatedCorrection = 0;

                for (Correction correction : corrections) {

                    correction.calculateCorrection(
                            new Time(timeRx),
                            rxPos,
                            observedSatellite.getSatellitePosition(),
                            rinexNavBds,
                            initialLocation);

                    accumulatedCorrection += correction.getCorrection();
                }

                observedSatellite.setAccumulatedCorrection(accumulatedCorrection);
            }

            // Remove from the list all the satellites that did not pass the masking criteria
            visibleButNotUsed += excludedSatellites.size();
            observedSatellites.removeAll(excludedSatellites);
            unusedSatellites.addAll(excludedSatellites);
        }
    }


    public static void registerClass() {
        register(
                NAME,
                BdsConstellation.class);
    }


    @Override
    public Coordinates getRxPos() {
        synchronized (this) {
            return rxPos;
        }
    }

    @Override
    public void setRxPos(Coordinates rxPos) {
        synchronized (this) {
            this.rxPos = rxPos;
        }
    }

    @Override
    public SatelliteParameters getSatellite(int index) {
        synchronized (this) {
            return observedSatellites.get(index);
        }
    }

    @Override
    public List<SatelliteParameters> getSatellites() {
        synchronized (this) {
            return observedSatellites;
        }
    }

    @Override
    public List<SatelliteParameters> getUnusedSatellites() {
        return unusedSatellites;
    }

    @Override
    public int getVisibleConstellationSize() {
        synchronized (this) {
            return getUsedConstellationSize() + visibleButNotUsed;
        }
    }

    @Override
    public int getUsedConstellationSize() {
        synchronized (this) {
            return observedSatellites.size();
        }
    }

}

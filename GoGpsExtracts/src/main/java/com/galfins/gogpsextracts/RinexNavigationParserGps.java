package com.galfins.gogpsextracts;

import android.location.Location;
import android.util.Log;

import com.google.location.suplclient.ephemeris.EphemerisResponse;
import com.google.location.suplclient.ephemeris.GnssEphemeris;
import com.google.location.suplclient.ephemeris.GpsEphemeris;

import java.util.ArrayList;

/**
 * <p>
 * Class for parsing RINEX navigation files
 * </p>
 *
 * @author Eugenio Realini, Cryms.com
 */
public class RinexNavigationParserGps extends EphemerisSystemGps implements NavigationProducer {

    public static String newline = System.getProperty("line.separator");

    private final String TAG = this.getClass().getSimpleName();

    private ArrayList<EphGps> eph = new ArrayList<EphGps>(); /* GPS broadcast ephemerides */
    //private double[] iono = new double[8]; /* Ionosphere model parameters */
    private Iono iono = null; /* Ionosphere model parameters */
    //	private double A0; /* Delta-UTC parameters: A0 */
    //	private double A1; /* Delta-UTC parameters: A1 */
    //	private double T; /* Delta-UTC parameters: T */
    //	private double W; /* Delta-UTC parameters: W */
    //	private int leaps; /* Leap seconds */

    public RinexNavigationParserGps(EphemerisResponse ephResponse) {
        for (GnssEphemeris eph : ephResponse.ephList) {
            if (eph instanceof GpsEphemeris) {
                this.eph.add(new EphGps((GpsEphemeris) eph));
            }
        }
        this.iono = new Iono(ephResponse.ionoProto);
    }

    private double gpsToUnixTime(Time toc, int tow) {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * @param unixTime
     * @param satID
     * @return Reference ephemeris set for given time and satellite
     */
    public EphGps findEph(long unixTime, int satID, char satType) {

        long dt = 0;
        long dtMin = 0;
        long dtMax = 0;
        long delta = 0;
        EphGps refEph = null;

        //long gpsTime = (new Time(unixTime)).getGpsTime();

        for (int i = 0; i < eph.size(); i++) {
            // Find ephemeris sets for given satellite
            if (eph.get(i).getSatID() == satID && eph.get(i).getSatType() == satType) {
                // Consider BeiDou time (BDT) for BeiDou satellites (14 sec difference wrt GPS time)
                if (satType == 'C') {
                    delta = 14000;
                    unixTime = unixTime - delta;
                }
                // Compare current time and ephemeris reference time
                dt = Math.abs(eph.get(i).getRefTime().getMsec() - unixTime /*getGpsTime() - gpsTime*/) / 1000;
                // If it's the first round, set the minimum time difference and
                // select the first ephemeris set candidate; if the current ephemeris set
                // is closer in time than the previous candidate, select new candidate
                if (refEph == null || dt < dtMin) {
                    dtMin = dt;
                    refEph = eph.get(i);
                }
            }
        }

        if (refEph == null)
            return null;

        if (refEph.getSvHealth() != 0) {
            return EphGps.UnhealthyEph;
        }

        //maximum allowed interval from ephemeris reference time
//        long fitInterval = refEph.getFitInt();

//        if (fitInterval != 0) {
//            dtMax = fitInterval * 3600 / 2;
//        } else {
        switch (refEph.getSatType()) {
            case 'R':
                dtMax = 950;
            case 'J':
                dtMax = 3600;
            default:
                dtMax = 7200;
        }
//        }
        if (dtMin > dtMax) {
            refEph = null;
        }

        return refEph;
    }

    public int getEphSize() {
        return eph.size();
    }

    public void addEph(EphGps eph) {
        this.eph.add(eph);
    }

    //	public void setIono(int i, double val){
    //		this.iono[i] = val;
    //	}
    public Iono getIono(long unixTime, Location initialLocation) {
        return iono;
    }

    //	/**
    //	 * @return the a0
    //	 */
    //	public double getA0() {
    //		return A0;
    //	}
    //	/**
    //	 * @param a0 the a0 to set
    //	 */
    //	public void setA0(double a0) {
    //		A0 = a0;
    //	}
    //	/**
    //	 * @return the a1
    //	 */
    //	public double getA1() {
    //		return A1;
    //	}
    //	/**
    //	 * @param a1 the a1 to set
    //	 */
    //	public void setA1(double a1) {
    //		A1 = a1;
    //	}
    //	/**
    //	 * @return the t
    //	 */
    //	public double getT() {
    //		return T;
    //	}
    //	/**
    //	 * @param t the t to set
    //	 */
    //	public void setT(double t) {
    //		T = t;
    //	}
    //	/**
    //	 * @return the w
    //	 */
    //	public double getW() {
    //		return W;
    //	}
    //	/**
    //	 * @param w the w to set
    //	 */
    //	public void setW(double w) {
    //		W = w;
    //	}
    //	/**
    //	 * @return the leaps
    //	 */
    //	public int getLeaps() {
    //		return leaps;
    //	}
    //	/**
    //	 * @param leaps the leaps to set
    //	 */
    //	public void setLeaps(int leaps) {
    //		this.leaps = leaps;
    //	}

    public boolean isTimestampInEpocsRange(long unixTime) {
        return eph.size() > 0 /*&&
                eph.get(0).getRefTime().getMsec() <= unixTime *//*&&
		unixTime <= eph.get(eph.size()-1).getRefTime().getMsec() missing interval +epochInterval*/;
    }


    /* (non-Javadoc)
     * @see org.gogpsproject.NavigationProducer#getGpsSatPosition(long, int, double)
     */
    @Override
    public SatellitePosition getGpsSatPosition(Observations obs, int satID, char satType, double receiverClockError) {
        long unixTime = obs.getRefTime().getMsec();
        double range = obs.getSatByIDType(satID, satType).getPseudorange(0);

        if (range == 0)
            return null;

        EphGps eph = findEph(unixTime, satID, satType);
        if (eph.equals(EphGps.UnhealthyEph))
            return SatellitePosition.UnhealthySat;

        if (eph != null) {

            //			char satType = eph.getSatType();

            SatellitePosition sp = computePositionGps(obs, satID, satType, eph, receiverClockError);
            //			SatellitePosition sp = computePositionGps(unixTime, satType, satID, eph, range, receiverClockError);
            //if(receiverPosition!=null) earthRotationCorrection(receiverPosition, sp);
            return sp;// new SatellitePosition(eph, unixTime, satID, range);
        }
        return null;
    }


    public SatellitePosition getSatPositionAndVelocities(long unixTime, double range, int satID, char satType, double receiverClockError) {
        //long unixTime = obs.getRefTime().getMsec();
        //double range = obs.getSatByIDType(satID, satType).getPseudorange(0);

        if (range == 0)
            return null;

        EphGps eph = findEph(unixTime, satID, satType);

        if (eph == null) {
            Log.e(TAG, "getSatPositionAndVelocities: Ephemeris failed to load...");
            return null;
        }

        if (eph.equals(EphGps.UnhealthyEph))
            return SatellitePosition.UnhealthySat;

        //			char satType = eph.getSatType();

        SatellitePosition sp = computeSatPositionAndVelocities(unixTime, range, satID, satType, eph, receiverClockError);
        //			SatellitePosition sp = computePositionGps(unixTime, satType, satID, eph, range, receiverClockError);
        //if(receiverPosition!=null) earthRotationCorrection(receiverPosition, sp);
        return sp;// new SatellitePosition(eph, unixTime, satID, range);

    }


    /* (non-Javadoc)
     * @see org.gogpsproject.NavigationProducer#init()
     */
    @Override
    public void init() {

    }

    /* (non-Javadoc)
     * @see org.gogpsproject.NavigationProducer#release()
     */
    @Override
    public void release(boolean waitForThread, long timeoutMs) throws InterruptedException {

    }
}
